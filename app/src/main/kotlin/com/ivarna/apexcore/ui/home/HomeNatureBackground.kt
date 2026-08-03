package com.ivarna.apexcore.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Home organic accent — three edge vines, leaves & blooms.
 *
 * Perf: one-shot growth animation, then a static pose (no infinite sway/breathe).
 * Continuous path redraw was ~50MB SW path masks + UI-thread jank.
 */
@Composable
fun HomeNatureBackground(
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val dim = if (dimmed) 0.42f else 1f

    val stemMain = scheme.primary.copy(alpha = 0.40f * dim)
    val stemGlow = scheme.primaryContainer.copy(alpha = 0.24f * dim)
    val stemDeep = scheme.primary.copy(alpha = 0.32f * dim)

    val leafA = scheme.primary.copy(alpha = 0.46f * dim)
    val leafATip = scheme.primaryContainer.copy(alpha = 0.38f * dim)
    val leafB = scheme.primaryContainer.copy(alpha = 0.48f * dim)
    val leafBTip = scheme.primary.copy(alpha = 0.36f * dim)
    val leafC = scheme.tertiary.copy(alpha = 0.20f * dim)
    val leafCTip = scheme.tertiaryContainer.copy(alpha = 0.26f * dim)

    val goldOuter = scheme.secondary.copy(alpha = 0.50f * dim)
    val goldInner = scheme.secondaryContainer.copy(alpha = 0.65f * dim)
    val goldCore = scheme.tertiary.copy(alpha = 0.55f * dim)
    val creamOuter = scheme.secondaryContainer.copy(alpha = 0.52f * dim)
    val creamInner = Color(1f, 0.96f, 0.88f, 0.58f * dim)
    val creamCore = scheme.secondary.copy(alpha = 0.45f * dim)
    val coralOuter = scheme.tertiary.copy(alpha = 0.40f * dim)
    val coralInner = scheme.tertiaryContainer.copy(alpha = 0.52f * dim)
    val coralCore = scheme.secondary.copy(alpha = 0.40f * dim)

    // One-shot grow-in only — freeze pose after completion (no infinite invalidation).
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        growth.animateTo(1f, tween(2800, easing = EaseOutCubic))
    }

    val pathMeasure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    val leafPalette = remember(leafA, leafATip, leafB, leafBTip, leafC, leafCTip) {
        listOf(
            LeafColors(leafA, leafATip),
            LeafColors(leafB, leafBTip),
            LeafColors(leafC, leafCTip)
        )
    }
    val flowerPalette = remember(
        goldOuter, goldInner, goldCore,
        creamOuter, creamInner, creamCore,
        coralOuter, coralInner, coralCore
    ) {
        listOf(
            FlowerColors(goldOuter, goldInner, goldCore, petals = 5),
            FlowerColors(creamOuter, creamInner, creamCore, petals = 5),
            FlowerColors(coralOuter, coralInner, coralCore, petals = 5)
        )
    }

    // Fixed idle pose (slight natural offsets, not animated).
    val s = 0.35f
    val b = 0.8f
    val amp = 3f

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val g = growth.value

        // Left main vine
        drawVine(
            start = Offset(-w * 0.03f, h * 1.03f),
            mid1 = Offset(w * 0.06f + sin(s) * amp, h * 0.70f),
            mid2 = Offset(w * 0.14f + cos(s * 0.9f) * amp, h * 0.40f),
            end = Offset(w * 0.09f + sin(s * 1.05f) * 5f, h * 0.08f),
            growth = g,
            sway = s,
            breathe = b,
            stemMain = stemMain,
            stemGlow = stemGlow,
            stemDeep = stemDeep,
            leafPalette = leafPalette,
            flowerPalette = flowerPalette,
            strokeBase = w * 0.012f,
            leafBase = w * 0.040f,
            bloomBase = w * 0.028f,
            leaves = leftLeaves,
            blooms = leftBlooms,
            tendrils = leftTendrils,
            side = 1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )

        // Right main vine
        drawVine(
            start = Offset(w * 1.03f, h * 1.03f),
            mid1 = Offset(w * 0.94f + sin(s + 1.1f) * amp, h * 0.74f),
            mid2 = Offset(w * 0.87f + cos(s * 0.85f) * amp, h * 0.44f),
            end = Offset(w * 0.91f + sin(s * 0.95f) * 5f, h * 0.11f),
            growth = (g - 0.06f).coerceIn(0f, 1f),
            sway = s + 1.0f,
            breathe = b + 0.7f,
            stemMain = stemMain,
            stemGlow = stemGlow,
            stemDeep = stemDeep,
            leafPalette = leafPalette,
            flowerPalette = flowerPalette,
            strokeBase = w * 0.011f,
            leafBase = w * 0.038f,
            bloomBase = w * 0.026f,
            leaves = rightLeaves,
            blooms = rightBlooms,
            tendrils = rightTendrils,
            side = -1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )

        // Soft hanging accent (top-right) — shorter, lighter
        drawVine(
            start = Offset(w * 1.02f, -h * 0.02f),
            mid1 = Offset(w * 0.90f + sin(s * 0.8f + 2f) * amp, h * 0.10f),
            mid2 = Offset(w * 0.78f + cos(s * 0.7f) * 9f, h * 0.22f),
            end = Offset(w * 0.72f + sin(s * 0.75f) * 5f, h * 0.34f),
            growth = (g - 0.14f).coerceIn(0f, 1f),
            sway = s + 1.8f,
            breathe = b + 1.2f,
            stemMain = stemMain.copy(alpha = stemMain.alpha * 0.85f),
            stemGlow = stemGlow,
            stemDeep = stemDeep,
            leafPalette = leafPalette,
            flowerPalette = flowerPalette,
            strokeBase = w * 0.009f,
            leafBase = w * 0.034f,
            bloomBase = w * 0.024f,
            leaves = hangLeaves,
            blooms = hangBlooms,
            tendrils = hangTendrils,
            side = -1f,
            pathMeasure = pathMeasure,
            segmentPath = segmentPath
        )
    }
}

private data class LeafSpec(
    val frac: Float,
    val sizeMul: Float,
    val colorIdx: Int,
    val flip: Float,
    val wide: Boolean = false
)

private data class BloomSpec(
    val frac: Float,
    val sizeMul: Float,
    val paletteIdx: Int,
    val flip: Float
)

private data class TendrilSpec(
    val frac: Float,
    val lengthMul: Float,
    val flip: Float,
    val leafSize: Float,
    val bloom: Boolean = false,
    val bloomPalette: Int = 0
)

private data class LeafColors(val fill: Color, val tip: Color)
private data class FlowerColors(
    val outer: Color,
    val inner: Color,
    val core: Color,
    val petals: Int
)

// Slightly fewer leaves/blooms — lower path-mask pressure while keeping density.
private val leftLeaves = arrayOf(
    LeafSpec(0.12f, 1.05f, 0, 1f),
    LeafSpec(0.28f, 0.85f, 1, -1f, wide = true),
    LeafSpec(0.46f, 1.15f, 0, 1f),
    LeafSpec(0.64f, 0.95f, 2, -1f),
    LeafSpec(0.82f, 1.05f, 1, 1f, wide = true)
)
private val leftBlooms = arrayOf(
    BloomSpec(0.38f, 1.05f, 0, 1f),
    BloomSpec(0.72f, 0.95f, 1, -1f)
)
private val leftTendrils = arrayOf(
    TendrilSpec(0.54f, 1.35f, 1f, 0.85f, bloom = true, bloomPalette = 1)
)

private val rightLeaves = arrayOf(
    LeafSpec(0.16f, 1.00f, 1, 1f, wide = true),
    LeafSpec(0.34f, 0.90f, 0, -1f),
    LeafSpec(0.52f, 1.10f, 1, 1f),
    LeafSpec(0.70f, 0.92f, 2, -1f),
    LeafSpec(0.88f, 1.00f, 0, 1f)
)
private val rightBlooms = arrayOf(
    BloomSpec(0.42f, 1.00f, 1, -1f),
    BloomSpec(0.76f, 1.05f, 0, 1f)
)
private val rightTendrils = arrayOf(
    TendrilSpec(0.58f, 1.35f, -1f, 0.9f, bloom = true, bloomPalette = 0)
)

private val hangLeaves = arrayOf(
    LeafSpec(0.28f, 0.95f, 0, 1f),
    LeafSpec(0.55f, 1.05f, 1, -1f, wide = true),
    LeafSpec(0.82f, 0.88f, 0, 1f)
)
private val hangBlooms = arrayOf(
    BloomSpec(0.65f, 1.00f, 1, 1f)
)
private val hangTendrils = arrayOf(
    TendrilSpec(0.48f, 1.2f, 1f, 0.8f)
)

private fun DrawScope.drawVine(
    start: Offset,
    mid1: Offset,
    mid2: Offset,
    end: Offset,
    growth: Float,
    sway: Float,
    breathe: Float,
    stemMain: Color,
    stemGlow: Color,
    stemDeep: Color,
    leafPalette: List<LeafColors>,
    flowerPalette: List<FlowerColors>,
    strokeBase: Float,
    leafBase: Float,
    bloomBase: Float,
    leaves: Array<LeafSpec>,
    blooms: Array<BloomSpec>,
    tendrils: Array<TendrilSpec>,
    side: Float,
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

    segmentPath.reset()
    pathMeasure.getSegment(0f, totalLen * growth, segmentPath, true)

    drawPath(
        path = segmentPath,
        color = stemGlow,
        style = Stroke(width = strokeBase * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = segmentPath,
        brush = Brush.linearGradient(
            colors = listOf(stemDeep, stemMain, stemDeep),
            start = start,
            end = end
        ),
        style = Stroke(width = strokeBase * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Short tendrils
    val tendrilGrowth = ((growth - 0.2f) / 0.8f).coerceIn(0f, 1f)
    if (tendrilGrowth > 0f) {
        for ((i, t) in tendrils.withIndex()) {
            if (t.frac > growth) continue
            val pos = pathPos(pathMeasure, totalLen * t.frac)
            val tan = pathTan(pathMeasure, totalLen * t.frac)
            val nx = -tan.y * side * t.flip
            val ny = tan.x * side * t.flip
            val len = strokeBase * 18f * t.lengthMul * tendrilGrowth
            val curl = sin(sway + i * 1.2f) * 0.35f
            val tip = Offset(
                pos.x + nx * len + tan.x * len * curl * 0.4f,
                pos.y + ny * len + tan.y * len * curl * 0.4f
            )
            val c1 = Offset(pos.x + nx * len * 0.4f, pos.y + ny * len * 0.4f)
            val path = Path().apply {
                moveTo(pos.x, pos.y)
                quadraticTo(c1.x + tan.x * len * curl, c1.y + tan.y * len * curl, tip.x, tip.y)
            }
            drawPath(
                path = path,
                color = stemMain.copy(alpha = stemMain.alpha * 0.9f),
                style = Stroke(width = strokeBase * 0.5f, cap = StrokeCap.Round)
            )
            if (tendrilGrowth > 0.4f) {
                val appear = ((tendrilGrowth - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val colors = leafPalette[i % leafPalette.size]
                drawLeaf(
                    attach = tip,
                    outward = Offset(nx, ny),
                    scale = leafBase * t.leafSize * appear,
                    fill = colors.fill,
                    tipColor = colors.tip,
                    stemColor = stemMain,
                    swayDeg = sin(sway + i).toFloat() * 6f,
                    wide = false
                )
                if (t.bloom && tendrilGrowth > 0.6f) {
                    val open = ((tendrilGrowth - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    val fp = flowerPalette[t.bloomPalette % flowerPalette.size]
                    drawFlower(
                        center = tip + Offset(nx, ny) * (bloomBase * 0.2f),
                        scale = bloomBase * 0.75f * easeOut(open) *
                            (1f + 0.05f * sin(breathe + i).toFloat()),
                        rotationDeg = sin(sway * 0.5f + i).toFloat() * 10f,
                        colors = fp
                    )
                }
            }
        }
    }

    for ((i, spec) in leaves.withIndex()) {
        if (spec.frac > growth) continue
        val appear = ((growth - spec.frac) / 0.15f).coerceIn(0f, 1f)
        if (appear <= 0f) continue
        val pos = pathPos(pathMeasure, totalLen * spec.frac)
        val tan = pathTan(pathMeasure, totalLen * spec.frac)
        val outward = Offset(-tan.y * side * spec.flip, tan.x * side * spec.flip)
        val colors = leafPalette[spec.colorIdx % leafPalette.size]
        val scale = leafBase * spec.sizeMul * appear *
            (0.9f + 0.1f * sin(breathe + i * 0.65f).toFloat())
        drawLeaf(
            attach = pos,
            outward = outward,
            scale = scale,
            fill = colors.fill,
            tipColor = colors.tip,
            stemColor = stemMain,
            swayDeg = sin(sway + i * 0.85f).toFloat() * 6f,
            wide = spec.wide
        )
    }

    for ((i, spec) in blooms.withIndex()) {
        val openStart = spec.frac * 0.82f + 0.1f
        val open = ((growth - openStart) / 0.2f).coerceIn(0f, 1f)
        if (open <= 0f) continue
        val frac = spec.frac.coerceAtMost(growth)
        val pos = pathPos(pathMeasure, totalLen * frac)
        val tan = pathTan(pathMeasure, totalLen * frac)
        val outward = Offset(-tan.y * side * spec.flip, tan.x * side * spec.flip)
        val spur = bloomBase * 0.5f
        val center = pos + outward * spur
        drawLine(
            color = stemMain,
            start = pos,
            end = center,
            strokeWidth = strokeBase * 0.4f,
            cap = StrokeCap.Round
        )
        val fp = flowerPalette[spec.paletteIdx % flowerPalette.size]
        drawFlower(
            center = center,
            scale = bloomBase * spec.sizeMul * easeOut(open) *
                (1f + 0.055f * sin(breathe + i * 1.1f).toFloat()),
            rotationDeg = Math.toDegrees(atan2(tan.y.toDouble(), tan.x.toDouble())).toFloat() +
                sin(sway * 0.4f + i).toFloat() * 8f,
            colors = fp
        )
    }
}

private fun DrawScope.drawLeaf(
    attach: Offset,
    outward: Offset,
    scale: Float,
    fill: Color,
    tipColor: Color,
    stemColor: Color,
    swayDeg: Float,
    wide: Boolean
) {
    if (scale <= 0.7f) return
    val oLen = sqrt(outward.x * outward.x + outward.y * outward.y).coerceAtLeast(0.001f)
    val ox = outward.x / oLen
    val oy = outward.y / oLen
    val petiole = scale * 0.24f
    val base = Offset(attach.x + ox * petiole, attach.y + oy * petiole)

    drawLine(
        color = stemColor.copy(alpha = stemColor.alpha * 0.92f),
        start = attach,
        end = base,
        strokeWidth = scale * 0.075f,
        cap = StrokeCap.Round
    )
    drawCircle(stemColor, scale * 0.05f, attach)

    val angleDeg = Math.toDegrees(atan2(oy.toDouble(), ox.toDouble())).toFloat() + 90f + swayDeg
    val halfW = if (wide) 0.58f else 0.42f
    val tipY = if (wide) 1.35f else 1.5f
    withTransform({
        translate(base.x, base.y)
        rotate(angleDeg, pivot = Offset.Zero)
    }) {
        val path = Path().apply {
            moveTo(0f, 0f)
            cubicTo(scale * halfW, -scale * 0.15f, scale * halfW * 1.05f, -scale * (tipY * 0.55f), 0f, -scale * tipY)
            cubicTo(-scale * halfW * 1.05f, -scale * (tipY * 0.55f), -scale * halfW, -scale * 0.15f, 0f, 0f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(fill.copy(alpha = fill.alpha * 0.78f), fill, tipColor),
                start = Offset(0f, 0f),
                end = Offset(0f, -scale * tipY)
            )
        )
        drawLine(
            color = fill.copy(alpha = fill.alpha * 0.42f),
            start = Offset(0f, -scale * 0.04f),
            end = Offset(0f, -scale * tipY * 0.88f),
            strokeWidth = scale * 0.048f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawFlower(
    center: Offset,
    scale: Float,
    rotationDeg: Float,
    colors: FlowerColors
) {
    if (scale <= 0.55f) return
    withTransform({
        translate(center.x, center.y)
        rotate(rotationDeg, pivot = Offset.Zero)
    }) {
        val n = colors.petals
        val petalLen = scale * 1.4f
        val petalW = scale * 0.4f
        for (i in 0 until n) {
            rotate(i * (360f / n), pivot = Offset.Zero) {
                val petal = Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(petalW, -petalLen * 0.22f, petalW * 0.85f, -petalLen * 0.7f, 0f, -petalLen)
                    cubicTo(-petalW * 0.85f, -petalLen * 0.7f, -petalW, -petalLen * 0.22f, 0f, 0f)
                    close()
                }
                drawPath(
                    path = petal,
                    brush = Brush.radialGradient(
                        listOf(colors.inner, colors.outer, colors.outer.copy(alpha = 0f)),
                        center = Offset(0f, -petalLen * 0.3f),
                        radius = petalLen
                    )
                )
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.core.copy(alpha = colors.core.alpha * 0.5f), Color.Transparent),
                radius = scale * 1.05f
            ),
            radius = scale * 1.05f
        )
        drawCircle(colors.core, scale * 0.34f)
        drawCircle(colors.inner.copy(alpha = colors.inner.alpha * 0.9f), scale * 0.15f)
    }
}

private fun pathPos(measure: PathMeasure, distance: Float): Offset =
    measure.getPosition(distance.coerceIn(0f, measure.length))

private fun pathTan(measure: PathMeasure, distance: Float): Offset {
    val tan = measure.getTangent(distance.coerceIn(0f, measure.length))
    val len = sqrt(tan.x * tan.x + tan.y * tan.y).coerceAtLeast(0.0001f)
    return Offset(tan.x / len, tan.y / len)
}

private fun easeOut(t: Float): Float {
    val p = 1f - t
    return 1f - p * p
}
