package com.example.timerecorder.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * タスクの実行セッションを表すエンティティ。
 * - startedAt / endedAt は EpochMillis（System.currentTimeMillis()）
 * - endedAt = null の行が「実行中」を表す
 */
@Entity(
    tableName = "task_sessions",
    indices = [
        Index("taskId"),
        Index("endedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val startedAt: Long,
    val endedAt: Long? = null
)
