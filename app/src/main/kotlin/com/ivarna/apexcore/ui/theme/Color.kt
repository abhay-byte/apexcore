package com.ivarna.apexcore.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Zen Organic design tokens — light + dark.
 * Prefer [MaterialTheme.colorScheme] at call sites; use [ZenColors] only for
 * non-M3 semantic aliases that must track theme via [zenSemantic] helpers.
 */
object ZenColors {
    // ── Light surfaces ──────────────────────────────────────────────
    val surface = Color(0xFFF3FAFF)
    val background = Color(0xFFF3FAFF)
    val surfaceBright = Color(0xFFF3FAFF)
    val surfaceDim = Color(0xFFBEDFEF)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFE6F6FF)
    val surfaceContainer = Color(0xFFD8F2FF)
    val surfaceContainerHigh = Color(0xFFCCEDFE)
    val surfaceContainerHighest = Color(0xFFC6E8F8)
    val surfaceVariant = Color(0xFFC6E8F8)

    val onSurface = Color(0xFF001F29)
    val onBackground = Color(0xFF001F29)
    val onSurfaceVariant = Color(0xFF3D4947)
    val inverseSurface = Color(0xFF123441)
    val inverseOnSurface = Color(0xFFDFF4FF)
    val outline = Color(0xFF6D7A77)
    val outlineVariant = Color(0xFFBCC9C6)
    val surfaceTint = Color(0xFF006A60)

    // Primary (sage teal)
    val primary = Color(0xFF00685D)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFF008376)
    val onPrimaryContainer = Color(0xFFF4FFFB)
    val inversePrimary = Color(0xFF6FD8C8)

    // Secondary (warm gold)
    val secondary = Color(0xFF765A05)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFD87C)
    val onSecondaryContainer = Color(0xFF795D08)

    // Tertiary (earth)
    val tertiary = Color(0xFF8B4C11)
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFA96428)
    val onTertiaryContainer = Color(0xFFFFFBFF)

    // Error
    val error = Color(0xFFBA1A1A)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF93000A)

    // Fixed
    val primaryFixed = Color(0xFF8CF5E4)
    val primaryFixedDim = Color(0xFF6FD8C8)
    val onPrimaryFixed = Color(0xFF00201C)
    val onPrimaryFixedVariant = Color(0xFF005048)
    val secondaryFixed = Color(0xFFFFDF96)
    val secondaryFixedDim = Color(0xFFE7C268)
    val onSecondaryFixed = Color(0xFF251A00)
    val onSecondaryFixedVariant = Color(0xFF5A4400)
    val tertiaryFixed = Color(0xFFFFDCC4)
    val tertiaryFixedDim = Color(0xFFFFB780)
    val onTertiaryFixed = Color(0xFF2F1400)
    val onTertiaryFixedVariant = Color(0xFF6F3800)

    // Semantic light aliases
    val statusActive = primary
    val statusInactive = Color(0xFF5A6F6B)
    val leafRamFill = primaryContainer
    val leafSwapFill = tertiary
    val bloom = primary.copy(alpha = 0.08f)

    // ── Dark surfaces (deep teal ink — calm, not pure black) ────────
    object Dark {
        val surface = Color(0xFF0C171B)
        val background = Color(0xFF0A1317)
        val surfaceBright = Color(0xFF1A2C33)
        val surfaceDim = Color(0xFF080F12)
        val surfaceContainerLowest = Color(0xFF070D10)
        val surfaceContainerLow = Color(0xFF121F24)
        val surfaceContainer = Color(0xFF16262C)
        val surfaceContainerHigh = Color(0xFF1F3239)
        val surfaceContainerHighest = Color(0xFF283D45)
        val surfaceVariant = Color(0xFF3A4F56)

        val onSurface = Color(0xFFE0F2F8)
        val onBackground = Color(0xFFE0F2F8)
        val onSurfaceVariant = Color(0xFFB0C4CA)
        val inverseSurface = Color(0xFFE0F2F8)
        val inverseOnSurface = Color(0xFF0C171B)
        val outline = Color(0xFF8A9FA6)
        val outlineVariant = Color(0xFF3A4F56)
        val surfaceTint = Color(0xFF6FD8C8)

        val primary = Color(0xFF6FD8C8)
        val onPrimary = Color(0xFF003731)
        val primaryContainer = Color(0xFF005048)
        val onPrimaryContainer = Color(0xFF8CF5E4)
        val inversePrimary = Color(0xFF00685D)

        val secondary = Color(0xFFE7C268)
        val onSecondary = Color(0xFF3D2E00)
        val secondaryContainer = Color(0xFF5A4400)
        val onSecondaryContainer = Color(0xFFFFDF96)

        val tertiary = Color(0xFFFFB780)
        val onTertiary = Color(0xFF4A2800)
        val tertiaryContainer = Color(0xFF6F3800)
        val onTertiaryContainer = Color(0xFFFFDCC4)

        val error = Color(0xFFFFB4AB)
        val onError = Color(0xFF690005)
        val errorContainer = Color(0xFF93000A)
        val onErrorContainer = Color(0xFFFFDAD6)

        val statusActive = primary
        val statusInactive = Color(0xFF7A9590)
        val leafRamFill = Color(0xFF4DB6A8)
        val leafSwapFill = Color(0xFFE09A5A)
        val bloom = primary.copy(alpha = 0.12f)
    }
}
