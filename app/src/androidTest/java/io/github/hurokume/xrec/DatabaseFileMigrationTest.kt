package io.github.hurokume.xrec

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hurokume.xrec.data.db.AppDatabase
import io.github.hurokume.xrec.data.db.DatabaseFileMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class DatabaseFileMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var directory: File
    private lateinit var context: Context
    private val legacyName = "timerecorder.db"

    @Before
    fun setup() {
        directory = File(instrumentation.targetContext.cacheDir, "db-migration-${UUID.randomUUID()}")
        check(directory.mkdirs())
        context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, File(name).name)
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)

            override fun deleteDatabase(name: String): Boolean =
                SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        }
    }

    @After
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun preservesRecordsFromEveryHistoricalSchema() = runBlocking {
        for (version in 1..5) {
            createFixture(context.getDatabasePath(legacyName), version).close()
            assertEquals("xrec.db", DatabaseFileMigration.prepare(context))
            assertFalse(context.getDatabasePath(legacyName).exists())

            withCurrent { database ->
                assertEquals("Existing task", database.taskDao().getTaskById(7)!!.name)
                assertEquals(900L, database.taskDao().getEventById(11)!!.durationMillis)
                assertEquals(2_000L, database.taskDao().getActiveOrNull()!!.startedAtEpochMs)
                if (version >= 2) {
                    assertEquals(2_000L, database.sessionDao().getActive()!!.startedAt)
                }
                if (version >= 4) {
                    assertEquals("preserved undo payload", database.workHistoryDao().changesSince(1).single().changesJson)
                }
                val task = database.taskDao().getTaskById(7)!!
                assertEquals(version >= 3, task.isArchived)
                assertEquals(version >= 5, task.isTemporary)
            }

            // The second launch must continue using the migrated database.
            assertEquals("xrec.db", DatabaseFileMigration.prepare(context))
            withCurrent { database ->
                assertEquals(11L, database.taskDao().getAllEventsForCsv().single().eventId)
            }
            SQLiteDatabase.deleteDatabase(context.getDatabasePath("xrec.db"))
        }
    }

    @Test
    fun includesCommittedRecordsStillInWal() = runBlocking {
        val writer = File(directory, "writer.db")
        createFixture(writer, 5).use { database ->
            assertTrue(database.enableWriteAheadLogging())
            database.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            database.execSQL("UPDATE task_events SET durationMillis=4321 WHERE id=11")
            val wal = File(writer.path + "-wal")
            assertTrue(wal.length() > 0)
            // Snapshot an abruptly stopped writer, without closing/checkpointing it first.
            writer.copyTo(context.getDatabasePath(legacyName))
            wal.copyTo(File(context.getDatabasePath(legacyName).path + "-wal"))
        }
        DatabaseFileMigration.prepare(context)
        withCurrent { database ->
            assertEquals(4321L, database.taskDao().getEventById(11)!!.durationMillis)
            assertEquals("preserved undo payload", database.workHistoryDao().changesSince(1).single().changesJson)
        }
    }

    @Test
    fun existingDestinationTakesPrecedenceOverRestoredLegacyBackup() = runBlocking {
        createFixture(context.getDatabasePath(legacyName), 5).close()
        createFixture(context.getDatabasePath("xrec.db"), 5).use {
            it.execSQL("UPDATE tasks SET name='Newer record' WHERE id=7")
        }
        DatabaseFileMigration.prepare(context)
        assertTrue(context.getDatabasePath(legacyName).exists())
        withCurrent { database ->
            assertEquals("Newer record", database.taskDao().getTaskById(7)!!.name)
        }
    }

    @Test
    fun corruptSourceIsPreservedWithoutCreatingEmptyDestination() {
        val source = context.getDatabasePath(legacyName)
        val original = ByteArray(4096) { 42 }
        source.writeBytes(original)
        try {
            DatabaseFileMigration.prepare(context)
            fail("Expected migration to stop")
        } catch (_: SQLiteException) {
            assertArrayEquals(original, source.readBytes())
            assertFalse(context.getDatabasePath("xrec.db").exists())
        }
    }

    @Test
    fun freshInstallUsesXRecDatabase() {
        assertEquals("xrec.db", DatabaseFileMigration.prepare(context))
        assertFalse(context.getDatabasePath(legacyName).exists())
    }

    @Test
    fun normalEntryPointMigratesOnceWhenOpenedConcurrently() = runBlocking {
        createFixture(context.getDatabasePath(legacyName), 5).close()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val databases = (1..8).map {
            async(Dispatchers.IO) { AppDatabase.get(context, scope) }
        }.awaitAll()
        val database = databases.first()
        try {
            databases.forEach { assertSame(database, it) }
            assertEquals(900L, database.taskDao().getEventById(11)!!.durationMillis)
            assertFalse(context.getDatabasePath(legacyName).exists())
        } finally {
            database.close()
            scope.cancel()
        }
    }

    @Test
    fun previousComponentNamesAndActionsRemainCompatible() {
        val app = instrumentation.targetContext
        val manager = app.packageManager
        val alias = manager.getActivityInfo(ComponentName(app.packageName, "com.example.timerecorder.MainActivity"), 0)
        assertEquals(MainActivity::class.java.name, alias.targetActivity)
        manager.getReceiverInfo(ComponentName(app, com.example.timerecorder.TemporaryTaskWidgetProvider::class.java), 0)
        manager.getReceiverInfo(ComponentName(app, com.example.timerecorder.TaskActionReceiver::class.java), 0)
        manager.getServiceInfo(ComponentName(app, com.example.timerecorder.ActiveTaskService::class.java), 0)
        for (suffix in listOf("NEXT", "SWITCH_TO", "TEMPORARY")) {
            assertEquals("io.github.hurokume.xrec.action.$suffix", ActiveTaskNotificationContract.normalizeAction("com.example.timerecorder.action.$suffix"))
        }
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DatabaseFileMigration.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

    private inline fun withCurrent(block: (AppDatabase) -> Unit) {
        val database = openCurrent()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun createFixture(file: File, version: Int): SQLiteDatabase {
        val schema = instrumentation.context.assets.open("io.github.hurokume.xrec.data.db.AppDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (indexNumber in 0 until indices.length()) {
                database.execSQL(indices.getJSONObject(indexNumber).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) database.execSQL(setup.getString(index))
        database.version = version
        database.execSQL("INSERT INTO tasks(id,name,sortOrder) VALUES(7,'Existing task',0)")
        database.execSQL("INSERT INTO task_events(id,taskId,pressedAtEpochMs,durationMillis) VALUES(11,7,100,900)")
        database.execSQL("INSERT INTO active_task_singleton(singletonId,taskId,startedAtEpochMs) VALUES(0,7,2000)")
        if (version >= 2) database.execSQL("INSERT INTO task_sessions(id,taskId,startedAt,endedAt) VALUES(13,7,2000,NULL)")
        if (version >= 3) database.execSQL("UPDATE tasks SET isArchived=1 WHERE id=7")
        if (version >= 4) database.execSQL("INSERT INTO work_history VALUES(17,3000,'RenameTask','Details',1,1,'preserved undo payload')")
        if (version >= 5) database.execSQL("UPDATE tasks SET isTemporary=1 WHERE id=7")
        return database
    }
}
