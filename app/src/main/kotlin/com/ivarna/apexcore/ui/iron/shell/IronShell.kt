package com.ivarna.apexcore.ui.iron.shell

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*

enum class GearTab(val label: String) { HOME("HOME"), GAMES("GAMES"), HUD("HUD"), TOOLS("TOOLS") }

@Composable
fun GearTabGlyph(tab: GearTab, tint: Color, modifier: Modifier = Modifier) = when (tab) {
    GearTab.HOME  -> GaugeGlyph(tint, modifier)
    GearTab.GAMES -> CartridgeGlyph(tint, modifier)
    GearTab.HUD   -> RailGlyph(tint, modifier)
    GearTab.TOOLS -> CaliperGlyph(tint, modifier)
}

/* ── §3.11 GearSelector ── */
@Composable
fun GearSelector(
    selected: GearTab,
    onSelect: (GearTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val tabs = GearTab.entries
    Column(modifier) {
        HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
        BoxWithConstraints(
            Modifier.fillMaxWidth().background(Iron.Anvil900).navigationBarsPadding().height(64.dp)
        ) {
            val w = maxWidth / tabs.size
            val indX by animateDpAsState(
                w * tabs.indexOf(selected) + (w - 44.dp) / 2, IronMotion.block(), label = "gearInd")
            Box(Modifier.offset(x = indX, y = 58.dp).size(44.dp, 4.dp).background(Iron.Brass400))
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { clack.tick(); onSelect(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.graphicsLayer { translationY = if (active) -1.dp.toPx() else 0f }) {
                                GearTabGlyph(tab, if (active) Iron.Bone100 else Iron.Bone500)
                            }
                            AnimatedVisibility(active, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                                Text(tab.label, style = IronType.MonoSm, color = Iron.Bone300)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── §3.12 BridgePlate ── */
@Composable
fun BridgePlate(
    backendName: String,
    backendLed: LedState,
    onBackendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Iron.Anvil900)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screw()
        Spacer(Modifier.width(10.dp))
        Column {
            RisoText("APEXCORE", IronType.Label.copy(fontSize = 14.sp))
            Text("MK·II", style = IronType.MonoSm, color = Iron.Bone500)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(IronShape.Slot)
                .border(1.dp, Iron.Anvil600, IronShape.Slot)
                .clickable(onClick = onBackendClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LedDot(backendLed)
            Spacer(Modifier.width(6.dp))
            Text(backendName, style = IronType.MonoSm, color = Iron.Bone300)
            Spacer(Modifier.width(4.dp))
            Text("▾", style = IronType.MonoSm, color = Iron.Bone500)
        }
        Spacer(Modifier.width(10.dp))
        Screw()
    }
}

/* ── §7.3 Tab transition: horizontal slide + fade, 240ms, ease.wind ── */
@Composable
fun GearTabTransition(targetState: GearTab, content: @Composable (GearTab) -> Unit) {
    AnimatedContent(
        targetState,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(tween(240, easing = IronMotion.EaseWind)) { it / 4 * dir } +
             fadeIn(tween(240))) togetherWith
            (slideOutHorizontally(tween(240, easing = IronMotion.EaseWind)) { -it / 4 * dir } +
             fadeOut(tween(180)))
        },
        label = "gearTabs"
    ) { tab -> content(tab) }
}

enum class IronSlot { NONE, TUNE, PRESSURE, LEDGER }

@Composable
fun IronShell(
    finish: IronFinish,
    tab: GearTab, onTab: (GearTab) -> Unit,
    backendName: String, backendLed: LedState, onBackend: () -> Unit,
    slot: IronSlot, onSlot: (IronSlot) -> Unit,
    home: @Composable () -> Unit,
    games: @Composable () -> Unit,
    optics: @Composable () -> Unit,
    toolbox: @Composable () -> Unit,
    slotContent: @Composable (IronSlot) -> Unit,
    replayOverlay: (@Composable () -> Unit)? = null,
    backendSheet: (@Composable () -> Unit)? = null,
) {
    Crossfade(finish, animationSpec = tween(220), label = "finish") { f ->
        CompositionLocalProvider(LocalIronFinish provides f) {
            Box(Modifier.fillMaxSize().background(ironSkin().canvas).ironGrain(0.04f)) {
                Column(Modifier.fillMaxSize()) {
                    BridgePlate(backendName, backendLed, onBackend)
                    Box(Modifier.weight(1f)) {
                        GearTabTransition(tab) { t ->
                            when (t) {
                                GearTab.HOME -> home()
                                GearTab.GAMES -> games()
                                GearTab.HUD -> optics()
                                GearTab.TOOLS -> toolbox()
                            }
                        }
                    }
                    GearSelector(tab, onTab)
                }
                FullScreenSlot(visible = slot != IronSlot.NONE, onDismiss = { onSlot(IronSlot.NONE) }) {
                    slotContent(slot)
                }
                replayOverlay?.invoke()
                backendSheet?.invoke()
            }
        }
    }
}

/** Slide-up slot with predictive-back scrub of the slide-down (§6.1). */
@Composable
fun FullScreenSlot(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { scrub = it.progress }
            onDismiss()
        } catch (_: Throwable) {
            scrub = 0f
        }
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(260, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = scrub * size.height * 0.25f
                    val s = 1f - 0.04f * scrub
                    scaleX = s
                    scaleY = s
                }
                .background(ironSkin().canvas).ironGrain(0.04f)
        ) { content() }
    }
}

/** §3.12 — Bridge chip opens this: same readiness data as Settings. */
@Composable
fun BackendBenchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    shizuku: KeyStatus, root: KeyStatus,
    preferred: BackendChoice?,
    onUse: (BackendChoice) -> Unit,
    onConfigure: (BackendChoice) -> Unit,
) {
    val clack = rememberClack()
    BenchSheet(visible = visible, onDismiss = onDismiss) {
        StampLabel("SYSTEM ACCESS", StampInk.Signal, slam = true)
        Spacer(Modifier.height(16.dp))
        BackendRow("SHIZUKU", shizuku, preferred == BackendChoice.SHIZUKU,
            { onUse(BackendChoice.SHIZUKU) }, { onConfigure(BackendChoice.SHIZUKU) })
        Spacer(Modifier.height(10.dp))
        BackendRow("ROOT", root, preferred == BackendChoice.ROOT,
            { onUse(BackendChoice.ROOT) }, { onConfigure(BackendChoice.ROOT) })
    }
}

@Composable
private fun BackendRow(
    name: String, s: KeyStatus, isPreferred: Boolean,
    onUse: () -> Unit, onConfigure: () -> Unit
) {
    val clack = rememberClack()
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        LedDot(when { s.ready -> LedState.READY; s.checking -> LedState.CHECKING; else -> LedState.BLOCKED })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = IronType.Label, color = Iron.Bone100)
                if (isPreferred) {
                    Spacer(Modifier.width(8.dp))
                    StampLabel("PREFERRED", StampInk.Brass, slam = false)
                }
            }
            Text(s.statusLine, style = IronType.MonoSm, color = Iron.Bone500)
        }
        ChamferButton(
            text = when { s.checking -> "…"; s.ready -> "USE"; else -> "SETUP" },
            onClick = { clack.row(); if (s.ready) onUse() else onConfigure() },
            enabled = !s.checking, tall = false,
            variant = if (s.ready) ChamferVariant.Primary else ChamferVariant.Outline,
        )
    }
}
