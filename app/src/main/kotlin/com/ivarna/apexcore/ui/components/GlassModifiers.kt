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
import dev.chrisbanes.haze.hazeChild

/**
 * Shared frosted-glass chrome tokens (top bar + bottom nav).
 * Same tint/blur/noise so both bars read as one material.
 */
object ZenFrost {
    /** Stronger than finalbenchmark 30dp so content under the glass softens clearly. */
    val blurRadius = 56.dp
    val noiseFactor = 0.08f
    /** Frost overlay — high enough for even color, low enough to keep glass. */
    const val tintAlpha = 0.42f

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
    backgroundColor = ZenFrost.tint(surface)
    blurRadius = ZenFrost.blurRadius
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
