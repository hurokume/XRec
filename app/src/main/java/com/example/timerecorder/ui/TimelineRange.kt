package com.example.timerecorder.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.timerecorder.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.text.DateFormatSymbols

enum class TimelinePeriod(val label: Int) {
    Today(R.string.action_today), ThreeDays(R.string.timeline_three_days),
    FiveDays(R.string.timeline_five_days), Weekly(R.string.timeline_week),
    Custom(R.string.timeline_custom)
}

internal fun timelinePeriodFromPreference(value: String?): TimelinePeriod =
    TimelinePeriod.entries.firstOrNull { it.name == value } ?: TimelinePeriod.ThreeDays

// Material date pickers represent calendar dates at UTC midnight, not local instants.
internal fun localDateToPickerMillis(ms: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

internal fun pickerMillisToLocalDate(ms: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
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
    var period by rememberSaveable { mutableStateOf(timelinePeriodFromPreference(prefs.getString("timeline_period", null))) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineRangeControls(range: TimelineRange) {
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM)
    var showCustomRange by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { range.shift(-1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.timeline_previous))
        }
        Text(stringResource(R.string.date_range, formatter.format(Date(range.startMs)), formatter.format(Date(range.endMs - 1))),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { range.shift(1) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.timeline_next))
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimelinePeriod.entries.forEach { period ->
            FilterChip(selected = range.period == period, onClick = {
                if (period == TimelinePeriod.Custom) {
                    showCustomRange = true
                } else range.select(period)
            }, label = { Text(stringResource(period.label)) })
        }
    }
    if (showCustomRange) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = localDateToPickerMillis(range.startMs),
            initialSelectedEndDateMillis = localDateToPickerMillis(range.endMs - 1)
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRange = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                    onClick = {
                        val start = picker.selectedStartDateMillis
                        val end = picker.selectedEndDateMillis
                        if (start != null && end != null) {
                            range.custom(pickerMillisToLocalDate(start), pickerMillisToLocalDate(end))
                            showCustomRange = false
                        }
                    }
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRange = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            RangeCalendar(picker, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeCalendar(state: DateRangePickerState, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val utc = TimeZone.getTimeZone("UTC")
    var monthMillis by rememberSaveable { mutableLongStateOf(state.displayedMonthMillis) }
    var selectingYear by rememberSaveable { mutableStateOf(false) }
    var selectingMonth by rememberSaveable { mutableStateOf(false) }
    val month = Calendar.getInstance(utc, locale).apply { timeInMillis = monthMillis }
    val year = month.get(Calendar.YEAR)
    val monthIndex = month.get(Calendar.MONTH)
    fun changeMonth(newYear: Int = year, newMonth: Int = monthIndex) {
        monthMillis = Calendar.getInstance(utc, locale).apply {
            clear(); set(newYear, newMonth, 1)
        }.timeInMillis
    }
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = utc }
    val fullDate = DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = utc }
    val monthNames = DateFormatSymbols(locale).months
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.timeline_select_range), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.timeline_range_start,
            state.selectedStartDateMillis?.let { dateFormat.format(Date(it)) } ?: "—"))
        Text(stringResource(R.string.timeline_range_end,
            state.selectedEndDateMillis?.let { dateFormat.format(Date(it)) } ?: "—"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(enabled = year > state.yearRange.first || monthIndex > 0,
                onClick = { changeMonth(newMonth = monthIndex - 1) }) {
                Icon(Icons.Filled.ChevronLeft, stringResource(R.string.timeline_previous))
            }
            TextButton(onClick = { selectingYear = true }) {
                Text(SimpleDateFormat("yyyy", locale).apply { timeZone = utc }.format(Date(monthMillis)))
            }
            TextButton(onClick = { selectingMonth = true }) { Text(monthNames[monthIndex]) }
            IconButton(enabled = year < state.yearRange.last || monthIndex < 11,
                onClick = { changeMonth(newMonth = monthIndex + 1) }) {
                Icon(Icons.Filled.ChevronRight, stringResource(R.string.timeline_next))
            }
        }
        val firstWeekday = month.firstDayOfWeek
        val weekdays = DateFormatSymbols(locale).shortWeekdays
        Row {
            repeat(7) { column ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(weekdays[(firstWeekday - 1 + column) % 7 + 1])
                }
            }
        }
        val leading = (month.get(Calendar.DAY_OF_WEEK) - firstWeekday + 7) % 7
        val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat((leading + days + 6) / 7) { row ->
            Row {
                repeat(7) { column ->
                    val day = row * 7 + column - leading + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val date = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
                            val start = state.selectedStartDateMillis
                            val end = state.selectedEndDateMillis
                            val selected = date == start || date == end
                            val inRange = start != null && end != null && date in start..end
                            TextButton(
                                onClick = {
                                    if (start == null || end != null || date < start) state.setSelection(date, null)
                                    else state.setSelection(start, date)
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        inRange -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) { Text(day.toString(), Modifier.semantics {
                                contentDescription = fullDate.format(Date(date))
                            }) }
                        }
                    }
                }
            }
        }
    }
    if (selectingYear) {
        AlertDialog(onDismissRequest = { selectingYear = false },
            title = { Text(stringResource(R.string.timeline_select_year)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp),
                    state = rememberLazyListState((year - state.yearRange.first - 2).coerceAtLeast(0))) {
                    items(state.yearRange.toList()) { candidate ->
                        TextButton(onClick = { changeMonth(newYear = candidate); selectingYear = false },
                            modifier = Modifier.fillMaxWidth()) { Text(candidate.toString()) }
                    }
                }
            }, confirmButton = {},
            dismissButton = { TextButton(onClick = { selectingYear = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
    if (selectingMonth) {
        AlertDialog(onDismissRequest = { selectingMonth = false },
            title = { Text(stringResource(R.string.timeline_select_month)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    repeat(4) { row ->
                        Row {
                            repeat(3) { column ->
                                val candidate = row * 3 + column
                                TextButton(onClick = { changeMonth(newMonth = candidate); selectingMonth = false },
                                    modifier = Modifier.weight(1f)) { Text(monthNames[candidate]) }
                            }
                        }
                    }
                }
            }, confirmButton = {},
            dismissButton = { TextButton(onClick = { selectingMonth = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
