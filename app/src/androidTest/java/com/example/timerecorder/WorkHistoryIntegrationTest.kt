package com.example.timerecorder

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timerecorder.data.RestoreWorkResult
import com.example.timerecorder.data.TaskDeletionMode
import com.example.timerecorder.data.TaskNameConflict
import com.example.timerecorder.data.TaskNameDecision
import com.example.timerecorder.data.TaskRepository
import com.example.timerecorder.data.WorkHistoryAction
import com.example.timerecorder.data.db.AppDatabase
import com.example.timerecorder.data.db.TaskEntity
import com.example.timerecorder.data.db.TaskEventEntity
import com.example.timerecorder.data.db.WorkHistoryEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkHistoryIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = TaskRepository(db, db.sessionDao(), db.taskDao())
    }

    @After fun close() { db.close() }

    private suspend fun entries() = repo.observeWorkHistory().first()
    private suspend fun restore(id: Long) = repo.restoreWorkHistory(id, entries().first().id)

    @Test fun keepsExactlyLatest100AndRestoresOldestRetainedChange() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Initial"))
        repeat(105) { repo.renameTask(task, "Name $it", emptyMap()) }
        val history = entries()
        assertEquals(100, history.size)
        assertEquals(105L, history.first().id)
        assertEquals(6L, history.last().id)
        assertEquals(RestoreWorkResult.Restored, restore(history.last().id))
        assertEquals("Name 4", db.taskDao().getTaskById(task)!!.name)
        assertEquals(100, entries().size)
        assertEquals(WorkHistoryAction.Restore.name, entries().first().action)
        assertEquals(RestoreWorkResult.Restored, restore(entries().first().id))
        assertEquals("Name 104", db.taskDao().getTaskById(task)!!.name)
    }

    @Test fun overlappingRecordChangesUndoTogetherAndRestorationCanBeUndone() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Task"))
        val original = TaskEventEntity(id = 1, taskId = task, pressedAtEpochMs = 100, durationMillis = 900)
        db.taskDao().insertEvent(original)
        repo.addManualRecord(task, 400, 600)
        assertEquals(3, db.taskDao().getAllEventsForCsv().size)
        assertEquals(1, entries().size)
        val added = entries().first().id
        repo.renameTask(task, "Renamed", emptyMap())
        assertEquals(RestoreWorkResult.Restored, restore(added))
        assertEquals(listOf(original.id), db.taskDao().getAllEventsForCsv().map { it.eventId })
        assertEquals(original, db.taskDao().getEventById(original.id))
        assertEquals("Task", db.taskDao().getTaskById(task)!!.name)
        assertEquals(RestoreWorkResult.Restored, restore(entries().first().id))
        assertEquals("Renamed", db.taskDao().getTaskById(task)!!.name)
        assertEquals(3, db.taskDao().getAllEventsForCsv().size)
        // Older entries remain valid even after undoing a restoration.
        assertEquals(RestoreWorkResult.Restored, restore(added))
        assertEquals(original, db.taskDao().getEventById(original.id))
        assertEquals("Task", db.taskDao().getTaskById(task)!!.name)
    }

    @Test fun restoresMergedTasksSessionsRecordsAndActiveStartWithoutCascadingUnchangedRecords() = runBlocking {
        val a = db.taskDao().insertTask(TaskEntity(name = "A"))
        val b = db.taskDao().insertTask(TaskEntity(name = "B"))
        val child = db.taskDao().insertTask(TaskEntity(name = "Child", parentId = a))
        val event = db.taskDao().insertEvent(TaskEventEntity(taskId = a, pressedAtEpochMs = 100, durationMillis = 500))
        val unchanged = db.taskDao().insertEvent(TaskEventEntity(taskId = b, pressedAtEpochMs = 700, durationMillis = 200))
        repo.startOrSwitch(a, 1_000)
        repo.renameTask(a, "B", mapOf("*" to TaskNameDecision(true)))
        assertNull(db.taskDao().getTaskById(a))
        assertEquals(RestoreWorkResult.Restored, restore(entries().first().id))
        assertEquals("A", db.taskDao().getTaskById(a)!!.name)
        assertEquals(a, db.taskDao().getTaskById(child)!!.parentId)
        assertEquals(a, db.taskDao().getEventById(event)!!.taskId)
        assertEquals(b, db.taskDao().getEventById(unchanged)!!.taskId)
        assertEquals(a, db.sessionDao().getActive()!!.taskId)
        assertEquals(a, db.taskDao().getActiveOrNull()!!.taskId)
        assertEquals(1_000L, db.sessionDao().getActive()!!.startedAt)
        assertEquals(1_000L, db.taskDao().getActiveOrNull()!!.startedAtEpochMs)
    }

    @Test fun importIsOneChangeAndFailuresAndNoOpsDoNotConsumeHistory() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Existing"))
        repo.renameTask(task, "Existing", emptyMap())
        repo.stopActive(5_000)
        assertTrue(entries().isEmpty())
        try {
            repo.importTasksJson("""[{"name":"New"},{"name":"Existing"}]""", false)
            fail("Expected name conflict")
        } catch (_: TaskNameConflict) { }
        assertEquals(1, db.taskDao().getAllTasksOnce().size)
        assertTrue(entries().isEmpty())
        repo.importEventsTsv("event_name\ttask_name\tsubtask_name\tstart\tend\tduration\nNew\tChild\t\t100\t200\t100\nNew\tChild\t\t300\t400\t100")
        assertEquals(1, entries().size)
        assertEquals(WorkHistoryAction.ImportRecords.name, entries().single().action)
        assertEquals(RestoreWorkResult.Restored, restore(entries().single().id))
        assertEquals(listOf(task), db.taskDao().getAllTasksOnce().map { it.id })
        assertTrue(db.taskDao().getAllEventsForCsv().isEmpty())
    }

    @Test fun deletionAndTimerStopRestoreConsistently() = runBlocking {
        val parent = db.taskDao().insertTask(TaskEntity(name = "Parent"))
        val child = db.taskDao().insertTask(TaskEntity(name = "Child", parentId = parent))
        repo.startOrSwitch(child, 1_000)
        repo.deleteTask(child, TaskDeletionMode.KeepHistory, nowMs = 5_000)
        assertTrue(db.taskDao().getTaskById(child)!!.isArchived)
        assertNull(db.sessionDao().getActive())
        assertEquals(RestoreWorkResult.Restored, restore(entries().first().id))
        assertFalse(db.taskDao().getTaskById(child)!!.isArchived)
        assertEquals(child, db.sessionDao().getActive()!!.taskId)
        assertTrue(db.taskDao().getAllEventsForCsv().isEmpty())
        repo.stopActive(8_000)
        assertEquals(RestoreWorkResult.Restored, restore(entries().first().id))
        assertEquals(1_000L, db.sessionDao().getActive()!!.startedAt)
        assertEquals(1_000L, db.taskDao().getActiveOrNull()!!.startedAtEpochMs)
    }

    @Test fun concurrentOperationsAreSeparateAndStaleConfirmationDoesNotRestore() = runBlocking {
        coroutineScope {
            listOf(async { repo.addTask("A", null) }, async { repo.addTask("B", null) }).forEach { it.await() }
        }
        assertEquals(2, entries().size)
        val selected = entries().first().id
        repo.addTask("C", null)
        assertEquals(RestoreWorkResult.HistoryChanged, repo.restoreWorkHistory(selected, selected))
        assertEquals(RestoreWorkResult.Missing, repo.restoreWorkHistory(-1, entries().first().id))
        assertEquals(3, db.taskDao().getAllTasksOnce().size)
        assertEquals(3, entries().size)
    }

    @Test fun failedRestorationRollsBackRowsAlreadyRestoredInTheSameTransaction() = runBlocking {
        val task = db.taskDao().insertTask(TaskEntity(name = "Initial"))
        db.workHistoryDao().insert(WorkHistoryEntity(
            createdAt = 1, action = WorkHistoryAction.RenameTask.name, details = "", taskCount = 0,
            recordCount = 0, changesJson = "invalid payload"
        ))
        val brokenEntry = entries().single().id
        repo.renameTask(task, "Latest", emptyMap())
        try {
            restore(brokenEntry)
            fail("Expected invalid history to reject restoration")
        } catch (_: org.json.JSONException) { }
        assertEquals("Latest", db.taskDao().getTaskById(task)!!.name)
        assertEquals(2, entries().size)
    }

    @Test fun historySurvivesDatabaseReopen() = runBlocking {
        val name = "work-history-test-${System.nanoTime()}.db"
        db.close()
        try {
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repo = TaskRepository(db, db.sessionDao(), db.taskDao())
            repo.addTask("Persistent", null)
            val entry = entries().single().id
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repo = TaskRepository(db, db.sessionDao(), db.taskDao())
            assertEquals(entry, entries().single().id)
            assertEquals(RestoreWorkResult.Restored, restore(entry))
            assertTrue(db.taskDao().getAllTasksOnce().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationFromVersion3PreservesExistingDataAndStartsWithEmptyWorkHistory() = runBlocking {
        val name = "work-history-migration-${System.nanoTime()}.db"
        db.close()
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, parentId INTEGER, colorInt INTEGER, sortOrder INTEGER NOT NULL, isArchived INTEGER NOT NULL DEFAULT 0)")
                old.execSQL("CREATE INDEX index_tasks_parentId ON tasks(parentId)")
                old.execSQL("CREATE TABLE active_task_singleton (singletonId INTEGER NOT NULL PRIMARY KEY, taskId INTEGER NOT NULL, startedAtEpochMs INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE task_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, pressedAtEpochMs INTEGER NOT NULL, durationMillis INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_task_events_taskId ON task_events(taskId)")
                old.execSQL("CREATE TABLE task_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_task_sessions_taskId ON task_sessions(taskId)")
                old.execSQL("CREATE INDEX index_task_sessions_endedAt ON task_sessions(endedAt)")
                old.execSQL("INSERT INTO tasks VALUES (7, 'Existing', NULL, NULL, 0, 0)")
                old.execSQL("INSERT INTO task_events VALUES (12, 7, 1000, 500)")
                old.execSQL("INSERT INTO task_sessions VALUES (3, 7, 2000, NULL)")
                old.execSQL("INSERT INTO active_task_singleton VALUES (0, 7, 2000)")
                old.version = 3
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_3_4).build()
            repo = TaskRepository(db, db.sessionDao(), db.taskDao())
            assertEquals("Existing", db.taskDao().getTaskById(7)!!.name)
            assertEquals(500L, db.taskDao().getEventById(12)!!.durationMillis)
            assertEquals(2_000L, db.sessionDao().getActive()!!.startedAt)
            assertTrue(entries().isEmpty())
            repo.stopActive(3_000)
            assertEquals(RestoreWorkResult.Restored, restore(entries().single().id))
            assertEquals(2_000L, db.taskDao().getActiveOrNull()!!.startedAtEpochMs)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
