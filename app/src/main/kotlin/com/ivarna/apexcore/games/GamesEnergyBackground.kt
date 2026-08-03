package com.ivarna.apexcore.games

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Games / launch ambient — energy core, hex lattice, sparks, rings.
 * Deliberately geometric (not organic vines).
 *
 * Perf: static pose (no infinite phase/pulse/spin). Still reads as energy
 * without continuous Canvas invalidation.
 */
@Composable
fun GamesEnergyBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    val coreGlow = scheme.secondary.copy(alpha = 0.18f)
    val coreHot = scheme.secondaryContainer.copy(alpha = 0.22f)
    val ringColor = scheme.primary.copy(alpha = 0.22f)
    val ringSoft = scheme.primaryContainer.copy(alpha = 0.14f)
    val hexStroke = scheme.primary.copy(alpha = 0.16f)
    val hexFill = scheme.primaryContainer.copy(alpha = 0.06f)
    val sparkA = scheme.secondary.copy(alpha = 0.55f)
    val sparkB = scheme.primary.copy(alpha = 0.45f)
    val streak = scheme.secondaryContainer.copy(alpha = 0.20f)
    val orbTeal = scheme.primaryContainer.copy(alpha = 0.12f)
    val orbGold = scheme.secondaryContainer.copy(alpha = 0.14f)

    // Fixed aesthetic pose (was continuous animation).
    val phase = 1.1f
    val pulse = 0.42f
    val rise = 0.55f
    val spin = 28f

    val sparks = remember {
        List(12) { i ->
            SparkSeed(
                xFrac = ((i * 37) % 100) / 100f * 0.92f + 0.04f,
                yBase = ((i * 53) % 100) / 100f,
                speed = 0.55f + (i % 5) * 0.12f,
                size = 1.6f + (i % 4) * 0.7f,
                gold = i % 3 != 0,
                phase = (i * 0.37f) % 6.28f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = phase
        val pu = pulse
        val r = rise

        val orb1 = Offset(w * 0.12f, h * 0.18f)
        val orb2 = Offset(w * 0.88f, h * 0.62f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbGold, Color.Transparent),
                center = orb1,
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = orb1
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbTeal, Color.Transparent),
                center = orb2,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = orb2
        )

        // Bottom power-core glow
        val core = Offset(w * 0.5f, h * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreHot, coreGlow, Color.Transparent),
                center = core,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = core
        )

        // Pulse rings frozen at staggered radii
        for (i in 0 until 3) {
            val t = ((pu + i / 3f) % 1f)
            val radius = w * (0.08f + t * 0.42f)
            val alphaScale = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = ringColor.copy(alpha = ringColor.alpha * alphaScale * 0.9f),
                radius = radius,
                center = core,
                style = Stroke(width = 2.2f * (1f - t * 0.5f))
            )
            drawCircle(
                color = ringSoft.copy(alpha = ringSoft.alpha * alphaScale),
                radius = radius * 0.92f,
                center = core,
                style = Stroke(width = 6f * (1f - t))
            )
        }

        rotate(degrees = spin * 0.15f, pivot = Offset(w * 0.08f, h * 0.35f)) {
            drawHexCluster(
                center = Offset(w * 0.08f, h * 0.35f),
                size = w * 0.055f,
                stroke = hexStroke,
                fill = hexFill
            )
        }
        rotate(degrees = -spin * 0.12f, pivot = Offset(w * 0.92f, h * 0.28f)) {
            drawHexCluster(
                center = Offset(w * 0.92f, h * 0.28f),
                size = w * 0.048f,
                stroke = hexStroke.copy(alpha = hexStroke.alpha * 0.85f),
                fill = hexFill
            )
        }
        rotate(degrees = spin * 0.1f, pivot = Offset(w * 0.9f, h * 0.78f)) {
            drawHexCluster(
                center = Offset(w * 0.9f, h * 0.78f),
                size = w * 0.042f,
                stroke = hexStroke.copy(alpha = hexStroke.alpha * 0.7f),
                fill = hexFill
            )
        }

        for (i in 0 until 4) {
            val t = ((r * 0.7f + i * 0.18f + sin(p + i) * 0.05f) % 1f)
            val y = h * (1.05f - t * 1.2f)
            val x0 = w * (0.15f + (i % 3) * 0.28f)
            val len = w * (0.12f + (i % 2) * 0.06f)
            val a = (sin(t * PI.toFloat()) * 0.9f).coerceIn(0f, 1f)
            drawLine(
                color = streak.copy(alpha = streak.alpha * a),
                start = Offset(x0, y),
                end = Offset(x0 + len * 0.35f, y - len),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        sparks.forEach { s ->
            val localT = ((r * s.speed + s.yBase) % 1f)
            val y = h * (1.05f - localT * 1.15f)
            val x = w * s.xFrac
            val a = (sin(localT * PI.toFloat()) * 0.95f).coerceIn(0.25f, 1f)
            val c = if (s.gold) sparkA else sparkB
            drawCircle(
                color = c.copy(alpha = c.alpha * a),
                radius = s.size,
                center = Offset(x, y)
            )
            drawCircle(
                color = c.copy(alpha = c.alpha * a * 0.35f),
                radius = s.size * 2.2f,
                center = Offset(x, y + s.size * 3f)
            )
        }

        val chevronY = h * 0.78f
        drawChevron(Offset(w * 0.5f, chevronY), w * 0.08f, ringColor.copy(alpha = 0.28f))
        drawChevron(
            Offset(w * 0.5f, chevronY + 18f),
            w * 0.055f,
            ringSoft.copy(alpha = 0.22f)
        )
    }
}

private data class SparkSeed(
    val xFrac: Float,
    val yBase: Float,
    val speed: Float,
    val size: Float,
    val gold: Boolean,
    val phase: Float
)

private fun DrawScope.drawHexCluster(
    center: Offset,
    size: Float,
    stroke: Color,
    fill: Color
) {
    val offsets = listOf(
        Offset(0f, 0f),
        Offset(size * 1.55f, 0f),
        Offset(size * 0.78f, size * 1.35f),
        Offset(-size * 0.78f, size * 1.35f),
        Offset(-size * 1.55f, 0f)
    )
    offsets.forEach { o ->
        drawHex(center + o, size, stroke, fill)
    }
}

private fun DrawScope.drawHex(
    center: Offset,
    radius: Float,
    stroke: Color,
    fill: Color
) {
    val path = Path()
    for (i in 0 until 6) {
        val ang = (PI / 3.0 * i - PI / 6.0).toFloat()
        val pt = Offset(center.x + cos(ang) * radius, center.y + sin(ang) * radius)
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(width = 1.6f))
}

private fun DrawScope.drawChevron(center: Offset, halfW: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - halfW, center.y + halfW * 0.35f)
        lineTo(center.x, center.y - halfW * 0.25f)
        lineTo(center.x + halfW, center.y + halfW * 0.35f)
    }
    drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
}
