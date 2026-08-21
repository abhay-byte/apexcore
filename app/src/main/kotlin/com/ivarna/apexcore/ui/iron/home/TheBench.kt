package com.ivarna.apexcore.ui.iron.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.delay

@Composable
fun TheBench(
    ui: BenchViewModel.Ui,
    onBoost: () -> Unit,
    onTune: () -> Unit,
    onPins: () -> Unit,
    onRamFree: () -> Unit,
    onSetup: () -> Unit,
    toast: StampToastState,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val ceremonyGate = rememberCeremonyGate()

    var phase by remember { mutableStateOf(BenchPhase.IDLE) }
    var workOrder by remember { mutableStateOf<WorkOrderData?>(null) }
    var purgeTick by remember { mutableIntStateOf(0) }
    var windUp by remember { mutableFloatStateOf(0f) }
    var stampText by remember { mutableStateOf<String?>(null) }
    var odometerText by remember { mutableStateOf<String?>(null) }
    val shavings = remember { ShavingsState() }

    val flightP = remember { Animatable(1f) }
    var flying by remember { mutableStateOf(false) }
    var slotCenter by remember { mutableStateOf(Offset.Zero) }
    var centerScreen by remember { mutableStateOf(Offset.Zero) }

    var dialCenter by remember { mutableStateOf(Offset.Zero) }
    var dialRadius by remember { mutableFloatStateOf(0f) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    val ramFraction = ui.mem.ramUsedMb.toFloat() / ui.mem.ramTotalMb.toFloat()
    val swapFraction = ui.mem.swapUsedMb.toFloat() / ui.mem.swapTotalMb.toFloat()
    val ramUsedGb = ui.mem.ramUsedMb / 1024f
    val ramTotalGb = ui.mem.ramTotalMb / 1024f

    fun purge() {
        if (!ui.elevated) {
            clack.no()
            return
        }
        phase = BenchPhase.BOOSTING
        onBoost()
        purgeTick++
    }

    LaunchedEffect(purgeTick) {
        if (purgeTick == 0) return@LaunchedEffect
        ceremonyGate.run {
            stampText = null
            odometerText = null
            windUp = 0.12f
            delay(180)
            clack.thud()
            shavings.burst(
                dialCenter.x - containerOrigin.x,
                dialCenter.y - containerOrigin.y,
                dialRadius * 0.8f, count = 160, speed = with(density) { 900.dp.toPx() }
            )
            stampText = "FROZEN ${ui.lastOrder?.apps ?: ""}"
            windUp = 0f
            delay(220)
            odometerText = "+%.1f GB".format(ui.lastOrder?.freedGb ?: 0f)
            delay(650)

            flying = true
            odometerText = null
            flightP.snapTo(0f)
            flightP.animateTo(1f, tween(320, easing = IronMotion.EaseWind))
            phase = BenchPhase.RESULT
            workOrder = ui.lastOrder
            flying = false
            clack.purgeDone()
        }
    }

    val thresholdPx = with(density) { 120.dp.toPx() }
    val pullConnection = remember(clack) {
        object : NestedScrollConnection {
            var pull by mutableFloatStateOf(0f)
            private var armed = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.3f)
                    if (pull >= thresholdPx && !armed) {
                        armed = true
                        clack.tick()
                    }
                    windUp = (pull / thresholdPx).coerceIn(0f, 1f) * 0.12f
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val past = pull >= thresholdPx
                pull = 0f
                armed = false
                windUp = 0f
                if (past) purge()
                return if (past) available else Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                centerScreen = Offset(it.size.width / 2f, it.size.height / 3f)
            }
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullConnection)
                .onGloballyPositioned { containerOrigin = it.positionInRoot() },
            contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val (txt, led) = when {
                    phase == BenchPhase.BOOSTING -> "PURGING BACKGROUND PROCESSES…" to LedState.LIVE
                    !ui.elevated -> "CONNECT SHIZUKU OR ROOT FOR DEEP FREEZE" to LedState.CHECKING
                    workOrder != null && workOrder!!.apps > 0 ->
                        "FROZEN ${workOrder!!.apps} APPS · FREED %.1f GB".format(workOrder!!.freedGb) to LedState.READY
                    workOrder != null -> "ALREADY OPTIMIZED" to LedState.READY
                    else -> "READY TO PURGE BLOAT" to LedState.READY
                }
                TickerLine(txt, led)
            }

            if (!ui.elevated) item {
                Spacer(Modifier.height(8.dp))
                ElevationSlip(
                    visible = true,
                    shake = false,
                    onShizuku = onSetup,
                    onRoot = onSetup
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                Box {
                    InstrumentDial(
                        value = ramFraction,
                        energized = ui.elevated,
                        freedFraction = ui.freedFraction,
                        boosting = phase == BenchPhase.BOOSTING,
                        over = windUp,
                        label = "RAM",
                        valueText = "%.1f / %.1f GB".format(ramUsedGb, ramTotalGb),
                        onLongPress = {
                            clipboard.setText(AnnotatedString("RAM ${ui.mem.ramUsedMb}/${ui.mem.ramTotalMb}MB · SWAP ${ui.mem.swapUsedMb}/${ui.mem.swapTotalMb}MB"))
                            clack.confirm()
                            toast.show("COPIED")
                        },
                        modifier = Modifier.onGloballyPositioned {
                            dialCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                            dialRadius = it.size.width / 2f
                        }
                    )
                    ShavingsLayer(shavings, Modifier.matchParentSize())
                    stampText?.let {
                        Box(Modifier.align(Alignment.Center)) { StampLabel(it, StampInk.Phosphor) }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InstrumentDial(
                        value = swapFraction,
                        energized = ui.elevated,
                        diameter = 96.dp,
                        numerals = false,
                        ignition = false,
                        label = "SWAP",
                        valueText = "${ui.mem.swapUsedMb} / ${ui.mem.swapTotalMb} MB"
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                FlipCard(
                    flipped = phase == BenchPhase.RESULT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            slotCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                        },
                    front = {
                        ChamferButton(
                            text = if (phase == BenchPhase.BOOSTING) "PURGING…" else "BOOST · DEEP FREEZE",
                            onClick = { if (phase == BenchPhase.IDLE) purge() },
                            busy = phase == BenchPhase.BOOSTING,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                    },
                    back = {
                        workOrder?.let { wo ->
                            WorkOrderCard(wo) { phase = BenchPhase.IDLE }
                        }
                    }
                )
            }

            if (ui.elevated) item {
                Spacer(Modifier.height(12.dp))
                ToolRow("Game optimisation", "Kernel & session parameters",
                    { GaugeGlyph(Iron.Bone300) }, onTune)
            }
            item {
                Spacer(Modifier.height(8.dp))
                ToolRow("Pin Apps", "Protect apps from being frozen",
                    { LoupeGlyph(Iron.Bone300) }, onPins)
            }
            item {
                Spacer(Modifier.height(8.dp))
                ToolRow("Pressure Room", "Force safe RAM reclaim",
                    { RailGlyph(Iron.Bone300) }, onRamFree)
            }

            item {
                Spacer(Modifier.height(16.dp))
                ThermometerStrip(batteryC = ui.batteryC, cpuC = ui.cpuC)
            }
            item {
                SerialFooter(1, "HOME", serial)
            }
        }

        if (flying) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "+%.1f GB".format(ui.lastOrder?.freedGb ?: 0f),
                    style = IronType.MonoLg, color = Iron.Bone100,
                    modifier = Modifier.graphicsLayer {
                        val p = flightP.value
                        translationX = centerScreen.x + (slotCenter.x - centerScreen.x) * p - size.width / 2f
                        translationY = centerScreen.y + (slotCenter.y - centerScreen.y) * p - size.height / 2f
                        val s = 1f - 0.62f * p
                        scaleX = s
                        scaleY = s
                        alpha = 1f - p * p
                    }
                )
            }
        } else if (odometerText != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OdometerCounter(odometerText!!, onSettled = { clack.off() })
            }
        }

        StampToastHost(state = toast)
    }
}

@Composable
fun WorkOrderCard(wo: WorkOrderData, onTap: () -> Unit) {
    PaperPlate(
        deckleTop = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RisoText("PURGE COMPLETE", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900,
                modifier = Modifier.weight(1f))
            StampLabel("FROZEN ${wo.apps}", StampInk.Phosphor)
        }
        Spacer(Modifier.height(14.dp))
        StatRow("FREED SIZE", "+%.1f GB".format(wo.freedGb),
            sub = "RAM +%.1f · SWAP +%.1f".format(wo.freedRamGb, wo.freedSwapGb))
        Spacer(Modifier.height(6.dp))
        StatRow("PURGED APPS", "${wo.apps}")
        Spacer(Modifier.height(6.dp))
        StatRow("DURATION", "%.1f S".format(wo.durationS))
        Spacer(Modifier.height(6.dp))
        StatRow("SKIPPED", "${wo.skipped}", sub = if (wo.failed > 0) "${wo.failed} FAILED" else null)
        Spacer(Modifier.height(14.dp))
        ChamferButton("PURGE AGAIN", onTap, Modifier.fillMaxWidth(), tall = false)
    }
}
