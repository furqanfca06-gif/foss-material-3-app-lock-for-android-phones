package com.example.applock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class LockPalette(
    val Canvas: Color,
    val SurfaceLow: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val SurfaceHighest: Color,
    val Primary: Color,
    val OnPrimary: Color,
    val Teal: Color,
    val OnSurface: Color,
    val Muted: Color,
    val Outline: Color,
    val Error: Color,
    val ErrorContainer: Color
)

private val DarkPalette = LockPalette(
    Canvas = Color(0xFF131315),
    SurfaceLow = Color(0xFF1B1B1D),
    Surface = Color(0xFF201F21),
    SurfaceHigh = Color(0xFF2A2A2C),
    SurfaceHighest = Color(0xFF353437),
    Primary = Color(0xFFB5CFFF),
    OnPrimary = Color(0xFF003061),
    Teal = Color(0xFF73D4E7),
    OnSurface = Color(0xFFE5E1E4),
    Muted = Color(0xFF8D919B),
    Outline = Color(0xFF424750),
    Error = Color(0xFFFFB4AB),
    ErrorContainer = Color(0xFF93000A)
)

private val LightPalette = LockPalette(
    Canvas = Color(0xFFFBF8FE),
    SurfaceLow = Color(0xFFF0F4F9),
    Surface = Color(0xFFFFFFFF),
    SurfaceHigh = Color(0xFFEAE7ED),
    SurfaceHighest = Color(0xFFD8DDE4),
    Primary = Color(0xFF0B57D0),
    OnPrimary = Color(0xFFFFFFFF),
    Teal = Color(0xFF4F5B92),
    OnSurface = Color(0xFF1B1B1F),
    Muted = Color(0xFF424654),
    Outline = Color(0xFFC3C6D6),
    Error = Color(0xFFBA1A1A),
    ErrorContainer = Color(0xFFFFDAD6)
)

private val LocalLockPalette = staticCompositionLocalOf { DarkPalette }

@Composable
internal fun AppLockTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = palette.Primary,
            onPrimary = palette.OnPrimary,
            secondary = palette.Teal,
            onSecondary = palette.Canvas,
            background = palette.Canvas,
            onBackground = palette.OnSurface,
            surface = palette.SurfaceLow,
            onSurface = palette.OnSurface,
            surfaceVariant = palette.SurfaceHigh,
            onSurfaceVariant = palette.Muted,
            outline = palette.Outline,
            error = palette.Error,
            errorContainer = palette.ErrorContainer
        )
    } else {
        lightColorScheme(
            primary = palette.Primary,
            onPrimary = palette.OnPrimary,
            secondary = palette.Teal,
            onSecondary = Color.White,
            background = palette.Canvas,
            onBackground = palette.OnSurface,
            surface = palette.Surface,
            onSurface = palette.OnSurface,
            surfaceVariant = palette.SurfaceHigh,
            onSurfaceVariant = palette.Muted,
            outline = palette.Outline,
            error = palette.Error,
            errorContainer = palette.ErrorContainer
        )
    }
    CompositionLocalProvider(LocalLockPalette provides palette) {
        MaterialTheme(colorScheme = colors) { content() }
    }
}

internal val LockColors: LockPalette
    @Composable get() = LocalLockPalette.current
