package org.threelx.payrelay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Colours with a meaning, next to the Material ones. */
@Immutable
data class Signals(val good: Color, val onGood: Color, val warn: Color, val onWarn: Color, val bkash: Color, val nagad: Color, val rocket: Color)

val LocalSignals = staticCompositionLocalOf {
    Signals(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private val Light = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1E9),
    onPrimaryContainer = Color(0xFF00382D),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF0F2),
    onSurfaceVariant = Color(0xFF55606A),
    outlineVariant = Color(0xFFDDE2E6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF5F120D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF63D6BC),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF12493E),
    onPrimaryContainer = Color(0xFFBDF0E3),
    background = Color(0xFF0F1413),
    surface = Color(0xFF181E1D),
    surfaceVariant = Color(0xFF232B2A),
    onSurfaceVariant = Color(0xFFA9B5B2),
    outlineVariant = Color(0xFF2F3938),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1712),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightSignals = Signals(
    good = Color(0xFFD3F1E9), onGood = Color(0xFF00382D),
    warn = Color(0xFFFFEDC2), onWarn = Color(0xFF4A3400),
    bkash = Color(0xFFD12053), nagad = Color(0xFFE8590C), rocket = Color(0xFF7B3FA8),
)

private val DarkSignals = Signals(
    good = Color(0xFF12493E), onGood = Color(0xFFBDF0E3),
    warn = Color(0xFF4F3B05), onWarn = Color(0xFFFFE2A0),
    bkash = Color(0xFFFF7FA3), nagad = Color(0xFFFFA36B), rocket = Color(0xFFC79BEA),
)

@Composable
fun PayRelayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSignals provides if (dark) DarkSignals else LightSignals) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
