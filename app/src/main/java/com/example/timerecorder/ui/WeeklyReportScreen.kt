package com.example.timerecorder.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timerecorder.R
import com.example.timerecorder.data.AnalysisDayType
import com.example.timerecorder.data.AnalysisLevel
import com.example.timerecorder.data.AnalysisRequest
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.WeeklyReportEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WeeklyReportScreen(
    viewModel: TaskViewModel,
    dailyPlans: DailyPlans,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeZoneId = remember { TimeZone.getDefault().id }
    val currentWeek = remember(timeZoneId) {
        WeeklyReportEngine.weekRangeContaining(System.currentTimeMillis(), timeZoneId)
    }
    var weekStartMs by rememberSaveable { mutableLongStateOf(currentWeek.startMs) }
    var timelineStartHour by rememberSaveable { mutableIntStateOf(0) }
    var timelineEndHour by rememberSaveable { mutableIntStateOf(24) }
    var level by rememberSaveable { mutableStateOf(AnalysisLevel.Large) }
    var isExporting by remember { mutableStateOf(false) }
    val selectedWeek = remember(weekStartMs, timeZoneId) {
        WeeklyReportEngine.weekRangeContaining(weekStartMs, timeZoneId)
    }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
    val weekLabel = remember(selectedWeek) {
        "${dateFormat.format(Date(selectedWeek.startMs))} - " +
                dateFormat.format(Date(selectedWeek.endMs - 1L))
    }

    fun moveWeek(offset: Int) {
        weekStartMs = WeeklyReportEngine.shiftWeek(selectedWeek, offset, timeZoneId).startMs
    }

    fun exportReport() {
        scope.launch {
            isExporting = true
            try {
                val data = viewModel.buildWeeklyReport(
                    request = AnalysisRequest(
                        nowMs = selectedWeek.endMs,
                        days = 7,
                        dayBoundaryMinutes = 0,
                        slotMinutes = 30,
                        level = level,
                        dayType = AnalysisDayType.All,
                        timeZoneId = timeZoneId
                    ),
                    timelineStartMinute = timelineStartHour * 60,
                    timelineEndMinute = timelineEndHour * 60,
                    dailyPlans = dailyPlans
                )
                val uri = withContext(Dispatchers.IO) {
                    createWeeklyReportPdf(context, data)
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        context.getString(R.string.weekly_report_share_subject)
                    )
                }
                context.startActivity(
                    Intent.createChooser(
                        share,
                        context.getString(R.string.weekly_report_share_chooser)
                    )
                )
            } catch (error: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.weekly_report_error,
                        error.message.orEmpty()
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isExporting = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.weekly_report_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            stringResource(R.string.weekly_report_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.weekly_report_target_week),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { moveWeek(-1) }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(
                                R.string.weekly_report_previous_week
                            )
                        )
                    }
                    Text(
                        weekLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { moveWeek(1) }) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.weekly_report_next_week)
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier.align(Alignment.End),
                    enabled = weekStartMs != currentWeek.startMs,
                    onClick = { weekStartMs = currentWeek.startMs }
                ) {
                    Text(stringResource(R.string.weekly_report_this_week))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.weekly_report_timeline_hours),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.weekly_report_timeline_hours_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HourDropdown(
                        label = stringResource(R.string.label_start),
                        value = timelineStartHour,
                        choices = 0 until timelineEndHour,
                        onValueChange = { timelineStartHour = it }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.range_separator_hyphen))
                    Spacer(Modifier.width(12.dp))
                    HourDropdown(
                        label = stringResource(R.string.label_end),
                        value = timelineEndHour,
                        choices = (timelineStartHour + 1)..24,
                        onValueChange = { timelineEndHour = it }
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.weekly_report_analysis_level),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalysisLevel.entries.forEach { option ->
                        FilterChip(
                            selected = level == option,
                            onClick = { level = option },
                            label = {
                                Text(
                                    when (option) {
                                        AnalysisLevel.Large -> stringResource(
                                            R.string.analysis_level_large
                                        )
                                        AnalysisLevel.Middle -> stringResource(
                                            R.string.analysis_level_middle
                                        )
                                        AnalysisLevel.Small -> stringResource(
                                            R.string.analysis_level_small
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
                Text(
                    stringResource(R.string.weekly_report_analysis_scope_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting,
            onClick = ::exportReport
        ) {
            if (isExporting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (isExporting) {
                    stringResource(R.string.weekly_report_creating)
                } else {
                    stringResource(R.string.weekly_report_create_pdf)
                }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HourDropdown(
    label: String,
    value: Int,
    choices: IntRange,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                stringResource(
                    R.string.weekly_report_hour_value,
                    label,
                    String.format(Locale.getDefault(), "%02d:00", value)
                )
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            choices.forEach { hour ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (hour == 24) "24:00" else {
                                String.format(Locale.getDefault(), "%02d:00", hour)
                            }
                        )
                    },
                    onClick = {
                        onValueChange(hour)
                        expanded = false
                    }
                )
            }
        }
    }
}
