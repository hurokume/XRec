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

    @Transaction
    @Query("SELECT * FROM tasks WHERE parentId IS NULL ORDER BY sortOrder, id")
    fun observeParentsWithChildren(): Flow<List<ParentWithChildren>>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun getChildren(parentId: Long): List<TaskEntity>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    // Active
    @Query("SELECT * FROM active_task_singleton WHERE singletonId = 0")
    suspend fun getActiveOrNull(): ActiveTaskEntity?

    @Transaction
    @Query("SELECT * FROM active_task_singleton WHERE singletonId = 0")
    fun observeActiveWithTask(): Flow<ActiveWithTask?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActive(active: ActiveTaskEntity)

    @Query("DELETE FROM active_task_singleton")
    suspend fun clearActive()

    // Events
    @Insert
    suspend fun insertEvent(event: TaskEventEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    fun observeChildren(parentId: Long): Flow<List<TaskEntity>>

    // 履歴ページ（HistoryRowDb がトップレベルにある想定）
    @Query(
        """
        SELECT e.id AS eventId,
               e.taskId AS taskId,
               t.name AS taskName,
               t.colorInt AS colorInt,
               e.pressedAtEpochMs AS pressedAtEpochMs,
               e.durationMillis AS durationMillis
        FROM task_events e
        JOIN tasks t ON t.id = e.taskId
        ORDER BY e.pressedAtEpochMs DESC, e.id DESC
        LIMIT :limit OFFSET :offset
    """
    )
    suspend fun getEventsPage(limit: Int, offset: Int): List<HistoryRowDb>

    @Query("DELETE FROM task_events")
    suspend fun clearAllEvents()
}
