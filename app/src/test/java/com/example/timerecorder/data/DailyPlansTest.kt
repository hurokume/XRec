package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyPlansTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    @Test
    fun codecRoundTripsBothDayTypesAndIgnoresInvalidInput() {
        val plans = DailyPlans(
            weekdays = listOf(DailyPlanEntry(2L, 9 * 60, 17 * 60)),
            holidays = listOf(DailyPlanEntry(3L, 10 * 60, 12 * 60))
        )

        assertEquals(plans, DailyPlansCodec.decode(DailyPlansCodec.encode(plans)))
        assertEquals(DailyPlans(), DailyPlansCodec.decode("not-a-plan"))
    }

    @Test
    fun calculatesDurationWeightedAchievementAndMatchesDescendantTasks() {
        // 2026-08-03 is Monday.
        val monday = utcMillis(2026, 8, 3)
        val root = AnalysisCategoryNode(1L, "仕事")
        val child = AnalysisCategoryNode(2L, "開発")
        val events = listOf(
            event(1L, listOf(root, child), monday + 9 * hour, monday + 13 * hour),
            // A duplicate must not double-count the same planned duration.
            event(2L, listOf(root, child), monday + 10 * hour, monday + 12 * hour)
        )
        val plans = DailyPlans(
            weekdays = listOf(DailyPlanEntry(root.id, 9 * 60, 17 * 60))
        )

        val result = DailyAchievementEngine.calculate(
            events = events,
            range = AnalysisRange(monday, monday + 2 * 24 * hour),
            plans = plans,
            timeZoneId = "UTC"
        )

        assertEquals(2, result.size)
        assertEquals(8 * hour, result[0].plannedMs)
        assertEquals(4 * hour, result[0].achievedMs)
        assertEquals(0.5, result[0].achievementRate, 0.0001)
        assertEquals(0L, result[1].achievedMs)
    }

    @Test
    fun usesHolidayPlanOnWeekendAndSkipsIncompleteDays() {
        val saturday = utcMillis(2026, 8, 8)
        val leisure = AnalysisCategoryNode(4L, "余暇")
        val plans = DailyPlans(
            holidays = listOf(DailyPlanEntry(leisure.id, 10 * 60, 12 * 60))
        )
        val event = event(
            1L,
            listOf(leisure),
            saturday + 10 * hour,
            saturday + 12 * hour
        )

        val result = DailyAchievementEngine.calculate(
            events = listOf(event),
            range = AnalysisRange(saturday, saturday + 2 * 24 * hour),
            plans = plans,
            timeZoneId = "UTC",
            completedThroughMs = saturday + 36 * hour
        )

        assertEquals(1, result.size)
        assertEquals(DailyPlanType.Holiday, result.single().planType)
        assertEquals(1.0, result.single().achievementRate, 0.0001)
    }

    private fun event(
        id: Long,
        path: List<AnalysisCategoryNode>,
        startMs: Long,
        endMs: Long
    ) = AnalysisEvent(id, path.last().id, path, startMs, endMs)

    private fun utcMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis
}
