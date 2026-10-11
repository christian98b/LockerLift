package com.lockerlift.wear.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme

/**
 * Dark-only fallback color scheme for the Wear OS app.
 *
 * Mirrors the mobile app's dark palette so both apps share the same brand
 * identity. Used when dynamic color is unavailable (API < 31). Watches always
 * use the dark scheme: AMOLED displays benefit from a near-black background
 * and Compose defaults must never be relied upon.
 */
val WearColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF004F48),
    onSecondaryContainer = Color(0xFFA7F0E6),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF252525),
    outline = Color(0xFF8A929A)
)

/**
 * Maps a Material 3 color scheme (from the phone-style Compose Material 3
 * library) to the Wear Compose Material 3 color scheme used by all wear
 * components. Wear Material 3 does not ship its own dynamic color helpers,
 * so dynamic schemes are obtained from the shared library and converted.
 */
fun ColorScheme.toWearColorScheme(): androidx.wear.compose.material3.ColorScheme =
    androidx.wear.compose.material3.ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        background = background,
        onBackground = onBackground,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer
    )

/**
 * Theme wrapper for all wear screens. Always dark, no light variant.
 * On Android 12+ the wallpaper-derived dynamic dark scheme is used, matching
 * the mobile app's dynamic color behavior. Earlier versions fall back to the
 * static LockerLift brand palette.
 */
@Composable
fun LockerLiftWearTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current).toWearColorScheme()
    } else {
        WearColorScheme.toWearColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
