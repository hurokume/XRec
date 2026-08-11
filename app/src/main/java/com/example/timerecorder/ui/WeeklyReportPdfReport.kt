package com.example.timerecorder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.timerecorder.data.WeeklyReportData
import com.example.timerecorder.data.WeeklyTimeline
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val A4_PORTRAIT_WIDTH_POINTS = 595
private const val A4_PORTRAIT_HEIGHT_POINTS = 842

fun createWeeklyReportPdf(
    context: Context,
    data: WeeklyReportData,
    probabilityItemLimit: Int = 20,
    paletteMask: Int = AnalysisPalette.defaultMask
): Uri {
    val outputDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val week = SimpleDateFormat("yyyyMMdd", Locale.US)
        .format(Date(data.timeline.range.startMs))
    val outputFile = File(outputDirectory, "weekly_report_${week}_$timestamp.pdf")
    val document = PdfDocument()

    try {
        WeeklyTimelinePdfWriter(document).write(data)
        AdvancedAnalysisPdfWriter(
            document = document,
            firstPageNumber = 2,
            reportTitle = "TimeRecorder Weekly Report",
            reportSubtitle = "Advanced Analysis",
            pageBreakBeforeProbability = true
        ).write(
            result = data.analysis,
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

private class WeeklyTimelinePdfWriter(
    private val document: PdfDocument
) {
    private val pageWidth = A4_PORTRAIT_WIDTH_POINTS.toFloat()
    private val pageHeight = A4_PORTRAIT_HEIGHT_POINTS.toFloat()
    private val margin = 30f
    private val regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularTypeface }

    fun write(data: WeeklyReportData) {
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(
                A4_PORTRAIT_WIDTH_POINTS,
                A4_PORTRAIT_HEIGHT_POINTS,
                1
            ).create()
        )
        val canvas = page.canvas.apply { drawColor(Color.WHITE) }
        drawHeader(canvas, data)
        drawTimeline(canvas, data.timeline)
        drawFooter(canvas)
        document.finishPage(page)
    }

    private fun drawHeader(canvas: Canvas, data: WeeklyReportData) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(31, 55, 86)
        canvas.drawRect(0f, 0f, pageWidth, 58f, paint)
        drawText(
            canvas,
            "TimeRecorder Weekly Report",
            margin,
            28f,
            18f,
            Color.WHITE,
            medium = true
        )
        drawText(
            canvas,
            "7-day Timeline and Advanced Analysis",
            margin,
            46f,
            8.5f,
            Color.rgb(218, 229, 242)
        )

        val timeline = data.timeline
        val dateFormat = SimpleDateFormat("yyyy/MM/dd (EEE)", Locale.getDefault())
        val rangeLabel = "${dateFormat.format(Date(timeline.range.startMs))} - " +
                dateFormat.format(Date(timeline.range.endMs - 1L))
        drawText(canvas, rangeLabel, margin, 77f, 10f, Color.rgb(36, 48, 61), true)
        val detail = "Timeline ${formatMinute(timeline.displayStartMinute)} - " +
                "${formatMinute(timeline.displayEndMinute)}  |  " +
                "記録 ${formatDurationForWeekly(data.analysis.quality.recordedMs)}  |  " +
                "記録率 ${formatPercentForWeekly(data.analysis.quality.coverageRate)}"
        drawText(canvas, detail, margin, 91f, 8f, Color.rgb(82, 95, 109))
    }

    private fun drawTimeline(canvas: Canvas, timeline: WeeklyTimeline) {
        val labelWidth = 38f
        val left = margin + labelWidth
        val right = pageWidth - margin
        val headerTop = 99f
        val gridTop = 122f
        val gridBottom = pageHeight - 47f
        val gridHeight = gridBottom - gridTop
        val visibleMinutes = timeline.displayEndMinute - timeline.displayStartMinute
        val dayCount = timeline.days.size.coerceAtLeast(1)
        val columnWidth = (right - left) / dayCount
        val dateFormat = SimpleDateFormat("M/d (E)", Locale.getDefault())

        timeline.days.forEachIndexed { index, day ->
            val columnLeft = left + index * columnWidth
            paint.style = Paint.Style.FILL
            paint.color = if (index == 0 || index == 6) {
                Color.rgb(248, 242, 242)
            } else {
                Color.rgb(246, 248, 250)
            }
            canvas.drawRect(columnLeft, headerTop, columnLeft + columnWidth, gridBottom, paint)
            configurePaint(8f, Color.rgb(48, 59, 72), true)
            val label = dateFormat.format(Date(day.startMs))
            canvas.drawText(
                label,
                columnLeft + (columnWidth - paint.measureText(label)) / 2f,
                headerTop + 15f,
                paint
            )
        }

        val firstHour = timeline.displayStartMinute / 60
        val lastHour = (timeline.displayEndMinute + 59) / 60
        val labelStepHours = if (visibleMinutes > 12 * 60) 2 else 1
        for (hour in firstHour..lastHour) {
            val minute = hour * 60
            if (minute !in timeline.displayStartMinute..timeline.displayEndMinute) continue
            val y = gridTop +
                    (minute - timeline.displayStartMinute).toFloat() / visibleMinutes * gridHeight
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (hour % labelStepHours == 0) 0.65f else 0.3f
            paint.color = if (hour % labelStepHours == 0) {
                Color.rgb(194, 203, 212)
            } else {
                Color.rgb(220, 226, 232)
            }
            canvas.drawLine(left, y, right, y, paint)
            if (hour % labelStepHours == 0) {
                val label = formatMinute(minute)
                configurePaint(7f, Color.rgb(92, 105, 120), false)
                canvas.drawText(label, margin, y + 2.5f, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.65f
        paint.color = Color.rgb(183, 193, 203)
        for (index in 0..dayCount) {
            val x = left + index * columnWidth
            canvas.drawLine(x, headerTop, x, gridBottom, paint)
        }
        canvas.drawRect(left, headerTop, right, gridBottom, paint)

        canvas.save()
        canvas.clipRect(left, gridTop, right, gridBottom)
        timeline.days.forEachIndexed { dayIndex, day ->
            val columnLeft = left + dayIndex * columnWidth + 1.5f
            day.entries.forEach { entry ->
                val top = gridTop +
                        (entry.startMinute - timeline.displayStartMinute).toFloat() /
                        visibleMinutes * gridHeight
                val bottom = gridTop +
                        (entry.endMinute - timeline.displayStartMinute).toFloat() /
                        visibleMinutes * gridHeight
                if (bottom <= top) return@forEach
                val blockRight = columnLeft + columnWidth - 3f
                paint.style = Paint.Style.FILL
                val projectColor = projectColor(entry.projectColorInt, entry.colorKey)
                paint.color = pastelColor(projectColor)
                canvas.drawRect(columnLeft, top + 0.7f, blockRight, bottom - 0.7f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.65f
                paint.color = projectColor
                canvas.drawRect(columnLeft, top + 0.7f, blockRight, bottom - 0.7f, paint)

                val blockHeight = bottom - top
                if (blockHeight >= 7.5f) {
                    drawEllipsizedText(
                        canvas = canvas,
                        text = entry.pathLabel,
                        x = columnLeft + 3f,
                        baseline = top + 7f,
                        maxWidth = columnWidth - 8f,
                        size = 6.2f,
                        color = Color.rgb(36, 48, 61),
                        medium = true
                    )
                }
                if (blockHeight >= 17f) {
                    drawText(
                        canvas,
                        "${formatMinute(entry.startMinute)}-${formatMinute(entry.endMinute)}",
                        columnLeft + 3f,
                        top + 14f,
                        5.7f,
                        Color.rgb(56, 68, 81)
                    )
                }
            }
        }
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = Color.rgb(205, 213, 222)
        canvas.drawLine(margin, pageHeight - 31f, pageWidth - margin, pageHeight - 31f, paint)
        val generatedAt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        drawText(
            canvas,
            "TimeRecorder | $generatedAt",
            margin,
            pageHeight - 17f,
            8f,
            Color.rgb(92, 105, 120)
        )
        val pageLabel = "Page 1"
        configurePaint(8f, Color.rgb(92, 105, 120), false)
        canvas.drawText(
            pageLabel,
            pageWidth - margin - paint.measureText(pageLabel),
            pageHeight - 17f,
            paint
        )
    }

    private fun drawEllipsizedText(
        canvas: Canvas,
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
            canvas.drawText(text, x, baseline, paint)
            return
        }
        val ellipsis = "..."
        val builder = StringBuilder()
        for (character in text) {
            if (paint.measureText(builder.toString() + character + ellipsis) <= maxWidth) {
                builder.append(character)
            } else {
                break
            }
        }
        canvas.drawText(builder.toString() + ellipsis, x, baseline, paint)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        medium: Boolean = false
    ) {
        configurePaint(size, color, medium)
        canvas.drawText(text, x, baseline, paint)
    }

    private fun configurePaint(size: Float, color: Int, medium: Boolean) {
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = if (medium) mediumTypeface else regularTypeface
    }

    private fun projectColor(configuredColor: Int?, key: Long): Int {
        if (configuredColor != null) {
            return Color.rgb(
                Color.red(configuredColor),
                Color.green(configuredColor),
                Color.blue(configuredColor)
            )
        }
        val hue = ((key % 360L + 360L) % 360L).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.58f, 0.70f))
    }

    private fun pastelColor(color: Int): Int {
        fun pastelChannel(channel: Int): Int =
            (channel + (255 - channel) * 0.62f).toInt().coerceIn(0, 255)
        return Color.rgb(
            pastelChannel(Color.red(color)),
            pastelChannel(Color.green(color)),
            pastelChannel(Color.blue(color))
        )
    }
}

private fun formatMinute(minutes: Int): String {
    if (minutes == 24 * 60) return "24:00"
    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        (minutes / 60) % 24,
        minutes % 60
    )
}

private fun formatDurationForWeekly(ms: Long): String {
    val totalMinutes = ms.coerceAtLeast(0L) / 60_000L
    return String.format(Locale.getDefault(), "%d:%02d", totalMinutes / 60L, totalMinutes % 60L)
}

private fun formatPercentForWeekly(value: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", value.coerceAtLeast(0.0) * 100.0)
