package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class DialPalette(
    val minor: Color, val major: Color, val numeral: Color,
    val needle: Color, val idleNeedle: Color, val freed: Color,
)

@Composable
fun dialPalette(): DialPalette =
    if (LocalIronFinish.current == IronFinish.VELLUM) DialPalette(
        minor = Iron.Ink600.copy(alpha = 0.35f), major = Iron.Ink900,
        numeral = Iron.Ink600, needle = Iron.Ink900,
        idleNeedle = Iron.Ink600.copy(alpha = 0.6f), freed = Iron.Phosphor600,
    ) else DialPalette(
        minor = Iron.Anvil500, major = Iron.Bone300, numeral = Iron.Bone300,
        needle = Iron.Bone100, idleNeedle = Iron.Bone500, freed = Iron.Phosphor400,
    )

/** Cached tick geometry — built once per size, never per frame (§11.1). */
private data class DialTick(val start: Offset, val end: Offset, val major: Boolean)

/** Cached numeral layout + precomputed position. */
private data class DialNumeral(val layout: TextLayoutResult, val pos: Offset)

@Composable
fun InstrumentDial(
    value: Float,
    energized: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 240.dp,
    label: String = "",
    valueText: String = "",
    freedFraction: Float = 0f,
    boosting: Boolean = false,
    over: Float = 0f,
    numerals: Boolean = true,
    ignition: Boolean = true,
    active: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    val clack = rememberClack()
    val reduced = LocalReducedMotion.current
    val finish = LocalIronFinish.current
    val rest = -0.025f
    val needle = remember { Animatable(if (energized) 0f else rest) }
    val freed = remember { Animatable(0f) }
    var swept by rememberSaveable { mutableStateOf(false) }
    val pal = dialPalette()
    val spec = if (reduced) tween<Float>(150, easing = LinearEasing) else IronMotion.needle()

    LaunchedEffect(finish) {
        if (!ignition) return@LaunchedEffect
        swept = false
    }

    LaunchedEffect(energized, ignition, swept) {
        if (energized && ignition && !swept) {
            swept = true
            if (!reduced) {
                launch { repeat(3) { delay(80); clack.tick() } }
                needle.animateTo(1f, tween(300, easing = LinearEasing))
            }
            needle.animateTo(value, spec)
        }
    }

    LaunchedEffect(value, energized) {
        if (!ignition || swept) {
            needle.animateTo(if (energized) value else rest, spec)
        }
    }
    LaunchedEffect(freedFraction) { freed.animateTo(freedFraction, spec) }

    var drift by remember { mutableFloatStateOf(0.5f) }
    var huntP by remember { mutableFloatStateOf(0f) }
    var shimP by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(energized, boosting, active) {
        if (!active || !energized || boosting || reduced) {
            drift = 0.5f
            return@LaunchedEffect
        }
        val t0 = System.nanoTime()
        while (true) {
            drift = ((System.nanoTime() - t0) / 4e9f) % 1f
            delay(100)
        }
    }

    LaunchedEffect(boosting, active) {
        if (!boosting || reduced || !active) return@LaunchedEffect
        val t0 = System.nanoTime()
        while (true) {
            huntP = ((System.nanoTime() - t0) / 1.1e9f) % 1f
            shimP = ((System.nanoTime() - t0) / 0.9e9f) % 1f
            delay(33)
        }
    }

    val measurer = rememberTextMeasurer()

    // §3.5 a11y — description changes (⇒ TalkBack re-announce) only on ≥5% steps
    val announcedPct = remember(value) { (((value * 100).toInt() / 5).coerceAtLeast(0)) * 5 }

    Box(
        modifier
            .size(diameter)
            .semantics { contentDescription = "$label $announcedPct percent" }
            .pointerInput(onLongPress) {
                if (onLongPress != null) detectTapGestures(onLongPress = { onLongPress() })
            }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    // ── cache scope: once per size/palette, zero per-frame allocation (§11.1)
                    val r = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ringR = r * 0.78f
                    val arcR = ringR - r * 0.07f
                    val startDeg = 150f
                    val sweepDeg = 240f
                    val arcW = r * 0.022f

                    val ticks = List(65) { i ->
                        val a = Math.toRadians((startDeg + sweepDeg * i / 64f).toDouble())
                        val len = if (i % 8 == 0) r * 0.075f else r * 0.045f
                        DialTick(
                            Offset(center.x + cos(a).toFloat() * ringR, center.y + sin(a).toFloat() * ringR),
                            Offset(center.x + cos(a).toFloat() * (ringR + len), center.y + sin(a).toFloat() * (ringR + len)),
                            i % 8 == 0
                        )
                    }

                    val needlePath = Path().apply {
                        val b = r * 0.03f
                        moveTo(r * 0.70f, 0f)
                        lineTo(-r * 0.20f, b)
                        lineTo(-r * 0.28f, b * 0.5f)
                        lineTo(-r * 0.28f, -b * 0.5f)
                        lineTo(-r * 0.20f, -b)
                        close()
                    }

                    val numeralOut: List<DialNumeral> = if (numerals) {
                        listOf(0, 25, 50, 75, 100).map { n ->
                            val ang = Math.toRadians((startDeg + sweepDeg * n / 100f).toDouble())
                            val rr = r * 0.94f
                            val l = measurer.measure(
                                "$n",
                                TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = pal.numeral)
                            )
                            DialNumeral(
                                l,
                                Offset(
                                    center.x + cos(ang).toFloat() * rr - l.size.width / 2f,
                                    center.y + sin(ang).toFloat() * rr - l.size.height / 2f
                                )
                            )
                        }
                    } else emptyList()

                    val restStopAngle = Math.toRadians((startDeg + sweepDeg * rest).toDouble())

                    // ── draw scope: reads only animated state → redraw-only invalidation ──
                    onDrawBehind {
                        val nv = needle.value + over +
                            (if (boosting) sin(huntP * 2f * PI).toFloat() * 0.012f else 0f) +
                            (if (energized && !boosting) (drift - 0.5f) * 0.004f else 0f)

                        val minorC = if (energized) pal.minor else pal.idleNeedle.copy(alpha = 0.35f)
                        val majorC = if (energized) pal.major else pal.idleNeedle.copy(alpha = 0.5f)

                        for (t in ticks) {
                            drawLine(
                                if (t.major) majorC else minorC,
                                t.start, t.end,
                                if (t.major) r * 0.010f else r * 0.006f
                            )
                        }

            val arcBox = Size(arcR * 2f, arcR * 2f)
            val arcTL = Offset(center.x - arcR, center.y - arcR)
            val shimmerA = if (boosting) 0.55f + 0.45f * sin(shimP * 2f * PI).toFloat() else 1f

            if (nv > 0.01f) {
                drawArc(
                    Iron.Signal500, startDeg, sweepDeg * nv.coerceIn(-0.1f, 1.15f),
                    false, arcTL, arcBox, style = Stroke(arcW), alpha = shimmerA
                )
                if (nv > 0.85f) {
                    drawArc(
                        Iron.Ember500, startDeg + sweepDeg * 0.85f,
                        sweepDeg * (nv - 0.85f).coerceAtLeast(0f), false, arcTL, arcBox, style = Stroke(arcW)
                    )
                }
            }
            if (freed.value > 0.005f) {
                drawArc(
                    pal.freed, startDeg + sweepDeg * nv,
                    sweepDeg * freed.value, false, arcTL, arcBox, style = Stroke(arcW * 0.8f)
                )
            }

                        if (!energized) {
                            drawLine(
                                Iron.Brass400,
                                Offset(
                                    center.x + cos(restStopAngle).toFloat() * (ringR - r * 0.045f),
                                    center.y + sin(restStopAngle).toFloat() * (ringR - r * 0.045f)
                                ),
                                Offset(center.x + cos(restStopAngle).toFloat() * ringR, center.y + sin(restStopAngle).toFloat() * ringR),
                                r * 0.010f
                            )
                        }

            withTransform({
                rotate(startDeg + sweepDeg * nv, pivot = center)
                translate(center.x, center.y)
            }) {
                drawPath(needlePath, if (energized) pal.needle else pal.idleNeedle)
                drawCircle(Iron.Brass400, r * 0.055f, Offset.Zero)
                drawCircle(Iron.Ink900, r * 0.018f, Offset.Zero)
            }

                        numeralOut.forEach { n -> drawText(n.layout, topLeft = n.pos) }
                    }
                }
        ) {}

        if (valueText.isNotEmpty() || label.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = diameter * 0.12f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (valueText.isNotEmpty()) {
                    Text(
                        valueText,
                        style = IronType.Mono,
                        color = if (energized) pal.needle else pal.idleNeedle
                    )
                }
                if (label.isNotEmpty()) {
                    Text(label, style = IronType.MonoSm, color = pal.idleNeedle)
                }
            }
        }
    }
}
