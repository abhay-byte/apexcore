package com.ivarna.apexcore.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Soft organic vines + flower blooms for the Home background only.
 *
 * Design goals:
 * - Match Zen sage/teal + warm gold secondary accents
 * - Grow-in once, then gentle idle sway (calm, not busy)
 * - Stay decorative: low alpha, edges only, never steals focus from leaves/Boost
 * - Zero third-party assets so light/dark and brand stay coherent
 */
@Composable
fun HomeNatureBackground(
    modifier: Modifier = Modifier,
    /** When true (e.g. BOOSTING), nature layer fades so purge UI stays primary. */
    dimmed: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme

    val vineColor = scheme.primary.copy(alpha = if (dimmed) 0.10f else 0.28f)
    val vineDeep = scheme.primaryContainer.copy(alpha = if (dimmed) 0.08f else 0.22f)
    val leafColor = scheme.primary.copy(alpha = if (dimmed) 0.12f else 0.32f)
    val leafTip = scheme.primaryContainer.copy(alpha = if (dimmed) 0.10f else 0.26f)
    val petalOuter = scheme.secondary.copy(alpha = if (dimmed) 0.14f else 0.38f)
    val petalInner = scheme.secondaryContainer.copy(alpha = if (dimmed) 0.18f else 0.55f)
    val bloomCenter = scheme.tertiary.copy(alpha = if (dimmed) 0.16f else 0.48f)
    val budColor = scheme.primary.copy(alpha = if (dimmed) 0.10f else 0.26f)

    // One-shot grow: vines unfurl, then blooms open (driven by growth curve)
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        growth.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2800, easing = EaseOutCubic)
        )
    }

    val infinite = rememberInfiniteTransition(label = "nature_idle")
    val sway by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sway_phase"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breathe_phase"
    )

    // Pre-built path measure re-used across frames (paths rebuilt when size changes)
    val pathMeasure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val g = growth.value
        val s = sway
        val b = breathe

        // ── Bottom-left climbing vine (main) ──────────────────────────
        drawVineSystem(
            start = Offset(-w * 0.02f, h * 1.02f),
            mid1 = Offset(w * 0.06f + sin(s) * 6f, h * 0.72f),
            mid2 = Offset(w * 0.14f + cos(s * 0.9f) * 8f, h * 0.42f),
            end = Offset(w * 0.08f + sin(s * 1.1f) * 5f, h * 0.12f),
            growth = g,
            sway = s,
            breathe = b,
            vineColor = vineColor,
            vineDeep = vineDeep,
            leafColor = leafColor,
            leafTip = leafTip,
            petalOuter = petalOuter,
            petalInner = petalInner,
            bloomCenter = bloomCenter,
            budColor = budColor,
            strokeBase = w * 0.011f,
            leafScale = w * 0.028f,
            bloomScale = w * 0.022f,
            leafSlots = floatArrayOf(0.18f, 0.32f, 0.46f, 0.58f, 0.70f, 0.82f),
            bloomSlots = floatArrayOf(0.40f, 0.68f, 0.92f),
            tendrilSide = 1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )

        // ── Bottom-right climbing vine (mirror, thinner) ──────────────
        drawVineSystem(
            start = Offset(w * 1.02f, h * 1.02f),
            mid1 = Offset(w * 0.94f + sin(s + 1.2f) * 5f, h * 0.78f),
            mid2 = Offset(w * 0.88f + cos(s * 0.85f + 0.5f) * 7f, h * 0.50f),
            end = Offset(w * 0.92f + sin(s * 0.95f + 0.8f) * 4f, h * 0.22f),
            growth = (g - 0.08f).coerceIn(0f, 1f),
            sway = s + 0.9f,
            breathe = b + 0.7f,
            vineColor = vineColor,
            vineDeep = vineDeep,
            leafColor = leafColor,
            leafTip = leafTip,
            petalOuter = petalOuter,
            petalInner = petalInner,
            bloomCenter = bloomCenter,
            budColor = budColor,
            strokeBase = w * 0.009f,
            leafScale = w * 0.024f,
            bloomScale = w * 0.018f,
            leafSlots = floatArrayOf(0.22f, 0.40f, 0.55f, 0.72f, 0.88f),
            bloomSlots = floatArrayOf(0.48f, 0.85f),
            tendrilSide = -1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )

        // ── Top-right hanging vine (drape) ────────────────────────────
        drawVineSystem(
            start = Offset(w * 1.02f, -h * 0.02f),
            mid1 = Offset(w * 0.90f + sin(s * 0.8f + 2f) * 6f, h * 0.10f),
            mid2 = Offset(w * 0.78f + cos(s * 0.7f + 1.4f) * 9f, h * 0.22f),
            end = Offset(w * 0.72f + sin(s * 0.75f + 1.1f) * 5f, h * 0.36f),
            growth = (g - 0.15f).coerceIn(0f, 1f),
            sway = s + 1.7f,
            breathe = b + 1.3f,
            vineColor = vineDeep,
            vineDeep = vineColor,
            leafColor = leafColor,
            leafTip = leafTip,
            petalOuter = petalOuter,
            petalInner = petalInner,
            bloomCenter = bloomCenter,
            budColor = budColor,
            strokeBase = w * 0.008f,
            leafScale = w * 0.022f,
            bloomScale = w * 0.016f,
            leafSlots = floatArrayOf(0.25f, 0.45f, 0.65f, 0.82f),
            bloomSlots = floatArrayOf(0.55f, 0.95f),
            tendrilSide = -1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )

        // ── Soft floating petal motes (depth, very subtle) ────────────
        if (g > 0.55f) {
            val moteAlpha = ((g - 0.55f) / 0.45f).coerceIn(0f, 1f) * (if (dimmed) 0.08f else 0.18f)
            drawFloatingMotes(
                w = w,
                h = h,
                phase = s,
                color = petalOuter.copy(alpha = moteAlpha),
                count = 7
            )
        }
    }
}

// ── Vine system ──────────────────────────────────────────────────────────────

private fun DrawScope.drawVineSystem(
    start: Offset,
    mid1: Offset,
    mid2: Offset,
    end: Offset,
    growth: Float,
    sway: Float,
    breathe: Float,
    vineColor: Color,
    vineDeep: Color,
    leafColor: Color,
    leafTip: Color,
    petalOuter: Color,
    petalInner: Color,
    bloomCenter: Color,
    budColor: Color,
    strokeBase: Float,
    leafScale: Float,
    bloomScale: Float,
    leafSlots: FloatArray,
    bloomSlots: FloatArray,
    tendrilSide: Float,
    pathMeasure: PathMeasure,
    segmentPath: Path
) {
    if (growth <= 0.001f) return

    val mainPath = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(mid1.x, mid1.y, mid2.x, mid2.y, end.x, end.y)
    }
    pathMeasure.setPath(mainPath, false)
    val totalLen = pathMeasure.length
    if (totalLen <= 1f) return

    // Progressive unfurl
    val visibleLen = totalLen * growth
    segmentPath.reset()
    pathMeasure.getSegment(0f, visibleLen, segmentPath, true)

    // Soft under-glow stroke then crisp vine
    drawPath(
        path = segmentPath,
        color = vineDeep,
        style = Stroke(
            width = strokeBase * 2.4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawPath(
        path = segmentPath,
        brush = Brush.linearGradient(
            colors = listOf(vineColor, vineDeep, vineColor),
            start = start,
            end = end
        ),
        style = Stroke(
            width = strokeBase,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Side tendrils (short branches that grow later)
    val tendrilGrowth = ((growth - 0.25f) / 0.75f).coerceIn(0f, 1f)
    if (tendrilGrowth > 0f) {
        val tendrilFracs = floatArrayOf(0.30f, 0.52f, 0.74f)
        for ((i, frac) in tendrilFracs.withIndex()) {
            if (frac > growth) continue
            val pos = pathPos(pathMeasure, totalLen * frac)
            val tan = pathTan(pathMeasure, totalLen * frac)
            val nx = -tan.y * tendrilSide
            val ny = tan.x * tendrilSide
            val len = strokeBase * (18f + i * 4f) * tendrilGrowth
            val curl = sin(sway + i * 1.3f) * 0.35f
            val c1 = Offset(
                pos.x + nx * len * 0.4f + tan.x * len * 0.15f,
                pos.y + ny * len * 0.4f + tan.y * len * 0.15f
            )
            val c2 = Offset(
                pos.x + nx * len * 0.85f + tan.x * len * curl,
                pos.y + ny * len * 0.85f + tan.y * len * curl
            )
            val tip = Offset(
                pos.x + nx * len + tan.x * len * curl * 0.5f,
                pos.y + ny * len + tan.y * len * curl * 0.5f
            )
            val tendril = Path().apply {
                moveTo(pos.x, pos.y)
                cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
            }
            drawPath(
                path = tendril,
                color = vineColor.copy(alpha = vineColor.alpha * 0.85f),
                style = Stroke(
                    width = strokeBase * 0.55f,
                    cap = StrokeCap.Round
                )
            )
            // Small bud at tendril tip
            if (tendrilGrowth > 0.6f) {
                val budOpen = ((tendrilGrowth - 0.6f) / 0.4f).coerceIn(0f, 1f)
                drawCircle(
                    color = budColor,
                    radius = leafScale * 0.22f * budOpen * (1f + 0.08f * sin(breathe + i)),
                    center = tip
                )
            }
        }
    }

    // Leaves along main vine
    for ((i, frac) in leafSlots.withIndex()) {
        if (frac > growth) continue
        val appear = ((growth - frac) / 0.18f).coerceIn(0f, 1f)
        if (appear <= 0f) continue
        val pos = pathPos(pathMeasure, totalLen * frac)
        val tan = pathTan(pathMeasure, totalLen * frac)
        val side = if (i % 2 == 0) tendrilSide else -tendrilSide
        val angleDeg = Math.toDegrees(atan2(tan.y.toDouble(), tan.x.toDouble())).toFloat() +
            side * 55f + sin(sway + i * 0.9f).toFloat() * 6f
        val scale = leafScale * appear * (0.85f + 0.15f * sin(breathe + i * 0.7f).toFloat())
        drawLeaf(
            center = pos + Offset(-tan.y * side * leafScale * 0.35f, tan.x * side * leafScale * 0.35f),
            scale = scale,
            rotationDeg = angleDeg,
            fill = leafColor,
            tip = leafTip
        )
    }

    // Flower blooms along vine (open after local growth)
    for ((i, frac) in bloomSlots.withIndex()) {
        val openStart = frac * 0.85f + 0.12f
        val open = ((growth - openStart) / 0.22f).coerceIn(0f, 1f)
        if (open <= 0f) continue
        val pos = pathPos(pathMeasure, totalLen * frac.coerceAtMost(growth))
        val tan = pathTan(pathMeasure, totalLen * frac.coerceAtMost(growth))
        val side = if (i % 2 == 0) -tendrilSide else tendrilSide
        val offset = Offset(-tan.y * side * bloomScale * 1.2f, tan.x * side * bloomScale * 1.2f)
        val pulse = 1f + 0.06f * sin(breathe + i * 1.1f).toFloat()
        drawFlower(
            center = pos + offset,
            scale = bloomScale * easeOutBack(open) * pulse,
            rotationDeg = Math.toDegrees(atan2(tan.y.toDouble(), tan.x.toDouble())).toFloat() +
                sin(sway * 0.5f + i).toFloat() * 8f,
            petalOuter = petalOuter,
            petalInner = petalInner,
            centerColor = bloomCenter,
            petalCount = if (i == 0) 5 else 6
        )
    }
}

// ── Primitives ───────────────────────────────────────────────────────────────

private fun DrawScope.drawLeaf(
    center: Offset,
    scale: Float,
    rotationDeg: Float,
    fill: Color,
    tip: Color
) {
    if (scale <= 0.5f) return
    withTransform({
        translate(center.x, center.y)
        rotate(rotationDeg, pivot = Offset.Zero)
    }) {
        val path = Path().apply {
            // Teardrop leaf (matches MemoryLeaf language)
            moveTo(0f, -scale)
            cubicTo(scale * 0.55f, -scale * 0.55f, scale * 0.75f, scale * 0.15f, 0f, scale * 0.95f)
            cubicTo(-scale * 0.75f, scale * 0.15f, -scale * 0.55f, -scale * 0.55f, 0f, -scale)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(tip, fill, fill.copy(alpha = fill.alpha * 0.7f)),
                start = Offset(0f, -scale),
                end = Offset(0f, scale)
            )
        )
        // Midrib
        drawLine(
            color = fill.copy(alpha = fill.alpha * 0.55f),
            start = Offset(0f, -scale * 0.75f),
            end = Offset(0f, scale * 0.7f),
            strokeWidth = scale * 0.06f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawFlower(
    center: Offset,
    scale: Float,
    rotationDeg: Float,
    petalOuter: Color,
    petalInner: Color,
    centerColor: Color,
    petalCount: Int
) {
    if (scale <= 0.5f) return
    withTransform({
        translate(center.x, center.y)
        rotate(rotationDeg, pivot = Offset.Zero)
    }) {
        val petalLen = scale * 1.35f
        val petalW = scale * 0.55f
        for (i in 0 until petalCount) {
            val angle = i * (360f / petalCount)
            rotate(angle, pivot = Offset.Zero) {
                val petal = Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(
                        petalW, -petalLen * 0.25f,
                        petalW * 0.85f, -petalLen * 0.75f,
                        0f, -petalLen
                    )
                    cubicTo(
                        -petalW * 0.85f, -petalLen * 0.75f,
                        -petalW, -petalLen * 0.25f,
                        0f, 0f
                    )
                    close()
                }
                drawPath(
                    path = petal,
                    brush = Brush.radialGradient(
                        colors = listOf(petalInner, petalOuter, petalOuter.copy(alpha = 0f)),
                        center = Offset(0f, -petalLen * 0.35f),
                        radius = petalLen
                    )
                )
            }
        }
        // Soft halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(centerColor.copy(alpha = centerColor.alpha * 0.5f), Color.Transparent),
                center = Offset.Zero,
                radius = scale * 1.1f
            ),
            radius = scale * 1.1f,
            center = Offset.Zero
        )
        // Stamen core
        drawCircle(
            color = centerColor,
            radius = scale * 0.28f,
            center = Offset.Zero
        )
        drawCircle(
            color = petalInner.copy(alpha = petalInner.alpha * 0.9f),
            radius = scale * 0.14f,
            center = Offset.Zero
        )
    }
}

private fun DrawScope.drawFloatingMotes(
    w: Float,
    h: Float,
    phase: Float,
    color: Color,
    count: Int
) {
    // Deterministic pseudo-random anchors so motes don't jump frames
    val seeds = floatArrayOf(
        0.12f, 0.28f, 0.41f, 0.55f, 0.63f, 0.77f, 0.88f, 0.33f, 0.70f
    )
    for (i in 0 until count) {
        val sx = seeds[i % seeds.size]
        val sy = seeds[(i * 3 + 1) % seeds.size]
        val x = w * (0.15f + sx * 0.7f) + sin(phase + i * 1.7f) * (w * 0.03f)
        val y = h * (0.18f + sy * 0.55f) + cos(phase * 0.8f + i * 1.1f) * (h * 0.02f)
        val r = w * (0.004f + (i % 3) * 0.002f)
        // Tiny petal diamond
        val p = Path().apply {
            moveTo(x, y - r * 1.6f)
            lineTo(x + r, y)
            lineTo(x, y + r * 1.6f)
            lineTo(x - r, y)
            close()
        }
        drawPath(path = p, color = color)
    }
}

// ── Path helpers ─────────────────────────────────────────────────────────────

private fun pathPos(measure: PathMeasure, distance: Float): Offset =
    measure.getPosition(distance.coerceIn(0f, measure.length))

private fun pathTan(measure: PathMeasure, distance: Float): Offset {
    val tan = measure.getTangent(distance.coerceIn(0f, measure.length))
    val len = sqrt(tan.x * tan.x + tan.y * tan.y).coerceAtLeast(0.0001f)
    return Offset(tan.x / len, tan.y / len)
}

/** Soft overshoot for bloom open (no spring dependency in DrawScope). */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = t - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}
