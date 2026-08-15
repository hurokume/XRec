package com.example.timerecorder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.timerecorder.R
import com.example.timerecorder.data.AdvancedAnalysisResult
import com.example.timerecorder.data.AnalysisCategoryMetric
import com.example.timerecorder.data.DailyPlanType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val A4_WIDTH_POINTS = 595
private const val A4_HEIGHT_POINTS = 842

fun createAdvancedAnalysisReportPdf(
    context: Context,
    result: AdvancedAnalysisResult,
    probabilityItemLimit: Int,
    paletteMask: Int
): Uri {
    val outputDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outputFile = File(outputDirectory, "analysis_$timestamp.pdf")
    val document = PdfDocument()

    try {
        AdvancedAnalysisPdfWriter(
            document = document,
            stringResolver = context::getString,
            reportTitle = context.getString(R.string.advanced_analysis_share_subject),
            reportSubtitle = context.getString(R.string.advanced_analysis_pdf_subtitle)
        ).write(
            result = result,
            probabilityItemLimit = probabilityItemLimit,
            paletteMask = paletteMask
        )
        FileOutputStream(outputFile).use { output -> document.writeTo(output) }
    } finally {
        document.close()
    }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile
    )
}

internal class AdvancedAnalysisPdfWriter(
    private val document: PdfDocument,
    private val stringResolver: (Int) -> String,
    firstPageNumber: Int = 1,
    private val reportTitle: String,
    private val reportSubtitle: String,
    private val pageBreakBeforeProbability: Boolean = false
) {
    private val pageWidth = A4_WIDTH_POINTS.toFloat()
    private val pageHeight = A4_HEIGHT_POINTS.toFloat()
    private val margin = 40f
    private val contentWidth = pageWidth - margin * 2f
    private val contentBottom = pageHeight - 46f
    private val generatedAt = SimpleDateFormat(
        "yyyy/MM/dd HH:mm",
        Locale.getDefault()
    ).format(Date())
    private val regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularTypeface }
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNumber = firstPageNumber - 1
    private var y = 0f

    fun write(
        result: AdvancedAnalysisResult,
        probabilityItemLimit: Int,
        paletteMask: Int
    ) {
        startPage()
        drawReportSummary(result)
        drawAchievementSection(result)
        drawQualitySection(result)
        drawCategorySection(result)
        if (pageBreakBeforeProbability) startNewPage()
        drawProbabilitySection(result, probabilityItemLimit, paletteMask)
        drawRepresentativeSection(result)
        drawTransitionSection(result)
        finishPage()
    }

    private fun startPage() {
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(
                A4_WIDTH_POINTS,
                A4_HEIGHT_POINTS,
                pageNumber
            ).create()
        )
        canvas = requireNotNull(page).canvas.apply { drawColor(Color.WHITE) }

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(31, 55, 86)
        requireCanvas().drawRect(0f, 0f, pageWidth, 62f, paint)
        drawText(
            text = reportTitle,
            x = margin,
            baseline = 30f,
            size = 18f,
            color = Color.WHITE,
            medium = true
        )
        drawText(
            text = reportSubtitle,
            x = margin,
            baseline = 48f,
            size = 8.5f,
            color = Color.rgb(218, 229, 242)
        )
        y = 80f
    }

    private fun finishPage() {
        val activePage = page ?: return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = Color.rgb(205, 213, 222)
        requireCanvas().drawLine(margin, pageHeight - 31f, pageWidth - margin, pageHeight - 31f, paint)
        paint.style = Paint.Style.FILL
        drawText(
            text = "TimeRecorder | $generatedAt",
            x = margin,
            baseline = pageHeight - 17f,
            size = 8f,
            color = Color.rgb(92, 105, 120)
        )
        val pageLabel = "Page $pageNumber"
        configurePaint(8f, Color.rgb(92, 105, 120), false)
        drawText(
            text = pageLabel,
            x = pageWidth - margin - paint.measureText(pageLabel),
            baseline = pageHeight - 17f,
            size = 8f,
            color = Color.rgb(92, 105, 120)
        )
        document.finishPage(activePage)
        page = null
        canvas = null
    }

    private fun ensureSpace(height: Float): Boolean {
        if (y + height <= contentBottom) return false
        finishPage()
        startPage()
        return true
    }

    private fun startNewPage() {
        finishPage()
        startPage()
    }

    private fun drawReportSummary(result: AdvancedAnalysisResult) {
        sectionTitle("分析概要")
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        keyValue(
            "分析期間",
            "${dateFormat.format(Date(result.range.startMs))} - ${dateFormat.format(Date(result.range.endMs))}"
        )
        keyValue("対象日", "${result.quality.analyzedDays}日（暦期間 ${result.quality.calendarDays}日）")
        keyValue("日境界 / 時間枠", "${formatClock(result.timeProfile.boundaryMinutes)} / ${result.timeProfile.slotMinutes}分")
        keyValue("レポート作成", generatedAt)
        y += 8f
    }

    private fun drawQualitySection(result: AdvancedAnalysisResult) {
        sectionTitle("2. データ品質")
        val quality = result.quality
        keyValue("記録率", formatPercent(quality.coverageRate))
        keyValue("記録時間", formatDuration(quality.recordedMs))
        keyValue("未記録時間", formatDuration(quality.missingMs))
        keyValue("重複時間", formatDuration(quality.overlapMs))
        keyValue("分類未指定", formatDuration(quality.unspecifiedMs))
        keyValue("60秒未満の記録", "${quality.shortEventCount}件")
        keyValue("95%以上記録された日", "${quality.daysAtLeast95Percent}日")
        y += 8f
    }

    private fun drawCategorySection(result: AdvancedAnalysisResult) {
        sectionTitle("3. 階層別時間配分・Pareto")
        if (result.categoryMetrics.isEmpty()) {
            paragraph("対象期間に記録がありません。")
            return
        }
        result.categoryMetrics.take(20).forEachIndexed { index, metric ->
            categoryMetric(index + 1, metric)
        }
        if (result.categoryMetrics.size > 20) {
            paragraph("上位20件を掲載（全${result.categoryMetrics.size}件）", color = Color.DKGRAY)
        }
        y += 8f
    }

    private fun categoryMetric(rank: Int, metric: AnalysisCategoryMetric) {
        ensureSpace(50f)
        val label = "$rank. ${metric.pathLabel}"
        drawEllipsizedText(label, margin, y + 10f, contentWidth, 10f, Color.rgb(36, 48, 61), true)
        y += 15f
        drawText(
            text = "総計 ${formatDuration(metric.totalMs)}  日平均 ${formatDuration(metric.averagePerDayMs)}  " +
                    "構成比 ${formatPercent(metric.share)}  実施日 ${formatPercent(metric.activeDayRate)}",
            x = margin,
            baseline = y + 9f,
            size = 8f,
            color = Color.rgb(82, 95, 109)
        )
        y += 14f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(231, 235, 239)
        requireCanvas().drawRoundRect(margin, y, margin + contentWidth, y + 5f, 2.5f, 2.5f, paint)
        paint.color = categoryColor(metric.id)
        requireCanvas().drawRoundRect(
            margin,
            y,
            margin + contentWidth * metric.share.toFloat().coerceIn(0f, 1f),
            y + 5f,
            2.5f,
            2.5f,
            paint
        )
        y += 13f
    }

    private fun drawProbabilitySection(
        result: AdvancedAnalysisResult,
        probabilityItemLimit: Int,
        paletteMask: Int
    ) {
        sectionTitle("4. 24時間行動確率")
        paragraph("色が濃いほど、その時刻に行っていた日の割合が高いことを示します。")
        val palettes = selectedAnalysisPalettes(paletteMask)
        paragraph(
            "パレット: ${palettes.joinToString(" / ") { stringResolver(it.labelRes) }}",
            size = 8f,
            color = Color.rgb(82, 95, 109)
        )
        val categories = if (probabilityItemLimit == Int.MAX_VALUE) {
            result.timeProfile.categories
        } else {
            result.timeProfile.categories.take(probabilityItemLimit)
        }
        if (categories.isEmpty()) {
            paragraph("時刻別確率を計算できる記録がありません。")
        } else {
            drawHeatmapAxis(result)
            categories.forEachIndexed { index, category ->
                if (ensureSpace(15f)) drawHeatmapAxis(result)
                val labelWidth = 128f
                drawEllipsizedText(
                    category.pathLabel,
                    margin,
                    y + 10f,
                    labelWidth - 7f,
                    7.2f,
                    Color.rgb(48, 59, 72),
                    false
                )
                val chartLeft = margin + labelWidth
                val chartWidth = contentWidth - labelWidth
                val cellWidth = chartWidth / category.probabilities.size.coerceAtLeast(1)
                val palette = palettes[index % palettes.size]
                category.probabilities.forEachIndexed { slot, probability ->
                    paint.style = Paint.Style.FILL
                    paint.color = probabilityColorArgb(palette, probability)
                    val left = chartLeft + slot * cellWidth
                    requireCanvas().drawRect(
                        left,
                        y + 1f,
                        (left + cellWidth - 0.35f).coerceAtLeast(left + 0.3f),
                        y + 12f,
                        paint
                    )
                }
                y += 14f
            }
        }
        y += 5f
        keyValue("規則性", formatPercent(result.timeProfile.regularity))
        keyValue("平均エントロピー", String.format(Locale.getDefault(), "%.2f", result.timeProfile.averageEntropy))
        val modal = modalDaySummaryForReport(result)
        if (modal.isNotEmpty()) {
            y += 4f
            paragraph("最頻行動で合成した一日", medium = true)
            paragraph(modal, size = 8f, color = Color.rgb(82, 95, 109))
        }
        y += 8f
    }

    private fun drawHeatmapAxis(result: AdvancedAnalysisResult) {
        ensureSpace(22f)
        val labelWidth = 128f
        val chartLeft = margin + labelWidth
        val chartWidth = contentWidth - labelWidth
        repeat(5) { index ->
            val minute = (result.timeProfile.boundaryMinutes + index * 6 * 60) % 1440
            val label = String.format(Locale.getDefault(), "%02d", minute / 60)
            val x = chartLeft + chartWidth * index / 4f
            configurePaint(7f, Color.rgb(92, 105, 120), false)
            drawText(label, x - paint.measureText(label) / 2f, y + 8f, 7f, Color.rgb(92, 105, 120))
        }
        y += 12f
    }

    private fun drawRepresentativeSection(result: AdvancedAnalysisResult) {
        sectionTitle("5. 代表日・典型日")
        val formatter = SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault())
        result.representativeDay?.let { representative ->
            paragraph("実在する代表日", medium = true)
            keyValue("日付", formatter.format(Date(representative.dayStartMs)))
            keyValue("平均距離", String.format(Locale.getDefault(), "%.2f", representative.meanDistance))
            keyValue("記録率", formatPercent(representative.coverageRate))
        } ?: paragraph("代表日を計算できる記録がありません。")

        if (result.typicalPatterns.isNotEmpty()) {
            y += 5f
            paragraph("典型的な生活パターン", medium = true)
            result.typicalPatterns.forEachIndexed { index, pattern ->
                paragraph(
                    "型${index + 1}: ${formatter.format(Date(pattern.medoidDayStartMs))}  " +
                            "${pattern.dayCount}日 (${formatPercent(pattern.share)})  " +
                            "平日${pattern.weekdayCount} / 休日${pattern.weekendCount}",
                    size = 8.5f
                )
            }
        }
        y += 8f
    }

    private fun drawAchievementSection(result: AdvancedAnalysisResult) {
        sectionTitle("1. Daily plan 達成度")
        val achievements = result.dailyAchievements
        if (achievements.isEmpty()) {
            paragraph("対象日に利用できるDaily planがありません。Settingsで予定を設定してください。")
            return
        }
        val totalPlanned = achievements.sumOf { it.plannedMs }
        val totalAchieved = achievements.sumOf { it.achievedMs }
        val overall = if (totalPlanned > 0L) totalAchieved.toDouble() / totalPlanned else 0.0
        keyValue("期間全体", formatPercent(overall))
        val formatter = SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault())
        achievements.forEach { achievement ->
            val type = when (achievement.planType) {
                DailyPlanType.Weekday -> "平日"
                DailyPlanType.Holiday -> "休日"
            }
            paragraph(
                "${formatter.format(Date(achievement.dayStartMs))} [$type]  " +
                        "${formatPercent(achievement.achievementRate)}  " +
                        "${formatDuration(achievement.achievedMs)} / ${formatDuration(achievement.plannedMs)}",
                size = 8.5f
            )
        }
        y += 8f
    }

    private fun drawTransitionSection(result: AdvancedAnalysisResult) {
        sectionTitle("6. 行動遷移")
        if (result.transitions.isEmpty()) {
            paragraph("連続する行動遷移がありません。")
            return
        }
        result.transitions.take(15).forEachIndexed { index, transition ->
            paragraph(
                "${index + 1}. ${transition.fromName} → ${transition.toName}: " +
                        "${transition.count}回 / ${formatPercent(transition.probability)}",
                size = 8.5f
            )
        }
    }

    private fun sectionTitle(title: String) {
        ensureSpace(34f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(232, 239, 247)
        requireCanvas().drawRoundRect(margin, y, margin + contentWidth, y + 25f, 4f, 4f, paint)
        drawText(
            text = title,
            x = margin + 10f,
            baseline = y + 17f,
            size = 11.5f,
            color = Color.rgb(31, 55, 86),
            medium = true
        )
        y += 33f
    }

    private fun keyValue(label: String, value: String) {
        ensureSpace(17f)
        drawText(label, margin, y + 10f, 8.5f, Color.rgb(73, 86, 100))
        configurePaint(8.5f, Color.rgb(36, 48, 61), true)
        drawEllipsizedText(
            text = value,
            x = margin + 165f,
            baseline = y + 10f,
            maxWidth = contentWidth - 165f,
            size = 8.5f,
            color = Color.rgb(36, 48, 61),
            medium = true
        )
        y += 15f
    }

    private fun paragraph(
        text: String,
        size: Float = 9f,
        color: Int = Color.rgb(48, 59, 72),
        medium: Boolean = false
    ) {
        configurePaint(size, color, medium)
        val lines = wrapText(text, contentWidth)
        val lineHeight = size * 1.45f
        lines.forEach { line ->
            ensureSpace(lineHeight)
            drawText(line, margin, y + size, size, color, medium)
            y += lineHeight
        }
        y += 2f
    }

    private fun wrapText(text: String, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.forEach { character ->
            if (character == '\n') {
                lines += current.toString()
                current = StringBuilder()
            } else {
                val candidate = current.toString() + character
                if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    lines += current.toString().trimEnd()
                    current = StringBuilder().append(character)
                } else {
                    current.append(character)
                }
            }
        }
        if (current.isNotEmpty()) lines += current.toString().trimEnd()
        return lines.ifEmpty { listOf("") }
    }

    private fun drawEllipsizedText(
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        size: Float,
        color: Int,
        medium: Boolean
    ) {
        configurePaint(size, color, medium)
        if (paint.measureText(text) <= maxWidth) {
            requireCanvas().drawText(text, x, baseline, paint)
            return
        }
        val ellipsis = "…"
        val builder = StringBuilder()
        for (character in text) {
            if (paint.measureText(builder.toString() + character + ellipsis) <= maxWidth) {
                builder.append(character)
            } else {
                break
            }
        }
        requireCanvas().drawText(builder.toString() + ellipsis, x, baseline, paint)
    }

    private fun drawText(
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        medium: Boolean = false
    ) {
        configurePaint(size, color, medium)
        requireCanvas().drawText(text, x, baseline, paint)
    }

    private fun configurePaint(size: Float, color: Int, medium: Boolean) {
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = if (medium) mediumTypeface else regularTypeface
    }

    private fun requireCanvas(): Canvas = requireNotNull(canvas)
}

private fun formatClock(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", (minutes / 60) % 24, minutes % 60)

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms.coerceAtLeast(0L) / 60_000L
    return String.format(Locale.getDefault(), "%d:%02d", totalMinutes / 60L, totalMinutes % 60L)
}

private fun formatPercent(value: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", value.coerceAtLeast(0.0) * 100.0)

private fun categoryColor(id: Long): Int {
    val hue = ((id % 360L + 360L) % 360L).toFloat()
    return Color.HSVToColor(floatArrayOf(hue, 0.58f, 0.84f))
}

private fun modalDaySummaryForReport(result: AdvancedAnalysisResult): String {
    val profile = result.timeProfile
    val entries = mutableListOf<String>()
    var previous: String? = null
    profile.modalLabels.forEachIndexed { index, label ->
        if (label != previous && label != null) {
            val minute = (profile.boundaryMinutes + index * profile.slotMinutes) % 1440
            entries += String.format(
                Locale.getDefault(),
                "%02d:%02d %s",
                minute / 60,
                minute % 60,
                label
            )
            previous = label
        } else if (label == null) {
            previous = null
        }
    }
    return entries.take(24).joinToString(" → ") + if (entries.size > 24) " …" else ""
}
