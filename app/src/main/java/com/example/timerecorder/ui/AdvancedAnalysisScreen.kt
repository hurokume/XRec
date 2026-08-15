package com.example.timerecorder.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.R
import com.example.timerecorder.data.AdvancedAnalysisResult
import com.example.timerecorder.data.AnalysisCategoryMetric
import com.example.timerecorder.data.AnalysisDayType
import com.example.timerecorder.data.AnalysisLevel
import com.example.timerecorder.data.AnalysisRequest
import com.example.timerecorder.data.AnalysisTimeProfile
import com.example.timerecorder.data.DailyPlans
import com.example.timerecorder.data.DailyPlanType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdvancedAnalysisScreen(
    viewModel: TaskViewModel,
    dailyPlans: DailyPlans,
    modifier: Modifier = Modifier
) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    var boundaryMinutes by rememberSaveable { mutableIntStateOf(240) }
    var level by rememberSaveable { mutableStateOf(AnalysisLevel.Large) }
    var dayType by rememberSaveable { mutableStateOf(AnalysisDayType.All) }
    var refreshAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var probabilityItemLimit by rememberSaveable { mutableIntStateOf(20) }
    var paletteMask by rememberSaveable { mutableIntStateOf(AnalysisPalette.defaultMask) }
    var isExportingPdf by remember { mutableStateOf(false) }
    val state by viewModel.advancedAnalysis.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(days, boundaryMinutes, level, dayType, refreshAt, dailyPlans) {
        viewModel.runAdvancedAnalysis(
            AnalysisRequest(
                nowMs = refreshAt,
                days = days,
                dayBoundaryMinutes = boundaryMinutes,
                slotMinutes = 30,
                level = level,
                dayType = dayType,
                timeZoneId = TimeZone.getDefault().id
            ),
            dailyPlans = dailyPlans
        )
    }

    fun exportPdf() {
        val result = state.result ?: return
        scope.launch {
            isExportingPdf = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    createAdvancedAnalysisReportPdf(
                        context = context,
                        result = result,
                        probabilityItemLimit = probabilityItemLimit,
                        paletteMask = paletteMask
                    )
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        context.getString(R.string.advanced_analysis_share_subject)
                    )
                }
                context.startActivity(
                    Intent.createChooser(
                        share,
                        context.getString(R.string.advanced_analysis_share_chooser)
                    )
                )
            } catch (error: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.advanced_analysis_pdf_error,
                        error.message.orEmpty()
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isExportingPdf = false
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.advanced_analysis_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(R.string.advanced_analysis_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshAt = System.currentTimeMillis() }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.advanced_analysis_refresh)
                )
            }
        }

        AnalysisFilters(
            days = days,
            onDaysChange = { days = it },
            level = level,
            onLevelChange = { level = it },
            dayType = dayType,
            onDayTypeChange = { dayType = it },
            boundaryMinutes = boundaryMinutes,
            onBoundaryChange = { boundaryMinutes = it }
        )

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.result != null && !state.isLoading && !isExportingPdf,
            onClick = ::exportPdf
        ) {
            if (isExportingPdf) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (isExportingPdf) {
                    stringResource(R.string.advanced_analysis_pdf_creating)
                } else {
                    stringResource(R.string.advanced_analysis_pdf_create)
                }
            )
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        state.errorMessage?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        state.result?.let { result ->
            AnalysisResultContent(
                result = result,
                probabilityItemLimit = probabilityItemLimit,
                onProbabilityItemLimitChange = { probabilityItemLimit = it },
                paletteMask = paletteMask,
                onPaletteMaskChange = { paletteMask = it }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnalysisFilters(
    days: Int,
    onDaysChange: (Int) -> Unit,
    level: AnalysisLevel,
    onLevelChange: (AnalysisLevel) -> Unit,
    dayType: AnalysisDayType,
    onDayTypeChange: (AnalysisDayType) -> Unit,
    boundaryMinutes: Int,
    onBoundaryChange: (Int) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.advanced_analysis_conditions),
                style = MaterialTheme.typography.titleSmall
            )
            ChoiceRow(stringResource(R.string.advanced_analysis_period)) {
                AnalysisChoice(stringResource(R.string.advanced_analysis_days, 7), days == 7) {
                    onDaysChange(7)
                }
                AnalysisChoice(stringResource(R.string.advanced_analysis_days, 30), days == 30) {
                    onDaysChange(30)
                }
                AnalysisChoice(stringResource(R.string.advanced_analysis_days, 90), days == 90) {
                    onDaysChange(90)
                }
            }
            ChoiceRow(stringResource(R.string.advanced_analysis_day_type)) {
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_all_days),
                    dayType == AnalysisDayType.All
                ) {
                    onDayTypeChange(AnalysisDayType.All)
                }
                AnalysisChoice(
                    stringResource(R.string.label_weekday),
                    dayType == AnalysisDayType.Weekday
                ) {
                    onDayTypeChange(AnalysisDayType.Weekday)
                }
                AnalysisChoice(
                    stringResource(R.string.label_holiday),
                    dayType == AnalysisDayType.Weekend
                ) {
                    onDayTypeChange(AnalysisDayType.Weekend)
                }
            }
            ChoiceRow(stringResource(R.string.advanced_analysis_level)) {
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_level_large_short),
                    level == AnalysisLevel.Large
                ) {
                    onLevelChange(AnalysisLevel.Large)
                }
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_level_middle_short),
                    level == AnalysisLevel.Middle
                ) {
                    onLevelChange(AnalysisLevel.Middle)
                }
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_level_small_short),
                    level == AnalysisLevel.Small
                ) {
                    onLevelChange(AnalysisLevel.Small)
                }
            }
            ChoiceRow(stringResource(R.string.advanced_analysis_day_boundary)) {
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_hour, 0),
                    boundaryMinutes == 0
                ) { onBoundaryChange(0) }
                AnalysisChoice(
                    stringResource(R.string.advanced_analysis_hour, 4),
                    boundaryMinutes == 240
                ) { onBoundaryChange(240) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun AnalysisChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun AnalysisResultContent(
    result: AdvancedAnalysisResult,
    probabilityItemLimit: Int,
    onProbabilityItemLimitChange: (Int) -> Unit,
    paletteMask: Int,
    onPaletteMaskChange: (Int) -> Unit
) {
    AnalysisSection(stringResource(R.string.advanced_analysis_section_daily_plan)) {
        val achievements = result.dailyAchievements
        if (achievements.isEmpty()) {
            Text(stringResource(R.string.advanced_analysis_no_daily_plan))
        } else {
            val totalPlanned = achievements.sumOf { it.plannedMs }
            val totalAchieved = achievements.sumOf { it.achievedMs }
            val overall = if (totalPlanned > 0L) totalAchieved.toDouble() / totalPlanned else 0.0
            KeyValue(stringResource(R.string.advanced_analysis_entire_period), percent(overall))
            val formatter = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
            achievements.forEach { achievement ->
                val type = when (achievement.planType) {
                    DailyPlanType.Weekday -> stringResource(R.string.label_weekday)
                    DailyPlanType.Holiday -> stringResource(R.string.label_holiday)
                }
                Text(
                    stringResource(
                        R.string.advanced_analysis_achievement_row,
                        formatter.format(Date(achievement.dayStartMs)),
                        type,
                        percent(achievement.achievementRate),
                        formatDuration(achievement.achievedMs),
                        formatDuration(achievement.plannedMs)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    AnalysisSection(stringResource(R.string.advanced_analysis_section_data_quality)) {
        val q = result.quality
        KeyValue(
            stringResource(R.string.advanced_analysis_target_days),
            stringResource(
                R.string.advanced_analysis_target_days_value,
                q.analyzedDays,
                q.calendarDays
            )
        )
        KeyValue(stringResource(R.string.advanced_analysis_coverage_rate), percent(q.coverageRate))
        KeyValue(stringResource(R.string.advanced_analysis_recorded_time), formatDuration(q.recordedMs))
        KeyValue(stringResource(R.string.advanced_analysis_missing_time), formatDuration(q.missingMs))
        KeyValue(stringResource(R.string.advanced_analysis_overlap_time), formatDuration(q.overlapMs))
        KeyValue(stringResource(R.string.advanced_analysis_unspecified), formatDuration(q.unspecifiedMs))
        KeyValue(
            stringResource(R.string.advanced_analysis_short_records),
            stringResource(R.string.label_item_count, q.shortEventCount)
        )
        KeyValue(
            stringResource(R.string.advanced_analysis_days_95),
            stringResource(R.string.advanced_analysis_days, q.daysAtLeast95Percent)
        )
    }

    AnalysisSection(stringResource(R.string.advanced_analysis_section_distribution)) {
        if (result.categoryMetrics.isEmpty()) {
            Text(stringResource(R.string.advanced_analysis_no_records))
        } else {
            result.categoryMetrics.take(20).forEach { metric ->
                CategoryMetricCard(metric)
            }
        }
    }

    AnalysisSection(stringResource(R.string.advanced_analysis_section_probability)) {
        Text(
            stringResource(R.string.advanced_analysis_probability_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ChoiceRow(stringResource(R.string.advanced_analysis_display)) {
            AnalysisChoice(stringResource(R.string.label_item_count, 10), probabilityItemLimit == 10) {
                onProbabilityItemLimitChange(10)
            }
            AnalysisChoice(stringResource(R.string.label_item_count, 20), probabilityItemLimit == 20) {
                onProbabilityItemLimitChange(20)
            }
            AnalysisChoice(
                stringResource(R.string.advanced_analysis_all_items),
                probabilityItemLimit == Int.MAX_VALUE
            ) {
                onProbabilityItemLimitChange(Int.MAX_VALUE)
            }
        }
        Text(
            stringResource(R.string.advanced_analysis_palette_title),
            style = MaterialTheme.typography.labelLarge
        )
        ProbabilityPaletteSelector(
            selectedMask = paletteMask,
            onSelectedMaskChange = onPaletteMaskChange
        )
        Spacer(Modifier.height(4.dp))
        ProbabilityHeatmap(
            profile = result.timeProfile,
            itemLimit = probabilityItemLimit,
            paletteMask = paletteMask
        )
        Spacer(Modifier.height(8.dp))
        KeyValue(
            stringResource(R.string.advanced_analysis_regularity),
            percent(result.timeProfile.regularity)
        )
        KeyValue(
            stringResource(R.string.advanced_analysis_average_entropy),
            formatDecimal(result.timeProfile.averageEntropy)
        )
        val modal = modalDaySummary(result.timeProfile)
        if (modal.isNotEmpty()) {
            Text(
                stringResource(R.string.advanced_analysis_modal_day),
                fontWeight = FontWeight.SemiBold
            )
            Text(modal, style = MaterialTheme.typography.bodySmall)
        }
    }

    AnalysisSection(stringResource(R.string.advanced_analysis_section_representative)) {
        val formatter = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
        result.representativeDay?.let { representative ->
            Text(
                stringResource(R.string.advanced_analysis_representative_day),
                fontWeight = FontWeight.SemiBold
            )
            KeyValue(
                stringResource(R.string.advanced_analysis_date),
                formatter.format(Date(representative.dayStartMs))
            )
            KeyValue(
                stringResource(R.string.advanced_analysis_mean_distance),
                formatDecimal(representative.meanDistance)
            )
            KeyValue(
                stringResource(R.string.advanced_analysis_coverage_rate),
                percent(representative.coverageRate)
            )
        } ?: Text(stringResource(R.string.advanced_analysis_no_representative_day))

        if (result.typicalPatterns.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.advanced_analysis_typical_patterns),
                fontWeight = FontWeight.SemiBold
            )
            result.typicalPatterns.forEachIndexed { index, pattern ->
                Text(
                    stringResource(
                        R.string.advanced_analysis_pattern_row,
                        index + 1,
                        formatter.format(Date(pattern.medoidDayStartMs)),
                        pattern.dayCount,
                        percent(pattern.share),
                        pattern.weekdayCount,
                        pattern.weekendCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    AnalysisSection(stringResource(R.string.advanced_analysis_section_transitions)) {
        if (result.transitions.isEmpty()) {
            Text(stringResource(R.string.advanced_analysis_no_transitions))
        } else {
            result.transitions.take(15).forEach { transition ->
                Text(
                    stringResource(
                        R.string.advanced_analysis_transition_row,
                        transition.fromName,
                        transition.toName,
                        transition.count,
                        percent(transition.probability)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AnalysisSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.advanced_analysis_collapse)
                    } else {
                        stringResource(R.string.advanced_analysis_expand)
                    }
                )
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun CategoryMetricCard(metric: AnalysisCategoryMetric) {
    val barColor = colorForId(metric.id)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                metric.pathLabel,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(
                    R.string.advanced_analysis_metric_summary,
                    formatDuration(metric.totalMs),
                    formatDuration(metric.averagePerDayMs),
                    percent(metric.share),
                    percent(metric.parentShare)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.advanced_analysis_metric_quartiles,
                    formatDuration(metric.medianPerDayMs),
                    formatDuration(metric.firstQuartileMs),
                    formatDuration(metric.thirdQuartileMs)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.advanced_analysis_metric_sessions,
                    metric.sessionCount,
                    formatDecimal(metric.occurrencesPerDay),
                    formatDuration(metric.averageSessionMs),
                    formatDuration(metric.maxSessionMs),
                    percent(metric.activeDayRate)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.advanced_analysis_metric_fragmentation,
                    formatDecimal(metric.fragmentationPerHour),
                    percent(metric.cumulativeShare)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(99.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(metric.share.toFloat().coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(barColor, RoundedCornerShape(99.dp))
                )
            }
        }
    }
}

@Composable
private fun ProbabilityPaletteSelector(
    selectedMask: Int,
    onSelectedMaskChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnalysisPalette.entries.forEach { palette ->
            val selected = selectedMask and palette.bit != 0
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selected,
                onClick = {
                    val updated = if (selected) {
                        selectedMask and palette.bit.inv()
                    } else {
                        selectedMask or palette.bit
                    }
                    if (updated != 0) onSelectedMaskChange(updated)
                },
                label = {
                    Text(
                        stringResource(palette.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(
                                Color(probabilityColorArgb(palette, 0.75)),
                                RoundedCornerShape(99.dp)
                            )
                    )
                }
            )
        }
    }
}

@Composable
private fun ProbabilityHeatmap(
    profile: AnalysisTimeProfile,
    itemLimit: Int,
    paletteMask: Int
) {
    val categories = if (itemLimit == Int.MAX_VALUE) {
        profile.categories
    } else {
        profile.categories.take(itemLimit)
    }
    if (categories.isEmpty()) {
        Text(stringResource(R.string.advanced_analysis_no_probability))
        return
    }
    val palettes = selectedAnalysisPalettes(paletteMask)

    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(112.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { index ->
                val minute = (profile.boundaryMinutes + index * 6 * 60) % 1440
                Text(String.format(Locale.getDefault(), "%02d", minute / 60), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    categories.forEachIndexed { categoryIndex, category ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.pathLabel,
                modifier = Modifier.width(112.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
            val palette = palettes[categoryIndex % palettes.size]
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(19.dp)
            ) {
                val cellWidth = size.width / category.probabilities.size.coerceAtLeast(1)
                category.probabilities.forEachIndexed { index, probability ->
                    drawRect(
                        color = Color(probabilityColorArgb(palette, probability)),
                        topLeft = Offset(index * cellWidth, 1f),
                        size = Size((cellWidth - 1f).coerceAtLeast(0.5f), size.height - 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun modalDaySummary(profile: AnalysisTimeProfile): String {
    val entries = mutableListOf<String>()
    var previous: String? = null
    profile.modalLabels.forEachIndexed { index, label ->
        if (label != previous && label != null) {
            val minute = (profile.boundaryMinutes + index * profile.slotMinutes) % 1440
            entries += String.format(Locale.getDefault(), "%02d:%02d %s", minute / 60, minute % 60, label)
            previous = label
        } else if (label == null) {
            previous = null
        }
    }
    return entries.take(16).joinToString(" → ") + if (entries.size > 16) " …" else ""
}

private fun colorForId(id: Long): Color {
    val hue = ((id % 360L + 360L) % 360L).toFloat()
    return Color.hsv(hue, saturation = 0.58f, value = 0.84f)
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
}

private fun percent(value: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", value.coerceAtLeast(0.0) * 100.0)

private fun formatDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)
