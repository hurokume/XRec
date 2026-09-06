package com.example.timerecorder.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimelineRangeTest {
    private fun date(year: Int, month: Int, day: Int, hour: Int = 0) = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0)
    }.timeInMillis

    @Test fun shortRangesEndAfterToday() {
        val now = date(2026, 9, 6, 15)
        for ((period, days) in listOf(TimelinePeriod.Today to 1, TimelinePeriod.ThreeDays to 3, TimelinePeriod.FiveDays to 5)) {
            val (start, end) = timelineBounds(period, now, 0, now, now)
            assertEquals(calendarDay(now, 1 - days), start)
            assertEquals(date(2026, 9, 7), end)
        }
    }
    @Test fun monthlyPagingUsesCalendarMonthsAndLeapYears() {
        val now = date(2024, 3, 31, 20)
        assertEquals(date(2024, 2, 1) to date(2024, 3, 1), timelineBounds(TimelinePeriod.Monthly, now, -1, now, now))
        assertEquals(date(2024, 4, 1) to date(2024, 5, 1), timelineBounds(TimelinePeriod.Monthly, now, 1, now, now))
    }
    @Test fun weeklyStartsOnSundayAndCustomIncludesLastDay() {
        val now = date(2026, 9, 9)
        assertEquals(date(2026, 9, 6) to date(2026, 9, 13), timelineBounds(TimelinePeriod.Weekly, now, 0, now, now))
        assertEquals(date(2026, 8, 31) to date(2026, 9, 3), timelineBounds(TimelinePeriod.Custom, now, 0, date(2026, 8, 31), date(2026, 9, 2)))
    }
    @Test fun daylightSavingKeepsMidnightBoundaries() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val (start, end) = timelineBounds(TimelinePeriod.Today, date(2026, 3, 8, 12), 0, 0, 0)
            assertEquals(23 * 3_600_000L, end - start)
            assertEquals(date(2026, 3, 9), end)
        } finally { TimeZone.setDefault(previous) }
    }
}
