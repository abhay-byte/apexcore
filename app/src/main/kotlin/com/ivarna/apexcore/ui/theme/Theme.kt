package com.ivarna.apexcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    background = BgDark,
    surface = SurfaceCard,
    onPrimary = TextTitle,
    onBackground = TextTitle,
    onSurface = TextBody,
    error = AccentWarning,
    outline = BorderGlass,
    onSurfaceVariant = TextMuted
)

/** Zen Organic light scheme (additive; ApexCoreTheme still uses DarkColorScheme). */
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

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium
    )
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
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
