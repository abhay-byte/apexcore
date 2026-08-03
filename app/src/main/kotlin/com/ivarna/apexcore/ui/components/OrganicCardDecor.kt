package com.ivarna.apexcore.ui.components

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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Soft organic accents for glass cards — flowers + leaves.
 * Style seeds vary layout, palette, shape & size.
 *
 * Perf: static pose (no infinite sway/spin). Continuous path redraw was a
 * major GPU path-mask + UI-thread cost when many cards were on screen.
 */
@Composable
fun OrganicCardDecor(
    modifier: Modifier = Modifier,
    /** Style seed — different layouts, mix of flowers/leaves, sizes */
    style: Int = 0,
    sizeScale: Float = 1f,
    alphaScale: Float = 1f
) {
    val scheme = MaterialTheme.colorScheme
    // Soft wash only — text must stay high-contrast. Cap keeps motifs transparent.
    val a = alphaScale.coerceIn(0.25f, 1.2f)

    val flowerPals = remember(
        scheme.secondary, scheme.secondaryContainer,
        scheme.tertiary, scheme.tertiaryContainer,
        scheme.primary, scheme.primaryContainer, a
    ) {
        listOf(
            FlowerPal(
                outer = scheme.secondary.copy(alpha = (0.18f * a).coerceAtMost(0.28f)),
                inner = scheme.secondaryContainer.copy(alpha = (0.22f * a).coerceAtMost(0.32f)),
                core = scheme.tertiary.copy(alpha = (0.20f * a).coerceAtMost(0.30f)),
                petals = 5,
                petalShape = PetalShape.ROUNDED
            ),
            FlowerPal(
                outer = scheme.secondaryContainer.copy(alpha = (0.20f * a).coerceAtMost(0.30f)),
                inner = Color(1f, 0.96f, 0.90f, (0.18f * a).coerceAtMost(0.28f)),
                core = scheme.secondary.copy(alpha = (0.18f * a).coerceAtMost(0.28f)),
                petals = 5,
                petalShape = PetalShape.POINTED
            ),
            FlowerPal(
                outer = scheme.tertiary.copy(alpha = (0.16f * a).coerceAtMost(0.26f)),
                inner = scheme.tertiaryContainer.copy(alpha = (0.20f * a).coerceAtMost(0.30f)),
                core = scheme.secondary.copy(alpha = (0.17f * a).coerceAtMost(0.27f)),
                petals = 5,
                petalShape = PetalShape.WIDE
            ),
            FlowerPal(
                outer = scheme.primary.copy(alpha = (0.14f * a).coerceAtMost(0.24f)),
                inner = scheme.primaryContainer.copy(alpha = (0.18f * a).coerceAtMost(0.28f)),
                core = scheme.secondary.copy(alpha = (0.16f * a).coerceAtMost(0.26f)),
                petals = 5,
                petalShape = PetalShape.NARROW
            ),
            FlowerPal(
                outer = scheme.primaryContainer.copy(alpha = (0.16f * a).coerceAtMost(0.26f)),
                inner = scheme.secondary.copy(alpha = (0.15f * a).coerceAtMost(0.25f)),
                core = scheme.tertiaryContainer.copy(alpha = (0.18f * a).coerceAtMost(0.28f)),
                petals = 4,
                petalShape = PetalShape.STAR
            ),
            FlowerPal(
                outer = scheme.tertiaryContainer.copy(alpha = (0.17f * a).coerceAtMost(0.27f)),
                inner = scheme.secondaryContainer.copy(alpha = (0.20f * a).coerceAtMost(0.30f)),
                core = scheme.primary.copy(alpha = (0.15f * a).coerceAtMost(0.25f)),
                petals = 5,
                petalShape = PetalShape.ROUNDED
            )
        )
    }

    val leafPals = remember(
        scheme.primary, scheme.primaryContainer, scheme.tertiary, a
    ) {
        listOf(
            LeafPal(
                fill = scheme.primary.copy(alpha = (0.16f * a).coerceAtMost(0.26f)),
                tip = scheme.primaryContainer.copy(alpha = (0.18f * a).coerceAtMost(0.28f)),
                stem = scheme.primary.copy(alpha = (0.12f * a).coerceAtMost(0.20f)),
                wide = true
            ),
            LeafPal(
                fill = scheme.primaryContainer.copy(alpha = (0.17f * a).coerceAtMost(0.27f)),
                tip = scheme.primary.copy(alpha = (0.15f * a).coerceAtMost(0.25f)),
                stem = scheme.primary.copy(alpha = (0.12f * a).coerceAtMost(0.20f)),
                wide = false
            ),
            LeafPal(
                fill = scheme.tertiary.copy(alpha = (0.12f * a).coerceAtMost(0.22f)),
                tip = scheme.primaryContainer.copy(alpha = (0.14f * a).coerceAtMost(0.24f)),
                stem = scheme.primary.copy(alpha = (0.10f * a).coerceAtMost(0.18f)),
                wide = true
            )
        )
    }

    val motifs = remember(style) { motifLayoutsFor(style) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val base = minOf(w, h) * sizeScale

        for ((i, m) in motifs.withIndex()) {
            // Static offsets derived from index (no continuous animation).
            val phase = i * 0.9f
            val center = Offset(w * m.x, h * m.y)
            val scale = base * m.scale
            val rot = m.baseRot + sin(phase).toFloat() * 4f

            when (m.kind) {
                MotifKind.FLOWER -> {
                    val pal = flowerPals[(m.palette + style) % flowerPals.size]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                pal.outer.copy(alpha = pal.outer.alpha * 0.32f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = scale * 1.5f
                        ),
                        radius = scale * 1.5f,
                        center = center
                    )
                    drawOrganicFlower(center, scale, rot, pal)
                }
                MotifKind.LEAF -> {
                    val pal = leafPals[(m.palette + style) % leafPals.size]
                    // Leaf points roughly outward from card center for natural feel
                    val outward = Offset(m.x - 0.5f, m.y - 0.5f).let {
                        val len = sqrt(it.x * it.x + it.y * it.y).coerceAtLeast(0.01f)
                        Offset(it.x / len, it.y / len)
                    }
                    drawOrganicLeaf(
                        attach = center,
                        outward = outward,
                        scale = scale,
                        rotationBias = rot,
                        pal = pal
                    )
                }
            }
        }
    }
}

private enum class MotifKind { FLOWER, LEAF }

private enum class PetalShape { ROUNDED, POINTED, WIDE, NARROW, STAR }

private data class FlowerPal(
    val outer: Color,
    val inner: Color,
    val core: Color,
    val petals: Int,
    val petalShape: PetalShape
)

private data class LeafPal(
    val fill: Color,
    val tip: Color,
    val stem: Color,
    val wide: Boolean
)

private data class Motif(
    val kind: MotifKind,
    val x: Float,
    val y: Float,
    val scale: Float,
    val palette: Int,
    val baseRot: Float
)

private fun motifLayoutsFor(style: Int): List<Motif> = when (style % 8) {
    0 -> listOf(
        Motif(MotifKind.FLOWER, 0.92f, 0.16f, 0.44f, 0, 8f),
        Motif(MotifKind.LEAF, 0.10f, 0.82f, 0.34f, 0, -20f),
        Motif(MotifKind.FLOWER, 0.08f, 0.90f, 0.22f, 1, -12f)
    )
    1 -> listOf(
        Motif(MotifKind.FLOWER, 0.88f, 0.78f, 0.40f, 1, -6f),
        Motif(MotifKind.LEAF, 0.14f, 0.20f, 0.30f, 1, 25f),
        Motif(MotifKind.LEAF, 0.92f, 0.28f, 0.22f, 2, -35f)
    )
    2 -> listOf(
        Motif(MotifKind.FLOWER, 0.94f, 0.48f, 0.50f, 2, 4f),
        Motif(MotifKind.FLOWER, 0.06f, 0.55f, 0.24f, 0, -10f),
        Motif(MotifKind.LEAF, 0.88f, 0.12f, 0.26f, 0, 40f)
    )
    3 -> listOf(
        Motif(MotifKind.FLOWER, 0.90f, 0.12f, 0.38f, 3, 10f),
        Motif(MotifKind.LEAF, 0.08f, 0.88f, 0.36f, 1, -15f),
        Motif(MotifKind.FLOWER, 0.95f, 0.90f, 0.20f, 0, 16f),
        Motif(MotifKind.LEAF, 0.12f, 0.18f, 0.20f, 2, 50f)
    )
    4 -> listOf(
        Motif(MotifKind.FLOWER, 0.86f, 0.22f, 0.36f, 4, 12f),
        Motif(MotifKind.FLOWER, 0.12f, 0.72f, 0.32f, 5, -18f),
        Motif(MotifKind.LEAF, 0.90f, 0.85f, 0.28f, 0, -40f)
    )
    5 -> listOf(
        Motif(MotifKind.LEAF, 0.08f, 0.30f, 0.40f, 0, 30f),
        Motif(MotifKind.LEAF, 0.92f, 0.70f, 0.36f, 1, -25f),
        Motif(MotifKind.FLOWER, 0.88f, 0.18f, 0.28f, 2, 6f)
    )
    6 -> listOf(
        Motif(MotifKind.FLOWER, 0.93f, 0.35f, 0.46f, 5, -4f),
        Motif(MotifKind.LEAF, 0.10f, 0.65f, 0.32f, 2, 18f),
        Motif(MotifKind.LEAF, 0.15f, 0.15f, 0.24f, 0, -50f),
        Motif(MotifKind.FLOWER, 0.90f, 0.88f, 0.18f, 1, 20f)
    )
    else -> listOf(
        Motif(MotifKind.FLOWER, 0.91f, 0.55f, 0.42f, 3, 2f),
        Motif(MotifKind.LEAF, 0.07f, 0.45f, 0.30f, 1, 55f),
        Motif(MotifKind.FLOWER, 0.14f, 0.88f, 0.26f, 0, -8f)
    )
}

private fun DrawScope.drawOrganicFlower(
    center: Offset,
    scale: Float,
    rotationDeg: Float,
    pal: FlowerPal
) {
    if (scale <= 1f) return
    withTransform({
        translate(center.x, center.y)
        rotate(rotationDeg, pivot = Offset.Zero)
    }) {
        val n = pal.petals
        val (petalLen, petalW) = when (pal.petalShape) {
            PetalShape.ROUNDED -> scale * 1.32f to scale * 0.40f
            PetalShape.POINTED -> scale * 1.45f to scale * 0.30f
            PetalShape.WIDE -> scale * 1.18f to scale * 0.52f
            PetalShape.NARROW -> scale * 1.40f to scale * 0.26f
            PetalShape.STAR -> scale * 1.50f to scale * 0.22f
        }
        for (i in 0 until n) {
            rotate(i * (360f / n), pivot = Offset.Zero) {
                val petal = Path().apply {
                    moveTo(0f, 0f)
                    when (pal.petalShape) {
                        PetalShape.POINTED, PetalShape.STAR, PetalShape.NARROW -> {
                            cubicTo(
                                petalW, -petalLen * 0.15f,
                                petalW * 0.55f, -petalLen * 0.72f,
                                0f, -petalLen
                            )
                            cubicTo(
                                -petalW * 0.55f, -petalLen * 0.72f,
                                -petalW, -petalLen * 0.15f,
                                0f, 0f
                            )
                        }
                        PetalShape.WIDE -> {
                            cubicTo(
                                petalW * 1.1f, -petalLen * 0.25f,
                                petalW * 1.05f, -petalLen * 0.55f,
                                0f, -petalLen * 0.92f
                            )
                            cubicTo(
                                -petalW * 1.05f, -petalLen * 0.55f,
                                -petalW * 1.1f, -petalLen * 0.25f,
                                0f, 0f
                            )
                        }
                        PetalShape.ROUNDED -> {
                            cubicTo(
                                petalW, -petalLen * 0.2f,
                                petalW * 0.9f, -petalLen * 0.68f,
                                0f, -petalLen
                            )
                            cubicTo(
                                -petalW * 0.9f, -petalLen * 0.68f,
                                -petalW, -petalLen * 0.2f,
                                0f, 0f
                            )
                        }
                    }
                    close()
                }
                drawPath(
                    path = petal,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            pal.inner,
                            pal.outer,
                            pal.outer.copy(alpha = 0f)
                        ),
                        center = Offset(0f, -petalLen * 0.28f),
                        radius = petalLen
                    )
                )
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    pal.core.copy(alpha = pal.core.alpha * 0.45f),
                    Color.Transparent
                ),
                radius = scale * 1.0f
            ),
            radius = scale * 1.0f
        )
        drawCircle(pal.core, scale * 0.32f)
        drawCircle(
            pal.inner.copy(alpha = (pal.inner.alpha * 0.95f).coerceAtMost(1f)),
            scale * 0.14f
        )
    }
}

private fun DrawScope.drawOrganicLeaf(
    attach: Offset,
    outward: Offset,
    scale: Float,
    rotationBias: Float,
    pal: LeafPal
) {
    if (scale <= 1f) return
    val oLen = sqrt(outward.x * outward.x + outward.y * outward.y).coerceAtLeast(0.001f)
    val ox = outward.x / oLen
    val oy = outward.y / oLen
    val angleDeg = Math.toDegrees(atan2(oy.toDouble(), ox.toDouble())).toFloat() + 90f + rotationBias

    val halfW = if (pal.wide) 0.58f else 0.40f
    val tipY = if (pal.wide) 1.28f else 1.48f

    withTransform({
        translate(attach.x, attach.y)
        rotate(angleDeg, pivot = Offset.Zero)
    }) {
        val path = Path().apply {
            moveTo(0f, 0f)
            cubicTo(
                scale * halfW, -scale * 0.12f,
                scale * halfW * 1.05f, -scale * (tipY * 0.55f),
                0f, -scale * tipY
            )
            cubicTo(
                -scale * halfW * 1.05f, -scale * (tipY * 0.55f),
                -scale * halfW, -scale * 0.12f,
                0f, 0f
            )
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    pal.fill.copy(alpha = pal.fill.alpha * 0.75f),
                    pal.fill,
                    pal.tip
                ),
                start = Offset(0f, 0f),
                end = Offset(0f, -scale * tipY)
            )
        )
        drawLine(
            color = pal.stem.copy(alpha = pal.stem.alpha * 0.85f),
            start = Offset(0f, -scale * 0.04f),
            end = Offset(0f, -scale * tipY * 0.86f),
            strokeWidth = scale * 0.05f,
            cap = StrokeCap.Round
        )
    }
}
