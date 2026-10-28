package com.example.timerecorder.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "work_history")
data class WorkHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val action: String,
    val details: String,
    val taskCount: Int,
    val recordCount: Int,
    val changesJson: String
)

// Keep potentially large undo payloads out of the list observed by the UI.
data class WorkHistoryItem(
    val id: Long,
    val createdAt: Long,
    val action: String,
    val details: String,
    val taskCount: Int,
    val recordCount: Int
)

@Dao
interface WorkHistoryDao {
    @Query("SELECT id, createdAt, action, details, taskCount, recordCount FROM work_history ORDER BY id DESC LIMIT 100")
    fun observe(): Flow<List<WorkHistoryItem>>

    @Query("SELECT * FROM work_history WHERE id >= :id ORDER BY id DESC")
    suspend fun changesSince(id: Long): List<WorkHistoryEntity>

    @Query("SELECT MAX(id) FROM work_history")
    suspend fun latestId(): Long?

    @Insert
    suspend fun insert(entry: WorkHistoryEntity)

    @Query("DELETE FROM work_history WHERE id NOT IN (SELECT id FROM work_history ORDER BY id DESC LIMIT 100)")
    suspend fun trim()
}
