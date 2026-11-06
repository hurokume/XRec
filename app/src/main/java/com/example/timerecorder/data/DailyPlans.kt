package com.example.timerecorder.data

import java.util.Calendar
import java.util.TimeZone

enum class DailyPlanType { Weekday, Holiday }

data class DailyPlanEntry(
    val targetTaskId: Long,
    val startMinute: Int,
    val endMinute: Int
) {
    init {
        require(targetTaskId > 0L) { "target task must be set" }
        require(startMinute in 0 until MINUTES_PER_DAY) { "start must be within a day" }
        require(endMinute in 1..MINUTES_PER_DAY) { "end must be within a day" }
        require(endMinute > startMinute) { "end must be after start" }
    }
}

data class DailyPlans(
    val weekdays: List<DailyPlanEntry> = emptyList(),
    val holidays: List<DailyPlanEntry> = emptyList()
) {
    fun entriesFor(type: DailyPlanType): List<DailyPlanEntry> = when (type) {
        DailyPlanType.Weekday -> weekdays
        DailyPlanType.Holiday -> holidays
    }
}

data class DailyAchievement(
    val dayStartMs: Long,
    val dayEndMs: Long,
    val planType: DailyPlanType,
    val plannedMs: Long,
    val achievedMs: Long
) {
    val achievementRate: Double
        get() = if (plannedMs > 0L) {
            achievedMs.toDouble() / plannedMs
        } else {
            0.0
        }
}

/** Compact, versioned representation suitable for SharedPreferences. */
object DailyPlansCodec {
    private const val VERSION = "1"

    fun encode(plans: DailyPlans): String = listOf(
        VERSION,
        encodeEntries(plans.weekdays),
        encodeEntries(plans.holidays)
    ).joinToString("|")

    fun decode(value: String?): DailyPlans {
        if (value.isNullOrBlank()) return DailyPlans()
        val parts = value.split('|')
        if (parts.size != 3 || parts[0] != VERSION) return DailyPlans()
        return DailyPlans(
            weekdays = decodeEntries(parts[1]),
            holidays = decodeEntries(parts[2])
        )
    }

    private fun encodeEntries(entries: List<DailyPlanEntry>): String = entries
        .sortedWith(compareBy<DailyPlanEntry> { it.startMinute }.thenBy { it.endMinute })
        .joinToString(";") { entry ->
            "${entry.targetTaskId},${entry.startMinute},${entry.endMinute}"
        }

    private fun decodeEntries(value: String): List<DailyPlanEntry> = value
        .split(';')
        .mapNotNull { encoded ->
            val fields = encoded.split(',')
            if (fields.size != 3) return@mapNotNull null
            val taskId = fields[0].toLongOrNull() ?: return@mapNotNull null
            val start = fields[1].toIntOrNull() ?: return@mapNotNull null
            val end = fields[2].toIntOrNull() ?: return@mapNotNull null
            runCatching { DailyPlanEntry(taskId, start, end) }.getOrNull()
        }
        .sortedWith(compareBy<DailyPlanEntry> { it.startMinute }.thenBy { it.endMinute })
}

/** Calculates duration-weighted adherence to the configured plan for each completed calendar day. */
object DailyAchievementEngine {
    fun calculate(
        events: List<AnalysisEvent>,
        range: AnalysisRange,
        plans: DailyPlans,
        timeZoneId: String = TimeZone.getDefault().id,
        completedThroughMs: Long = range.endMs,
        dayType: AnalysisDayType = AnalysisDayType.All
    ): List<DailyAchievement> {
        if (range.endMs <= range.startMs) return emptyList()
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val results = mutableListOf<DailyAchievement>()
        var dayStart = calendarDayStart(range.startMs, timeZone)
        if (dayStart < range.startMs) dayStart = addCalendarDays(dayStart, 1, timeZone)

        while (dayStart < range.endMs) {
            val dayEnd = addCalendarDays(dayStart, 1, timeZone)
            if (dayEnd > range.endMs || dayEnd > completedThroughMs) break
            val planType = planType(dayStart, timeZone)
            val selectedByFilter = when (dayType) {
                AnalysisDayType.All -> true
                AnalysisDayType.Weekday -> planType == DailyPlanType.Weekday
                AnalysisDayType.Weekend -> planType == DailyPlanType.Holiday
            }
            val entries = plans.entriesFor(planType)
            if (selectedByFilter && entries.isNotEmpty()) {
                var plannedMs = 0L
                var achievedMs = 0L
                entries.forEach { entry ->
                    val plannedStart = instantAtMinute(dayStart, entry.startMinute, timeZone)
                    val plannedEnd = instantAtMinute(dayStart, entry.endMinute, timeZone)
                    val duration = (plannedEnd - plannedStart).coerceAtLeast(0L)
                    plannedMs += duration

                    val matchingIntervals = events.asSequence()
                        .filter { event ->
                            event.endMs > plannedStart &&
                                    event.startMs < plannedEnd &&
                                    event.path.any { node -> node.id == entry.targetTaskId }
                        }
                        .map { event ->
                            Interval(
                                startMs = maxOf(event.startMs, plannedStart),
                                endMs = minOf(event.endMs, plannedEnd)
                            )
                        }
                        .toList()
                    achievedMs += mergeIntervals(matchingIntervals)
                        .sumOf { interval -> interval.endMs - interval.startMs }
                        .coerceAtMost(duration)
                }
                results += DailyAchievement(
                    dayStartMs = dayStart,
                    dayEndMs = dayEnd,
                    planType = planType,
                    plannedMs = plannedMs,
                    achievedMs = achievedMs.coerceAtMost(plannedMs)
                )
            }
            dayStart = dayEnd
        }
        return results
    }

    private data class Interval(val startMs: Long, val endMs: Long)

    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        val sorted = intervals.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<Interval>()
        var current = sorted.first()
        sorted.drop(1).forEach { next ->
            if (next.startMs <= current.endMs) {
                current = Interval(current.startMs, maxOf(current.endMs, next.endMs))
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun planType(timeMs: Long, timeZone: TimeZone): DailyPlanType {
        val dayOfWeek = Calendar.getInstance(timeZone).apply { timeInMillis = timeMs }
            .get(Calendar.DAY_OF_WEEK)
        return if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            DailyPlanType.Holiday
        } else {
            DailyPlanType.Weekday
        }
    }

    private fun calendarDayStart(timeMs: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun instantAtMinute(dayStartMs: Long, minute: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = dayStartMs
            if (minute == MINUTES_PER_DAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            } else {
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }.timeInMillis

    private fun addCalendarDays(timeMs: Long, days: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMs
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
}

private const val MINUTES_PER_DAY = 24 * 60
