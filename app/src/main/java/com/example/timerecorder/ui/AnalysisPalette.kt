package com.example.timerecorder.ui

import androidx.annotation.StringRes
import com.example.timerecorder.R
import kotlin.math.roundToInt

enum class AnalysisPalette(
    val bit: Int,
    @param:StringRes val labelRes: Int,
    val lowColor: Int,
    val middleColor: Int,
    val highColor: Int
) {
    Ocean(
        bit = 1 shl 0,
        labelRes = R.string.analysis_palette_ocean,
        lowColor = 0xFFE3F2FD.toInt(),
        middleColor = 0xFF42A5F5.toInt(),
        highColor = 0xFF0D47A1.toInt()
    ),
    Sunset(
        bit = 1 shl 1,
        labelRes = R.string.analysis_palette_sunset,
        lowColor = 0xFFFFF8E1.toInt(),
        middleColor = 0xFFFFA726.toInt(),
        highColor = 0xFFB71C1C.toInt()
    ),
    Forest(
        bit = 1 shl 2,
        labelRes = R.string.analysis_palette_forest,
        lowColor = 0xFFE8F5E9.toInt(),
        middleColor = 0xFF66BB6A.toInt(),
        highColor = 0xFF1B5E20.toInt()
    ),
    Violet(
        bit = 1 shl 3,
        labelRes = R.string.analysis_palette_violet,
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
