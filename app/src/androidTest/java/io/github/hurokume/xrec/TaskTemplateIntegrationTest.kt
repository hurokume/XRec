package io.github.hurokume.xrec

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.RestoreWorkResult
import io.github.hurokume.xrec.data.TaskRepository
import io.github.hurokume.xrec.data.TaskTemplateType
import io.github.hurokume.xrec.data.TemplateApplyMode
import io.github.hurokume.xrec.data.WorkHistoryAction
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TaskTemplateIntegrationTest(
    private val templateType: TaskTemplateType,
    private val expectedEntryCount: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun templates() = listOf(
            arrayOf<Any>(TaskTemplateType.SecondaryStudents, 30),
            arrayOf<Any>(TaskTemplateType.Undergraduates, 31),
            arrayOf<Any>(TaskTemplateType.LawStudents, 31),
            arrayOf<Any>(TaskTemplateType.WorkingAdults, 29),
            arrayOf<Any>(TaskTemplateType.Homemakers, 30)
        )
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val template get() = templateType.projects { context.getString(it) }
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = TaskRepository(db, db.sessionDao(), db.taskDao())
    }

    @After
    fun close() = db.close()

    private suspend fun seedExistingData(): Long {
        val parent = db.taskDao().insertTask(TaskEntity(name = template.first().name, colorInt = 123))
        val child = db.taskDao().insertTask(TaskEntity(name = "Existing child", parentId = parent))
        val archived = db.taskDao().insertTask(TaskEntity(name = "Archived", isArchived = true))
        db.taskDao().insertTask(TaskEntity(name = "Hidden child", parentId = archived, isArchived = true))
        repo.addManualRecord(archived, 100, 500)
        repo.startOrSwitch(child, 1_000)
        return child
    }

    @Test
    fun addingPreservesExistingDataAndCreatesSeparateProjectsOnRepeatedUse() = runBlocking {
        seedExistingData()
        val oldTasks = db.taskDao().getAllTasksOnce()
        val oldRecords = db.taskDao().getAllEventsForCsv()
        val oldActive = db.taskDao().getActiveOrNull()
        val oldSession = db.sessionDao().getActive()
        val historyCount = repo.observeWorkHistory().first().size

        repo.applyTaskTemplate(template, TemplateApplyMode.Add)
        repo.applyTaskTemplate(template, TemplateApplyMode.Add)

        oldTasks.forEach { assertEquals(it, db.taskDao().getTaskById(it.id)) }
        assertEquals(oldRecords, db.taskDao().getAllEventsForCsv())
        assertEquals(oldActive, db.taskDao().getActiveOrNull())
        assertEquals(oldSession, db.sessionDao().getActive())
        val roots = db.taskDao().getChildrenOnce(null)
        assertTrue(roots.any { it.name == "${template.first().name} (2)" })
        assertTrue(roots.any { it.name == "${template.first().name} (3)" })
        val added = roots.single { it.name == "${template.first().name} (2)" }
        assertEquals(template.first().tasks, db.taskDao().getChildren(added.id).map { it.name })
        assertEquals(oldTasks.size + expectedEntryCount * 2, db.taskDao().getAllTasksOnce().size)
        val history = repo.observeWorkHistory().first()
        assertEquals(historyCount + 2, history.size)
        assertEquals(WorkHistoryAction.ApplyTemplate.name, history.first().action)
    }

    @Test
    fun replacingRemovesArchivedTasksRecordsAndTimersAndCanBeRestored() = runBlocking {
        seedExistingData()
        val oldTasks = db.taskDao().getAllTasksOnce()
        val oldRecords = db.taskDao().getAllEventsForCsv()
        val oldActive = db.taskDao().getActiveOrNull()
        val oldSession = db.sessionDao().getActive()

        repo.applyTaskTemplate(template, TemplateApplyMode.ReplaceAll)

        oldTasks.forEach { assertNull(db.taskDao().getTaskById(it.id)) }
        assertTrue(db.taskDao().getAllEventsForCsv().isEmpty())
        assertNull(db.taskDao().getActiveOrNull())
        assertNull(db.sessionDao().getActive())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM task_sessions").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        val roots = db.taskDao().getChildrenOnce(null)
        assertEquals(template.map { it.name }, roots.map { it.name })
        assertEquals(expectedEntryCount, db.taskDao().getAllTasksOnce().size)
        template.zip(roots).forEach { (project, root) ->
            assertEquals(project.tasks, db.taskDao().getChildren(root.id).map { it.name })
        }
        val entry = repo.observeWorkHistory().first().first()
        assertEquals(WorkHistoryAction.ApplyTemplate.name, entry.action)
        assertEquals(RestoreWorkResult.Restored, repo.restoreWorkHistory(entry.id, entry.id))
        assertEquals(oldTasks, db.taskDao().getAllTasksOnce())
        assertEquals(oldRecords, db.taskDao().getAllEventsForCsv())
        assertEquals(oldActive, db.taskDao().getActiveOrNull())
        assertEquals(oldSession, db.sessionDao().getActive())
    }

    @Test
    fun failureWhileCreatingChildrenRollsBackDeletionAndPartialTemplate() = runBlocking {
        seedExistingData()
        val oldTasks = db.taskDao().getAllTasksOnce()
        val oldRecords = db.taskDao().getAllEventsForCsv()
        val oldActive = db.taskDao().getActiveOrNull()
        val oldSession = db.sessionDao().getActive()
        val oldHistory = repo.observeWorkHistory().first()
        db.openHelper.writableDatabase.execSQL(
            """CREATE TEMP TRIGGER fail_template_child BEFORE INSERT ON tasks
               WHEN NEW.parentId IS NOT NULL
               BEGIN SELECT RAISE(ABORT, 'Simulated template write failure'); END"""
        )
        try {
            repo.applyTaskTemplate(template, TemplateApplyMode.ReplaceAll)
            fail("Expected the template write to fail")
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        assertEquals(oldTasks, db.taskDao().getAllTasksOnce())
        assertEquals(oldRecords, db.taskDao().getAllEventsForCsv())
        assertEquals(oldActive, db.taskDao().getActiveOrNull())
        assertEquals(oldSession, db.sessionDao().getActive())
        assertEquals(oldHistory, repo.observeWorkHistory().first())
    }
}
