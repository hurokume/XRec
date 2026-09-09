package com.example.timerecorder.ui

import com.example.timerecorder.R
import java.util.Calendar
import java.util.TimeZone

internal enum class StatisticsPeriod(val label: Int) {
    Today(R.string.action_today), Yesterday(R.string.stats_yesterday),
    OneDay(R.string.stats_one_day), SevenDays(R.string.stats_seven_days),
    ThirtyDays(R.string.stats_thirty_days), NinetyDays(R.string.stats_ninety_days),
    OneYear(R.string.stats_one_year)
}

internal fun statisticsBounds(
    period: StatisticsPeriod,
    nowMs: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Pair<Long, Long> {
    val today = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val midnight = today.timeInMillis
    return when (period) {
        StatisticsPeriod.Today -> midnight to nowMs
        StatisticsPeriod.OneDay -> (nowMs - 24 * 3_600_000L) to nowMs
        else -> {
            when (period) {
                StatisticsPeriod.OneYear -> today.add(Calendar.YEAR, -1)
                else -> today.add(Calendar.DAY_OF_MONTH, -when (period) {
                    StatisticsPeriod.SevenDays -> 7
                    StatisticsPeriod.ThirtyDays -> 30
                    StatisticsPeriod.NinetyDays -> 90
                    else -> 1
                })
            }
            today.timeInMillis to midnight
        }
    }
}
