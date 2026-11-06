package com.example.timerecorder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSelectionTest {
    @Test
    fun selectingParentAddsEveryDescendant() {
        assertEquals(
            setOf(1L, 2L, 3L),
            toggleTaskTreeSelection(emptySet(), listOf(1L, 2L, 3L))
        )
    }

    @Test
    fun selectingPartiallySelectedParentCompletesTheSubtree() {
        assertEquals(
            setOf(1L, 2L, 3L, 9L),
            toggleTaskTreeSelection(setOf(2L, 9L), listOf(1L, 2L, 3L))
        )
    }

    @Test
    fun selectingFullySelectedParentClearsTheSubtreeOnly() {
        assertEquals(
            setOf(9L),
            toggleTaskTreeSelection(setOf(1L, 2L, 3L, 9L), listOf(1L, 2L, 3L))
        )
    }
}
