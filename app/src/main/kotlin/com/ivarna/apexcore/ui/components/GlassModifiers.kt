package com.ivarna.apexcore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.ui.theme.ZenColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Shared frosted-glass chrome tokens (top bar + bottom nav).
 * Same tint/blur/noise so both bars read as one material.
 *
 * Haze 1.0 applies **blur of the [haze] source**, then **[tints]** on top.
 * [backgroundColor] is the solid base / fallback sample — not the glass veil.
 * Without [tints], only a weak translucent plate shows (no real frost).
 */
object ZenFrost {
    /** Strong soften under chrome — in the ballpark of dialog FLAG_BLUR_BEHIND. */
    val blurRadius = 48.dp
    /** Content cards: still soft, slightly less than chrome so vines read through. */
    val cardBlurRadius = 40.dp
    val noiseFactor = 0.15f
    /**
     * Glass veil alpha on [HazeTint]. High enough that scrolled text under bars
     * is soft/illegible; low enough to keep material as glass not solid.
     */
    const val tintAlpha = 0.78f
    /**
     * Card veil — a bit denser than chrome so stats/labels stay crisp over nature BG.
     */
    const val cardTintAlpha = 0.86f
    /** When RenderEffect blur is unavailable, stay nearly solid so chrome still reads. */
    const val fallbackTintAlpha = 0.94f

    fun tint(surface: Color): Color = surface.copy(alpha = tintAlpha)
}

/**
 * Normative solid glass surface for cards/dialogs (no backdrop blur).
 */
fun Modifier.zenGlassBackground(
    shape: Shape = RoundedCornerShape(50),
    fill: Color,
    borderColor: Color,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(borderWidth, borderColor, shape)

/**
 * True backdrop frost via Haze (same recipe on top bar + bottom nav).
 * Prefer [MaterialTheme.colorScheme.surface] as [surface] for light/dark match.
 */
fun Modifier.zenFrostChild(
    hazeState: HazeState,
    surface: Color
): Modifier = this.hazeChild(state = hazeState) {
    // Opaque base (Haze samples this when content can't be drawn / for compositing)
    backgroundColor = surface
    // Actual glass veil — required for a visible frost (was missing before)
    tints = listOf(HazeTint(surface.copy(alpha = ZenFrost.tintAlpha)))
    fallbackTint = HazeTint(surface.copy(alpha = ZenFrost.fallbackTintAlpha))
    blurRadius = ZenFrost.blurRadius
    noiseFactor = ZenFrost.noiseFactor
}

/**
 * Frosted glass for content cards (purge result, etc.).
 * Slightly stronger veil than chrome so dense body text stays legible over vines,
 * while still showing true Haze backdrop blur.
 */
fun Modifier.zenFrostCard(
    hazeState: HazeState,
    surface: Color,
    shape: Shape = RoundedCornerShape(32.dp)
): Modifier = this
    .clip(shape)
    .hazeChild(state = hazeState) {
        backgroundColor = surface
        tints = listOf(HazeTint(surface.copy(alpha = ZenFrost.cardTintAlpha)))
        fallbackTint = HazeTint(surface.copy(alpha = ZenFrost.fallbackTintAlpha))
        blurRadius = ZenFrost.cardBlurRadius
        noiseFactor = ZenFrost.noiseFactor
    }

/**
 * Soft primary-tinted bloom shadow for floating island chrome.
 * Prefer passing theme primary from the call site for dark-mode correctness.
 */
fun Modifier.zenBloom(
    shape: Shape,
    color: Color = ZenColors.primary
): Modifier = this.shadow(
    elevation = 12.dp,
    shape = shape,
    ambientColor = color.copy(alpha = 0.10f),
    spotColor = color.copy(alpha = 0.08f)
)
