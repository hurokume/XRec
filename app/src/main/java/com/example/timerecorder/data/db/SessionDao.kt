// SessionDao.kt
package com.example.timerecorder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ActiveWithTaskDb(
    val sessionId: Long,
    val taskId: Long,
    val taskName: String,
    val colorInt: Int?,
    val startedAt: Long
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(e: TaskSessionEntity): Long

    @Query("SELECT * FROM task_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActive(): TaskSessionEntity?

    @Query(
        """
        SELECT s.id AS sessionId, s.taskId, t.name AS taskName, t.colorInt, s.startedAt
        FROM task_sessions s
        INNER JOIN tasks t ON t.id = s.taskId
        WHERE s.endedAt IS NULL
        LIMIT 1
    """
    )
    fun observeActiveWithTask(): Flow<ActiveWithTaskDb?>

    @Query("UPDATE task_sessions SET endedAt = :now WHERE endedAt IS NULL")
    suspend fun stopAll(now: Long)

    @Transaction
    suspend fun start(taskId: Long, now: Long = System.currentTimeMillis()) {
        val running = getActive()
        if (running != null) {
            if (running.taskId == taskId) {
                // すでに同じタスクが走っていれば何もしない
                return
            } else {
                stopAll(now)
            }
        }
        insert(TaskSessionEntity(taskId = taskId, startedAt = now))
    }
}
