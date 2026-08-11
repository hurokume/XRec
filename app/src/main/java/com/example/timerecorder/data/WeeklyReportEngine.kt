package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone

data class WeeklyTimelineEntry(
    val eventId: Long,
    val taskId: Long,
    val pathLabel: String,
    val colorKey: Long,
    val projectColorInt: Int?,
    val startMinute: Int,
    val endMinute: Int
)

data class WeeklyTimelineDay(
    val startMs: Long,
    val endMs: Long,
    val entries: List<WeeklyTimelineEntry>
)

data class WeeklyTimeline(
    val range: AnalysisRange,
    val displayStartMinute: Int,
    val displayEndMinute: Int,
    val days: List<WeeklyTimelineDay>
)

data class WeeklyReportData(
    val timeline: WeeklyTimeline,
    val analysis: AdvancedAnalysisResult
)

/** Pure date/range preparation shared by the Weekly report UI, PDF writer, and tests. */
object WeeklyReportEngine {
    fun weekRangeContaining(
        timeMs: Long,
        timeZoneId: String = TimeZone.getDefault().id,
        firstDayOfWeek: Int = Calendar.SUNDAY
    ): AnalysisRange {
        require(firstDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY)
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val daysBack = (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
            add(Calendar.DAY_OF_MONTH, -daysBack)
        }.timeInMillis
        return AnalysisRange(start, addCalendarDays(start, 7, timeZone))
    }

    fun shiftWeek(
        range: AnalysisRange,
        weeks: Int,
        timeZoneId: String = TimeZone.getDefault().id
    ): AnalysisRange {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val start = addCalendarDays(range.startMs, weeks * 7, timeZone)
        return AnalysisRange(start, addCalendarDays(start, 7, timeZone))
    }

    fun buildTimeline(
        events: List<AnalysisEvent>,
        range: AnalysisRange,
        displayStartMinute: Int,
        displayEndMinute: Int,
        timeZoneId: String = TimeZone.getDefault().id
    ): WeeklyTimeline {
        require(range.endMs > range.startMs) { "report range must not be empty" }
        require(displayStartMinute in 0 until 24 * 60) {
            "timeline start must be within a day"
        }
        require(displayEndMinute in 1..24 * 60) {
            "timeline end must be within a day"
        }
        require(displayEndMinute > displayStartMinute) {
            "timeline end must be after start"
        }

        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val days = mutableListOf<WeeklyTimelineDay>()
        var dayStart = range.startMs
        while (dayStart < range.endMs) {
            val dayEnd = minOf(addCalendarDays(dayStart, 1, timeZone), range.endMs)
            val visibleStart = instantAtMinute(dayStart, displayStartMinute, timeZone)
            val visibleEnd = minOf(
                instantAtMinute(dayStart, displayEndMinute, timeZone),
                dayEnd
            )
            val entries = if (visibleEnd <= visibleStart) {
                emptyList()
            } else {
                events.asSequence()
                    .filter { event ->
                        event.path.isNotEmpty() &&
                                event.endMs > visibleStart && event.startMs < visibleEnd
                    }
                    .mapNotNull { event ->
                        val start = maxOf(event.startMs, visibleStart)
                        val end = minOf(event.endMs, visibleEnd)
                        if (end <= start) return@mapNotNull null
                        WeeklyTimelineEntry(
                            eventId = event.eventId,
                            taskId = event.taskId,
                            pathLabel = event.path.joinToString(" / ") { it.name },
                            colorKey = event.path.first().id,
                            projectColorInt = event.path.first().colorInt,
                            startMinute = if (start <= visibleStart) {
                                displayStartMinute
                            } else {
                                minuteOfDay(start, timeZone)
                            },
                            endMinute = if (end >= visibleEnd) {
                                displayEndMinute
                            } else {
                                minuteOfDay(end, timeZone)
                            }
                        ).takeIf { it.endMinute > it.startMinute }
                    }
                    .sortedWith(
                        compareBy<WeeklyTimelineEntry> { it.startMinute }
                            .thenBy { it.endMinute }
                    )
                    .toList()
            }
            days += WeeklyTimelineDay(dayStart, dayEnd, entries)
            dayStart = dayEnd
        }

        return WeeklyTimeline(
            range = range,
            displayStartMinute = displayStartMinute,
            displayEndMinute = displayEndMinute,
            days = days
        )
    }

    private fun instantAtMinute(dayStartMs: Long, minute: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = dayStartMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (minute == 24 * 60) {
                add(Calendar.DAY_OF_MONTH, 1)
            } else {
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
            }
        }.timeInMillis

    private fun minuteOfDay(timeMs: Long, timeZone: TimeZone): Int =
        Calendar.getInstance(timeZone).apply { timeInMillis = timeMs }.let { calendar ->
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }

    private fun addCalendarDays(timeMs: Long, days: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
}
