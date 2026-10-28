package com.example.timerecorder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * タスク実行セッション（task_sessions）用の DAO。
 */
@Dao
interface SessionDao {
    @Query("UPDATE task_sessions SET taskId = :taskId WHERE id = :sessionId AND endedAt IS NULL")
    suspend fun changeActiveTask(sessionId: Long, taskId: Long): Int

    @Query("SELECT * FROM task_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActive(): TaskSessionEntity?

    @Query("SELECT * FROM task_sessions WHERE endedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<TaskSessionEntity?>

    @Query(
        """
        SELECT * FROM task_sessions
        WHERE endedAt IS NOT NULL AND endedAt <= :startExclusive
        ORDER BY endedAt DESC
        LIMIT 1
    """
    )
    suspend fun getLastFinishedBefore(startExclusive: Long): TaskSessionEntity?

    @Query("UPDATE task_sessions SET endedAt = :newEnd WHERE id = :sessionId")
    suspend fun updateEnd(sessionId: Long, newEnd: Long): Int

    @Query("DELETE FROM task_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long): Int

    @Query("UPDATE task_sessions SET taskId = :newTaskId WHERE taskId = :oldTaskId")
    suspend fun replaceTask(oldTaskId: Long, newTaskId: Long): Int

    @Insert
    suspend fun insert(entity: TaskSessionEntity): Long
}
