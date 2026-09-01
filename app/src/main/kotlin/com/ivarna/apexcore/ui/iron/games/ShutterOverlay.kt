package com.ivarna.apexcore.ui.iron.games

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivarna.apexcore.ui.iron.Iron
import com.ivarna.apexcore.ui.iron.IronMotion
import com.ivarna.apexcore.ui.iron.IronSkin
import com.ivarna.apexcore.ui.iron.IronType
import com.ivarna.apexcore.ui.iron.LocalReducedMotion
import com.ivarna.apexcore.ui.iron.StampInk
import com.ivarna.apexcore.ui.iron.StampLabel
import com.ivarna.apexcore.ui.iron.ironGrain
import com.ivarna.apexcore.ui.iron.ironSkin
import com.ivarna.apexcore.ui.iron.rememberClack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hydraulic-press palette — Graphite anvil plates, Vellum paper plates. */
private data class ShutterPalette(
    val plate: Color,
    val grid: Color,
    val contour: Color,
    val dim: Color,
    val iconWell: Color,
    val text: Color,
    val textDim: Color,
    val tickOff: Color,
) {
    companion object {
        fun from(skin: IronSkin): ShutterPalette =
            if (skin.isPaper) ShutterPalette(
                plate = Iron.Bone100,
                grid = Iron.Ink600.copy(alpha = 0.22f),
                contour = Iron.Ink600.copy(alpha = 0.35f),
                dim = Iron.Ink900.copy(alpha = 0.28f),
                iconWell = Iron.Bone50,
                text = Iron.Ink900,
                textDim = Iron.Ink600,
                tickOff = Iron.Ink600.copy(alpha = 0.40f),
            ) else ShutterPalette(
                plate = Iron.Anvil900,
                grid = Iron.Anvil700,
                contour = Iron.Anvil600,
                dim = Iron.Anvil950,
                iconWell = Iron.Anvil900,
                text = Iron.Bone300,
                textDim = Iron.Bone500,
                tickOff = Iron.Anvil600,
            )
    }
}

/* ═══ The hydraulic press — renders LaunchState, fires the haptics ════ */

@Composable
fun ShutterOverlay(
    state: LaunchState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val reduced = LocalReducedMotion.current
    val pal = ShutterPalette.from(ironSkin())

    // 0 = fully open (off-screen) · 1 = closed (seam meet)
    val plateTop = remember { Animatable(0f) }
    val plateBottom = remember { Animatable(0f) }
    val iconAppear = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(1f) }
    val squash = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val tickFill = remember { Animatable(0f) }
    var scanX by remember { mutableFloatStateOf(0f) }
    var backScrub by remember { mutableFloatStateOf(0f) }
    // Keep the overlay composed through the IDLE snap-open so exit motion can finish.
    var mounted by remember { mutableStateOf(false) }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            LaunchPhase.WIND -> {
                mounted = true
                backScrub = 0f
                // Clean start every ceremony — never animate from a half-open leftover.
                plateTop.snapTo(0f)
                plateBottom.snapTo(0f)
                iconAppear.snapTo(0f)
                iconAlpha.snapTo(1f)
                squash.snapTo(0f)
                tickFill.snapTo(0f)
                clack.confirm()
                if (reduced) {
                    // Identity stays visible; no plate travel / squash / scan.
                    iconAppear.snapTo(1f)
                    iconAlpha.snapTo(1f)
                    scrim.animateTo(0.97f, tween(200))
                } else {
                    launch { iconAppear.animateTo(1f, tween(140, easing = IronMotion.EaseWind)) }
                    launch { plateTop.animateTo(1f, tween(160, easing = IronMotion.EaseWind)) }
                    delay(40) // bottom lags 40ms
                    plateBottom.animateTo(1f, tween(120, easing = IronMotion.EaseWind))
                }
            }
            LaunchPhase.PRESS -> {
                mounted = true
                clack.thud()
                if (!reduced) {
                    squash.animateTo(1f, IronMotion.machined())
                }
            }
            LaunchPhase.FREEZE -> {
                mounted = true
                // Direct FREEZE (tests / restore): plates closed, logo always readable.
                if (plateTop.value < 0.9f) plateTop.snapTo(1f)
                if (plateBottom.value < 0.9f) plateBottom.snapTo(1f)
                // Never leave a half-faded identity sitting on the seam readout.
                iconAppear.snapTo(1f)
                iconAlpha.snapTo(1f)
                // Stamp then rebound — logo stays clear of OPTIMIZED / FREEZING text.
                if (!reduced) {
                    launch {
                        squash.animateTo(0f, tween(110, easing = FastOutSlowInEasing))
                    }
                }
            }
            LaunchPhase.PART -> {
                mounted = true
                if (reduced) {
                    scrim.animateTo(0f, tween(200))
                    iconAlpha.animateTo(0f, tween(80))
                } else {
                    // Fade identity while plates open — do not wait on icon fade.
                    launch { iconAlpha.animateTo(0f, tween(120)) }
                    launch { iconAppear.animateTo(0f, tween(140)) }
                    launch { plateTop.animateTo(0f, tween(280, easing = IronMotion.EaseWind)) }
                    delay(40)
                    plateBottom.animateTo(0f, tween(240, easing = IronMotion.EaseWind))
                }
            }
            LaunchPhase.FAILED -> {
                mounted = true
                clack.no()
                plateTop.snapTo(1f)
                plateBottom.snapTo(1f)
                iconAppear.snapTo(1f)
                iconAlpha.snapTo(1f)
                squash.snapTo(0f)
                if (reduced) scrim.snapTo(0.97f)
            }
            LaunchPhase.IDLE -> {
                if (!mounted) return@LaunchedEffect
                val d = if (plateTop.value > 0.5f || plateBottom.value > 0.5f) 160 else 220
                launch { plateTop.animateTo(0f, tween(d, easing = IronMotion.EaseWind)) }
                launch { plateBottom.animateTo(0f, tween(d, easing = IronMotion.EaseWind)) }
                launch { iconAppear.animateTo(0f, tween(120)) }
                launch { scrim.animateTo(0f, tween(140)) }
                iconAlpha.snapTo(1f)
                squash.snapTo(0f)
                backScrub = 0f
                delay(d.toLong() + 20)
                mounted = false
            }
        }
    }

    val target = if (state.totalTargets > 0) {
        state.frozenCount.toFloat() / state.totalTargets.toFloat()
    } else if (state.phase == LaunchPhase.FREEZE || state.phase == LaunchPhase.PART) {
        // One-shot / empty freeze: single sweep-fill so the seam still reads as work done.
        1f
    } else {
        0f
    }
    LaunchedEffect(target, state.phase) {
        if (state.phase == LaunchPhase.FREEZE || state.phase == LaunchPhase.PART) {
            tickFill.animateTo(target, tween(160, easing = LinearEasing))
        } else if (state.phase == LaunchPhase.WIND) {
            tickFill.snapTo(0f)
        }
    }

    LaunchedEffect(state.phase, reduced) {
        if (state.phase != LaunchPhase.FREEZE || reduced) return@LaunchedEffect
        val t0 = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val p = ((now - t0) / 0.9e9f) % 1f
                scanX = if (p < 0.5f) p * 2f else (1f - p) * 2f
            }
        }
    }

    PredictiveBackHandler(
        enabled = state.phase == LaunchPhase.WIND || state.phase == LaunchPhase.PRESS
    ) { progress ->
        try {
            progress.collect { backScrub = it.progress }
            onCancel()
        } catch (_: Throwable) {
            backScrub = 0f
        }
    }

    if (!mounted) return

    Box(modifier.fillMaxSize()) {
        if (reduced) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim.value }
                    .background(pal.plate)
                    .ironGrain(0.05f)
            )
            // Reduced motion still shows selected app identity — keep status below the logo.
            LaunchAppIcon(
                state = state,
                pal = pal,
                iconAppear = { iconAppear.value },
                iconAlpha = { iconAlpha.value },
                squash = { 0f },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-48).dp),
            )
            if (
                state.phase == LaunchPhase.FREEZE ||
                state.phase == LaunchPhase.FAILED ||
                state.phase == LaunchPhase.PRESS ||
                state.phase == LaunchPhase.WIND
            ) {
                CenterStatus(
                    state,
                    pal,
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = 56.dp)
                        .zIndex(3f)
                        .testTag("launch_readout"),
                )
            }
            return@Box
        }

        // Dim the bench under the press so the iris reads cleanly.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val closed = (plateTop.value + plateBottom.value) / 2f
                    alpha = closed * 0.55f
                }
                .background(pal.dim)
        )

        PressPlate(
            pal = pal,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .zIndex(0f)
                .testTag("launch_press_top")
                .graphicsLayer {
                    // closed=0 translation; open=fully off-screen; scrub nudges open
                    translationY = -size.height * ((1f - plateTop.value) + backScrub * 0.22f)
                },
        )
        PressPlate(
            pal = pal,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .zIndex(0f)
                .testTag("launch_press_bottom")
                .graphicsLayer {
                    translationY = size.height * ((1f - plateBottom.value) + backScrub * 0.22f)
                },
        )

        // Vertical stack with clear bands so logo never sits on the readout:
        //   [ logo ]  ~72dp above center
        //   =seam=    at center
        //   readout   ~28dp below center
        val seamAlpha = {
            val closed = (plateTop.value + plateBottom.value) / 2f
            ((closed - 0.85f) / 0.15f).coerceIn(0f, 1f) * (1f - backScrub) *
                if (state.phase == LaunchPhase.PART) 0.35f else 1f
        }

        LaunchAppIcon(
            state = state,
            pal = pal,
            iconAppear = { iconAppear.value },
            iconAlpha = { iconAlpha.value },
            squash = { squash.value },
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(2f)
                .offset(y = (-72).dp),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .zIndex(1f)
                .testTag("launch_seam")
                .graphicsLayer { alpha = seamAlpha() },
        ) {
            SeamCanvas(
                pal = pal,
                tickFill = { tickFill.value },
                scanX = { scanX },
                scanning = state.phase == LaunchPhase.FREEZE,
                tickCount = state.totalTargets,
                optimized = state.phase == LaunchPhase.FREEZE && state.totalTargets == 0,
            )
        }

        val readout = when {
            state.errorTitle != null -> null
            state.phase == LaunchPhase.PART ->
                "LAUNCHING · ${state.app?.name?.uppercase().orEmpty()}"
            state.totalTargets > 0 ->
                "FREEZING · ${state.frozenCount} / ${state.totalTargets}"
            state.phase == LaunchPhase.FREEZE -> "OPTIMIZED"
            state.phase == LaunchPhase.PRESS || state.phase == LaunchPhase.WIND -> null
            else -> null
        }
        readout?.let {
            Text(
                it,
                style = IronType.Mono,
                color = pal.text,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 28.dp)
                    .zIndex(3f)
                    .graphicsLayer { alpha = seamAlpha() }
                    .testTag("launch_readout"),
            )
        }

        if (state.phase == LaunchPhase.FAILED) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 56.dp)
                    .padding(horizontal = 32.dp)
                    .zIndex(3f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.errorTitle?.let { StampLabel(it, StampInk.Ember) }
                Spacer(Modifier.height(10.dp))
                state.errorDetail?.let {
                    Text(it, style = IronType.MonoSm, color = pal.textDim)
                }
            }
        }
    }
}

@Composable
private fun LaunchAppIcon(
    state: LaunchState,
    pal: ShutterPalette,
    iconAppear: () -> Float,
    iconAlpha: () -> Float,
    squash: () -> Float,
    modifier: Modifier = Modifier,
) {
    val app = state.app ?: return
    Box(
        modifier
            .testTag("launch_app_icon")
            .graphicsLayer {
                val base = 0.82f + 0.18f * iconAppear()
                // ~45% vertical compression at full squash — readable stamp, not a line.
                val pressedY = 1f - 0.45f * squash()
                scaleX = base
                scaleY = base * pressedY
                alpha = iconAppear() * iconAlpha()
            }
            .size(88.dp)
            .clip(CircleShape)
            .background(pal.iconWell)
            .border(2.dp, Iron.Brass400, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().padding(14.dp),
            contentAlignment = Alignment.Center,
        ) { app.icon() }
    }
}

@Composable
private fun CenterStatus(state: LaunchState, pal: ShutterPalette, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.errorTitle != null) {
            StampLabel(state.errorTitle!!, StampInk.Ember)
            Spacer(Modifier.height(8.dp))
            Text(state.errorDetail.orEmpty(), style = IronType.MonoSm, color = pal.textDim)
        } else {
            Text(
                if (state.totalTargets > 0) {
                    "FREEZING · ${state.frozenCount} / ${state.totalTargets}"
                } else if (state.phase == LaunchPhase.FREEZE) {
                    "OPTIMIZED"
                } else {
                    "PREPARING…"
                },
                style = IronType.Mono,
                color = pal.text,
            )
        }
    }
}

@Composable
private fun PressPlate(pal: ShutterPalette, modifier: Modifier) {
    val grid = pal.grid
    val contour = pal.contour
    Box(
        modifier
            .background(pal.plate)
            .ironGrain(0.05f)
            .drawWithCache {
                val step = 24.dp.toPx()
                val vCount = (size.width / step).toInt() + 1
                val hCount = (size.height / step).toInt() + 1
                val arc1 = Size(size.width * 0.9f, size.height * 1.4f)
                onDrawBehind {
                    val w = 0.75.dp.toPx()
                    repeat(vCount) { i ->
                        drawLine(grid, Offset(i * step, 0f), Offset(i * step, size.height), w)
                    }
                    repeat(hCount) { i ->
                        drawLine(grid, Offset(0f, i * step), Offset(size.width, i * step), w)
                    }
                    // Topographic contour arcs — survey nod on each press plate.
                    drawArc(
                        contour, 200f, 120f, false,
                        topLeft = Offset(-size.width * 0.2f, size.height * 0.1f),
                        size = arc1, style = Stroke(1.dp.toPx()),
                    )
                    drawArc(
                        contour, 20f, 140f, false,
                        topLeft = Offset(size.width * 0.55f, -size.height * 0.3f),
                        size = arc1, style = Stroke(1.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
private fun SeamCanvas(
    pal: ShutterPalette,
    tickFill: () -> Float,
    scanX: () -> Float,
    scanning: Boolean,
    tickCount: Int,
    optimized: Boolean,
) {
    val tickOff = pal.tickOff
    Canvas(
        Modifier
            .fillMaxWidth(0.86f)
            .height(20.dp)
    ) {
        val cy = 6.dp.toPx()
        val tickH = 7.dp.toPx()
        val w = 2.dp.toPx()
        val scanW = 1.5.dp.toPx()
        val fill = tickFill()
        val n = if (optimized) 0 else tickCount.coerceIn(0, 28)

        drawLine(Iron.Brass400, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())

        if (n > 0) {
            val lit = fill * n
            for (i in 0 until n) {
                val x = size.width * (i + 0.5f) / n
                val on = i < lit
                drawLine(
                    if (on) Iron.Brass400 else tickOff,
                    Offset(x, cy + 3.dp.toPx()),
                    Offset(x, cy + 3.dp.toPx() + tickH),
                    w,
                )
            }
            val idx = lit.toInt()
            if (idx in 0 until n) {
                val x = size.width * (idx + 0.5f) / n
                drawLine(
                    Iron.Signal500,
                    Offset(x, cy + 1.dp.toPx()),
                    Offset(x, cy + 3.dp.toPx() + tickH + 2.dp.toPx()),
                    w,
                )
            }
        }

        if (scanning) {
            val x = size.width * scanX()
            drawLine(
                Iron.Brass400,
                Offset(x, cy - 5.dp.toPx()),
                Offset(x, cy + 12.dp.toPx()),
                scanW,
            )
        }
    }
}
