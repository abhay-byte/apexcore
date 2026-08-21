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
import androidx.compose.ui.unit.dp

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

    val transition = rememberInfiniteTransition(label = "stripe")
    val stripe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripeFloat"
    )

    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            if (variant == ChamferVariant.Primary) clack.confirm() else clack.row()
        }
    }

    Box(
        modifier
            .height(if (tall) 56.dp else 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (variant == ChamferVariant.Primary)
                    (if (pressed) Iron.Signal700 else Iron.Signal500)
                else Color.Transparent
            )
            .then(
                if (variant == ChamferVariant.Outline)
                    Modifier.border(2.dp, Iron.Bone300, shape)
                else Modifier
            )
            .drawWithCache {
                val path = chamferPath(size, 4.dp.toPx(), 10.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    if (variant == ChamferVariant.Primary) {
                        drawLine(
                            Iron.Signal300.copy(alpha = 0.7f),
                            Offset(6.dp.toPx(), 1.5.dp.toPx()),
                            Offset(size.width - 14.dp.toPx(), 1.5.dp.toPx()),
                            1.dp.toPx()
                        )
                    }
                    if (busy) {
                        clipPath(path) {
                            val gap = 24.dp.toPx()
                            val w = 8.dp.toPx()
                            var x = -size.height + stripe * gap
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
        Text(
            text,
            style = IronType.Label,
            color = if (variant == ChamferVariant.Primary) Iron.Ink900 else Iron.Bone300
        )
    }
}
