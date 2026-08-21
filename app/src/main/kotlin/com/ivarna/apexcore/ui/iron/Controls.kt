package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/* ── §3.10 Toggle: brass knob, spring travel, 2-frame wobble on arrival ── */
@Composable
fun MachinedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clack = rememberClack()
    val travel = 24.dp
    val x = remember { Animatable(if (checked) 1f else 0f) }
    val wob = remember { Animatable(0f) }

    LaunchedEffect(checked, enabled) {
        if (!enabled) return@LaunchedEffect
        x.animateTo(if (checked) 1f else 0f, IronMotion.machined())
        wob.snapTo(0f)
        wob.animateTo(0f, keyframes {
            durationMillis = 90
            3f at 30
            (-3f) at 60
        })
    }

    Box(
        modifier
            .size(52.dp, 28.dp)
            .clip(IronShape.Slot)
            .background(
                when {
                    !enabled -> Iron.Anvil800
                    checked  -> Iron.Phosphor400.copy(alpha = 0.30f)
                    else     -> Iron.Anvil600
                }
            )
            .border(1.dp, if (checked) Iron.Phosphor400.copy(alpha = 0.4f) else Iron.Anvil600, IronShape.Slot)
            .toggleable(
                value = checked,
                role = Role.Switch,
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { newValue ->
                    if (newValue) clack.confirm() else clack.off()
                    onCheckedChange(newValue)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(24.dp)
                .graphicsLayer {
                    translationX = travel.toPx() * x.value
                    rotationZ = wob.value
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (enabled) Iron.Brass400 else Iron.Bone500)
                .border(1.dp, Iron.Ink900, androidx.compose.foundation.shape.CircleShape)
        )
    }
}

/* ── §3.10 Segment: groove + sliding brass block + ink-filled active label ── */
@Composable
fun MachinedSegment(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(IronShape.Slot)
            .background(Iron.Anvil950)
            .border(1.dp, Iron.Anvil600, IronShape.Slot)
            .ironGrain(0.03f)
    ) {
        val w = maxWidth / options.size
        val blockX by animateDpAsState(w * selected, IronMotion.block(), label = "segBlock")
        Box(
            Modifier
                .offset(x = blockX)
                .width(w)
                .fillMaxHeight()
                .background(Iron.Brass400)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            clack.tick()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        style = IronType.Label,
                        color = if (i == selected) Iron.Ink900 else Iron.Bone300
                    )
                }
            }
        }
    }
}
