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

/**
 * Normative v1 glass surface. Use for ZenBottomNav island, GlassCard, dialog chrome.
 * Same behavior on minSdk 24 through current target — no API branch required.
 * Solid glass only: fill + border. Do **not** apply RenderEffect blur.
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
 * Soft primary-tinted bloom shadow for floating island chrome.
 */
fun Modifier.zenBloom(
    shape: Shape,
    color: Color = ZenColors.primary
): Modifier = this.shadow(
    elevation = 12.dp,
    shape = shape,
    ambientColor = color.copy(alpha = 0.08f),
    spotColor = color.copy(alpha = 0.06f)
)
