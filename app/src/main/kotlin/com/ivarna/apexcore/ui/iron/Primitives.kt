package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos

/* ── §2.1 LED semantics ── */
enum class LedState { OFF, READY, CHECKING, BLOCKED, LIVE }

@Composable
fun LedDot(state: LedState, modifier: Modifier = Modifier, diameter: Dp = 6.dp) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            LedState.OFF, LedState.READY -> phase.snapTo(0f)
            else -> while (true) {
                val dur = when (state) {
                    LedState.BLOCKED -> 1000
                    LedState.CHECKING -> 1200
                    else -> 2000
                }
                phase.animateTo(1f, tween(dur, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }
    Canvas(modifier.size(diameter)) {
        val p = phase.value
        val (color, alpha) = when (state) {
            LedState.OFF      -> Iron.Bone500 to 0.4f
            LedState.READY    -> Iron.Phosphor400 to 1f
            LedState.CHECKING -> Iron.Signal500 to (0.35f + 0.65f * (0.5f - 0.5f * cos(p * 2f * Math.PI)).toFloat())
            LedState.BLOCKED  -> Iron.Ember500 to when {
                p < 0.15f -> 1f
                p < 0.30f -> 0.15f
                p < 0.45f -> 1f
                else -> 0.15f
            }
            LedState.LIVE     -> Iron.Phosphor400 to (0.7f + 0.3f * (0.5f - 0.5f * cos(p * 2f * Math.PI)).toFloat())
        }
        drawCircle(color, radius = size.minDimension / 2f, alpha = alpha)
    }
}

/* ── §2.1 The riso recipe — exactly one per screen ── */
@Composable
fun RisoText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Iron.Bone100) {
    val count = LocalRisoCount.current
    SideEffect { count.intValue++ }
    Box(modifier) {
        Text(
            text,
            style = style,
            color = Iron.Signal500,
            modifier = Modifier.offset(x = 1.5.dp, y = 1.dp).alpha(0.9f)
        )
        Text(text, style = style, color = color)
    }
}

/* ── §2.1 Engraving (dark surfaces) ── */
@Composable
fun EngravedText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Iron.Bone300) {
    Box(modifier) {
        Text(
            text,
            style = style,
            color = Iron.Anvil950,
            modifier = Modifier.offset(y = 1.dp).alpha(0.4f)
        )
        Text(text, style = style, color = color)
    }
}

/* ── §2.5 Brass screw ── */
@Composable
fun Screw(modifier: Modifier = Modifier) {
    Canvas(modifier.size(8.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Iron.Brass400, r, c)
        drawCircle(Iron.Ink900.copy(alpha = 0.3f), r, c, style = Stroke(1f))
        drawLine(Iron.Ink900, Offset(c.x - r * 0.55f, c.y), Offset(c.x + r * 0.55f, c.y), 1.2f)
        drawLine(Iron.Ink900, Offset(c.x, c.y - r * 0.55f), Offset(c.x, c.y + r * 0.55f), 1.2f)
    }
}

/* ── §2.6 Instrument Glyphs — 2dp stroke, tick terminals ── */
private fun DrawScope.glyphStroke(s: Float) = Stroke(width = 2f * s, cap = StrokeCap.Square)
private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, s: Float, c: Color) =
    drawLine(c, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = 2f * s, cap = StrokeCap.Square)

private fun DrawScope.tickAt(x: Float, y: Float, dx: Float, dy: Float, s: Float, c: Color) {
    val n = 3f * s
    drawLine(
        c,
        Offset((x - dy) * s - dx * n, (y + dx) * s - dy * n),
        Offset((x - dy) * s + dx * n, (y + dx) * s + dy * n),
        strokeWidth = 2f * s,
        cap = StrokeCap.Square
    )
}

@Composable
fun GaugeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    drawCircle(tint, radius = 9f * s, center = center, style = glyphStroke(s))
    line(12f, 12f, 18f, 6f, s, tint)
    tickAt(18f, 6f, 0.707f, -0.707f, s, tint)
    line(3f, 17f, 6f, 17f, s, tint)
    line(18f, 17f, 21f, 17f, s, tint)
}

@Composable
fun CartridgeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    drawRoundRect(
        tint,
        topLeft = Offset(4f * s, 6f * s),
        size = Size(16f * s, 13f * s),
        cornerRadius = CornerRadius(2f * s),
        style = glyphStroke(s)
    )
    line(10f, 6f, 14f, 6f, s, tint)
    line(4f, 3f, 9f, 3f, s, tint)
    tickAt(9f, 3f, 1f, 0f, s, tint)
}

@Composable
fun RailGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    line(8f, 3f, 8f, 21f, s, tint)
    tickAt(8f, 21f, 0f, 1f, s, tint)
    line(8f, 7f, 14f, 7f, s, tint)
    line(8f, 12f, 17f, 12f, s, tint)
    line(8f, 17f, 14f, 17f, s, tint)
}

@Composable
fun CaliperGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    line(4f, 5f, 12f, 5f, s, tint)
    line(12f, 5f, 12f, 19f, s, tint)
    line(20f, 5f, 20f, 13f, s, tint)
    line(20f, 13f, 13f, 13f, s, tint)
    drawCircle(tint, 2f * s, Offset(16f * s, 9f * s))
}

@Composable
fun ChevronGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    val s = size.width / 16f
    line(4f, 3f, 9f, 8f, s, tint)
    line(9f, 8f, 4f, 13f, s, tint)
    tickAt(4f, 13f, -0.707f, 0.707f, s, tint)
}

@Composable
fun LoupeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val s = size.width / 20f
    drawCircle(tint, radius = 6f * s, center = Offset(8f * s, 8f * s), style = glyphStroke(s))
    line(12.5f, 12.5f, 17f, 17f, s, tint)
    tickAt(17f, 17f, 0.707f, 0.707f, s, tint)
}
