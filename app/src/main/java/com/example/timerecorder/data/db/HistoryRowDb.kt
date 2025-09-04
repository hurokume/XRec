package com.example.timerecorder.data.db

import androidx.room.ColumnInfo

/**
 * 履歴1行分の DTO（TaskEventEntity と TaskEntity を JOIN した結果を受け取る）
 */
data class HistoryRowDb(
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "taskId") val taskId: Long,
    @ColumnInfo(name = "taskName") val taskName: String,
    @ColumnInfo(name = "colorInt") val colorInt: Int?,
    @ColumnInfo(name = "pressedAtEpochMs") val pressedAtEpochMs: Long,
    @ColumnInfo(name = "durationMillis") val durationMillis: Long
)
