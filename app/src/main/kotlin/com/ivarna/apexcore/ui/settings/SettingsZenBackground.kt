package com.ivarna.apexcore.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Settings ambient — calm aurora bands, soft pebbles, zen rings, constellation.
 *
 * Perf: static pose (no continuous breathe/flow/twinkle).
 */
@Composable
fun SettingsZenBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    val auroraA = scheme.primaryContainer.copy(alpha = 0.11f)
    val auroraB = scheme.secondaryContainer.copy(alpha = 0.10f)
    val auroraC = scheme.tertiaryContainer.copy(alpha = 0.07f)
    val ring = scheme.primary.copy(alpha = 0.14f)
    val ringSoft = scheme.primaryContainer.copy(alpha = 0.10f)
    val pebbleA = scheme.primary.copy(alpha = 0.12f)
    val pebbleB = scheme.secondary.copy(alpha = 0.10f)
    val pebbleC = scheme.tertiary.copy(alpha = 0.09f)
    val star = scheme.onSurface.copy(alpha = 0.22f)
    val starWarm = scheme.secondary.copy(alpha = 0.28f)
    val washTop = scheme.primaryContainer.copy(alpha = 0.09f)
    val washSide = scheme.secondaryContainer.copy(alpha = 0.08f)

    // Fixed aesthetic pose
    val breathe = 0.6f
    val flow = 1.4f

    val pebbles = remember {
        listOf(
            Pebble(0.12f, 0.22f, 0.055f, 0.0f, 0),
            Pebble(0.88f, 0.18f, 0.042f, 1.1f, 1),
            Pebble(0.78f, 0.55f, 0.070f, 2.0f, 2),
            Pebble(0.18f, 0.62f, 0.048f, 0.6f, 0),
            Pebble(0.55f, 0.78f, 0.060f, 1.7f, 1),
            Pebble(0.92f, 0.82f, 0.035f, 2.4f, 2)
        )
    }

    val stars = remember {
        List(16) { i ->
            StarSeed(
                x = ((i * 41) % 97) / 97f * 0.94f + 0.03f,
                y = ((i * 67) % 89) / 89f * 0.92f + 0.04f,
                size = 1.2f + (i % 4) * 0.55f,
                phase = (i * 0.51f) % 6.28f,
                warm = i % 4 == 0
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val b = breathe
        val f = flow

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(washTop, Color.Transparent),
                center = Offset(w * 0.2f, 0f),
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.2f, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(washSide, Color.Transparent),
                center = Offset(w * 1.0f, h * 0.65f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w, h * 0.65f)
        )

        drawAuroraBand(
            yCenter = h * (0.20f + sin(f) * 0.02f),
            amplitude = h * 0.035f,
            phase = f,
            color = auroraA,
            width = w,
            thickness = h * 0.14f
        )
        drawAuroraBand(
            yCenter = h * (0.48f + cos(f * 0.8f) * 0.025f),
            amplitude = h * 0.04f,
            phase = f + 1.2f,
            color = auroraB,
            width = w,
            thickness = h * 0.16f
        )
        drawAuroraBand(
            yCenter = h * (0.75f + sin(f * 0.6f + 0.5f) * 0.02f),
            amplitude = h * 0.03f,
            phase = f + 2.4f,
            color = auroraC,
            width = w,
            thickness = h * 0.12f
        )

        val zen = Offset(w * 0.5f, h * 0.42f)
        val breathScale = 1f + sin(b) * 0.04f
        for (i in 1..3) {
            val r = w * (0.10f + i * 0.08f) * breathScale
            val a = (0.55f - i * 0.1f).coerceAtLeast(0.15f)
            drawCircle(
                color = if (i % 2 == 0) ring.copy(alpha = ring.alpha * a)
                else ringSoft.copy(alpha = ringSoft.alpha * a),
                radius = r,
                center = zen,
                style = Stroke(width = 1.5f + (4 - i) * 0.4f)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scheme.primaryContainer.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = zen,
                radius = w * 0.18f
            ),
            radius = w * 0.18f,
            center = zen
        )

        pebbles.forEach { p ->
            val c = when (p.tone) {
                0 -> pebbleA
                1 -> pebbleB
                else -> pebbleC
            }
            val center = Offset(w * p.x, h * p.y)
            val rx = w * p.r
            val ry = rx * (0.72f + (p.tone * 0.06f))
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(c, Color.Transparent),
                    center = center,
                    radius = rx * 1.6f
                ),
                topLeft = Offset(center.x - rx * 1.6f, center.y - ry * 1.6f),
                size = androidx.compose.ui.geometry.Size(rx * 3.2f, ry * 3.2f)
            )
            drawOval(
                color = c.copy(alpha = c.alpha * 1.3f),
                topLeft = Offset(center.x - rx, center.y - ry),
                size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
            )
        }

        stars.forEach { s ->
            val a = 0.55f + (sin(s.phase).toFloat() * 0.25f)
            val c = if (s.warm) starWarm else star
            val pos = Offset(w * s.x, h * s.y)
            drawCircle(c.copy(alpha = c.alpha * a), s.size, pos)
            if (s.warm) {
                val arm = s.size * 2.4f
                drawLine(
                    c.copy(alpha = c.alpha * a * 0.5f),
                    Offset(pos.x - arm, pos.y),
                    Offset(pos.x + arm, pos.y),
                    1f
                )
                drawLine(
                    c.copy(alpha = c.alpha * a * 0.5f),
                    Offset(pos.x, pos.y - arm),
                    Offset(pos.x, pos.y + arm),
                    1f
                )
            }
        }
    }
}

private data class Pebble(
    val x: Float,
    val y: Float,
    val r: Float,
    val phase: Float,
    val tone: Int
)

private data class StarSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val phase: Float,
    val warm: Boolean
)

private fun DrawScope.drawAuroraBand(
    yCenter: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    width: Float,
    thickness: Float
) {
    val path = Path()
    val steps = 16
    path.moveTo(0f, yCenter)
    for (i in 0..steps) {
        val x = width * (i / steps.toFloat())
        val y = yCenter + sin(phase + i * 0.45f) * amplitude +
            cos(phase * 0.7f + i * 0.25f) * amplitude * 0.4f
        path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color.copy(alpha = color.alpha * 0.35f),
        style = Stroke(width = thickness)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = thickness * 0.45f)
    )
}
