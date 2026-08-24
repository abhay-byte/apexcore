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

@Composable
fun GearSelector(
    selected: GearTab,
    onSelect: (GearTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val tabs = GearTab.entries
    val skin = ironSkin()
    val isPaper = skin.isPaper
    // Vellum = white bar for explicit user request; Graphite = dark anvil
    val barBg = if (isPaper) Color.White else Iron.Anvil900
    val dividerColor = if (isPaper) skin.hairline else Iron.Anvil600
    Column(modifier) {
        HorizontalDivider(color = dividerColor, thickness = 1.dp)
        BoxWithConstraints(
            Modifier.fillMaxWidth().background(barBg).navigationBarsPadding().height(64.dp)
        ) {
            val w = maxWidth / tabs.size
            val indX by animateDpAsState(
                w * tabs.indexOf(selected) + (w - 44.dp) / 2, IronMotion.block(), label = "gearInd")
            Box(Modifier.offset(x = indX, y = 58.dp).size(44.dp, 4.dp).background(Iron.Brass400))
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val glyphTint = when {
                        isPaper && active -> Iron.Ink900
                        isPaper -> Iron.Ink600
                        active -> Iron.Bone100
                        else -> Iron.Bone500
                    }
                    val labelColor = if (isPaper) Iron.Ink600 else Iron.Bone300
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { clack.tick(); onSelect(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.graphicsLayer { translationY = if (active) -1.dp.toPx() else 0f }) {
                                GearTabGlyph(tab, glyphTint)
                            }
                            AnimatedVisibility(active, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                                Text(tab.label, style = IronType.MonoSm, color = labelColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BridgePlate(
    backendName: String,
    backendLed: LedState,
    onBackendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = ironSkin()
    val isPaper = skin.isPaper
    val barBg = if (isPaper) Color.White else Iron.Anvil900
    Row(
        modifier
            .fillMaxWidth()
            .background(barBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screw()
        Spacer(Modifier.width(10.dp))
        Column {
            if (isPaper) {
                Text("APEXCORE", style = IronType.Label.copy(fontSize = 14.sp), color = Iron.Ink900)
            } else {
                EngravedText("APEXCORE", IronType.Label.copy(fontSize = 14.sp))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(IronShape.Slot)
                .border(1.dp, if (isPaper) skin.hairline else Iron.Anvil600, IronShape.Slot)
                .clickable(onClick = onBackendClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LedDot(backendLed)
            Spacer(Modifier.width(6.dp))
            Text(backendName, style = IronType.MonoSm, color = if (isPaper) Iron.Ink600 else Iron.Bone300)
            Spacer(Modifier.width(4.dp))
            Text("▾", style = IronType.MonoSm, color = if (isPaper) Iron.Ink600 else Iron.Bone500)
        }
        Spacer(Modifier.width(10.dp))
        Screw()
    }
}

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
    val gate = rememberCeremonyGate()
    val canvas by animateColorAsState(ironSkin().canvas, tween(220), label = "finishCanvas")

    // NOTE: status/nav bars are owned by MainScreen (single writer) — a second writer
    // here kept re-darkening icons on Graphite after slot/sheet state churn.

    CompositionLocalProvider(LocalCeremonyGate provides gate) {
        Box(Modifier.fillMaxSize().background(canvas).ironGrain(0.04f)) {
            Column(Modifier.fillMaxSize()) {
                BridgePlate(backendName, backendLed, onBackend)
                Box(Modifier.weight(1f)) {
                    if (replayOverlay == null) {
                        GearTabTransition(tab) { t ->
                            when (t) {
                                GearTab.HOME -> home()
                                GearTab.GAMES -> games()
                                GearTab.HUD -> optics()
                                GearTab.TOOLS -> toolbox()
                            }
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
                .background(ironSkin().canvas)
        ) { content() }
    }
}

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
                Text(name, style = IronType.Label, color = Iron.Bone100, maxLines = 1, softWrap = false)
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
