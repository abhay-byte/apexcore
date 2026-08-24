package com.ivarna.apexcore.ui.iron.ram

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.flow.drop

enum class PressurePhase { IDLE, PREFREEZE, FILLING, HOLDING, RELEASING, DONE }
private val runningPhases = setOf(
    PressurePhase.PREFREEZE, PressurePhase.FILLING,
    PressurePhase.HOLDING, PressurePhase.RELEASING
)

data class RamModeUi(val name: String, val ready: Boolean)

data class PressureUiState(
    val phase: PressurePhase = PressurePhase.IDLE,
    val ramUsedMb: Int = 0, val ramTotalMb: Int = 1,
    val swapUsedMb: Int = 0, val swapTotalMb: Int = 1,
    val resultGb: Float? = null,
)

@Composable
fun PressureRoom(
    state: PressureUiState,
    modes: List<RamModeUi>,
    selectedMode: RamModeUi?,
    preFreeze: Boolean,
    onMode: (RamModeUi) -> Unit,
    onPreFreeze: (Boolean) -> Unit,
    onStart: () -> Unit,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val skin = ironSkin()
    val running = state.phase in runningPhases

    val view = LocalView.current
    DisposableEffect(running) {
        val window = view.context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, running) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && running) onCancel()
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            if (running) onCancel()
        }
    }

    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = running) { progress ->
        try {
            progress.collect { scrub = it.progress }
            onCancel()
        } catch (_: Throwable) {
            scrub = 0f
        }
    }

    var modeMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BackArrow(skin.textDim, onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                "PRESSURE ROOM", style = IronType.Display.copy(fontSize = 14.sp, letterSpacing = 0.15.sp), color = skin.text,
                modifier = Modifier.weight(1f),
                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Box {
                Row(
                    Modifier
                        .clip(IronShape.Slot)
                        .border(1.dp, Iron.Anvil600, IronShape.Slot)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            clack.row()
                            modeMenu = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedDot(if (selectedMode?.ready == true) LedState.READY else LedState.BLOCKED)
                    Spacer(Modifier.width(6.dp))
                    Text(selectedMode?.name ?: "MODE", style = IronType.MonoSm, color = skin.textDim)
                    Text("  ▾", style = IronType.MonoSm, color = skin.textDim)
                }
                if (modeMenu) {
                    IronDropdown(onDismiss = { modeMenu = false }) {
                        modes.forEach { m ->
                            DropdownLedRow(m.name, m.ready, m == selectedMode) {
                                if (m.ready) onMode(m) else clack.no()
                                modeMenu = false
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TubeManometer(
            ramFraction = state.ramUsedMb.toFloat() / state.ramTotalMb.coerceAtLeast(1),
            swapFraction = state.swapUsedMb.toFloat() / state.swapTotalMb.coerceAtLeast(1),
            ramText = "%d / %d MB".format(state.ramUsedMb, state.ramTotalMb),
            swapText = "%d / %d MB".format(state.swapUsedMb, state.swapTotalMb),
            energized = true,
        )

        Spacer(Modifier.height(6.dp))

        EngravedText("STATE RAILWAY", IronType.Label, color = skin.textDim)
        Spacer(Modifier.height(8.dp))
        StateRailway(state.phase)

        AnimatedVisibility(
            state.phase == PressurePhase.DONE,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160))
        ) {
            state.resultGb?.let {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RESULT", style = IronType.MonoSm, color = skin.textDim)
                    Spacer(Modifier.width(10.dp))
                    OdometerCounter("+%.1f".format(it), style = IronType.Mono.copy(fontSize = 26.sp))
                    Text(" GB RECLAIMED", style = IronType.Mono, color = ironSkin().phosphor())
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRE-FREEZE BEFORE FILL", style = IronType.Label, color = skin.text)
                Text("Purge user apps before pressure test", style = IronType.Caption, color = skin.textDim)
            }
            MachinedToggle(preFreeze, onPreFreeze)
        }

        Spacer(Modifier.height(12.dp))

        val (label, primary, action) = when (state.phase) {
            PressurePhase.IDLE, PressurePhase.DONE -> Triple("START PRESSURE TEST", true, onStart)
            PressurePhase.PREFREEZE, PressurePhase.FILLING -> Triple("HOLD", true, onHold)
            PressurePhase.HOLDING -> Triple("RELEASE", false, onRelease)
            PressurePhase.RELEASING -> Triple("CANCEL", false, onCancel)
        }
        ChamferButton(
            label, action,
            variant = if (primary) ChamferVariant.Primary else ChamferVariant.Outline,
            busy = state.phase == PressurePhase.FILLING,
            modifier = Modifier.fillMaxWidth(),
        )

        SerialFooter(8, "PRESSURE", serial)
    }

    if (scrub > 0.01f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PaperPlate(Modifier.alpha(scrub)) {
                RisoText("END SESSION?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900)
                Text("Release to cancel the pressure test.", style = IronType.Caption, color = Iron.Ink600)
            }
        }
    }
}

@Composable
fun TubeManometer(
    ramFraction: Float,
    swapFraction: Float,
    ramText: String,
    swapText: String,
    energized: Boolean
) {
    val ramLvl by animateFloatAsState(ramFraction.coerceIn(0f, 1f), IronMotion.needle(), label = "ramL")
    val swapLvl by animateFloatAsState(swapFraction.coerceIn(0f, 1f), IronMotion.needle(), label = "swapL")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 268.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Tube(196.dp, ramLvl, "RAM", ramText, energized)
        Spacer(Modifier.width(40.dp))
        Tube(124.dp, swapLvl, "SWAP", swapText, energized)
    }
}

@Composable
private fun Tube(tubeH: Dp, level: Float, label: String, valueText: String, energized: Boolean) {
    val measurer = rememberTextMeasurer()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.width(72.dp).height(tubeH).drawWithCache {
            val w = 12.dp.toPx()
            val x = (this.size.width - w) / 2f + 8.dp.toPx()
            val pct = listOf(0f, 50f, 100f).map {
                measurer.measure(
                    if (it == 100f) "1.0" else ".${it.toInt()}",
                    TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 8.sp, color = Iron.Bone500)
                )
            }
            onDrawBehind {
                val lvl = level * (this.size.height - 4.dp.toPx())
                drawRoundRect(
                    Iron.Anvil600, Offset(x - 1f, 0f),
                    Size(w + 2f, this.size.height), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx())
                )
                drawRoundRect(
                    if (energized) Iron.Signal500 else Iron.Anvil500,
                    Offset(x, this.size.height - lvl), Size(w, lvl), CornerRadius(2.dp.toPx())
                )
                (0..10).forEach { m ->
                    val y = this.size.height * (1f - m / 10f)
                    drawLine(Iron.Anvil500, Offset(x - 7.dp.toPx(), y), Offset(x - 2.dp.toPx(), y), 1.dp.toPx())
                }
                drawText(pct[0], topLeft = Offset(x - 30.dp.toPx(), this.size.height - 6.dp.toPx()))
                drawText(pct[1], topLeft = Offset(x - 30.dp.toPx(), this.size.height / 2f - 6.dp.toPx()))
                drawText(pct[2], topLeft = Offset(x - 34.dp.toPx(), 0f))

                val y = this.size.height - lvl
                drawLine(Iron.Brass400, Offset(x + w, y), Offset(x + w + 9.dp.toPx(), y), 2.dp.toPx())
                drawPath(Path().apply {
                    moveTo(x + w + 9.dp.toPx(), y)
                    lineTo(x + w + 17.dp.toPx(), y - 5.dp.toPx())
                    lineTo(x + w + 9.dp.toPx(), y - 10.dp.toPx())
                    close()
                }, Iron.Brass400)
            }
        }) {}
        Spacer(Modifier.height(6.dp))
        Text(
            valueText,
            style = IronType.Mono.copy(fontSize = 13.sp),
            color = Iron.Bone100,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val railLabels = listOf("PRE", "FILL", "HOLD", "REL", "DONE")

@Composable
fun StateRailway(phase: PressurePhase) {
    val clack = rememberClack()
    val phosphor = ironSkin().phosphor()
    val idx = when (phase) {
        PressurePhase.IDLE -> -1
        PressurePhase.PREFREEZE -> 0
        PressurePhase.FILLING -> 1
        PressurePhase.HOLDING -> 2
        PressurePhase.RELEASING -> 3
        PressurePhase.DONE -> 4
    }

    LaunchedEffect(Unit) {
        snapshotFlow { idx }.drop(1).collect { if (it >= 0) clack.tick() }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STATE", style = IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                idx,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                },
                label = "railState"
            ) { i ->
                Text(
                    if (i < 0) "STANDBY" else railLabels[i], style = IronType.Mono,
                    color = if (i < 0) Iron.Bone500 else phosphor
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
            val segW = maxWidth / 5
            Canvas(Modifier.fillMaxSize()) {
                val segWpx = this.size.width / 5f
                val cy = 20.dp.toPx()
                drawLine(Iron.Anvil600, Offset(segWpx * 0.5f, cy), Offset(segWpx * 4.5f, cy), 2.dp.toPx())
                repeat(5) { i ->
                    val cx = segWpx * (i + 0.5f)
                    when {
                        idx > i -> drawCircle(phosphor, 5.dp.toPx(), Offset(cx, cy))
                        idx == i -> {
                            drawCircle(phosphor, 5.dp.toPx(), Offset(cx, cy))
                            drawCircle(phosphor.copy(alpha = 0.5f), 8.dp.toPx(),
                                Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                        }
                        else -> drawCircle(Iron.Anvil500, 5.dp.toPx(), Offset(cx, cy),
                            style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            val carriageX by animateDpAsState(
                if (idx < 0) -20.dp else segW * (idx + 0.5f) - 7.dp,
                IronMotion.drawer(), label = "carriage"
            )
            Box(
                Modifier
                    .offset(x = carriageX.coerceAtLeast(0.dp), y = 15.dp)
                    .size(14.dp, 10.dp)
                    .clip(IronShape.Slot)
                    .background(Iron.Brass400)
            )
            Row(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                repeat(5) { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            railLabels[i],
                            style = IronType.MonoSm,
                            color = when {
                                i == idx -> Iron.Bone100
                                idx > i -> phosphor
                                else -> Iron.Bone500
                            }
                        )
                    }
                }
            }
        }
    }
}
