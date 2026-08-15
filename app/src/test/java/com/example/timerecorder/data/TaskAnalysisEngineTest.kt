package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAnalysisEngineTest {
    private val utcId = "UTC"
    private val hour = 60L * 60L * 1000L

    @Test
    fun movingAverageUsesCompletedTrailingWindow() {
        val result = TaskAnalysisEngine.movingAverage(
            values = listOf(1L, 2L, 6L, 4L),
            windowSize = 3
        )

        assertEquals(listOf(null, null, 3L, 4L), result)
        assertEquals(3, TaskAnalysisEngine.movingAverageWindow(TaskAnalysisPeriod.Day))
        assertEquals(7, TaskAnalysisEngine.movingAverageWindow(TaskAnalysisPeriod.Month))
    }

    @Test
    fun probabilitiesAreNormalizedByTheMaximum() {
        val result = TaskAnalysisEngine.normalizeToMaximum(listOf(0.0, 0.2, 0.5))

        assertEquals(listOf(0.0, 0.4, 1.0), result)
        assertEquals(listOf(0.0, 0.0), TaskAnalysisEngine.normalizeToMaximum(listOf(0.0, 0.0)))
    }

    @Test
    fun cumulativeValuesAfterCurrentBucketAreZero() {
        val buckets = listOf(
            bucket(startMs = 0L, endMs = 10L, total = 2L),
            bucket(startMs = 10L, endMs = 20L, total = 3L),
            bucket(startMs = 20L, endMs = 30L, total = 4L)
        )

        val result = TaskAnalysisEngine.cumulativeCategoryTotals(
            buckets = buckets,
            completedThroughMs = 15L
        )

        assertEquals(listOf(mapOf(1L to 2L), mapOf(1L to 5L), emptyMap()), result)
    }

    @Test
    fun weekRangeStartsOnMondayAndUsesCalendarDays() {
        val range = TaskAnalysisEngine.rangeFor(
            period = TaskAnalysisPeriod.Week,
            anchorMs = utcMillis(2026, 8, 14, 12),
            timeZoneId = utcId
        )

        assertEquals(utcMillis(2026, 8, 10), range.startMs)
        assertEquals(utcMillis(2026, 8, 17), range.endMs)
    }

    @Test
    fun selectedTaskIncludesDescendantsAndBuildsHourlyBuckets() {
        val project = AnalysisCategoryNode(1L, "仕事", 0xFF336699.toInt())
        val task = AnalysisCategoryNode(2L, "開発")
        val feature = AnalysisCategoryNode(3L, "機能A")
        val other = AnalysisCategoryNode(4L, "運動")
        val day = utcMillis(2026, 8, 14)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 3L, listOf(project, task, feature), day + 9 * hour, day + 10 * hour + hour / 2),
                AnalysisEvent(11L, 4L, listOf(other), day + 12 * hour, day + 13 * hour)
            ),
            request = request(2L, "開発", TaskAnalysisPeriod.Day, day + 12 * hour)
        )

        assertEquals(hour + hour / 2, result.totalMs)
        assertEquals(hour, result.buckets[9].totalMs)
        assertEquals(hour / 2, result.buckets[10].totalMs)
        assertEquals(listOf("機能A"), result.categories.map { it.name })
        assertEquals("機能A", result.dayGroups.single().entries.single().taskLabel)
    }

    @Test
    fun periodBucketsKeepSubtaskTotalsSeparate() {
        val task = AnalysisCategoryNode(2L, "開発")
        val featureA = AnalysisCategoryNode(3L, "機能A")
        val featureB = AnalysisCategoryNode(4L, "機能B")
        val day = utcMillis(2026, 8, 14)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 3L, listOf(task, featureA), day + 9 * hour, day + 10 * hour),
                AnalysisEvent(11L, 4L, listOf(task, featureB), day + 9 * hour, day + 11 * hour)
            ),
            request = request(2L, "開発", TaskAnalysisPeriod.Day, day + 12 * hour)
        )

        assertEquals(setOf(3L, 4L), result.categories.map { it.id }.toSet())
        assertEquals(listOf(4L, 3L), result.categories.map { it.id })
        assertEquals(mapOf(3L to hour, 4L to hour), result.buckets[9].categoryTotals)
        assertEquals(mapOf(4L to hour), result.buckets[10].categoryTotals)
    }

    @Test
    fun visibleMovingAverageUsesLookbackAndStopsAtCurrentBucket() {
        val task = AnalysisCategoryNode(2L, "開発")
        val day = utcMillis(2026, 8, 14)
        val request = request(2L, "開発", TaskAnalysisPeriod.Day, day)
            .copy(nowMs = day + hour + hour / 2)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(1L, 2L, listOf(task), day - 2 * hour, day - hour),
                AnalysisEvent(2L, 2L, listOf(task), day - hour, day),
                AnalysisEvent(3L, 2L, listOf(task), day, day + hour),
                AnalysisEvent(4L, 2L, listOf(task), day + hour, day + hour + hour / 2)
            ),
            request = request
        )

        assertEquals(hour, result.movingAverageMs[0])
        assertEquals((hour * 5 / 2) / 3, result.movingAverageMs[1])
        assertEquals(null, result.movingAverageMs[2])
    }

    @Test
    fun customRangeUsesInclusiveDatesAndDailyBuckets() {
        val start = utcMillis(2026, 8, 10)
        val end = utcMillis(2026, 8, 14)
        val request = request(2L, "開発", TaskAnalysisPeriod.Custom, start)
            .copy(customStartMs = start, customEndMs = end)

        val range = TaskAnalysisEngine.rangeFor(request)
        val result = TaskAnalysisEngine.analyze(emptyList(), request)

        assertEquals(start, range.startMs)
        assertEquals(end, range.endMs)
        assertEquals(4, result.buckets.size)
        assertEquals("8/10", result.buckets.first().label)
    }

    @Test
    fun multipleCheckedTasksAreAnalyzedTogetherWithoutDoubleCounting() {
        val project = AnalysisCategoryNode(1L, "仕事")
        val featureA = AnalysisCategoryNode(3L, "機能A")
        val featureB = AnalysisCategoryNode(4L, "機能B")
        val day = utcMillis(2026, 8, 14)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 3L, listOf(project, featureA), day + 9 * hour, day + 10 * hour),
                AnalysisEvent(11L, 4L, listOf(project, featureB), day + 10 * hour, day + 12 * hour)
            ),
            request = request(
                taskId = Long.MIN_VALUE,
                taskName = "選択した2項目",
                period = TaskAnalysisPeriod.Day,
                anchorMs = day + 12 * hour
            ).copy(taskIds = setOf(1L, 3L, 4L))
        )

        assertEquals(3 * hour, result.totalMs)
        assertEquals(setOf(3L, 4L), result.categories.map { it.id }.toSet())
    }

    @Test
    fun recordsCrossingMidnightAreSplitIntoTwoDayGroups() {
        val task = AnalysisCategoryNode(2L, "開発")
        val start = utcMillis(2026, 8, 14, 23, 30)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 2L, listOf(task), start, start + hour)
            ),
            request = request(2L, "開発", TaskAnalysisPeriod.Week, start)
        )

        assertEquals(2, result.dayGroups.size)
        assertEquals(listOf(hour / 2, hour / 2), result.dayGroups.map { it.totalMs })
        assertTrue(result.dayGroups.all { it.entries.single().taskLabel == "開発" })
    }

    @Test
    fun heatMapShowsProbabilityForEachWeekdayAndHour() {
        val task = AnalysisCategoryNode(2L, "開発")
        val monday = utcMillis(2026, 8, 10)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 2L, listOf(task), monday + 9 * hour, monday + 10 * hour)
            ),
            request = request(2L, "開発", TaskAnalysisPeriod.Week, monday)
        )

        val mondayAtNine = result.heatCells.single {
            it.dayOfWeekIndex == 0 && it.hourOfDay == 9
        }
        val tuesdayAtNine = result.heatCells.single {
            it.dayOfWeekIndex == 1 && it.hourOfDay == 9
        }
        assertEquals(1.0, mondayAtNine.probability, 0.0001)
        assertEquals(0.0, tuesdayAtNine.probability, 0.0001)
    }

    private fun request(
        taskId: Long,
        taskName: String,
        period: TaskAnalysisPeriod,
        anchorMs: Long
    ) = TaskAnalysisRequest(
        taskId = taskId,
        taskName = taskName,
        period = period,
        anchorMs = anchorMs,
        nowMs = utcMillis(2027, 1, 1),
        timeZoneId = utcId
    )

    private fun bucket(startMs: Long, endMs: Long, total: Long) = TaskAnalysisBucket(
        startMs = startMs,
        endMs = endMs,
        label = startMs.toString(),
        totalMs = total,
        categoryTotals = mapOf(1L to total)
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Long = Calendar.getInstance(TimeZone.getTimeZone(utcId)).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
}
