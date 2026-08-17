package com.ivarna.apexcore.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.shell.State
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenIcons

/**
 * River-pebble Purge Engine CTA.
 * RESULT is not shown here — parent AnimatedContent swaps to UnifiedResultCard.
 * Perf: press springs only — no idle infinite breath/shimmer (UI-thread cost).
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

    val isBoosting = state == State.BOOSTING
    val displayTitle = if (isBoosting) "Purging system…" else title
    val displaySubtitle = if (isBoosting) "Freezing background services" else subtitle

    val fillBrush = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.primary.copy(alpha = 0.88f))
    )
    val borderColor = scheme.onPrimary.copy(alpha = if (isBoosting) 0.55f else 0.28f)
    val iconBgAlpha = if (isBoosting) 0.22f else 0.14f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .scale(pressScale)
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.onPrimary.copy(alpha = iconBgAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZenIcons.WaterDrop,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
