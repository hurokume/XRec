package com.example.timerecorder.ui

import kotlin.math.roundToInt

enum class AnalysisPalette(
    val bit: Int,
    val label: String,
    val lowColor: Int,
    val middleColor: Int,
    val highColor: Int
) {
    Ocean(
        bit = 1 shl 0,
        label = "海",
        lowColor = 0xFFE3F2FD.toInt(),
        middleColor = 0xFF42A5F5.toInt(),
        highColor = 0xFF0D47A1.toInt()
    ),
    Sunset(
        bit = 1 shl 1,
        label = "夕焼け",
        lowColor = 0xFFFFF8E1.toInt(),
        middleColor = 0xFFFFA726.toInt(),
        highColor = 0xFFB71C1C.toInt()
    ),
    Forest(
        bit = 1 shl 2,
        label = "森林",
        lowColor = 0xFFE8F5E9.toInt(),
        middleColor = 0xFF66BB6A.toInt(),
        highColor = 0xFF1B5E20.toInt()
    ),
    Violet(
        bit = 1 shl 3,
        label = "紫",
        lowColor = 0xFFF3E5F5.toInt(),
        middleColor = 0xFFAB47BC.toInt(),
        highColor = 0xFF4A148C.toInt()
    );

    companion object {
        val defaultMask: Int = entries.fold(0) { mask, palette -> mask or palette.bit }
    }
}

fun selectedAnalysisPalettes(mask: Int): List<AnalysisPalette> =
    AnalysisPalette.entries.filter { palette -> mask and palette.bit != 0 }
        .ifEmpty { listOf(AnalysisPalette.Ocean) }

fun probabilityColorArgb(palette: AnalysisPalette, probability: Double): Int {
    val value = probability.coerceIn(0.0, 1.0)
    return if (value <= 0.5) {
        interpolateArgb(palette.lowColor, palette.middleColor, value * 2.0)
    } else {
        interpolateArgb(palette.middleColor, palette.highColor, (value - 0.5) * 2.0)
    }
}

private fun interpolateArgb(start: Int, end: Int, fraction: Double): Int {
    val amount = fraction.coerceIn(0.0, 1.0)
    fun channel(shift: Int): Int {
        val startChannel = start ushr shift and 0xFF
        val endChannel = end ushr shift and 0xFF
        return (startChannel + (endChannel - startChannel) * amount).roundToInt()
    }

    return (channel(24) shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
}
