package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* Manual ink adapts to the finish: ink on paper, bone on metal (themed manual). */
@Composable
internal fun inkColor(): Color = if (ironSkin().isPaper) Iron.Ink600 else Iron.Bone500

/** Accent strokes: Signal700 prints on paper, Signal500 reads on anvil. */
@Composable
internal fun accentColor(): Color = if (ironSkin().isPaper) Iron.Signal700 else Iron.Signal500

/* ── Doodles: 1.5dp stroke, hand wiggle ── */

@Composable
fun DoodleArrow(tint: Color = inkColor(), modifier: Modifier = Modifier) = Canvas(modifier.size(44.dp)) {
    val st = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
    val path = Path().apply {
        moveTo(4.dp.toPx(), size.height - 6.dp.toPx())
        quadraticTo(
            size.width * 0.42f, size.height * 0.88f,
            size.width - 8.dp.toPx(), 8.dp.toPx()
        )
    }
    drawPath(path, tint, style = st)
    val tip = Offset(size.width - 8.dp.toPx(), 8.dp.toPx())
    drawLine(tint, tip, tip - Offset(8.dp.toPx(), 2.dp.toPx()), st.width, StrokeCap.Round)
    drawLine(tint, tip, tip - Offset(2.dp.toPx(), 8.dp.toPx()), st.width, StrokeCap.Round)
}

@Composable
fun DoodleStar(tint: Color = inkColor(), modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val c = center
    val r = size.minDimension / 2f - 2f
    val w = 1.5.dp.toPx()
    repeat(5) { i ->
        val a0 = (i * 144f) * PI.toFloat() / 180f - PI.toFloat() / 2
        val a1 = ((i + 2) * 144f) * PI.toFloat() / 180f - PI.toFloat() / 2
        drawLine(
            tint, Offset(c.x + cos(a0) * r, c.y + sin(a0) * r),
            Offset(c.x + cos(a1) * r, c.y + sin(a1) * r), w, StrokeCap.Round
        )
    }
}

@Composable
fun DoodleSquiggle(tint: Color = inkColor(), modifier: Modifier = Modifier) = Canvas(modifier.size(60.dp, 10.dp)) {
    val path = Path().apply {
        moveTo(2f, size.height / 2f)
        var x = 2f
        var up = true
        while (x < size.width - 2f) {
            quadraticTo(x + 4f, if (up) 0f else size.height, x + 8f, size.height / 2f)
            x += 8f
            up = !up
        }
    }
    drawPath(path, tint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
fun DoodleCircle(tint: Color = Iron.Signal700, modifier: Modifier = Modifier) = Canvas(modifier.size(34.dp)) {
    drawArc(
        tint, 0f, 300f, false, Offset(2f, 2f), size.copy(width = size.width - 4f, height = size.height - 4f),
        style = Stroke(1.5.dp.toPx())
    )
    drawArc(
        tint, 310f, 40f, false, Offset(4f, 4f),
        size.copy(width = size.width - 8f, height = size.height - 8f), style = Stroke(1.5.dp.toPx())
    )
}

/* ── The two keys (§7.2 page 4) ── */

@Composable
fun SkeletonKeyGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(22.dp)) {
    val s = size.width / 22f
    drawCircle(tint, 6f * s, Offset(6f * s, 11f * s), style = Stroke(2f * s, cap = StrokeCap.Round))
    drawLine(tint, Offset(12f * s, 11f * s), Offset(21f * s, 11f * s), 2f * s, StrokeCap.Round)
    drawLine(tint, Offset(17f * s, 11f * s), Offset(17f * s, 15f * s), 2f * s, StrokeCap.Round)
    drawLine(tint, Offset(20f * s, 11f * s), Offset(20f * s, 16f * s), 2f * s, StrokeCap.Round)
}

@Composable
fun AllenKeyGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val s = size.width / 20f
    drawLine(tint, Offset(5f * s, 4f * s), Offset(5f * s, 16f * s), 2.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(5f * s, 16f * s), Offset(15f * s, 16f * s), 2.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(3.5f * s, 3f * s), Offset(6.5f * s, 3f * s), 1.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(13.5f * s, 14.5f * s), Offset(16.5f * s, 14.5f * s), 1.5f * s, StrokeCap.Round)
}

/* ── FIG frames: corner registration ticks + label ── */

@Composable
fun FigFrame(label: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ic = inkColor()
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val t = 12.dp.toPx()
            val w = 1.5.dp.toPx()
            fun mark(c: Offset, dx: Float, dy: Float) {
                drawLine(ic, c, c + Offset(dx * t, 0f), w)
                drawLine(ic, c, c + Offset(0f, dy * t), w)
            }
            mark(Offset(0f, 0f), 1f, 1f)
            mark(Offset(size.width, 0f), -1f, 1f)
            mark(Offset(0f, size.height), 1f, -1f)
            mark(Offset(size.width, size.height), -1f, -1f)
        }
        Text(label, style = IronType.MonoSm, color = ic, modifier = Modifier.padding(6.dp))
        content()
    }
}

/* ── FIG artwork: ink line-art, 1.5dp strokes, one accent each ── */

@Composable
fun FigArtwork(figure: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val ic = inkColor()
    val ac = accentColor()
    val ph = ironSkin().phosphor()
    Canvas(modifier.size(200.dp)) {
        val s = size.width / 200f
        fun d(v: Int) = v * s
        val w = 1.5.dp.toPx()

        when (figure) {
            0 -> {
                drawCircle(ic, d(66), center, style = Stroke(w))
                repeat(12) { i ->
                    val a = i / 12f * 2f * PI.toFloat() - PI.toFloat() / 2
                    drawLine(
                        ic, Offset(center.x + cos(a) * d(66), center.y + sin(a) * d(66)),
                        Offset(center.x + cos(a) * d(74), center.y + sin(a) * d(74)), w
                    )
                }
                drawLine(ac, center, Offset(center.x + d(26), center.y - d(44)), 2.5f * s)
                drawCircle(ic, d(4), center)
                drawText(
                    measurer.measure(
                        "S/N 3F-0042",
                        TextStyle(fontFamily = PlexMono, fontSize = 9.sp, color = ic)
                    ),
                    topLeft = Offset(center.x - d(34), center.y + d(50))
                )
            }
            1 -> {
                val c = Offset(d(95), d(72))
                drawCircle(ic, d(44), c, style = Stroke(w))
                repeat(16) { i ->
                    val a = i / 16f * 2f * PI.toFloat()
                    drawLine(
                        ic, Offset(c.x + cos(a) * d(44), c.y + sin(a) * d(44)),
                        Offset(c.x + cos(a) * d(50), c.y + sin(a) * d(50)), w
                    )
                }
                drawPath(
                    Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(d(148), d(40))
                    },
                    ic,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 4f * s)))
                )
                drawLine(ac, Offset(d(148), d(40)), Offset(d(172), d(16)), 2.5f * s)
                drawCircle(Iron.Brass400, d(4), Offset(d(148), d(40)))
                repeat(6) { i ->
                    val x = d(128 + i * 12)
                    val y = d(148 + (i % 3) * 14)
                    drawPath(
                        Path().apply {
                            moveTo(x, y)
                            lineTo(x + d(9), y - d(3))
                            lineTo(x + d(7), y + d(5))
                            close()
                        },
                        if (i % 2 == 0) ac else ic
                    )
                }
                drawText(
                    measurer.measure(
                        "+1.4 GB",
                        TextStyle(fontFamily = PlexMono, fontSize = 13.sp, color = ac)
                    ),
                    topLeft = Offset(d(20), d(150))
                )
            }
            2 -> {
                drawRoundRect(
                    ic, Offset(d(55), d(16)),
                    Size(d(90), d(168)), CornerRadius(d(8)), style = Stroke(w)
                )
                drawLine(Iron.Brass400, Offset(d(68), d(52)), Offset(d(68), d(148)), 2f * s)
                drawRoundRect(ic, Offset(d(74), d(56)), Size(d(46), d(92)), CornerRadius(d(3)), style = Stroke(w))
                drawText(
                    measurer.measure(
                        "144",
                        TextStyle(fontFamily = PlexMono, fontSize = 17.sp, color = ph)
                    ),
                    topLeft = Offset(d(80), d(60))
                )
                drawText(
                    measurer.measure("FPS", TextStyle(fontFamily = PlexMono, fontSize = 7.sp, color = ic)),
                    topLeft = Offset(d(80), d(80))
                )
                val sp = Path().apply {
                    moveTo(d(78), d(108))
                    lineTo(d(86), d(102))
                    lineTo(d(94), d(110))
                    lineTo(d(102), d(99))
                    lineTo(d(114), d(104))
                }
                drawPath(sp, ac, style = Stroke(1.2.dp.toPx()))
                repeat(8) { i ->
                    val h = 4 + (i * 37 % 13)
                    drawLine(ic, Offset(d(78 + i * 5), d(142)), Offset(d(78 + i * 5), d(142 - h)), 2.5f * s)
                }
                drawText(
                    measurer.measure("❄", TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = Iron.Brass400)),
                    topLeft = Offset(d(88), d(120))
                )
            }
            3 -> {
                drawRoundRect(ic, Offset(d(26), d(58)), Size(d(82), d(42)), CornerRadius(d(6)), style = Stroke(w))
                drawCircle(ic, d(7), Offset(d(38), d(79)), style = Stroke(w))
                drawCircle(Iron.Brass400, d(3), Offset(d(38), d(79)))
                drawPath(
                    Path().apply {
                        moveTo(d(45), d(79))
                        quadraticTo(d(105), d(52), d(138), d(70))
                    },
                    ic,
                    style = Stroke(w)
                )
                drawLine(ac, Offset(d(138), d(70)), Offset(d(168), d(70)), 2.5f * s)
                drawCircle(ac, d(5), Offset(d(134), d(70)), style = Stroke(2f * s))
                drawRoundRect(ic, Offset(d(150), d(118)), Size(d(14), d(66)), CornerRadius(d(3)), style = Stroke(w))
                drawRect(ac, Offset(d(152), d(148)), Size(d(10), d(34)))
                drawLine(Iron.Brass400, Offset(d(146), d(146)), Offset(d(168), d(146)), 2f * s)
                drawText(
                    measurer.measure("CAP", TextStyle(fontFamily = PlexMono, fontSize = 7.sp, color = Iron.Brass400)),
                    topLeft = Offset(d(146), d(132))
                )
            }
            4 -> {
                drawCircle(ic, d(24), Offset(d(48), d(100)), style = Stroke(2.5f * s))
                drawLine(ic, Offset(d(72), d(100)), Offset(d(118), d(100)), 2.5f * s)
                drawLine(ic, Offset(d(104), d(100)), Offset(d(104), d(112)), 2.5f * s)
                drawLine(ic, Offset(d(115), d(100)), Offset(d(115), d(114)), 2.5f * s)
                drawLine(ac, Offset(d(150), d(52)), Offset(d(150), d(122)), 3f * s)
                drawLine(ac, Offset(d(150), d(122)), Offset(d(176), d(122)), 3f * s)
                drawText(
                    measurer.measure("or", TextStyle(fontFamily = Caveat, fontSize = 13.sp, color = ic)),
                    topLeft = Offset(d(122), d(52))
                )
            }
        }
    }
}
