package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChamferVariant { Primary, Outline }

@Composable
fun ChamferButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChamferVariant = ChamferVariant.Primary,
    tall: Boolean = true,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val clack = rememberClack()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, IronMotion.machined(), label = "press")
    val shape = remember { ChamferShape() }
    val skin = ironSkin()

    val stripe = remember { Animatable(0f) }
    LaunchedEffect(busy, pressed) {
        if (!busy && !pressed) {
            stripe.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            stripe.snapTo(0f)
            stripe.animateTo(1f, tween(1200, easing = LinearEasing))
        }
    }

    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            if (variant == ChamferVariant.Primary) clack.confirm() else clack.row()
        }
    }

    // Primary stays ink-on-signal in both finishes (high contrast on orange).
    // Outline tracks the active skin so Vellum stays readable.
    // Disabled must look inert (APPLY with no game, etc.) — not full-strength Signal.
    val outlineColor = if (skin.isPaper) Iron.Ink600 else Iron.Bone300
    val fillColor = when {
        variant != ChamferVariant.Primary -> Color.Transparent
        !enabled -> Iron.Signal500.copy(alpha = 0.32f)
        pressed -> Iron.Signal700
        else -> Iron.Signal500
    }
    val borderColor = when {
        variant != ChamferVariant.Outline -> Color.Transparent
        !enabled -> outlineColor.copy(alpha = 0.35f)
        else -> outlineColor
    }
    val labelColor = when {
        !enabled && variant == ChamferVariant.Primary -> Iron.Ink900.copy(alpha = 0.40f)
        !enabled -> outlineColor.copy(alpha = 0.40f)
        variant == ChamferVariant.Primary -> Iron.Ink900
        else -> outlineColor
    }
    val labelStyle = IronType.Label.copy(
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.7.sp,
    )

    Box(
        modifier
            .height(if (tall) 56.dp else 44.dp)
            .clip(shape)
            .background(fillColor)
            .then(
                if (variant == ChamferVariant.Outline)
                    Modifier.border(2.dp, borderColor, shape)
                else Modifier
            )
            .drawWithCache {
                val path = chamferPath(size, 4.dp.toPx(), 10.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    if (variant == ChamferVariant.Primary && enabled) {
                        drawLine(
                            Iron.Signal300.copy(alpha = 0.7f),
                            Offset(6.dp.toPx(), 1.5.dp.toPx()),
                            Offset(size.width - 14.dp.toPx(), 1.5.dp.toPx()),
                            1.dp.toPx()
                        )
                    }
                    if (busy && enabled) {
                        clipPath(path) {
                            val gap = 24.dp.toPx()
                            val w = 8.dp.toPx()
                            var x = -size.height + stripe.value * gap
                            while (x < size.width + size.height) {
                                drawLine(
                                    Iron.Ink900.copy(alpha = 0.18f),
                                    Offset(x, size.height),
                                    Offset(x + size.height, 0f),
                                    w
                                )
                                x += gap
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Scale the label only — scaling the clipped chamfer layer blew glyphs up on wide tablet panes.
        // Caller controls width via outer modifier; never force fillMaxWidth here (breaks Row + weight siblings).
        Text(
            text = text,
            style = labelStyle,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
