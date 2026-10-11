package com.lockerlift.wear.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the conversion from the phone-style Material 3 color scheme to the
 * Wear Compose Material 3 color scheme consumed by all wear components.
 */
class WearColorSchemeMappingTest {

    @Test
    fun testBrandFallbackSchemeMapsLosslessly() {
        val wear = WearColorScheme.toWearColorScheme()

        assertEquals(0xFF90CAF9.toInt(), wear.primary.toArgb())
        assertEquals(0xFF003258.toInt(), wear.onPrimary.toArgb())
        assertEquals(0xFF80CBC4.toInt(), wear.secondary.toArgb())
        assertEquals(0xFFE0E0E0.toInt(), wear.onSurface.toArgb())
        assertEquals(0xFFCAC4D0.toInt(), wear.onSurfaceVariant.toArgb())
        assertEquals(0xFF1E1E1E.toInt(), wear.surfaceContainer.toArgb())
        assertEquals(0xFF1A1A1A.toInt(), wear.surfaceContainerLow.toArgb())
        assertEquals(0xFF252525.toInt(), wear.surfaceContainerHigh.toArgb())
        assertEquals(0xFF121212.toInt(), wear.background.toArgb())
    }

    @Test
    fun testCustomSchemeSlotsAreCarriedOver() {
        val source = darkColorScheme(
            primary = Color(0xFF123456),
            onPrimary = Color(0xFFABCDEF),
            secondaryContainer = Color(0xFF246810),
            onSecondaryContainer = Color(0xFF13579B),
            tertiary = Color(0xFFFEDCBA),
            surfaceContainer = Color(0xFF111213),
            surfaceContainerLow = Color(0xFF101112),
            surfaceContainerHigh = Color(0xFF131415),
            background = Color(0xFF0A0B0C),
            onBackground = Color(0xFF0D0E0F),
            errorContainer = Color(0xFFB00020),
            onErrorContainer = Color(0xFFFFFFFF)
        )

        val wear = source.toWearColorScheme()

        assertEquals(0xFF123456.toInt(), wear.primary.toArgb())
        assertEquals(0xFFABCDEF.toInt(), wear.onPrimary.toArgb())
        assertEquals(0xFF246810.toInt(), wear.secondaryContainer.toArgb())
        assertEquals(0xFF13579B.toInt(), wear.onSecondaryContainer.toArgb())
        assertEquals(0xFFFEDCBA.toInt(), wear.tertiary.toArgb())
        assertEquals(0xFF111213.toInt(), wear.surfaceContainer.toArgb())
        assertEquals(0xFF101112.toInt(), wear.surfaceContainerLow.toArgb())
        assertEquals(0xFF131415.toInt(), wear.surfaceContainerHigh.toArgb())
        assertEquals(0xFF0A0B0C.toInt(), wear.background.toArgb())
        assertEquals(0xFF0D0E0F.toInt(), wear.onBackground.toArgb())
        assertEquals(0xFFB00020.toInt(), wear.errorContainer.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), wear.onErrorContainer.toArgb())
    }
}
