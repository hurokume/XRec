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
    fun recordsCrossingMidnightRemainInStartingDayGroup() {
        val task = AnalysisCategoryNode(2L, "開発")
        val start = utcMillis(2026, 8, 14, 23, 30)
        val result = TaskAnalysisEngine.analyze(
            events = listOf(
                AnalysisEvent(10L, 2L, listOf(task), start, start + hour)
            ),
            request = request(2L, "開発", TaskAnalysisPeriod.Week, start)
        )

        val group = result.dayGroups.single()
        val entry = group.entries.single()
        assertEquals(utcMillis(2026, 8, 14), group.dayStartMs)
        assertEquals(hour, group.totalMs)
        assertEquals("開発", entry.taskLabel)
        assertEquals(start, entry.startMs)
        assertEquals(start + hour, entry.endMs)
        assertEquals(hour, entry.durationMs)
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

    @Test
    fun dailyAverageCombinesTasksAndIncludesEmptyDaysButNotFutureDays() {
        val firstTask = AnalysisCategoryNode(1L, "Work")
        val secondTask = AnalysisCategoryNode(2L, "Study")
        val start = utcMillis(2026, 8, 10)
        val thirdDay = utcMillis(2026, 8, 12)
        val events = listOf(
            AnalysisEvent(1L, 1L, listOf(firstTask), start + hour, start + 2 * hour),
            AnalysisEvent(2L, 2L, listOf(secondTask), start + 3 * hour, start + 6 * hour),
            AnalysisEvent(3L, 1L, listOf(firstTask), thirdDay + hour, thirdDay + 3 * hour)
        )

        for ((period, elapsedDays) in listOf(
            TaskAnalysisPeriod.Week to 3,
            TaskAnalysisPeriod.Month to 12,
            TaskAnalysisPeriod.Custom to 3
        )) {
            val result = TaskAnalysisEngine.analyze(
                events,
                request(1L, "Selected tasks", period, start).copy(
                    taskIds = setOf(1L, 2L),
                    nowMs = thirdDay + 12 * hour,
                    customStartMs = start,
                    customEndMs = utcMillis(2026, 8, 15)
                )
            )

            assertEquals(6 * hour, result.totalMs)
            assertEquals(4 * hour, result.buckets.single { it.startMs == start }.totalMs)
            assertEquals(0L, result.buckets.single { it.startMs == utcMillis(2026, 8, 11) }.totalMs)
            assertEquals(2 * hour, result.buckets.single { it.startMs == thirdDay }.totalMs)
            assertEquals("$period daily average", 6 * hour / elapsedDays, result.averagePerBucketMs)
        }
    }

    @Test
    fun monthlyAverageUsesCalendarMonthTotalsIncludingEmptyMonths() {
        val firstTask = AnalysisCategoryNode(1L, "Work")
        val secondTask = AnalysisCategoryNode(2L, "Study")
        val january = utcMillis(2026, 1, 10)
        val march = utcMillis(2026, 3, 10)
        val events = listOf(
            AnalysisEvent(1L, 1L, listOf(firstTask), january, january + hour),
            AnalysisEvent(2L, 2L, listOf(secondTask), january + hour, january + 4 * hour),
            AnalysisEvent(3L, 1L, listOf(firstTask), march, march + 2 * hour)
        )
        val analysisRequest = request(1L, "Selected tasks", TaskAnalysisPeriod.Year, january)
            .copy(taskIds = setOf(1L, 2L))
        val currentYear = TaskAnalysisEngine.analyze(
            events, analysisRequest.copy(nowMs = utcMillis(2026, 3, 15))
        )
        val pastYear = TaskAnalysisEngine.analyze(events, analysisRequest)

        assertEquals(listOf(4 * hour, 0L, 2 * hour), currentYear.buckets.take(3).map { it.totalMs })
        assertEquals(2 * hour, currentYear.averagePerBucketMs)
        assertEquals(hour / 2, pastYear.averagePerBucketMs)
    }

    @Test
    fun dailyAverageSplitsCrossMidnightTimeIntoCalendarDays() {
        val task = AnalysisCategoryNode(1L, "Work")
        val start = utcMillis(2026, 8, 10)
        val result = TaskAnalysisEngine.analyze(
            listOf(AnalysisEvent(1L, 1L, listOf(task), start + 23 * hour, start + 26 * hour)),
            request(1L, "Work", TaskAnalysisPeriod.Custom, start).copy(
                customStartMs = start,
                customEndMs = utcMillis(2026, 8, 13)
            )
        )

        assertEquals(listOf(hour, 2 * hour, 0L), result.buckets.map { it.totalMs })
        assertEquals(hour, result.averagePerBucketMs)
    }

    @Test
    fun totalsAndHourlyAverageStopAtNowIncludingInsideCurrentBucket() {
        val task = AnalysisCategoryNode(1L, "Work")
        val day = utcMillis(2026, 8, 10)
        val now = day + hour + hour / 2
        val result = TaskAnalysisEngine.analyze(
            listOf(
                AnalysisEvent(1L, 1L, listOf(task), day + hour / 2, day + 2 * hour),
                AnalysisEvent(2L, 1L, listOf(task), day + 4 * hour, day + 5 * hour)
            ),
            request(1L, "Work", TaskAnalysisPeriod.Day, day).copy(nowMs = now)
        )
        val cumulative = TaskAnalysisEngine.cumulativeCategoryTotals(result.buckets, now)

        assertEquals(hour, result.totalMs)
        assertEquals(hour / 2, result.averagePerBucketMs)
        assertEquals(listOf(hour / 2, hour / 2, 0L), result.buckets.take(3).map { it.totalMs })
        assertEquals(result.totalMs, cumulative[1].values.sum())
        assertTrue(cumulative.drop(2).all { it.isEmpty() })
    }

    @Test
    fun dailyAverageExcludesDayThatHasNotStartedAtMidnight() {
        val task = AnalysisCategoryNode(1L, "Work")
        val start = utcMillis(2026, 8, 10)
        val result = TaskAnalysisEngine.analyze(
            listOf(AnalysisEvent(1L, 1L, listOf(task), start, start + 4 * hour)),
            request(1L, "Work", TaskAnalysisPeriod.Week, start)
                .copy(nowMs = utcMillis(2026, 8, 12))
        )

        assertEquals(2 * hour, result.averagePerBucketMs)
    }

    @Test
    fun whollyFuturePeriodHasZeroTotalAndAverage() {
        val task = AnalysisCategoryNode(1L, "Work")
        val day = utcMillis(2026, 8, 10)
        val result = TaskAnalysisEngine.analyze(
            listOf(AnalysisEvent(1L, 1L, listOf(task), day, day + hour)),
            request(1L, "Work", TaskAnalysisPeriod.Day, day).copy(nowMs = day - hour)
        )

        assertEquals(0L, result.totalMs)
        assertEquals(0L, result.averagePerBucketMs)
        assertTrue(result.buckets.all { it.totalMs == 0L })
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
