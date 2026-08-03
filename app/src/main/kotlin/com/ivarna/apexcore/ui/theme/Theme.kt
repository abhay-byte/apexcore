package com.ivarna.apexcore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Zen Organic light scheme. */
val ZenLightColorScheme = lightColorScheme(
    primary = ZenColors.primary,
    onPrimary = ZenColors.onPrimary,
    primaryContainer = ZenColors.primaryContainer,
    onPrimaryContainer = ZenColors.onPrimaryContainer,
    inversePrimary = ZenColors.inversePrimary,
    secondary = ZenColors.secondary,
    onSecondary = ZenColors.onSecondary,
    secondaryContainer = ZenColors.secondaryContainer,
    onSecondaryContainer = ZenColors.onSecondaryContainer,
    tertiary = ZenColors.tertiary,
    onTertiary = ZenColors.onTertiary,
    tertiaryContainer = ZenColors.tertiaryContainer,
    onTertiaryContainer = ZenColors.onTertiaryContainer,
    error = ZenColors.error,
    onError = ZenColors.onError,
    errorContainer = ZenColors.errorContainer,
    onErrorContainer = ZenColors.onErrorContainer,
    background = ZenColors.background,
    onBackground = ZenColors.onBackground,
    surface = ZenColors.surface,
    onSurface = ZenColors.onSurface,
    surfaceVariant = ZenColors.surfaceVariant,
    onSurfaceVariant = ZenColors.onSurfaceVariant,
    surfaceTint = ZenColors.surfaceTint,
    inverseSurface = ZenColors.inverseSurface,
    inverseOnSurface = ZenColors.inverseOnSurface,
    outline = ZenColors.outline,
    outlineVariant = ZenColors.outlineVariant,
    surfaceBright = ZenColors.surfaceBright,
    surfaceDim = ZenColors.surfaceDim,
    surfaceContainer = ZenColors.surfaceContainer,
    surfaceContainerHigh = ZenColors.surfaceContainerHigh,
    surfaceContainerHighest = ZenColors.surfaceContainerHighest,
    surfaceContainerLow = ZenColors.surfaceContainerLow,
    surfaceContainerLowest = ZenColors.surfaceContainerLowest,
)

/** Zen Organic dark scheme — deep teal ink, light accents. */
val ZenDarkColorScheme = darkColorScheme(
    primary = ZenColors.Dark.primary,
    onPrimary = ZenColors.Dark.onPrimary,
    primaryContainer = ZenColors.Dark.primaryContainer,
    onPrimaryContainer = ZenColors.Dark.onPrimaryContainer,
    inversePrimary = ZenColors.Dark.inversePrimary,
    secondary = ZenColors.Dark.secondary,
    onSecondary = ZenColors.Dark.onSecondary,
    secondaryContainer = ZenColors.Dark.secondaryContainer,
    onSecondaryContainer = ZenColors.Dark.onSecondaryContainer,
    tertiary = ZenColors.Dark.tertiary,
    onTertiary = ZenColors.Dark.onTertiary,
    tertiaryContainer = ZenColors.Dark.tertiaryContainer,
    onTertiaryContainer = ZenColors.Dark.onTertiaryContainer,
    error = ZenColors.Dark.error,
    onError = ZenColors.Dark.onError,
    errorContainer = ZenColors.Dark.errorContainer,
    onErrorContainer = ZenColors.Dark.onErrorContainer,
    background = ZenColors.Dark.background,
    onBackground = ZenColors.Dark.onBackground,
    surface = ZenColors.Dark.surface,
    onSurface = ZenColors.Dark.onSurface,
    surfaceVariant = ZenColors.Dark.surfaceVariant,
    onSurfaceVariant = ZenColors.Dark.onSurfaceVariant,
    surfaceTint = ZenColors.Dark.surfaceTint,
    inverseSurface = ZenColors.Dark.inverseSurface,
    inverseOnSurface = ZenColors.Dark.inverseOnSurface,
    outline = ZenColors.Dark.outline,
    outlineVariant = ZenColors.Dark.outlineVariant,
    surfaceBright = ZenColors.Dark.surfaceBright,
    surfaceDim = ZenColors.Dark.surfaceDim,
    surfaceContainer = ZenColors.Dark.surfaceContainer,
    surfaceContainerHigh = ZenColors.Dark.surfaceContainerHigh,
    surfaceContainerHighest = ZenColors.Dark.surfaceContainerHighest,
    surfaceContainerLow = ZenColors.Dark.surfaceContainerLow,
    surfaceContainerLowest = ZenColors.Dark.surfaceContainerLowest,
)

/** Theme-aware non-M3 semantic colors (leaves, status, bloom). */
data class ZenSemanticColors(
    val statusActive: Color,
    val statusInactive: Color,
    val leafRamFill: Color,
    val leafSwapFill: Color,
    val bloom: Color,
    val isDark: Boolean
)

val LocalZenSemantics = staticCompositionLocalOf {
    ZenSemanticColors(
        statusActive = ZenColors.statusActive,
        statusInactive = ZenColors.statusInactive,
        leafRamFill = ZenColors.leafRamFill,
        leafSwapFill = ZenColors.leafSwapFill,
        bloom = ZenColors.bloom,
        isDark = false
    )
}

val ZenLightSemantics = ZenSemanticColors(
    statusActive = ZenColors.statusActive,
    statusInactive = ZenColors.statusInactive,
    leafRamFill = ZenColors.leafRamFill,
    leafSwapFill = ZenColors.leafSwapFill,
    bloom = ZenColors.bloom,
    isDark = false
)

val ZenDarkSemantics = ZenSemanticColors(
    statusActive = ZenColors.Dark.statusActive,
    statusInactive = ZenColors.Dark.statusInactive,
    leafRamFill = ZenColors.Dark.leafRamFill,
    leafSwapFill = ZenColors.Dark.leafSwapFill,
    bloom = ZenColors.Dark.bloom,
    isDark = true
)

/**
 * App theme. Follows system light/dark by default.
 * @param darkTheme override; null = follow system
 */
@Composable
fun ApexCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZenDarkColorScheme else ZenLightColorScheme
    val semantics = if (darkTheme) ZenDarkSemantics else ZenLightSemantics

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is android.app.Activity) {
                val window = context.window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowCompat.getInsetsController(window, view)
                // Light bars = dark icons (for light bg); dark bars = light icons
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalZenSemantics provides semantics) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZenTypography,
            content = content
        )
    }
}
