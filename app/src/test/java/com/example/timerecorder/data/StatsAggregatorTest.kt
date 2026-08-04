package com.example.timerecorder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsAggregatorTest {
    private val records = listOf(
        StatsRecord(taskId = 1L, taskName = "Task A", startMs = 0L, endMs = 100L),
        StatsRecord(taskId = 2L, taskName = "Task B", startMs = 50L, endMs = 200L)
    )

    @Test
    fun clampsToWindowAndAggregatesByResolvedTarget() {
        val result = StatsAggregator.aggregate(
            records = records,
            windowStartMs = 25L,
            windowEndMs = 150L
        ) { StatsTarget(id = 100L, name = "Project") }

        assertEquals(listOf(StatsTotal(100L, "Project", 175L)), result)
    }

    @Test
    fun filtersBeforeGrouping() {
        val result = StatsAggregator.aggregate(
            records = records,
            windowStartMs = 25L,
            windowEndMs = 150L,
            includedLeafIds = setOf(2L)
        ) { StatsTarget(id = 100L, name = "Project") }

        assertEquals(listOf(StatsTotal(100L, "Project", 100L)), result)
    }

    @Test
    fun leafTargetsRemainSeparateAndAreSortedByDuration() {
        val result = StatsAggregator.aggregate(
            records = records,
            windowStartMs = 25L,
            windowEndMs = 150L
        ) { StatsTarget(id = it.taskId, name = it.taskName) }

        assertEquals(
            listOf(
                StatsTotal(2L, "Task B", 100L),
                StatsTotal(1L, "Task A", 75L)
            ),
            result
        )
    }
}
