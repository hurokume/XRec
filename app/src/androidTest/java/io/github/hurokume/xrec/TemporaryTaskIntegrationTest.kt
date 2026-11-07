package io.github.hurokume.xrec

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.RestoreWorkResult
import io.github.hurokume.xrec.data.TaskRepository
import io.github.hurokume.xrec.data.WorkHistoryAction
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.TaskEntity
import io.github.hurokume.xrec.data.db.WorkHistoryEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporaryTaskIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = TaskRepository(db, db.sessionDao(), db.taskDao(), context::getString)
    }

    @After
    fun close() = db.close()

    @Test
    fun repeatedPressesKeepOneTaskAndEveryShortIntervalIncludingTheReturn() = runBlocking {
        val normal = db.taskDao().insertTask(TaskEntity(name = "Work"))
        repo.startOrSwitch(normal, 1_000)
        repo.startTemporaryTask(2_000)
        val temporary = db.sessionDao().getActive()!!.taskId
        repo.startTemporaryTask(2_100)
        repo.startTemporaryTask(2_200)
        repo.startOrSwitch(normal, 2_300)

        assertEquals(1, db.taskDao().getAllTasksOnce().count { it.isTemporary })
        val events =
            db.taskDao().getAllEventsForCsv().map { db.taskDao().getEventById(it.eventId)!! }
                .sortedBy { it.pressedAtEpochMs }
        assertEquals(listOf(normal, temporary, temporary, temporary), events.map { it.taskId })
        assertEquals(listOf(1_000L, 2_000L, 2_100L, 2_200L), events.map { it.pressedAtEpochMs })
        assertEquals(listOf(1_000L, 100L, 100L, 100L), events.map { it.durationMillis })
        assertEquals(2_300L, db.sessionDao().getActive()!!.startedAt)
        assertEquals(2_300L, db.taskDao().getActiveOrNull()!!.startedAtEpochMs)
    }

    @Test
    fun coldStartAndConcurrentPressesDoNotDuplicateTheTemporaryTaskOrLoseRecords() = runBlocking {
        coroutineScope {
            (0 until 5).map { async { repo.startTemporaryTask(10_000) } }.awaitAll()
        }
        assertEquals(1, db.taskDao().getAllTasksOnce().count { it.isTemporary })
        assertEquals(4, db.taskDao().getAllEventsForCsv().size)
        assertEquals(10_000L, db.sessionDao().getActive()!!.startedAt)
        assertEquals(5, repo.observeWorkHistory().first().size)
        assertTrue(
            repo.observeWorkHistory().first()
                .all { it.action == WorkHistoryAction.StartTemporary.name })
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM task_sessions WHERE endedAt IS NULL")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
    }

    @Test
    fun renamingAndReopeningRepositoryKeepTheSameTemporaryTask() = runBlocking {
        // A normal user-created task with the same label must not be repurposed.
        db.taskDao().insertTask(TaskEntity(name = context.getString(R.string.temporary_task_name)))
        repo.startTemporaryTask(1_000)
        val taskId = db.sessionDao().getActive()!!.taskId
        repo.renameTask(taskId, "Interruptions", emptyMap())
        repo = TaskRepository(db, db.sessionDao(), db.taskDao(), context::getString)
        repo.startTemporaryTask(2_000)
        assertEquals(taskId, db.sessionDao().getActive()!!.taskId)
        assertEquals(2, db.taskDao().getAllTasksOnce().size)
        assertEquals("Interruptions", db.taskDao().getTaskById(taskId)!!.name)
    }

    @Test
    fun eachPressCanBeUndoneAtomicallyWithItsTaskAndSession() = runBlocking {
        repo.startTemporaryTask(1_000)
        val firstChange = repo.observeWorkHistory().first().single().id
        repo.startTemporaryTask(2_000)
        val secondChange = repo.observeWorkHistory().first().first().id
        assertEquals(
            RestoreWorkResult.Restored,
            repo.restoreWorkHistory(secondChange, secondChange)
        )
        assertEquals(1_000L, db.sessionDao().getActive()!!.startedAt)
        assertTrue(db.taskDao().getAllEventsForCsv().isEmpty())
        assertTrue(db.taskDao().getAllTasksOnce().single().isTemporary)
        val latest = repo.observeWorkHistory().first().first().id
        assertEquals(RestoreWorkResult.Restored, repo.restoreWorkHistory(firstChange, latest))
        assertTrue(db.taskDao().getAllTasksOnce().isEmpty())
        assertNull(db.sessionDao().getActive())
        assertNull(db.taskDao().getActiveOrNull())
    }

    @Test
    fun oldWorkHistoryWithoutTemporaryColumnCanStillBeRestored() = runBlocking {
        db.taskDao().insertTask(TaskEntity(id = 7, name = "New name"))
        db.workHistoryDao().insert(
            WorkHistoryEntity(
                createdAt = 1,
                action = WorkHistoryAction.RenameTask.name,
                details = "",
                taskCount = 1,
                recordCount = 0,
                changesJson = """{"version":1,"changes":[{"table":"tasks","id":7,"before":[7,"Old name",null,null,0,0],"after":[7,"New name",null,null,0,0]}]}"""
            )
        )
        val change = repo.observeWorkHistory().first().single().id
        assertEquals(RestoreWorkResult.Restored, repo.restoreWorkHistory(change, change))
        assertEquals("Old name", db.taskDao().getTaskById(7)!!.name)
        assertFalse(db.taskDao().getTaskById(7)!!.isTemporary)
    }

    @Test
    fun temporaryMarkerSurvivesTaskExportAndImport() = runBlocking {
        repo.startTemporaryTask(1_000)
        repo.stopActive(2_000)
        val json = repo.exportTasksJson()
        repo.importTasksJson(json, replaceAll = true)
        val imported = db.taskDao().getAllTasksOnce().single()
        assertTrue(imported.isTemporary)
        repo.startTemporaryTask(3_000)
        assertEquals(imported.id, db.sessionDao().getActive()!!.taskId)
    }
}
