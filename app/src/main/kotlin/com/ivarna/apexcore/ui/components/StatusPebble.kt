package com.ivarna.apexcore.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.ui.theme.LocalZenSemantics

/**
 * Compact status indicator pebble.
 * - true  → solid statusActive (theme primary)
 * - false → statusInactive
 * - null  → outline pulse (checking)
 */
@Composable
fun StatusPebble(
    active: Boolean?,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val zen = LocalZenSemantics.current
    val outline = MaterialTheme.colorScheme.outline
    when (active) {
        true -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(zen.statusActive)
        )
        false -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(zen.statusInactive)
        )
        null -> {
            val infinite = rememberInfiniteTransition(label = "statusPebblePulse")
            val alpha by infinite.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "statusPebbleAlpha"
            )
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = outline.copy(alpha = alpha),
                        shape = CircleShape
                    )
                    .background(Color.Transparent)
            )
        }
    }
}
