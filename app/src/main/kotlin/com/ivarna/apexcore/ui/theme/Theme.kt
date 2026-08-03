package com.ivarna.apexcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Zen Organic light scheme — app default (v1 light-only). */
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

@Composable
fun ApexCoreTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is android.app.Activity) {
                val window = context.window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = ZenLightColorScheme,
        typography = ZenTypography,
        content = content
    )
}
