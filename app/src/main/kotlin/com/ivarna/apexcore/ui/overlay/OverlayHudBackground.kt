package com.ivarna.apexcore.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay / HUD ambient — tactical grid, scan line, radar, brackets, blips.
 * Tech HUD feel; no vines, no energy sparks.
 *
 * Perf: static pose (no continuous scan/sweep animation).
 */
@Composable
fun OverlayHudBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    val gridLine = scheme.primary.copy(alpha = 0.07f)
    val scanColor = scheme.primaryContainer.copy(alpha = 0.14f)
    val bracket = scheme.primary.copy(alpha = 0.28f)
    val radarStroke = scheme.primary.copy(alpha = 0.20f)
    val radarSoft = scheme.primaryContainer.copy(alpha = 0.12f)
    val blipHot = scheme.secondary.copy(alpha = 0.55f)
    val blipCool = scheme.primary.copy(alpha = 0.40f)
    val vignette = scheme.primaryContainer.copy(alpha = 0.10f)
    val reticle = scheme.outlineVariant.copy(alpha = 0.35f)

    // Fixed pose
    val scan = 0.38f
    val sweep = 42f
    val drift = 0.9f

    val blips = remember {
        listOf(
            Blip(0.18f, 0.32f, 0.0f, true),
            Blip(0.78f, 0.28f, 1.2f, false),
            Blip(0.62f, 0.55f, 2.1f, true),
            Blip(0.28f, 0.68f, 0.7f, false),
            Blip(0.85f, 0.72f, 1.8f, true),
            Blip(0.42f, 0.22f, 2.8f, false)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(vignette, Color.Transparent),
                center = Offset(w * 0.5f, h * 0.05f),
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.5f, h * 0.05f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.secondaryContainer.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * 0.95f, h * 0.5f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.95f, h * 0.5f)
        )

        // Coarser grid = fewer draw calls
        val step = w * 0.11f
        var x = 0f
        while (x <= w) {
            drawLine(gridLine, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y <= h) {
            drawLine(gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += step
        }

        val scanY = h * scan
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    scanColor,
                    Color.Transparent
                ),
                startY = scanY - 28f,
                endY = scanY + 28f
            ),
            topLeft = Offset(0f, scanY - 28f),
            size = Size(w, 56f)
        )
        drawLine(
            color = scanColor.copy(alpha = (scanColor.alpha * 1.6f).coerceAtMost(0.35f)),
            start = Offset(0f, scanY),
            end = Offset(w, scanY),
            strokeWidth = 1.4f
        )

        val m = w * 0.06f
        val arm = w * 0.07f
        drawBracket(Offset(m, m), arm, bracket, topLeft = true)
        drawBracket(Offset(w - m, m), arm, bracket, topLeft = false, top = true)
        drawBracket(Offset(m, h - m), arm, bracket, topLeft = true, top = false)
        drawBracket(Offset(w - m, h - m), arm, bracket, topLeft = false, top = false)

        val radarCenter = Offset(w * 0.82f, h * 0.38f)
        val r1 = w * 0.12f
        val r2 = w * 0.18f
        val r3 = w * 0.24f
        drawCircle(radarSoft, r3, radarCenter, style = Stroke(1.2f))
        drawCircle(radarStroke, r2, radarCenter, style = Stroke(1.4f))
        drawCircle(radarStroke.copy(alpha = radarStroke.alpha * 0.7f), r1, radarCenter, style = Stroke(1.2f))
        rotate(degrees = sweep, pivot = radarCenter) {
            drawLine(
                color = radarStroke.copy(alpha = 0.35f),
                start = radarCenter,
                end = Offset(radarCenter.x, radarCenter.y - r3),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(radarSoft.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(radarCenter.x, radarCenter.y - r2 * 0.5f),
                    radius = r2
                ),
                radius = r2 * 0.55f,
                center = Offset(radarCenter.x + r1 * 0.2f, radarCenter.y - r2 * 0.45f)
            )
        }

        val ret = Offset(w * 0.22f, h * 0.48f)
        val rr = w * 0.05f
        drawCircle(reticle, rr, ret, style = Stroke(1.2f))
        drawLine(reticle, Offset(ret.x - rr * 1.4f, ret.y), Offset(ret.x - rr * 0.55f, ret.y), 1.2f)
        drawLine(reticle, Offset(ret.x + rr * 0.55f, ret.y), Offset(ret.x + rr * 1.4f, ret.y), 1.2f)
        drawLine(reticle, Offset(ret.x, ret.y - rr * 1.4f), Offset(ret.x, ret.y - rr * 0.55f), 1.2f)
        drawLine(reticle, Offset(ret.x, ret.y + rr * 0.55f), Offset(ret.x, ret.y + rr * 1.4f), 1.2f)

        blips.forEach { b ->
            val a = if (b.hot) 0.85f else 0.55f
            val c = if (b.hot) blipHot else blipCool
            val pos = Offset(w * b.x, h * b.y)
            drawCircle(c.copy(alpha = c.alpha * a), 3.2f, pos)
            drawCircle(c.copy(alpha = c.alpha * a * 0.25f), 9f, pos)
            drawLine(
                c.copy(alpha = c.alpha * a * 0.6f),
                Offset(pos.x - 7f, pos.y),
                Offset(pos.x + 7f, pos.y),
                1f
            )
            drawLine(
                c.copy(alpha = c.alpha * a * 0.6f),
                Offset(pos.x, pos.y - 7f),
                Offset(pos.x, pos.y + 7f),
                1f
            )
        }

        for (i in 0 until 3) {
            val ang = drift + i * 1.4f
            val cx = w * (0.35f + i * 0.16f) + cos(ang) * 8f
            val cy = h * 0.70f
            drawHexOutline(Offset(cx, cy), w * 0.028f, gridLine.copy(alpha = 0.18f))
        }
    }
}

private data class Blip(val x: Float, val y: Float, val phase: Float, val hot: Boolean)

private fun DrawScope.drawBracket(
    origin: Offset,
    arm: Float,
    color: Color,
    topLeft: Boolean,
    top: Boolean = true
) {
    val xDir = if (topLeft) 1f else -1f
    val yDir = if (top) 1f else -1f
    drawLine(
        color,
        origin,
        Offset(origin.x + arm * xDir, origin.y),
        strokeWidth = 2.4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color,
        origin,
        Offset(origin.x, origin.y + arm * yDir),
        strokeWidth = 2.4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawHexOutline(center: Offset, radius: Float, color: Color) {
    val pts = (0 until 6).map { i ->
        val ang = (PI / 3.0 * i - PI / 6.0).toFloat()
        Offset(center.x + cos(ang) * radius, center.y + sin(ang) * radius)
    }
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        drawLine(color, a, b, strokeWidth = 1.2f)
    }
}
