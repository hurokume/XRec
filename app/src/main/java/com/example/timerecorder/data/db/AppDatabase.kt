package com.example.timerecorder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [TaskEntity::class, ActiveTaskEntity::class, TaskEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context, scope: CoroutineScope): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "timerecorder.db")
                    .fallbackToDestructiveMigration()
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

        /** 指定の初期タスク構成を投入 */
        internal suspend fun prepopulate(dao: TaskDao) {
            // 親カテゴリ（色は仮）
            val wasteId = dao.insertTask(TaskEntity(name = "WASTE", colorInt = 0xFFEF5350.toInt()))
            val breakId = dao.insertTask(TaskEntity(name = "Break", colorInt = 0xFF42A5F5.toInt()))
            val choreId = dao.insertTask(TaskEntity(name = "Chore", colorInt = 0xFF66BB6A.toInt()))
            val hwId = dao.insertTask(TaskEntity(name = "HW", colorInt = 0xFFAB47BC.toInt()))

            // WASTE 子
            val chatId = dao.insertTask(
                TaskEntity(
                    name = "雑談",
                    parentId = wasteId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "晩酌",
                    parentId = wasteId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "ゲーム",
                    parentId = wasteId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )
            val mediaId = dao.insertTask(
                TaskEntity(
                    name = "メディア",
                    parentId = wasteId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )
            // 雑談の孫
            dao.insertTask(TaskEntity(name = "A", parentId = chatId, colorInt = 0xFFEF5350.toInt()))
            dao.insertTask(TaskEntity(name = "B", parentId = chatId, colorInt = 0xFFEF5350.toInt()))
            dao.insertTask(TaskEntity(name = "C", parentId = chatId, colorInt = 0xFFEF5350.toInt()))
            // メディアの孫
            dao.insertTask(
                TaskEntity(
                    name = "SNS",
                    parentId = mediaId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "Comic",
                    parentId = mediaId,
                    colorInt = 0xFFEF5350.toInt()
                )
            )

            // Break
            dao.insertTask(
                TaskEntity(
                    name = "休憩",
                    parentId = breakId,
                    colorInt = 0xFF42A5F5.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "食事",
                    parentId = breakId,
                    colorInt = 0xFF42A5F5.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "待機時間",
                    parentId = breakId,
                    colorInt = 0xFF42A5F5.toInt()
                )
            )

            // Chore
            dao.insertTask(
                TaskEntity(
                    name = "病院",
                    parentId = choreId,
                    colorInt = 0xFF66BB6A.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "コミュニケーション",
                    parentId = choreId,
                    colorInt = 0xFF66BB6A.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "移動時間",
                    parentId = choreId,
                    colorInt = 0xFF66BB6A.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "身支度",
                    parentId = choreId,
                    colorInt = 0xFF66BB6A.toInt()
                )
            )

            // HW
            dao.insertTask(
                TaskEntity(
                    name = "整理整頓",
                    parentId = hwId,
                    colorInt = 0xFFAB47BC.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "料理",
                    parentId = hwId,
                    colorInt = 0xFFAB47BC.toInt()
                )
            )
            dao.insertTask(
                TaskEntity(
                    name = "事務作業",
                    parentId = hwId,
                    colorInt = 0xFFAB47BC.toInt()
                )
            )
        }
    }
}
