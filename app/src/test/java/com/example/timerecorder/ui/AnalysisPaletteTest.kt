package com.example.timerecorder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPaletteTest {
    @Test
    fun selectedPalettes_preserveDeclaredOrderAndSupportMultipleSelections() {
        val mask = AnalysisPalette.Sunset.bit or AnalysisPalette.Violet.bit

        assertEquals(
            listOf(AnalysisPalette.Sunset, AnalysisPalette.Violet),
            selectedAnalysisPalettes(mask)
        )
    }

    @Test
    fun selectedPalettes_fallBackToOceanWhenMaskIsEmpty() {
        assertEquals(listOf(AnalysisPalette.Ocean), selectedAnalysisPalettes(0))
    }

    @Test
    fun probabilityColor_usesPaletteEndpointsAndClampsInput() {
        val palette = AnalysisPalette.Forest

        assertEquals(palette.lowColor, probabilityColorArgb(palette, -1.0))
        assertEquals(palette.middleColor, probabilityColorArgb(palette, 0.5))
        assertEquals(palette.highColor, probabilityColorArgb(palette, 2.0))
    }

    @Test
    fun defaultMask_selectsEveryPalette() {
        val selected = selectedAnalysisPalettes(AnalysisPalette.defaultMask)

        assertEquals(AnalysisPalette.entries.toList(), selected)
        assertTrue(selected.size > 1)
    }
}
