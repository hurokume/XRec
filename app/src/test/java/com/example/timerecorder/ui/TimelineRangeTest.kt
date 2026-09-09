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
    @Test fun customRangeIncludesLeapDayAndCanCrossMonths() {
        val now = date(2024, 3, 31)
        assertEquals(date(2024, 2, 28) to date(2024, 3, 2),
            timelineBounds(TimelinePeriod.Custom, now, 0, date(2024, 2, 28), date(2024, 3, 1)))
    }
    @Test fun retiredMonthlyPreferenceFallsBackToThreeDays() {
        assertEquals(TimelinePeriod.ThreeDays, timelinePeriodFromPreference("Monthly"))
        assertEquals(TimelinePeriod.Custom, timelinePeriodFromPreference("Custom"))
    }
    @Test fun pickerDatesKeepLocalCalendarDayInBothHemispheres() {
        val previous = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Tokyo", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val local = date(2026, 3, 8)
                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear(); set(2026, Calendar.MARCH, 8)
                }.timeInMillis
                assertEquals(utc, localDateToPickerMillis(local))
                assertEquals(local, pickerMillisToLocalDate(utc))
            }
        } finally { TimeZone.setDefault(previous) }
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
