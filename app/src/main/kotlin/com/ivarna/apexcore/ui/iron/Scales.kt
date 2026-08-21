package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

/* ── §3.6 PressureScale — the ruler that replaces progress bars ── */
@Composable
fun PressureScale(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    labels: List<String>? = null,
    valueText: String? = null,
    caption: String? = null,
    onMajorTickCrossed: () -> Unit = {},
) {
    val clack = rememberClack()
    val marker by animateFloatAsState(fraction.coerceIn(0f, 1f), IronMotion.drawer(), label = "marker")
    var lastMajor by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        snapshotFlow { (marker * 4f).toInt() }.drop(1).collect { idx ->
            if (idx != lastMajor) {
                lastMajor = idx
                if (idx > 0) {
                    clack.off()
                    onMajorTickCrossed()
                }
            }
        }
    }
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val cy = size.height * 0.55f
            val labelLayouts = (labels ?: emptyList()).map {
                measurer.measure(
                    it,
                    TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = Iron.Bone500)
                )
            }

            val mx = marker * size.width
            drawRect(
                Iron.Signal500,
                topLeft = Offset(0f, cy - 4.dp.toPx()),
                size = Size(mx, 8.dp.toPx())
            )
            drawLine(Iron.Anvil600, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
            var x = 0f
            var i = 0
            while (x <= size.width + 0.5f) {
                val major = i % 5 == 0
                drawLine(
                    if (major) Iron.Bone300 else Iron.Anvil500,
                    Offset(x, cy), Offset(x, cy - (if (major) 10.dp else 5.dp).toPx()),
                    (if (major) 1.5f else 1f).dp.toPx() * 0.8f
                )
                if (major && labels != null) {
                    val li = i / 5
                    if (li < labelLayouts.size) {
                        val l = labelLayouts[li]
                        drawText(l, topLeft = Offset(x - l.size.width / 2f, cy + 3.dp.toPx()))
                    }
                }
                x += size.width / 20f
                i++
            }
            drawRect(
                Iron.Brass400,
                topLeft = Offset(mx - 1.dp.toPx(), cy - 13.dp.toPx()),
                size = Size(2.dp.toPx(), 13.dp.toPx())
            )
            drawRect(
                Iron.Brass400,
                topLeft = Offset(mx, cy - 13.dp.toPx()),
                size = Size(6.dp.toPx(), 4.dp.toPx())
            )
        }
        if (valueText != null || caption != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (caption != null) Text(caption, style = IronType.MonoSm, color = Iron.Bone500)
                if (valueText != null) Text(valueText, style = IronType.Mono, color = Iron.Bone100)
            }
        }
    }
}

/* ── §3.7 ThermometerStrip — shared 30–60°C scale, two needles ── */
@Composable
fun ThermometerStrip(
    batteryC: Int,
    cpuC: Int,
    modifier: Modifier = Modifier,
) {
    val throttling = cpuC > 45
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(throttling) {
        if (!throttling) {
            pulse = 1f
            return@LaunchedEffect
        }
        val t0 = System.nanoTime()
        while (true) {
            pulse = 0.4f + 0.6f * (((System.nanoTime() - t0) / 0.8e9f) % 1f)
            delay(50)
        }
    }
    val measurer = rememberTextMeasurer()

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val labelL = listOf("30", "35", "40", "45", "50", "55", "60").map {
            measurer.measure(
                "$it°",
                TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = Iron.Bone500)
            )
        }
        val cy = 14.dp.toPx()
        drawLine(Iron.Anvil600, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
        for (i in 0..6) {
            val x = size.width * i / 6f
            val ember = i >= 3
            drawLine(
                if (ember) Iron.Ember500.copy(alpha = 0.7f) else Iron.Bone300,
                Offset(x, cy), Offset(x, cy - 8.dp.toPx()), 1.dp.toPx()
            )
            if (ember) {
                drawLine(
                    Iron.Ember500.copy(alpha = 0.35f),
                    Offset(x, cy), Offset(x, cy + 4.dp.toPx()), 1.dp.toPx()
                )
            }
            drawText(labelL[i], topLeft = Offset(x - labelL[i].size.width / 2f, cy + 5.dp.toPx()))
        }
        fun flag(v: Int, tint: Color, alpha: Float, text: String) {
            val x = size.width * ((v - 30f) / 30f).coerceIn(0.02f, 0.98f)
            drawLine(tint.copy(alpha = alpha), Offset(x, cy - 20.dp.toPx()), Offset(x, cy - 4.dp.toPx()), 2.dp.toPx())
            drawPath(
                Path().apply {
                    moveTo(x, cy - 20.dp.toPx())
                    lineTo(x + 18.dp.toPx(), cy - 17.dp.toPx())
                    lineTo(x, cy - 14.dp.toPx())
                    close()
                },
                tint.copy(alpha = alpha)
            )
            val l = measurer.measure(
                text,
                TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = tint)
            )
            drawText(l, topLeft = Offset(x + 3.dp.toPx(), cy - 34.dp.toPx()))
        }
        flag(batteryC, Iron.Bone300, 1f, "BATT $batteryC°")
        flag(cpuC, if (throttling) Iron.Ember500 else Iron.Signal500, if (throttling) pulse else 1f, "CPU $cpuC°")
    }
}
