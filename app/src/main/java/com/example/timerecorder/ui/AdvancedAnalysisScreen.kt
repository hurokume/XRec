package com.example.timerecorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timerecorder.data.AdvancedAnalysisResult
import com.example.timerecorder.data.AnalysisCategoryMetric
import com.example.timerecorder.data.AnalysisDayType
import com.example.timerecorder.data.AnalysisLevel
import com.example.timerecorder.data.AnalysisRequest
import com.example.timerecorder.data.AnalysisTimeProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdvancedAnalysisScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    var boundaryMinutes by rememberSaveable { mutableIntStateOf(240) }
    var level by rememberSaveable { mutableStateOf(AnalysisLevel.Large) }
    var dayType by rememberSaveable { mutableStateOf(AnalysisDayType.All) }
    var refreshAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val state by viewModel.advancedAnalysis.collectAsStateWithLifecycle()

    LaunchedEffect(days, boundaryMinutes, level, dayType, refreshAt) {
        viewModel.runAdvancedAnalysis(
            AnalysisRequest(
                nowMs = refreshAt,
                days = days,
                dayBoundaryMinutes = boundaryMinutes,
                slotMinutes = 30,
                level = level,
                dayType = dayType,
                timeZoneId = TimeZone.getDefault().id
            )
        )
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
                Text("高度分析", style = MaterialTheme.typography.titleLarge)
                Text(
                    "完了した解析日を対象に、時間配分・生活パターン・遷移を分析します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshAt = System.currentTimeMillis() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh analysis")
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
            AnalysisResultContent(result)
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
            Text("分析条件", style = MaterialTheme.typography.titleSmall)
            ChoiceRow("期間") {
                AnalysisChoice("7日", days == 7) { onDaysChange(7) }
                AnalysisChoice("30日", days == 30) { onDaysChange(30) }
                AnalysisChoice("90日", days == 90) { onDaysChange(90) }
            }
            ChoiceRow("日型") {
                AnalysisChoice("全日", dayType == AnalysisDayType.All) {
                    onDayTypeChange(AnalysisDayType.All)
                }
                AnalysisChoice("平日", dayType == AnalysisDayType.Weekday) {
                    onDayTypeChange(AnalysisDayType.Weekday)
                }
                AnalysisChoice("休日", dayType == AnalysisDayType.Weekend) {
                    onDayTypeChange(AnalysisDayType.Weekend)
                }
            }
            ChoiceRow("階層") {
                AnalysisChoice("大", level == AnalysisLevel.Large) {
                    onLevelChange(AnalysisLevel.Large)
                }
                AnalysisChoice("中", level == AnalysisLevel.Middle) {
                    onLevelChange(AnalysisLevel.Middle)
                }
                AnalysisChoice("小", level == AnalysisLevel.Small) {
                    onLevelChange(AnalysisLevel.Small)
                }
            }
            ChoiceRow("日境界") {
                AnalysisChoice("0時", boundaryMinutes == 0) { onBoundaryChange(0) }
                AnalysisChoice("4時", boundaryMinutes == 240) { onBoundaryChange(240) }
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
private fun AnalysisResultContent(result: AdvancedAnalysisResult) {
    AnalysisSection("1. データ品質") {
        val q = result.quality
        KeyValue("対象日", "${q.analyzedDays}日（暦期間 ${q.calendarDays}日）")
        KeyValue("記録率", percent(q.coverageRate))
        KeyValue("記録時間", formatDuration(q.recordedMs))
        KeyValue("未記録時間", formatDuration(q.missingMs))
        KeyValue("重複時間", formatDuration(q.overlapMs))
        KeyValue("分類未指定", formatDuration(q.unspecifiedMs))
        KeyValue("60秒未満の記録", "${q.shortEventCount}件")
        KeyValue("95%以上記録された日", "${q.daysAtLeast95Percent}日")
    }

    AnalysisSection("2. 階層別時間配分・Pareto") {
        if (result.categoryMetrics.isEmpty()) {
            Text("対象期間に記録がありません。")
        } else {
            result.categoryMetrics.take(20).forEach { metric ->
                CategoryMetricCard(metric)
            }
        }
    }

    AnalysisSection("3. 24時間行動確率") {
        Text(
            "色が濃いほど、その時刻に行っていた日の割合が高いことを示します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ProbabilityHeatmap(result.timeProfile)
        Spacer(Modifier.height(8.dp))
        KeyValue("規則性", percent(result.timeProfile.regularity))
        KeyValue("平均エントロピー", formatDecimal(result.timeProfile.averageEntropy))
        val modal = modalDaySummary(result.timeProfile)
        if (modal.isNotEmpty()) {
            Text("最頻行動で合成した一日", fontWeight = FontWeight.SemiBold)
            Text(modal, style = MaterialTheme.typography.bodySmall)
        }
    }

    AnalysisSection("4. 代表日・典型日") {
        val formatter = remember { SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault()) }
        result.representativeDay?.let { representative ->
            Text("実在する代表日", fontWeight = FontWeight.SemiBold)
            KeyValue("日付", formatter.format(Date(representative.dayStartMs)))
            KeyValue("平均距離", formatDecimal(representative.meanDistance))
            KeyValue("記録率", percent(representative.coverageRate))
        } ?: Text("代表日を計算できる記録がありません。")

        if (result.typicalPatterns.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("典型的な生活パターン", fontWeight = FontWeight.SemiBold)
            result.typicalPatterns.forEachIndexed { index, pattern ->
                Text(
                    "型${index + 1}: ${formatter.format(Date(pattern.medoidDayStartMs))} " +
                            "${pattern.dayCount}日 (${percent(pattern.share)}) " +
                            "平日${pattern.weekdayCount}/休日${pattern.weekendCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    AnalysisSection("5. 行動遷移") {
        if (result.transitions.isEmpty()) {
            Text("連続する行動遷移がありません。")
        } else {
            result.transitions.take(15).forEach { transition ->
                Text(
                    "${transition.fromName} → ${transition.toName}: " +
                            "${transition.count}回 / ${percent(transition.probability)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AnalysisSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            content()
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
                "総計 ${formatDuration(metric.totalMs)} / 日平均 ${formatDuration(metric.averagePerDayMs)} " +
                        "/ 全体 ${percent(metric.share)} / 親内 ${percent(metric.parentShare)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "中央値 ${formatDuration(metric.medianPerDayMs)} " +
                        "[Q1 ${formatDuration(metric.firstQuartileMs)}, Q3 ${formatDuration(metric.thirdQuartileMs)}]",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${metric.sessionCount}回 (${formatDecimal(metric.occurrencesPerDay)}回/日) / " +
                        "平均 ${formatDuration(metric.averageSessionMs)} / 最長 ${formatDuration(metric.maxSessionMs)} / " +
                        "実施日 ${percent(metric.activeDayRate)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "断片化 ${formatDecimal(metric.fragmentationPerHour)}区間/時間 / " +
                        "累積 ${percent(metric.cumulativeShare)}",
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
private fun ProbabilityHeatmap(profile: AnalysisTimeProfile) {
    val categories = profile.categories.take(10)
    if (categories.isEmpty()) {
        Text("時刻別確率を計算できる記録がありません。")
        return
    }

    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(112.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { index ->
                val minute = (profile.boundaryMinutes + index * 6 * 60) % 1440
                Text(String.format(Locale.getDefault(), "%02d", minute / 60), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    categories.forEach { category ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.pathLabel,
                modifier = Modifier.width(112.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
            val baseColor = colorForId(category.id)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(19.dp)
            ) {
                val cellWidth = size.width / category.probabilities.size.coerceAtLeast(1)
                category.probabilities.forEachIndexed { index, probability ->
                    drawRect(
                        color = baseColor.copy(alpha = (0.08 + probability * 0.92).toFloat()),
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
