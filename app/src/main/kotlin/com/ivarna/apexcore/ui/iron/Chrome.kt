package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.isActive

/** Anchored menu — put inside the trigger's Box; aligns to its top-end. */
@Composable
fun IronDropdown(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        EngravedPlate(modifier, padding = PaddingValues(8.dp)) {
            Box(Modifier.width(width)) { Column(content = content) }
        }
    }
}

@Composable
fun DropdownLedRow(label: String, ready: Boolean, selected: Boolean, onClick: () -> Unit) {
    val clack = rememberClack()
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(IronShape.Slot)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clack.row()
                onClick()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(if (ready) LedState.READY else LedState.BLOCKED)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = IronType.Mono,
            color = if (selected) Iron.Bone100 else Iron.Bone300,
            modifier = Modifier.weight(1f)
        )
        if (selected) Text("●", style = IronType.MonoSm, color = Iron.Brass400)
    }
}

@Composable
fun BackArrow(tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clack = rememberClack()
    Canvas(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                clack.row()
                onClick()
            }
    ) {
        val c = center
        drawLine(tint, Offset(c.x - 9.dp.toPx(), c.y), Offset(c.x + 9.dp.toPx(), c.y), 2.dp.toPx())
        drawLine(tint, Offset(c.x - 4.dp.toPx(), c.y - 5.dp.toPx()), Offset(c.x - 9.dp.toPx(), c.y), 2.dp.toPx())
        drawLine(tint, Offset(c.x - 4.dp.toPx(), c.y + 5.dp.toPx()), Offset(c.x - 9.dp.toPx(), c.y), 2.dp.toPx())
        tickAtGlyph(Offset(c.x + 9.dp.toPx(), c.y), 1f, 0f, tint)
    }
}

private fun DrawScope.tickAtGlyph(
    p: Offset, dx: Float, dy: Float, tint: Color,
) {
    val n = 3.dp.toPx()
    drawLine(tint, Offset(p.x - dy * n, p.y + dx * n), Offset(p.x + dy * n, p.y - dx * n), 2.dp.toPx())
}

/** §8 — probing is ALWAYS a spinning needle, never a skeleton. */
@Composable
fun LoadingNeedle(tint: Color = Iron.Bone300, modifier: Modifier = Modifier) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            rot.snapTo(0f)
            rot.animateTo(360f, tween(900, easing = LinearEasing))
        }
    }
    Canvas(modifier.size(28.dp)) {
        withTransform({ rotate(rot.value, pivot = center) }) {
            drawLine(tint, center, Offset(center.x, center.y - size.minDimension / 2f + 2.dp.toPx()), 2.dp.toPx())
        }
        drawCircle(tint, 2.dp.toPx(), center)
    }
}
