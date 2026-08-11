package com.example.timerecorder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeeklyReportEngineTest {
    private val utc = TimeZone.getTimeZone("UTC")
    @Test
    fun weekRangeContaining_usesSundayThroughSaturday() {
        val wednesday = utcMillis(2026, 8, 5, 15)

        val range = WeeklyReportEngine.weekRangeContaining(wednesday, "UTC")

        assertEquals(utcMillis(2026, 8, 2), range.startMs)
        assertEquals(utcMillis(2026, 8, 9), range.endMs)
    }

    @Test
    fun buildTimeline_splitsAtDayBoundaryAndClipsToDisplayHours() {
        val range = AnalysisRange(
            startMs = utcMillis(2026, 8, 2),
            endMs = utcMillis(2026, 8, 9)
        )
        val projectColor = 0xFF1565C0.toInt()
        val project = AnalysisCategoryNode(1L, "仕事", projectColor)
        val task = AnalysisCategoryNode(2L, "開発")
        val crossingMidnight = AnalysisEvent(
            eventId = 10L,
            taskId = task.id,
            path = listOf(project, task),
            startMs = utcMillis(2026, 8, 2, 7),
            endMs = utcMillis(2026, 8, 3, 10)
        )

        val timeline = WeeklyReportEngine.buildTimeline(
            events = listOf(crossingMidnight),
            range = range,
            displayStartMinute = 8 * 60,
            displayEndMinute = 20 * 60,
            timeZoneId = "UTC"
        )

        assertEquals(7, timeline.days.size)
        assertEquals(8 * 60, timeline.days[0].entries.single().startMinute)
        assertEquals(20 * 60, timeline.days[0].entries.single().endMinute)
        assertEquals(8 * 60, timeline.days[1].entries.single().startMinute)
        assertEquals(10 * 60, timeline.days[1].entries.single().endMinute)
        assertEquals("仕事 / 開発", timeline.days[0].entries.single().pathLabel)
        assertEquals(projectColor, timeline.days[0].entries.single().projectColorInt)
    }

    @Test
    fun shiftWeek_preservesCalendarWeekAcrossDst() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val start = localMillis(zone, 2026, 3, 1)
        val range = AnalysisRange(start, localMillis(zone, 2026, 3, 8))

        val shifted = WeeklyReportEngine.shiftWeek(range, 1, zone.id)

        assertEquals(localMillis(zone, 2026, 3, 8), shifted.startMs)
        assertEquals(localMillis(zone, 2026, 3, 15), shifted.endMs)
        assertEquals(7, calendarDayDifference(shifted.startMs, shifted.endMs, zone))
    }

    @Test
    fun buildTimeline_fullDayIncludesLateEveningOnDstFallBackDay() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val dayStart = localMillis(zone, 2026, 11, 1)
        val dayEnd = localMillis(zone, 2026, 11, 2)
        val project = AnalysisCategoryNode(1L, "Late work")
        val event = AnalysisEvent(
            eventId = 20L,
            taskId = project.id,
            path = listOf(project),
            startMs = localMillis(zone, 2026, 11, 1, 23, 30),
            endMs = localMillis(zone, 2026, 11, 1, 23, 45)
        )

        val timeline = WeeklyReportEngine.buildTimeline(
            events = listOf(event),
            range = AnalysisRange(dayStart, dayEnd),
            displayStartMinute = 0,
            displayEndMinute = 24 * 60,
            timeZoneId = zone.id
        )

        assertEquals(23 * 60 + 30, timeline.days.single().entries.single().startMinute)
        assertEquals(23 * 60 + 45, timeline.days.single().entries.single().endMinute)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hourOfDay: Int = 0): Long =
        localMillis(utc, year, month, day, hourOfDay)

    private fun localMillis(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hourOfDay: Int = 0,
        minute: Int = 0
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month - 1, day, hourOfDay, minute, 0)
    }.timeInMillis

    private fun calendarDayDifference(startMs: Long, endMs: Long, timeZone: TimeZone): Int {
        var cursor = startMs
        var days = 0
        while (cursor < endMs) {
            cursor = Calendar.getInstance(timeZone).apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            days++
        }
        return days
    }
}
