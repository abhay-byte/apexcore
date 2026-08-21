package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* ── §3.15 OdometerCounter — per-digit roll, 30ms stagger right→left ── */
@Composable
fun OdometerCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IronType.MonoLg,
    onSettled: () -> Unit = {},
) {
    val density = LocalDensity.current
    val digitH = with(density) { style.lineHeight.toDp() }
    val n = text.length
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, c ->
            if (c.isDigit()) {
                DigitRoll(
                    digit = c,
                    height = digitH,
                    style = style,
                    staggerMs = (n - 1 - i) * 30,
                    settled = if (i == n - 1) onSettled else ({})
                )
            } else {
                Text(c.toString(), style = style)
            }
        }
    }
}

@Composable
private fun DigitRoll(digit: Char, height: Dp, style: TextStyle, staggerMs: Int, settled: () -> Unit) {
    val pos = remember { Animatable(0f) }
    LaunchedEffect(digit) {
        delay(staggerMs.toLong())
        pos.animateTo((digit - '0').toFloat(), IronMotion.drawer())
        settled()
    }
    Box(Modifier.height(height).clipToBounds()) {
        Column(Modifier.graphicsLayer { translationY = -pos.value * height.toPx() }) {
            (0..9).forEach { d ->
                Text(d.toString(), style = style, modifier = Modifier.height(height).wrapContentSize(Alignment.Center))
            }
        }
    }
}

/* ── §3.16 ShavingsParticles — fixed pool, zero per-frame allocation ── */
class ShavingsState {
    val data = FloatArray(CAP * 7)
    var alive = 0
        private set
    var version by mutableIntStateOf(0)
        private set

    fun burst(ox: Float, oy: Float, radius: Float, count: Int, speed: Float) {
        val rnd = Random(0xC0FFEE + version)
        var n = 0
        while (n < count && alive < CAP) {
            val i = alive * 7
            val a = rnd.nextFloat() * 2f * Math.PI.toFloat()
            data[i]     = ox + cos(a) * radius * rnd.nextFloat()
            data[i + 1] = oy + sin(a) * radius * rnd.nextFloat()
            val v = speed * (0.5f + rnd.nextFloat())
            data[i + 2] = cos(a) * v
            data[i + 3] = sin(a) * v - speed * 0.6f
            data[i + 4] = rnd.nextFloat() * 360f
            data[i + 5] = (rnd.nextFloat() - 0.5f) * 720f
            data[i + 6] = 1f
            alive++
            n++
        }
        version++
    }

    fun step(dt: Float, gravity: Float, floorY: Float) {
        for (p in 0 until alive) {
            val i = p * 7
            if (data[i + 6] <= 0f) continue
            data[i + 3] += gravity * dt
            data[i]     += data[i + 2] * dt
            data[i + 1] += data[i + 3] * dt
            data[i + 4] += data[i + 5] * dt
            if (data[i + 1] > floorY && data[i + 3] > 0f) {
                data[i + 3] *= -0.15f
                data[i + 5] *= 0.5f
                data[i + 6] = 0.9f
            } else if (data[i + 6] < 1f) {
                data[i + 6] -= dt * 2.5f
            }
        }
        while (alive > 0 && data[(alive - 1) * 7 + 6] <= 0f) alive--
    }

    companion object { const val CAP = 220 }
}

@Composable
fun ShavingsLayer(
    state: ShavingsState,
    modifier: Modifier = Modifier,
    floorFromBottom: Dp = 140.dp,
) {
    val density = LocalDensity.current
    var h by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.version) {
        if (state.version == 0) return@LaunchedEffect
        val gravity = with(density) { 2400.dp.toPx() }
        val floor = h - with(density) { floorFromBottom.toPx() }
        var last = 0L
        while (state.alive > 0) {
            withInfiniteAnimationFrameMillis { now ->
                val dt = if (last == 0L) 0.016f
                else ((now - last) / 1000f).coerceIn(0f, 0.05f)
                last = now
                state.step(dt, gravity, floor)
                tick++
            }
        }
    }

    val colors = listOf(Iron.Signal500, Iron.Signal700, Iron.Anvil500)
    val path = remember { Path() }
    Canvas(modifier.fillMaxSize().onSizeChanged { h = it.height.toFloat() }) {
        if (tick >= 0) { /* trigger redraw */ }
        val s = 2.6.dp.toPx()
        for (p in 0 until state.alive) {
            val i = p * 7
            val a = state.data[i + 6]
            if (a <= 0f) continue
            withTransform({
                translate(state.data[i], state.data[i + 1])
                rotate(state.data[i + 4], pivot = Offset.Zero)
            }) {
                path.reset()
                path.moveTo(-s, -s * 0.4f)
                path.lineTo(s, -s * 0.6f)
                path.lineTo(s * 0.8f, s * 0.5f)
                path.lineTo(-s * 1.1f, s * 0.3f)
                path.close()
                drawPath(path, colors[p % 3], alpha = a)
            }
        }
    }
}

/* ── §7.4 FlipCard — Work Order tap-to-flip back to idle (guarded on init) ── */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val rot = remember { Animatable(0f) }
    var showBack by remember { mutableStateOf(flipped) }
    LaunchedEffect(flipped) {
        if (showBack != flipped) {
            rot.animateTo(90f, tween(160, easing = FastOutSlowInEasing))
            showBack = flipped
            rot.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier.graphicsLayer { rotationX = rot.value }) {
        if (showBack) back() else front()
    }
}
