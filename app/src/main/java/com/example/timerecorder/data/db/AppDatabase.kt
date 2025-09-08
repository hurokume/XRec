package com.example.timerecorder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        TaskEntity::class,
        ActiveTaskEntity::class,
        TaskEventEntity::class,
        TaskSessionEntity::class, // ← 追加済み
    ],
    version = 2,                 // ★ 1 → 2 に上げる
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao

    companion object {
        // 1→2: task_sessions テーブルとインデックスを追加
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_sessions_taskId ON task_sessions(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_sessions_endedAt ON task_sessions(endedAt)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context, scope: CoroutineScope): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "timerecorder.db")
                    .addMigrations(MIGRATION_1_2)         // ★ 追加
                    .fallbackToDestructiveMigration()      // 他バージョンの時は破壊的でもOKにしたい場合は残す
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // 初回だけ初期データ投入
                            scope.launch { prepopulate(INSTANCE!!.taskDao()) }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }

        // 既存の prepopulate(...) はそのまま
        internal suspend fun prepopulate(dao: TaskDao) { /* 省略（あなたの現行実装のまま） */
        }
    }
}
