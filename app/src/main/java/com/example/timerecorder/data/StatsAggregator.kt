package com.example.timerecorder.data

data class StatsRecord(
    val taskId: Long,
    val taskName: String,
    val startMs: Long,
    val endMs: Long
)

data class StatsTarget(
    val id: Long,
    val name: String
)

data class StatsTotal(
    val id: Long,
    val name: String,
    val totalMs: Long
)

/** 表示期間へのクランプ、末端タスクのフィルター、集約を一か所で行う。 */
object StatsAggregator {
    fun aggregate(
        records: List<StatsRecord>,
        windowStartMs: Long,
        windowEndMs: Long,
        includedTaskIds: Set<Long>? = null,
        targetFor: (StatsRecord) -> StatsTarget
    ): List<StatsTotal> {
        if (windowEndMs <= windowStartMs) return emptyList()

        val includeAll = includedTaskIds == null
        val totals = linkedMapOf<Long, StatsTotal>()

        records.forEach { record ->
            if (!includeAll && record.taskId !in requireNotNull(includedTaskIds)) return@forEach

            val start = maxOf(record.startMs, windowStartMs)
            val end = minOf(record.endMs, windowEndMs)
            val duration = (end - start).coerceAtLeast(0L)
            if (duration == 0L) return@forEach

            val target = targetFor(record)
            val current = totals[target.id]
            totals[target.id] = StatsTotal(
                id = target.id,
                name = target.name,
                totalMs = (current?.totalMs ?: 0L) + duration
            )
        }

        return totals.values.sortedByDescending { it.totalMs }
    }
}
