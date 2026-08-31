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
    val reduced = LocalReducedMotion.current
    val skin = ironSkin()
    val phosphor = skin.phosphor()
    val travel = 24.dp
    val x = remember { Animatable(if (checked) 1f else 0f) }
    val wob = remember { Animatable(0f) }

    LaunchedEffect(checked, enabled) {
        if (!enabled) return@LaunchedEffect
        x.animateTo(if (checked) 1f else 0f, IronMotion.machined())
        if (reduced) return@LaunchedEffect
        wob.snapTo(0f)
        wob.animateTo(0f, keyframes {
            durationMillis = 90
            3f at 30
            (-3f) at 60
        })
    }

    val trackOff = if (skin.isPaper) Iron.Bone300 else Iron.Anvil600
    // Disabled tracks must remain visible on plate surfaces (Bone100 used to vanish on paper).
    val trackDisabled = if (skin.isPaper) Iron.Bone300 else Iron.Anvil800
    val trackBorder = when {
        !enabled && skin.isPaper -> Iron.Ink600.copy(alpha = 0.35f)
        !enabled -> Iron.Anvil500
        checked -> phosphor.copy(alpha = 0.4f)
        skin.isPaper -> skin.hairline
        else -> Iron.Anvil600
    }
    val knobFill = when {
        enabled -> Iron.Brass400
        skin.isPaper -> Iron.Bone500
        else -> Iron.Anvil500
    }
    val knobBorder = when {
        enabled -> Iron.Ink900
        skin.isPaper -> Iron.Ink600.copy(alpha = 0.55f)
        else -> Iron.Anvil500
    }

    Box(
        modifier
            .size(52.dp, 28.dp)
            .clip(IronShape.Slot)
            .background(
                when {
                    !enabled -> trackDisabled
                    checked -> phosphor.copy(alpha = if (skin.isPaper) 0.22f else 0.30f)
                    else -> trackOff
                }
            )
            .border(1.dp, trackBorder, IronShape.Slot)
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
                .background(knobFill)
                .border(1.dp, knobBorder, androidx.compose.foundation.shape.CircleShape)
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
    val skin = ironSkin()
    val groove = if (skin.isPaper) Iron.Bone100 else Iron.Anvil950
    val grooveBorder = if (skin.isPaper) skin.hairline else Iron.Anvil600
    val idleLabel = if (skin.isPaper) Iron.Ink600 else Iron.Bone300

    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(IronShape.Slot)
            .background(groove)
            .border(1.dp, grooveBorder, IronShape.Slot)
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
                        color = if (i == selected) Iron.Ink900 else idleLabel
                    )
                }
            }
        }
    }
}
