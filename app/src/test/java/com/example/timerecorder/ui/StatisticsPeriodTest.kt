package com.example.timerecorder.ui

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsPeriodTest {
    private val zone = TimeZone.getTimeZone("Asia/Tokyo")
    private fun date(year: Int, month: Int, day: Int, hour: Int = 0, tz: TimeZone = zone) =
        Calendar.getInstance(tz).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    @Test fun todayYesterdayAndRollingDayHaveDistinctBounds() {
        val now = date(2026, 9, 9, 14)
        assertEquals(date(2026, 9, 9) to now, statisticsBounds(StatisticsPeriod.Today, now, zone))
        assertEquals(date(2026, 9, 8) to date(2026, 9, 9), statisticsBounds(StatisticsPeriod.Yesterday, now, zone))
        assertEquals(date(2026, 9, 8, 14) to now, statisticsBounds(StatisticsPeriod.OneDay, now, zone))
    }

    @Test fun existingPeriodsContinueToExcludeToday() {
        val now = date(2026, 9, 9, 14)
        for ((period, days) in listOf(StatisticsPeriod.SevenDays to 7,
            StatisticsPeriod.ThirtyDays to 30, StatisticsPeriod.NinetyDays to 90)) {
            val end = date(2026, 9, 9)
            assertEquals((end - days * 86_400_000L) to end, statisticsBounds(period, now, zone))
        }
    }

    @Test fun oneYearIncludesLeapDayAndClampsLeapYearAnniversary() {
        assertEquals(date(2023, 3, 1) to date(2024, 3, 1),
            statisticsBounds(StatisticsPeriod.OneYear, date(2024, 3, 1, 14), zone))
        assertEquals(date(2023, 2, 28) to date(2024, 2, 29),
            statisticsBounds(StatisticsPeriod.OneYear, date(2024, 2, 29, 14), zone))
    }

    @Test fun yesterdayUsesLocalMidnightsAcrossDaylightSaving() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val now = date(2026, 3, 9, 14, tz)
        val (start, end) = statisticsBounds(StatisticsPeriod.Yesterday, now, tz)
        assertEquals(date(2026, 3, 8, tz = tz), start)
        assertEquals(date(2026, 3, 9, tz = tz), end)
        assertEquals(23 * 3_600_000L, end - start)
        val rolling = statisticsBounds(StatisticsPeriod.OneDay, now, tz)
        assertEquals(24 * 3_600_000L, rolling.second - rolling.first)
    }
}
