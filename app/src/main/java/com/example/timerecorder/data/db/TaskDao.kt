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

    /** 追加: 1回だけ子タスク一覧を取得（FlowでなくList） */
    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun getChildrenOnce(parentId: Long?): List<TaskEntity>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET colorInt = :colorInt WHERE id = :id")
    suspend fun updateTaskColor(id: Long, colorInt: Int)

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

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks WHERE parentId IS :parentId")
    suspend fun getMaxSortOrder(parentId: Long?): Int

    @Query("UPDATE tasks SET name = :name WHERE id = :id")
    suspend fun updateTaskName(id: Long, name: String)

    @Transaction
    suspend fun updateSortOrdersForParent(parentId: Long, idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id ->
            updateSortOrder(id, index)
        }
    }

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :taskId")
    suspend fun updateSortOrder(taskId: Long, sortOrder: Int)

    @Query("SELECT COUNT(*) FROM tasks WHERE parentId IS :parentId AND name = :name")
    suspend fun countByNameInParent(parentId: Long?, name: String): Int

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
        WHERE (e.pressedAtEpochMs + MAX(e.durationMillis, 0)) > :startMs
          AND e.pressedAtEpochMs < :endMs
        ORDER BY e.pressedAtEpochMs ASC
        """
    )
    suspend fun getEventsInRange(startMs: Long, endMs: Long): List<HistoryRowDb>

    @Query("DELETE FROM task_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)

    @Query("UPDATE task_events SET pressedAtEpochMs = :newStart, durationMillis = :newDuration WHERE id = :eventId")
    suspend fun updateEventTime(eventId: Long, newStart: Long, newDuration: Long)

    @Query("SELECT * FROM task_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): TaskEventEntity?

    @Query("UPDATE task_events SET taskId = :newTaskId WHERE id = :eventId")
    suspend fun updateEventTask(eventId: Long, newTaskId: Long)
}
