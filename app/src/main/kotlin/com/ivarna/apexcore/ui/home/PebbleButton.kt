package com.ivarna.apexcore.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.shell.State
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenIcons

/**
 * River-pebble Purge Engine CTA.
 * RESULT is not shown here — parent AnimatedContent swaps to UnifiedResultCard.
 * Home-only: large looping flowers + lively icon / breath motion.
 */
@Composable
fun PebbleButton(
    state: State,
    title: String = "Purge Engine",
    subtitle: String = "Clear background bloat",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)

    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "pebble_offset"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "pebble_scale"
    )

    val infinite = rememberInfiniteTransition(label = "pebble_infinite")
    val breath by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pebble_breath"
    )
    val shimmer by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pebble_shimmer"
    )
    val iconBob by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_bob"
    )
    val iconSpin by infinite.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_spin"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val displayScale = if (state == State.IDLE) breath * pressScale else pressScale
    val displayTitle = if (state == State.BOOSTING) "Purging system…" else title
    val displaySubtitle = if (state == State.BOOSTING) "Freezing background services" else subtitle

    val fillBrush = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.primary.copy(alpha = 0.88f))
    )
    val borderColor = if (state == State.BOOSTING) {
        scheme.onPrimary.copy(alpha = shimmer * 0.7f)
    } else {
        scheme.onPrimary.copy(alpha = 0.28f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .scale(displayScale)
            .offset(y = pressOffset),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fillBrush)
                .border(width = 2.dp, color = borderColor, shape = shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = scheme.primary),
                    onClick = onClick
                )
        ) {
            // Soft flowers on primary CTA (onPrimary-tinted via alphaScale)
            HomeCardFlowerDecor(
                style = 2,
                sizeScale = 1.4f,
                alphaScale = 0.75f,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = displayTitle,
                        color = scheme.onPrimary,
                        fontSize = 20.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displaySubtitle,
                        color = scheme.onPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            translationY = if (state == State.BOOSTING) 0f else iconBob
                            rotationZ = if (state == State.BOOSTING) shimmer * 12f else iconSpin
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.onPrimary.copy(alpha = glowPulse)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZenIcons.WaterDrop,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = if (state == State.BOOSTING) 0.9f + shimmer * 0.15f else 1f
                                scaleY = if (state == State.BOOSTING) 0.9f + shimmer * 0.15f else 1f
                            }
                    )
                }
            }
        }
    }
}
