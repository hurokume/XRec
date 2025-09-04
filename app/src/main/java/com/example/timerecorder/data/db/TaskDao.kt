package com.example.timerecorder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // ツリー表示
    @Transaction
    @Query("SELECT * FROM tasks WHERE parentId IS NULL ORDER BY sortOrder, id")
    fun observeParentsWithChildren(): Flow<List<ParentWithChildren>>

    // 子一覧（必要に応じて）
    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun getChildren(parentId: Long): List<TaskEntity>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    // アクティブタスク
    @Query("SELECT * FROM active_task_singleton WHERE singletonId = 0")
    suspend fun getActiveOrNull(): ActiveTaskEntity?

    @Transaction
    @Query("SELECT * FROM active_task_singleton WHERE singletonId = 0")
    fun observeActiveWithTask(): Flow<ActiveWithTask?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActive(active: ActiveTaskEntity)

    @Query("DELETE FROM active_task_singleton")
    suspend fun clearActive()

    // 履歴
    @Insert
    suspend fun insertEvent(event: TaskEventEntity)

    @Query("SELECT * FROM task_events ORDER BY id DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<TaskEventEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    fun observeChildren(parentId: Long): Flow<List<TaskEntity>>

}
