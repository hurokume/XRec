package com.example.timerecorder

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.*
import com.example.timerecorder.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TaskEditingIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = TaskRepository(db, db.sessionDao(), db.taskDao())
    }
    @After fun close() { db.close() }

    @Test fun tsvExportSupportsEveryTaskLevelIncludingRootRecords() = runBlocking {
        val project = repo.addTask("Project", null)
        val task = repo.addTask("Task", project)
        val subtask = repo.addTask("Subtask", task)
        for (id in listOf(project, task, subtask)) {
            db.taskDao().insertEvent(TaskEventEntity(
                taskId = id, pressedAtEpochMs = 1_000L, durationMillis = 3_000L))
        }
        val expectedNames = listOf(
            listOf("Project", null, null),
            listOf("Project", "Task", null),
            listOf("Project", "Task", "Subtask")
        )
        val ranged = db.taskDao().getEventsForTsvInRange(2_000L, 3_000L)
        assertEquals(expectedNames, ranged.map { listOf(it.eventName, it.taskName, it.subtaskName) })
        val all = db.taskDao().getAllEventsForTsv()
        assertEquals(expectedNames, all.map { listOf(it.eventName, it.taskName, it.subtaskName) })
        val exported = repo.exportEventsTsv(2_000L, 3_000L).trimEnd('\n').lines()
        assertEquals(4, exported.size)
        assertEquals(listOf(
            listOf("Project", "", ""), listOf("Project", "Task", ""),
            listOf("Project", "Task", "Subtask")
        ), exported.drop(1).map { it.split('\t').take(3) })
        exported.drop(1).forEach {
            assertEquals(listOf("2000", "3000", "1000"), it.split('\t').subList(3, 6))
        }
    }

    @Test fun runningReclassificationPreservesStartAndRejectsStaleEditor() = runBlocking {
        val a = repo.addTask("A", null); val b = repo.addTask("B", null)
        repo.startOrSwitch(a, 1_000L)
        assertTrue(repo.changeRunningTask(a, 1_000L, b))
        assertEquals(1_000L, db.sessionDao().getActive()!!.startedAt)
        assertEquals(b, db.sessionDao().getActive()!!.taskId)
        assertEquals(b, db.taskDao().getActiveOrNull()!!.taskId)
        assertFalse(repo.changeRunningTask(a, 1_000L, a))
        repo.stopActive(10_000L)
        val record = db.taskDao().getAllEventsForCsv().single()
        assertEquals(b, record.taskId); assertEquals(9_000L, record.durationMillis)
    }

    @Test fun recursiveMergeRollsBackUntilEveryConflictIsResolved() = runBlocking {
        val a = repo.addTask("A", null); val b = repo.addTask("B", null)
        val aChild = repo.addTask("Child", a); val bChild = repo.addTask("Child", b)
        val event = db.taskDao().insertEvent(TaskEventEntity(taskId = aChild, pressedAtEpochMs = 10L, durationMillis = 100L))
        repo.startOrSwitch(aChild, 1_000L)
        val first = try { repo.renameTask(a, "B", emptyMap()); error("Expected conflict") } catch (c: TaskNameConflict) { c }
        val decisions = mapOf(first.key to TaskNameDecision(true))
        try { repo.renameTask(a, "B", decisions); fail("Expected subtask conflict") } catch (_: TaskNameConflict) { }
        assertNotNull(db.taskDao().getTaskById(a))
        assertEquals(aChild, db.taskDao().getEventById(event)!!.taskId)
        val mapping = repo.renameTask(a, "B", mapOf("*" to TaskNameDecision(true)))
        assertEquals(b, mapping[a]); assertEquals(bChild, mapping[aChild])
        assertEquals(bChild, db.taskDao().getEventById(event)!!.taskId)
        assertEquals(bChild, db.sessionDao().getActive()!!.taskId)
        assertEquals(1_000L, db.sessionDao().getActive()!!.startedAt)
        assertNull(db.taskDao().getTaskById(aChild))
    }

    @Test fun importedSeparatePathsReuseTaskAcrossTsvRows() = runBlocking {
        val p = repo.addTask("Project", null); val t = repo.addTask("Task", p)
        repo.addTask("Sub", t)
        val tsv = "event_name\ttask_name\tsubtask_name\tstart\tend\tduration\nProject\tTask\tSub\t1000\t2000\t1000\nProject\tTask\tSub\t3000\t4000\t1000"
        assertEquals(2, repo.importEventsTsv(tsv, decisions = mapOf("*" to TaskNameDecision(false))))
        val events = db.taskDao().getAllEventsForCsv()
        assertEquals(1, events.map { it.taskId }.distinct().size)
        assertEquals(2, db.taskDao().getChildrenOnce(null).size)
        assertEquals("Project (2)", db.taskDao().getChildrenOnce(null).last().name)
    }

    @Test fun jsonCancelRollsBackAllNewTasksAndSeparateNamesStayUnique() = runBlocking {
        repo.addTask("Existing", null)
        val json = """[{"name":"New"},{"name":"Existing","children":[{"name":"Sub"},{"name":"Sub"}]}]"""
        try { repo.importTasksJson(json, false); fail("Expected conflict") } catch (_: TaskNameConflict) { }
        assertEquals(listOf("Existing"), db.taskDao().getChildrenOnce(null).map { it.name })
        repo.importTasksJson(json, false, mapOf("*" to TaskNameDecision(false)))
        val separate = db.taskDao().getChildrenOnce(null).first { it.name == "Existing (2)" }
        assertEquals(listOf("Sub", "Sub (2)"), db.taskDao().getChildren(separate.id).map { it.name })
    }

    @Test fun crossMidnightRecordKeepsFullBounds() = runBlocking {
        val task = repo.addTask("Night", null)
        val start = 23 * 3_600_000L; val duration = 3 * 3_600_000L
        val id = db.taskDao().insertEvent(TaskEventEntity(taskId = task, pressedAtEpochMs = start, durationMillis = duration))
        val dayTwo = db.taskDao().getEventsInRange(86_400_000L, 172_800_000L).single()
        assertEquals(id, dayTwo.eventId)
        val original = db.taskDao().getEventById(dayTwo.eventId)!!
        assertEquals(start, original.pressedAtEpochMs)
        assertEquals(duration, original.durationMillis)
    }

    @Test fun reportsRenderInJapaneseAndEnglishWithLiteralPercentLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = AdvancedAnalysisEngine.analyze(emptyList(), AnalysisRequest(System.currentTimeMillis(), days = 1))
        for (locale in listOf(java.util.Locale.JAPANESE, java.util.Locale.ENGLISH)) {
            val configuration = android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(configuration)
            val uri = com.example.timerecorder.ui.createAdvancedAnalysisReportPdf(localized, result, 20, 1)
            localized.contentResolver.openInputStream(uri)!!.use { input ->
                val signature = ByteArray(4)
                assertEquals(4, input.read(signature))
                assertEquals("%PDF", String(signature))
            }
        }
    }
}
