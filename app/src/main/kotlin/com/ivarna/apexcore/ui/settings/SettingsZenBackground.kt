package com.ivarna.apexcore.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Settings ambient — calm aurora bands, drifting soft pebbles, breathing zen rings,
 * slow constellation twinkles. Soft & contemplative (not vines, not HUD, not energy).
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

    val infinite = rememberInfiniteTransition(label = "settings_zen")
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breathe"
    )
    val flow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )
    val twinkle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twinkle"
    )

    val pebbles = remember {
        listOf(
            Pebble(0.12f, 0.22f, 0.055f, 0.0f, 0),
            Pebble(0.88f, 0.18f, 0.042f, 1.1f, 1),
            Pebble(0.78f, 0.55f, 0.070f, 2.0f, 2),
            Pebble(0.18f, 0.62f, 0.048f, 0.6f, 0),
            Pebble(0.55f, 0.78f, 0.060f, 1.7f, 1),
            Pebble(0.92f, 0.82f, 0.035f, 2.4f, 2),
            Pebble(0.35f, 0.35f, 0.030f, 0.9f, 0),
            Pebble(0.65f, 0.28f, 0.038f, 1.4f, 1)
        )
    }

    val stars = remember {
        List(28) { i ->
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
        val t = twinkle

        // Soft corner light washes
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

        // Aurora bands — slow horizontal light waves
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

        // Breathing zen rings (center-ish, soft)
        val zen = Offset(
            w * 0.5f + cos(f * 0.3f) * 10f,
            h * 0.42f + sin(f * 0.25f) * 8f
        )
        val breathScale = 1f + sin(b) * 0.06f
        for (i in 1..4) {
            val r = w * (0.10f + i * 0.07f) * breathScale
            val a = (0.55f - i * 0.08f).coerceAtLeast(0.12f)
            drawCircle(
                color = if (i % 2 == 0) ring.copy(alpha = ring.alpha * a)
                else ringSoft.copy(alpha = ringSoft.alpha * a),
                radius = r,
                center = zen,
                style = Stroke(width = 1.5f + (4 - i) * 0.4f)
            )
        }
        // Soft fill heart of rings
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scheme.primaryContainer.copy(alpha = 0.08f * (0.7f + 0.3f * sin(b))),
                    Color.Transparent
                ),
                center = zen,
                radius = w * 0.18f
            ),
            radius = w * 0.18f,
            center = zen
        )

        // Drifting soft pebbles
        pebbles.forEach { p ->
            val dx = cos(f + p.phase) * w * 0.02f
            val dy = sin(f * 0.85f + p.phase) * h * 0.015f
            val scale = 1f + sin(b + p.phase) * 0.08f
            val c = when (p.tone) {
                0 -> pebbleA
                1 -> pebbleB
                else -> pebbleC
            }
            val center = Offset(w * p.x + dx, h * p.y + dy)
            val rx = w * p.r * scale
            val ry = rx * (0.72f + (p.tone * 0.06f))
            // soft outer glow
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

        // Constellation twinkles
        stars.forEach { s ->
            val a = ((sin(t + s.phase) + 1f) * 0.5f).coerceIn(0.1f, 1f)
            val c = if (s.warm) starWarm else star
            val pos = Offset(w * s.x, h * s.y)
            drawCircle(c.copy(alpha = c.alpha * a), s.size, pos)
            if (s.warm) {
                // tiny cross sparkle
                val arm = s.size * 2.8f * a
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
    val steps = 24
    path.moveTo(0f, yCenter)
    for (i in 0..steps) {
        val x = width * (i / steps.toFloat())
        val y = yCenter + sin(phase + i * 0.45f) * amplitude +
            cos(phase * 0.7f + i * 0.25f) * amplitude * 0.4f
        path.lineTo(x, y)
    }
    // Stroke thick soft band via layered strokes
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
