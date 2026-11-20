package com.oiuy.volumecontrol.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oiuy.volumecontrol.R

/**
 * Dark, flat palette in the spirit of the Windows 10 Calculator and Calendar
 * dark mode: near-black background, slightly lighter panes, thin borders.
 * The app is always dark.
 */
object FluentDark {
    val Background = Color(0xFF050505)
    val Pane = Color(0xFF0E0E0E)
    val PaneHigh = Color(0xFF151515)
    /** Unlit borders and ring lines: near-black, only just visible. */
    val Border = Color(0xFF1E1E1E)
    val BorderStrong = Color(0xFF2A2A2A)
    val Text = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFFB8B8B8)

    /** Knob face and the unlit color of minor marks away from the pointer. */
    val Knob = Color(0xFF0A0A0A)
    val MarkDim = Color(0xFF242424)

    /** Neutral part of the Reveal highlight color. */
    val RevealNeutral = Color(0xFFD6D6D6)

    /** Accent when the system has no accent (Android 11 and older). */
    val FallbackAccent = Color(0xFF4CC2FF)
}

/** Segoe UI Semilight's weight. */
val SemilightWeight = FontWeight(350)

/**
 * Selawik, Microsoft's open-source Segoe UI-compatible font (SIL OFL 1.1).
 * The font files are fetched at build time; see docs/BUILDING.md.
 */
val Selawik = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_semilight, SemilightWeight),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
)

/** Material type scale with every style in Selawik. */
private val SelawikTypography: Typography by lazy(LazyThreadSafetyMode.NONE) { selawikTypography() }

private fun selawikTypography(): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Selawik),
        displayMedium = displayMedium.copy(fontFamily = Selawik),
        displaySmall = displaySmall.copy(fontFamily = Selawik),
        headlineLarge = headlineLarge.copy(fontFamily = Selawik),
        headlineMedium = headlineMedium.copy(fontFamily = Selawik),
        headlineSmall = headlineSmall.copy(fontFamily = Selawik),
        titleLarge = titleLarge.copy(fontFamily = Selawik),
        titleMedium = titleMedium.copy(fontFamily = Selawik),
        titleSmall = titleSmall.copy(fontFamily = Selawik),
        bodyLarge = bodyLarge.copy(fontFamily = Selawik),
        bodyMedium = bodyMedium.copy(fontFamily = Selawik),
        bodySmall = bodySmall.copy(fontFamily = Selawik),
        labelLarge = labelLarge.copy(fontFamily = Selawik),
        labelMedium = labelMedium.copy(fontFamily = Selawik),
        labelSmall = labelSmall.copy(fontFamily = Selawik),
    )
}

/** The accent used for the dial's major marks, pointer and progress line. */
val LocalGlowColor = staticCompositionLocalOf { FluentDark.FallbackAccent }

@Composable
fun VolumeControlTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = FluentDark.FallbackAccent,
            onPrimary = Color(0xFF00344F),
            primaryContainer = Color(0xFF004C6F),
            onPrimaryContainer = Color(0xFFC9E6FF),
            secondary = FluentDark.FallbackAccent,
        )
    }
    // Material You accent: system accent 1 at shade 300 is a clear, saturated
    // accent. Older Android versions use the fixed fallback.
    val glow = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent1_300)
    } else {
        FluentDark.FallbackAccent
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )

    CompositionLocalProvider(LocalGlowColor provides glow) {
        MaterialExpressiveTheme(
            colorScheme = base.fluentNeutrals(),
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            typography = SelawikTypography,
            content = content,
        )
    }
}

/** Keeps the accent colors and swaps every neutral for the Fluent dark ones. */
private fun ColorScheme.fluentNeutrals(): ColorScheme = copy(
    background = FluentDark.Background,
    onBackground = FluentDark.Text,
    surface = FluentDark.Background,
    onSurface = FluentDark.Text,
    surfaceVariant = FluentDark.Pane,
    onSurfaceVariant = FluentDark.TextSecondary,
    surfaceDim = FluentDark.Background,
    surfaceBright = FluentDark.PaneHigh,
    surfaceContainerLowest = FluentDark.Background,
    surfaceContainerLow = FluentDark.Pane,
    surfaceContainer = FluentDark.Pane,
    surfaceContainerHigh = FluentDark.Pane,
    surfaceContainerHighest = FluentDark.PaneHigh,
    outline = FluentDark.BorderStrong,
    outlineVariant = FluentDark.Border,
)
