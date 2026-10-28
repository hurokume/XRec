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
        WorkHistoryEntity::class,
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun workHistoryDao(): WorkHistoryDao

    companion object {
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS work_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        details TEXT NOT NULL,
                        taskCount INTEGER NOT NULL,
                        recordCount INTEGER NOT NULL,
                        changesJson TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

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

        // 2→3: 履歴を保持したままタスク一覧から非表示にできるようにする。
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context, scope: CoroutineScope): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "timerecorder.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        // 既存の prepopulate(...) はそのまま（過去の初期投入内容を復元）
        internal suspend fun prepopulate(dao: TaskDao) {
            // すでに何か入っていれば何もしない（重複生成回避）
            // parentId IS NULL のタスク（親タスク）が 1 つでもあればスキップ
            val hasAnyParent =
                try {
                    dao.getChildrenOnce(parentId = null).isNotEmpty()
                } catch (_: Throwable) {
                    false
                }
            if (hasAnyParent) return

            // デフォルトの親タスク「Project」と、その配下に子タスク「Task」を 1 つ作成
            // （以前の初期データと同等：UI のプレースホルダ JSON と揃えています）
            val projectId = dao.insertTask(
                TaskEntity(
                    name = "Project",
                    parentId = null,
                    colorInt = 0xFF90CAF9.toInt(), // 任意の初期色（過去の色味に近い水色）
                    sortOrder = 0
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "Task",
                    parentId = projectId,
                    colorInt = null,
                    sortOrder = 0
                )
            )
        }
    }
}
