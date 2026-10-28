package com.example.timerecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AdvancedAnalysisEngineTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    @Test
    fun calculatesCoverageCategoryBurdenAndTransitions() {
        val day1 = utcMillis(2026, 1, 1)
        val day2 = utcMillis(2026, 1, 2)
        val end = utcMillis(2026, 1, 3)
        val sleep = listOf(
            AnalysisCategoryNode(1L, "睡眠"),
            AnalysisCategoryNode(11L, "夜間睡眠")
        )
        val work = listOf(
            AnalysisCategoryNode(2L, "仕事"),
            AnalysisCategoryNode(21L, "研究")
        )
        val events = listOf(
            event(1L, sleep, day1, day1 + 8 * hour),
            event(2L, work, day1 + 8 * hour, day1 + 24 * hour),
            event(3L, sleep, day2, day2 + 6 * hour),
            event(4L, sleep, day2 + 8 * hour, day2 + 10 * hour),
            event(5L, work, day2 + 10 * hour, day2 + 24 * hour)
        )

        val result = AdvancedAnalysisEngine.analyze(
            events,
            request(nowMs = end, days = 2, level = AnalysisLevel.Large)
        )

        assertEquals(48 * hour, result.quality.expectedMs)
        assertEquals(46 * hour, result.quality.recordedMs)
        assertEquals(2 * hour, result.quality.missingMs)
        assertEquals(0L, result.quality.overlapMs)
        assertEquals(1, result.quality.daysAtLeast95Percent)

        val sleepMetric = result.categoryMetrics.first { it.id == 1L }
        assertEquals(16 * hour, sleepMetric.totalMs)
        assertEquals(8 * hour, sleepMetric.averagePerDayMs)
        assertEquals(3, sleepMetric.sessionCount)
        assertEquals(8 * hour, sleepMetric.maxSessionMs)

        val sleepToWork = result.transitions.first { it.fromId == 1L && it.toId == 2L }
        assertEquals(2, sleepToWork.count)
        assertEquals(1.0, sleepToWork.probability, 0.0001)
        assertNotNull(result.representativeDay)
    }

    @Test
    fun separatesHierarchyLevelsAndKeepsUnspecifiedTime() {
        val start = utcMillis(2026, 2, 1)
        val project = AnalysisCategoryNode(1L, "仕事")
        val task = AnalysisCategoryNode(2L, "研究")
        val analysis = AnalysisCategoryNode(3L, "解析")
        val events = listOf(
            event(1L, listOf(project, task, analysis), start, start + 2 * hour),
            event(2L, listOf(project, task), start + 2 * hour, start + 3 * hour)
        )

        val middle = AdvancedAnalysisEngine.analyze(
            events,
            request(start + 24 * hour, days = 1, level = AnalysisLevel.Middle)
        )
        val small = AdvancedAnalysisEngine.analyze(
            events,
            request(start + 24 * hour, days = 1, level = AnalysisLevel.Small),
            unspecifiedLabel = "Unspecified"
        )

        assertEquals(1, middle.categoryMetrics.size)
        assertEquals(3 * hour, middle.categoryMetrics.single().totalMs)
        assertEquals(2, small.categoryMetrics.size)
        assertEquals(1 * hour, small.quality.unspecifiedMs)
        assertTrue(small.categoryMetrics.any { it.name == "Unspecified" && it.totalMs == hour })
    }

    @Test
    fun extractsTwoTypicalPatternsFromFourDays() {
        val start = utcMillis(2026, 3, 1)
        val home = listOf(AnalysisCategoryNode(1L, "在宅"))
        val outside = listOf(AnalysisCategoryNode(2L, "外出"))
        val events = buildList {
            repeat(4) { day ->
                val dayStart = start + day * 24 * hour
                val path = if (day < 2) home else outside
                add(event((day + 1).toLong(), path, dayStart, dayStart + 24 * hour))
            }
        }

        val result = AdvancedAnalysisEngine.analyze(
            events,
            request(start + 4 * 24 * hour, days = 4, level = AnalysisLevel.Large)
        )

        assertEquals(2, result.typicalPatterns.size)
        assertEquals(listOf(2, 2), result.typicalPatterns.map { it.dayCount }.sorted())
    }

    @Test
    fun weekdayFilterChangesExpectedDayCount() {
        // 2026-03-02 is Monday; seven completed days contain five weekdays.
        val end = utcMillis(2026, 3, 9)
        val result = AdvancedAnalysisEngine.analyze(
            emptyList(),
            request(
                nowMs = end,
                days = 7,
                level = AnalysisLevel.Large,
                dayType = AnalysisDayType.Weekday
            )
        )

        assertEquals(7, result.quality.calendarDays)
        assertEquals(5, result.quality.analyzedDays)
        assertEquals(5 * 24 * hour, result.quality.expectedMs)
    }

    @Test
    fun completelyMissingDayIsNotSelectedAsRepresentative() {
        val start = utcMillis(2026, 4, 1)
        val recorded = listOf(AnalysisCategoryNode(1L, "記録あり"))
        val result = AdvancedAnalysisEngine.analyze(
            listOf(event(1L, recorded, start, start + 12 * hour)),
            request(start + 3 * 24 * hour, days = 3, level = AnalysisLevel.Large)
        )

        assertEquals(start, result.representativeDay?.dayStartMs)
        assertEquals(1, result.typicalPatterns.sumOf { it.dayCount })
    }

    private fun request(
        nowMs: Long,
        days: Int,
        level: AnalysisLevel,
        dayType: AnalysisDayType = AnalysisDayType.All
    ) = AnalysisRequest(
        nowMs = nowMs,
        days = days,
        dayBoundaryMinutes = 0,
        slotMinutes = 60,
        level = level,
        dayType = dayType,
        timeZoneId = "UTC"
    )

    private fun event(
        id: Long,
        path: List<AnalysisCategoryNode>,
        startMs: Long,
        endMs: Long
    ) = AnalysisEvent(
        eventId = id,
        taskId = path.last().id,
        path = path,
        startMs = startMs,
        endMs = endMs
    )

    private fun utcMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis
}
