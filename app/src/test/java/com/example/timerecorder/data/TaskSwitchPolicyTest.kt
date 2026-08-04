package com.example.timerecorder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSwitchPolicyTest {
    @Test
    fun noActiveTaskStartsFresh() {
        val result = TaskSwitchPolicy.decide(null, null, 10L, 1_000L, 60_000L)

        assertEquals(TaskSwitchAction.StartFresh, result.action)
        assertEquals(0L, result.previousDurationMs)
    }

    @Test
    fun selectingCurrentTaskDoesNothing() {
        val result = TaskSwitchPolicy.decide(10L, 1_000L, 10L, 90_000L, 60_000L)

        assertEquals(TaskSwitchAction.NoChange, result.action)
    }

    @Test
    fun thresholdBoundaryIsMerged() {
        val result = TaskSwitchPolicy.decide(10L, 1_000L, 20L, 61_000L, 60_000L)

        assertEquals(TaskSwitchAction.MergeShort, result.action)
        assertEquals(60_000L, result.previousDurationMs)
    }

    @Test
    fun durationBeyondThresholdIsRecordedNormally() {
        val result = TaskSwitchPolicy.decide(10L, 1_000L, 20L, 61_001L, 60_000L)

        assertEquals(TaskSwitchAction.SwitchNormal, result.action)
        assertEquals(60_001L, result.previousDurationMs)
    }
}
