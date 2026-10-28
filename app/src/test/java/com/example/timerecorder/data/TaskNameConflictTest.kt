package com.example.timerecorder.data

import org.junit.Assert.*
import org.junit.Test

class TaskNameConflictTest {
    @Test fun duplicateComparisonIgnoresCaseAndSurroundingSpaces() {
        assertTrue(sameTaskName(" Study ", "study"))
        assertFalse(sameTaskName("Study", "Research"))
    }
    @Test fun separateNamesSkipExistingSuffixes() {
        assertEquals("Study (4)", uniqueTaskName("Study", listOf("study", "Study (2)", "STUDY (3)")))
    }
    @Test fun generatedNamesRespectNameLimit() {
        val original = "課".repeat(128)
        val result = uniqueTaskName(original, listOf(original))
        assertEquals(128, result.length)
        assertTrue(result.endsWith(" (2)"))
    }
}
