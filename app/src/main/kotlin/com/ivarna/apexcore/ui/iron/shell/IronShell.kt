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
import com.ivarna.apexcore.ui.iron.window.IronFold
import com.ivarna.apexcore.ui.iron.window.IronFormFactor
import com.ivarna.apexcore.ui.iron.window.LocalIronFold
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow

enum class GearTab(val label: String) { HOME("HOME"), GAMES("GAMES"), HUD("HUD"), TOOLS("TOOLS") }

@Composable
fun GearTabGlyph(tab: GearTab, tint: Color, modifier: Modifier = Modifier) = when (tab) {
    GearTab.HOME -> GaugeGlyph(tint, modifier)
    GearTab.GAMES -> CartridgeGlyph(tint, modifier)
    GearTab.HUD -> RailGlyph(tint, modifier)
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

/** Vertical nav: brass indicator slides along the groove wall. */
@Composable
fun GearRail(
    selected: GearTab,
    onSelect: (GearTab) -> Unit,
    modifier: Modifier = Modifier,
    labels: Boolean = false,
) {
    val clack = rememberClack()
    val tabs = GearTab.entries
    val skin = ironSkin()
    val isPaper = skin.isPaper
    val railW = if (labels) 84.dp else 64.dp
    val barBg = if (isPaper) Color.White else Iron.Anvil900
    val groove = if (isPaper) skin.hairline else Iron.Anvil600

    Box(
        modifier
            .fillMaxHeight()
            .width(railW)
            .background(barBg)
            .ironGrain(0.04f)
    ) {
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(1.dp).background(groove))

        BoxWithConstraints(Modifier.fillMaxHeight().padding(end = 8.dp)) {
            val slot = maxHeight / tabs.size
            val indY by animateDpAsState(
                slot * tabs.indexOf(selected) + (slot - 44.dp) / 2,
                IronMotion.block(),
                label = "railInd",
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = indY)
                    .size(4.dp, 44.dp)
                    .background(Iron.Brass400)
            )
        }

        Column(Modifier.fillMaxHeight().padding(end = 10.dp)) {
            tabs.forEach { tab ->
                val active = tab == selected
                val glyphTint = when {
                    isPaper && active -> Iron.Ink900
                    isPaper -> Iron.Ink600
                    active -> Iron.Bone100
                    else -> Iron.Bone500
                }
                val labelColor = when {
                    isPaper && active -> Iron.Ink900
                    isPaper -> Iron.Ink600
                    active -> Iron.Bone300
                    else -> Iron.Bone500
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickableNoIndication { clack.tick(); onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.graphicsLayer { translationY = if (active) -1.dp.toPx() else 0f }) {
                            GearTabGlyph(tab, glyphTint)
                        }
                        if (labels) {
                            Spacer(Modifier.height(4.dp))
                            Text(tab.label, style = IronType.MonoSm, color = labelColor)
                        }
                    }
                }
            }
        }
    }
}

/** 1dp hairline + optional brass seam pin. */
@Composable
fun IronSeamColumn(brass: Boolean, modifier: Modifier = Modifier) {
    val hairline = ironSkin().hairline
    Box(modifier.fillMaxHeight().width(3.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(hairline))
        if (brass) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(3.dp, 44.dp)
                    .background(Iron.Brass400)
            )
        }
    }
}

@Composable
fun BridgePlate(
    backendName: String,
    backendLed: LedState,
    onBackendClick: () -> Unit,
    modifier: Modifier = Modifier,
    serial: String = "",
    compact: Boolean = false,
    verbose: Boolean = false,
) {
    val skin = ironSkin()
    val isPaper = skin.isPaper
    val barBg = if (isPaper) Color.White else Iron.Anvil900
    val titleColor = if (isPaper) Iron.Ink900 else Iron.Bone100
    val dimColor = if (isPaper) Iron.Ink600 else Iron.Bone500
    val chipColor = if (isPaper) Iron.Ink600 else Iron.Bone300
    Row(
        modifier
            .fillMaxWidth()
            .background(barBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .padding(vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Screw()
        Spacer(Modifier.width(10.dp))
        Column {
            if (isPaper) {
                Text("APEXCORE", style = IronType.Label.copy(fontSize = 14.sp), color = titleColor)
            } else {
                EngravedText("APEXCORE", IronType.Label.copy(fontSize = 14.sp))
            }
            if (!compact) {
                Text("MK·II", style = IronType.MonoSm, color = dimColor)
            }
        }
        if (verbose && serial.isNotEmpty()) {
            Spacer(Modifier.width(14.dp))
            Text("S/N $serial", style = IronType.MonoSm, color = dimColor)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(IronShape.Slot)
                .border(1.dp, if (isPaper) skin.hairline else Iron.Anvil600, IronShape.Slot)
                .clickableNoIndication(onBackendClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LedDot(backendLed)
            Spacer(Modifier.width(6.dp))
            Text(backendName, style = IronType.MonoSm, color = chipColor)
            Spacer(Modifier.width(4.dp))
            Text("▾", style = IronType.MonoSm, color = dimColor)
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
        label = "gearTabs",
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
    shutterOverlay: (@Composable () -> Unit)? = null,
    serial: String? = null,
) {
    val gate = rememberCeremonyGate()
    val win = LocalIronWindow.current
    val bridgeSerial = serial ?: rememberSerial()
    val canvas by animateColorAsState(ironSkin().canvas, tween(220), label = "finishCanvas")

    CompositionLocalProvider(LocalCeremonyGate provides gate) {
        Box(Modifier.fillMaxSize().background(canvas).ironGrain(0.04f)) {
            if (win.form == IronFormFactor.PHONE) {
                Column(Modifier.fillMaxSize()) {
                    BridgePlate(backendName, backendLed, onBackend, serial = bridgeSerial)
                    Box(Modifier.weight(1f)) {
                        if (replayOverlay == null) {
                            TabHost(tab, home, games, optics, toolbox, phoneFrameHome = true)
                        }
                    }
                    GearSelector(tab, onTab)
                }
            } else {
                val railW = if (win.railWithLabels) 84.dp else 64.dp
                Row(Modifier.fillMaxSize()) {
                    GearRail(
                        selected = tab,
                        onSelect = onTab,
                        modifier = Modifier.navigationBarsPadding(),
                        labels = win.railWithLabels,
                    )
                    val fold = LocalIronFold.current
                    val contentFold = if (fold is IronFold.Seam && fold.x != null) {
                        fold.copy(x = fold.x!! - railW)
                    } else {
                        fold
                    }
                    CompositionLocalProvider(LocalIronFold provides contentFold) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            BridgePlate(
                                backendName = backendName,
                                backendLed = backendLed,
                                onBackendClick = onBackend,
                                serial = bridgeSerial,
                                compact = win.form == IronFormFactor.LANDSCAPE,
                                verbose = win.split,
                            )
                            Box(Modifier.weight(1f).imePadding()) {
                                if (replayOverlay == null) {
                                    TabHost(tab, home, games, optics, toolbox, phoneFrameHome = false)
                                }
                            }
                        }
                    }
                }
            }

            FullScreenSlot(visible = slot != IronSlot.NONE, onDismiss = { onSlot(IronSlot.NONE) }) {
                slotContent(slot)
            }
            replayOverlay?.invoke()
            backendSheet?.invoke()
            shutterOverlay?.invoke()
        }
    }
}

@Composable
private fun TabHost(
    tab: GearTab,
    home: @Composable () -> Unit,
    games: @Composable () -> Unit,
    optics: @Composable () -> Unit,
    toolbox: @Composable () -> Unit,
    phoneFrameHome: Boolean,
) {
    GearTabTransition(tab) { t ->
        when (t) {
            GearTab.HOME -> if (phoneFrameHome) IronContentFrame { home() } else home()
            GearTab.GAMES -> games()
            GearTab.HUD -> optics()
            GearTab.TOOLS -> toolbox()
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
            Modifier
                .fillMaxSize()
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
    onUse: () -> Unit, onConfigure: () -> Unit,
) {
    val clack = rememberClack()
    val skin = ironSkin()
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        LedDot(when {
            s.ready -> LedState.READY
            s.checking -> LedState.CHECKING
            else -> LedState.BLOCKED
        })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = IronType.Label, color = skin.text, maxLines = 1, softWrap = false)
                if (isPreferred) {
                    Spacer(Modifier.width(8.dp))
                    StampLabel("PREFERRED", StampInk.Brass, slam = false)
                }
            }
            Text(s.statusLine, style = IronType.MonoSm, color = skin.textDim)
        }
        ChamferButton(
            text = when {
                s.checking -> "…"
                s.ready -> "USE"
                else -> "SETUP"
            },
            onClick = { clack.row(); if (s.ready) onUse() else onConfigure() },
            enabled = !s.checking, tall = false,
            variant = if (s.ready) ChamferVariant.Primary else ChamferVariant.Outline,
        )
    }
}
