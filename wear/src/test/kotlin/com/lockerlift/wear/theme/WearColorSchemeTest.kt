package com.lockerlift.wear.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Verifies the wear color scheme stays dark, on-brand and legible.
 */
class WearColorSchemeTest {

    @Test
    fun testBackgroundAndSurfaceAreNearBlackForAmoled() {
        assertEquals(0xFF121212.toInt(), WearColorScheme.background.toArgb())
        assertEquals(0xFF121212.toInt(), WearColorScheme.surface.toArgb())
        assertTrue(luminance(WearColorScheme.background.toArgb()) < 0.05)
    }

    @Test
    fun testPaletteMatchesMobileBrandColors() {
        assertEquals(0xFF90CAF9.toInt(), WearColorScheme.primary.toArgb())
        assertEquals(0xFF80CBC4.toInt(), WearColorScheme.secondary.toArgb())
        assertEquals(0xFFE0E0E0.toInt(), WearColorScheme.onSurface.toArgb())
    }

    @Test
    fun testTextOnSurfaceMeetsWcagAaContrast() {
        val ratio = contrast(
            WearColorScheme.onSurface.toArgb(),
            WearColorScheme.surface.toArgb()
        )
        assertTrue("onSurface/surface contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun testTextOnPrimaryMeetsWcagAaContrast() {
        val ratio = contrast(
            WearColorScheme.onPrimary.toArgb(),
            WearColorScheme.primary.toArgb()
        )
        assertTrue("onPrimary/primary contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun testOnSurfaceVariantIsLegibleAgainstSurface() {
        val ratio = contrast(
            WearColorScheme.onSurfaceVariant.toArgb(),
            WearColorScheme.surface.toArgb()
        )
        assertTrue("onSurfaceVariant/surface contrast was $ratio", ratio >= 4.5)
    }

    private fun contrast(argb1: Int, argb2: Int): Double {
        val l1 = luminance(argb1)
        val l2 = luminance(argb2)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
