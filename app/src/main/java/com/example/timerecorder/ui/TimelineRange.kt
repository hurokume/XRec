package com.example.timerecorder.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerecorder.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

enum class TimelinePeriod(val label: Int) {
    Today(R.string.action_today), ThreeDays(R.string.timeline_three_days),
    FiveDays(R.string.timeline_five_days), Weekly(R.string.timeline_week),
    Monthly(R.string.timeline_month), Custom(R.string.timeline_custom)
}

internal fun calendarDay(ms: Long, offset: Int = 0): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, offset)
}.timeInMillis

internal fun timelineBounds(period: TimelinePeriod, now: Long, offset: Int, customStart: Long, customEnd: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply { timeInMillis = calendarDay(now) }
    return when (period) {
        TimelinePeriod.Custom -> calendarDay(customStart) to calendarDay(customEnd, 1)
        TimelinePeriod.Monthly -> {
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.add(Calendar.MONTH, offset)
            val start = cal.timeInMillis; cal.add(Calendar.MONTH, 1)
            start to cal.timeInMillis
        }
        TimelinePeriod.Weekly -> {
            cal.add(Calendar.DAY_OF_MONTH, -(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            cal.timeInMillis to calendarDay(cal.timeInMillis, 7)
        }
        else -> {
            val days = when (period) { TimelinePeriod.ThreeDays -> 3; TimelinePeriod.FiveDays -> 5; else -> 1 }
            val start = calendarDay(now, offset * days - days + 1)
            start to calendarDay(start, days)
        }
    }
}

internal class TimelineRange(
    val period: TimelinePeriod, val startMs: Long, val endMs: Long,
    val select: (TimelinePeriod) -> Unit, val shift: (Int) -> Unit,
    val custom: (Long, Long) -> Unit
) {
    val dayCount: Int get() {
        var count = 0; var day = startMs
        while (day < endMs) { count++; day = calendarDay(day, 1) }
        return count
    }
    fun dayStart(index: Int) = calendarDay(startMs, index)
}

@Composable
internal fun rememberTimelineRange(now: Long): TimelineRange {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var period by rememberSaveable { mutableStateOf(runCatching {
        TimelinePeriod.valueOf(prefs.getString("timeline_period", null).orEmpty())
    }.getOrDefault(TimelinePeriod.ThreeDays)) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var customStart by rememberSaveable { mutableLongStateOf(prefs.getLong("timeline_custom_start", calendarDay(now, -2))) }
    var customEnd by rememberSaveable { mutableLongStateOf(prefs.getLong("timeline_custom_end", calendarDay(now))) }
    val (start, end) = timelineBounds(period, now, offset, customStart, customEnd)
    return TimelineRange(period, start, end,
        select = { period = it; offset = 0 },
        shift = { step ->
            if (period == TimelinePeriod.Custom) {
                var days = 0; var day = start
                while (day < end) { days++; day = calendarDay(day, 1) }
                customStart = calendarDay(customStart, days * step)
                customEnd = calendarDay(customEnd, days * step)
            } else offset += step
        },
        custom = { s, e ->
            customStart = s; customEnd = e; period = TimelinePeriod.Custom
            prefs.edit().putLong("timeline_custom_start", s).putLong("timeline_custom_end", e).apply()
        })
}

@Composable
internal fun TimelineRangeControls(range: TimelineRange) {
    val ctx = LocalContext.current
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM)
    fun pick(initial: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(y, m, d); onPick(calendarDay(cal.timeInMillis))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    Row {
        TextButton(onClick = { range.shift(-1) }) { Text(stringResource(R.string.action_previous)) }
        Text("${formatter.format(Date(range.startMs))} – ${formatter.format(Date(range.endMs - 1))}", modifier = Modifier.weight(1f))
        TextButton(onClick = { range.shift(1) }) { Text(stringResource(R.string.action_next)) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimelinePeriod.entries.forEach { period ->
            FilterChip(selected = range.period == period, onClick = {
                if (period == TimelinePeriod.Custom) {
                    pick(range.startMs) { start ->
                        val cal = Calendar.getInstance().apply { timeInMillis = maxOf(start, range.endMs - 1) }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            cal.set(y, m, d)
                            range.custom(start, maxOf(start, calendarDay(cal.timeInMillis)))
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                            datePicker.minDate = start
                        }.show()
                    }
                } else range.select(period)
            }, label = { Text(stringResource(period.label)) })
        }
    }
}
