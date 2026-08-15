package com.example.timerecorder.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timerecorder.data.TaskAnalysisBucket
import com.example.timerecorder.data.TaskAnalysisCategory
import com.example.timerecorder.data.TaskAnalysisEngine
import com.example.timerecorder.data.TaskAnalysisPeriod
import com.example.timerecorder.data.TaskAnalysisResult
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@Composable
internal fun TotalBarChart(result: TaskAnalysisResult, taskColor: Color) {
    val average = result.averagePerBucketMs
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val movingAverageColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val palette = analysisCategoryColors(
        categories = result.categories,
        fallbackColor = taskColor.takeIf {
            result.categories.all { category -> category.id == result.request.taskId }
        }
    )
    val movingAverageWindow = TaskAnalysisEngine.movingAverageWindow(result.request.period)
    val movingAverage = result.movingAverageMs
    val maxValue = max(
        max(result.buckets.maxOfOrNull { it.totalMs } ?: 0L, average),
        movingAverage.filterNotNull().maxOrNull() ?: 0L
    )
    val axis = remember(maxValue) { durationAxis(maxValue) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("期間別累計", style = MaterialTheme.typography.titleMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(top = 12.dp)
        ) {
            val baseline = size.height - 28.dp.toPx()
            val chartHeight = baseline - 8.dp.toPx()
            val plotLeft = 50.dp.toPx()
            val plotRight = size.width - 4.dp.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val slotWidth = plotWidth / result.buckets.size.coerceAtLeast(1)
            drawDurationYAxis(
                axis = axis,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = baseline - chartHeight,
                baseline = baseline,
                labelColor = labelColor,
                gridColor = lineColor.copy(alpha = 0.18f)
            )
            val averageY = baseline - chartHeight * average.toFloat() / axis.maxMs.toFloat()
            drawLine(
                color = lineColor.copy(alpha = 0.8f),
                start = Offset(plotLeft, averageY),
                end = Offset(plotRight, averageY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            result.buckets.forEachIndexed { index, bucket ->
                var bottom = baseline
                result.categories.forEach { category ->
                    val value = bucket.categoryTotals[category.id] ?: 0L
                    val segmentHeight =
                        chartHeight * value.toFloat() / axis.maxMs.toFloat()
                    if (segmentHeight > 0f) {
                        drawRect(
                            color = palette.getValue(category.id),
                            topLeft = Offset(
                                plotLeft + index * slotWidth + slotWidth * 0.18f,
                                bottom - segmentHeight
                            ),
                            size = Size(slotWidth * 0.64f, segmentHeight)
                        )
                        bottom -= segmentHeight
                    }
                }
            }
            val movingPath = Path()
            var hasMovingPoint = false
            movingAverage.forEachIndexed { index, value ->
                if (value != null) {
                    val x = plotLeft + index * slotWidth + slotWidth / 2f
                    val y = baseline - chartHeight * value.toFloat() / axis.maxMs.toFloat()
                    if (!hasMovingPoint) {
                        movingPath.moveTo(x, y)
                        hasMovingPoint = true
                    } else {
                        movingPath.lineTo(x, y)
                    }
                    drawCircle(movingAverageColor, radius = 2.dp.toPx(), center = Offset(x, y))
                }
            }
            if (hasMovingPoint) {
                drawPath(
                    path = movingPath,
                    color = movingAverageColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx()
                    )
                )
            }
            drawBucketLabels(
                buckets = result.buckets,
                plotLeft = plotLeft,
                plotWidth = plotWidth,
                y = baseline + 15.dp.toPx(),
                color = labelColor
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 50.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChartLineLegend("期間平均", lineColor, dashed = true)
            ChartLineLegend(
                movingAverageLabel(result, movingAverageWindow),
                movingAverageColor,
                dashed = false
            )
        }
        if (result.categories.any { it.id != result.request.taskId }) {
            AnalysisLegend(result.categories, palette)
        }
    }
}

@Composable
internal fun CumulativeStackedChart(result: TaskAnalysisResult) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val outline = MaterialTheme.colorScheme.outline
    val palette = analysisCategoryColors(result.categories)
    val completedThroughMs = minOf(result.request.nowMs, result.range.endMs)
    val cumulative = remember(result.buckets, completedThroughMs) {
        TaskAnalysisEngine.cumulativeCategoryTotals(
            buckets = result.buckets,
            completedThroughMs = completedThroughMs
        )
    }
    val maxValue = cumulative.maxOfOrNull { it.values.sum() } ?: 0L
    val axis = remember(maxValue) { durationAxis(maxValue) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("累積積み上げ", style = MaterialTheme.typography.titleMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 12.dp)
        ) {
            val baseline = size.height - 28.dp.toPx()
            val chartHeight = baseline - 8.dp.toPx()
            val plotLeft = 50.dp.toPx()
            val plotRight = size.width - 4.dp.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val slotWidth = plotWidth / cumulative.size.coerceAtLeast(1)
            drawDurationYAxis(
                axis = axis,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = baseline - chartHeight,
                baseline = baseline,
                labelColor = labelColor,
                gridColor = outline.copy(alpha = 0.18f)
            )
            cumulative.forEachIndexed { index, totals ->
                var bottom = baseline
                result.categories.forEach { category ->
                    val value = totals[category.id] ?: 0L
                    val height = chartHeight * value.toFloat() / axis.maxMs.toFloat()
                    if (height > 0f) {
                        drawRect(
                            color = palette.getValue(category.id),
                            topLeft = Offset(
                                plotLeft + index * slotWidth + slotWidth * 0.15f,
                                bottom - height
                            ),
                            size = Size(slotWidth * 0.7f, height)
                        )
                        bottom -= height
                    }
                }
            }
            drawBucketLabels(
                buckets = result.buckets,
                plotLeft = plotLeft,
                plotWidth = plotWidth,
                y = baseline + 15.dp.toPx(),
                color = labelColor
            )
        }
        AnalysisLegend(result.categories, palette)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBucketLabels(
    buckets: List<TaskAnalysisBucket>,
    plotLeft: Float,
    plotWidth: Float,
    y: Float,
    color: Int
) {
    if (buckets.isEmpty()) return
    val slotWidth = plotWidth / buckets.size
    val interval = when {
        buckets.size <= 12 -> 1
        buckets.size <= 24 -> 4
        else -> 5
    }
    val paint = Paint().apply {
        this.color = color
        textSize = 9.sp.toPx()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    buckets.forEachIndexed { index, bucket ->
        if (index % interval == 0 || index == buckets.lastIndex) {
            drawContext.canvas.nativeCanvas.drawText(
                bucket.label,
                plotLeft + index * slotWidth + slotWidth / 2f,
                y,
                paint
            )
        }
    }
}

private data class DurationAxis(
    val maxMs: Long,
    val ticks: List<Long>
)

private fun durationAxis(valueMs: Long, desiredIntervals: Int = 4): DurationAxis {
    val normalized = valueMs.coerceAtLeast(60_000L)
    val targetStep = ceil(normalized.toDouble() / desiredIntervals).toLong()
    val candidates = listOf(
        1_000L,
        5_000L,
        10_000L,
        30_000L,
        60_000L,
        2 * 60_000L,
        5 * 60_000L,
        10 * 60_000L,
        15 * 60_000L,
        30 * 60_000L,
        60 * 60_000L,
        2 * 60 * 60_000L,
        3 * 60 * 60_000L,
        4 * 60 * 60_000L,
        6 * 60 * 60_000L,
        8 * 60 * 60_000L,
        12 * 60 * 60_000L,
        24 * 60 * 60_000L
    )
    val step = candidates.firstOrNull { it >= targetStep }
        ?: niceWholeHours(targetStep)
    val maxMs = ceil(normalized.toDouble() / step).toLong() * step
    return DurationAxis(
        maxMs = maxMs,
        ticks = generateSequence(0L) { previous ->
            (previous + step).takeIf { it <= maxMs }
        }.toList()
    )
}

private fun niceWholeHours(targetStepMs: Long): Long {
    val targetHours = ceil(targetStepMs / 3_600_000.0).toLong().coerceAtLeast(1L)
    var magnitude = 1L
    while (targetHours > 10L * magnitude) magnitude *= 10L
    val multiplier = listOf(1L, 2L, 5L, 10L)
        .first { it * magnitude >= targetHours }
    return multiplier * magnitude * 3_600_000L
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDurationYAxis(
    axis: DurationAxis,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    baseline: Float,
    labelColor: Int,
    gridColor: Color
) {
    val paint = Paint().apply {
        color = labelColor
        textSize = 9.sp.toPx()
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    axis.ticks.forEach { tick ->
        val y = baseline - (baseline - plotTop) * tick.toFloat() / axis.maxMs.toFloat()
        drawLine(
            color = gridColor,
            start = Offset(plotLeft, y),
            end = Offset(plotRight, y),
            strokeWidth = 0.75.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            formatAxisDuration(tick),
            plotLeft - 6.dp.toPx(),
            y + 3.dp.toPx(),
            paint
        )
    }
    drawLine(
        color = Color(labelColor),
        start = Offset(plotLeft, plotTop),
        end = Offset(plotLeft, baseline),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = Color(labelColor),
        start = Offset(plotLeft, baseline),
        end = Offset(plotRight, baseline),
        strokeWidth = 1.dp.toPx()
    )
}

private fun formatAxisDuration(valueMs: Long): String {
    if (valueMs == 0L) return "0"
    val hour = 60L * 60L * 1_000L
    val minute = 60L * 1_000L
    return when {
        valueMs >= hour && valueMs % hour == 0L -> "${valueMs / hour}h"
        valueMs >= hour -> String.format(Locale.US, "%.1fh", valueMs.toDouble() / hour)
        valueMs >= minute -> "${valueMs / minute}m"
        else -> "${valueMs / 1_000L}s"
    }
}

@Composable
private fun ChartLineLegend(text: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 10.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = if (dashed) 1.dp.toPx() else 2.dp.toPx(),
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
                } else {
                    null
                }
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

private fun movingAverageLabel(result: TaskAnalysisResult, window: Int): String = when (
    result.request.period
) {
    TaskAnalysisPeriod.Day -> "${window}時間移動平均"
    TaskAnalysisPeriod.Week -> "${window}日移動平均"
    TaskAnalysisPeriod.Month -> "${window}日移動平均"
    TaskAnalysisPeriod.Year -> "${window}か月移動平均"
    TaskAnalysisPeriod.Custom -> "${window}日移動平均"
}

@Composable
internal fun ProbabilityHeatMap(result: TaskAnalysisResult) {
    val low = MaterialTheme.colorScheme.surfaceVariant
    val high = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.surface
    val weekdays = listOf("月", "火", "水", "木", "金", "土", "日")
    val maximumProbability = remember(result.heatCells) {
        result.heatCells.maxOfOrNull { it.probability } ?: 0.0
    }
    val normalizedCells = remember(result.heatCells) {
        val normalized = TaskAnalysisEngine.normalizeToMaximum(
            result.heatCells.map { it.probability }
        )
        result.heatCells.zip(normalized).associate { (cell, value) ->
            (cell.dayOfWeekIndex to cell.hourOfDay) to value
        }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("24時間の記録確率", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row {
            Column(modifier = Modifier.width(24.dp)) {
                weekdays.forEach { weekday ->
                    Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                        Text(weekday, fontSize = 10.sp)
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(154.dp)
            ) {
                val cellWidth = size.width / 24f
                val cellHeight = size.height / 7f
                for (weekday in 0 until 7) {
                    for (hour in 0 until 24) {
                        val probability = normalizedCells[weekday to hour] ?: 0.0
                        drawRect(
                            color = lerp(low, high, probability.toFloat().coerceIn(0f, 1f)),
                            topLeft = Offset(hour * cellWidth, weekday * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                        drawRect(
                            color = outline.copy(alpha = 0.55f),
                            topLeft = Offset(hour * cellWidth, weekday * cellHeight),
                            size = Size(cellWidth, cellHeight),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 0.5.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(start = 24.dp).fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24時").forEach { label ->
                Text(
                    label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("0%", style = MaterialTheme.typography.labelSmall)
            repeat(5) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(width = 18.dp, height = 10.dp)
                        .background(lerp(low, high, index / 4f))
                )
            }
            Text(
                "最大 ${formatProbability(maximumProbability)}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AnalysisLegend(
    categories: List<TaskAnalysisCategory>,
    colors: Map<Long, Color>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(colors.getValue(category.id), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(4.dp))
                Text(category.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun analysisCategoryColors(
    categories: List<TaskAnalysisCategory>,
    fallbackColor: Color? = null
): Map<Long, Color> {
    val fallback = listOf(
        Color(0xFF4E79A7),
        Color(0xFFF28E2B),
        Color(0xFFE15759),
        Color(0xFF76B7B2),
        Color(0xFF59A14F),
        Color(0xFFEDC948),
        Color(0xFFB07AA1),
        Color(0xFFFF9DA7)
    )
    return categories.mapIndexed { index, category ->
        val color = category.colorInt?.let(::Color)
            ?: fallbackColor?.takeIf { categories.size == 1 }
            ?: fallback[index % fallback.size]
        category.id to color
    }.toMap()
}

private fun formatProbability(value: Double): String =
    String.format(Locale.JAPAN, "%.0f%%", value.coerceIn(0.0, 1.0) * 100.0)
