package com.ivarna.apexcore.ui.iron.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.iron.shell.IronSeamColumn
import com.ivarna.apexcore.ui.iron.window.IronFold
import com.ivarna.apexcore.ui.iron.window.IronFormFactor
import com.ivarna.apexcore.ui.iron.window.LocalIronFold
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow
import com.ivarna.apexcore.ui.iron.window.dialSizeFor
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
    active: Boolean = true,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val ceremonyGate = LocalCeremonyGate.current
    val reduced = LocalReducedMotion.current
    val skin = ironSkin()
    val win = LocalIronWindow.current
    var tickerWide by rememberSaveable { mutableStateOf(true) }

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

    val ramFraction = ui.mem.ramUsedMb.toFloat() / ui.mem.ramTotalMb.coerceAtLeast(1).toFloat()
    val swapFraction = ui.mem.swapUsedMb.toFloat() / ui.mem.swapTotalMb.coerceAtLeast(1).toFloat()
    val ramUsedGb = ui.mem.ramUsedMb / 1024f
    val ramTotalGb = ui.mem.ramTotalMb / 1024f

    fun purge() {
        if (!active) return
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
            if (reduced) {
                phase = BenchPhase.RESULT
                workOrder = ui.lastOrder
                clack.purgeDone()
                return@run
            }
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

    val (tickTxt, tickLed) = when {
        phase == BenchPhase.BOOSTING -> "PURGING BACKGROUND PROCESSES…" to LedState.LIVE
        !ui.elevated -> "CONNECT SHIZUKU OR ROOT FOR DEEP FREEZE" to LedState.CHECKING
        workOrder != null && workOrder!!.apps > 0 ->
            "FROZEN ${workOrder!!.apps} APPS · FREED %.1f GB".format(workOrder!!.freedGb) to LedState.READY
        workOrder != null -> "ALREADY OPTIMIZED" to LedState.READY
        else -> "READY TO PURGE BLOAT" to LedState.READY
    }

    IronScreen("HOME") {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    centerScreen = Offset(it.size.width / 2f, it.size.height / 3f)
                    containerOrigin = it.positionInRoot()
                }
        ) {
            when (win.form) {
                IronFormFactor.PHONE -> BenchColumn(
                    ui = ui,
                    ramFraction = ramFraction,
                    swapFraction = swapFraction,
                    ramUsedGb = ramUsedGb,
                    ramTotalGb = ramTotalGb,
                    tickTxt = tickTxt,
                    tickLed = tickLed,
                    tickerWide = tickerWide,
                    onToggleTicker = { tickerWide = !tickerWide },
                    phase = phase,
                    stampText = stampText,
                    windUp = windUp,
                    onWindUp = { windUp = it },
                    workOrder = workOrder,
                    active = active,
                    onPurge = ::purge,
                    onReset = { phase = BenchPhase.IDLE },
                    onDialGeometry = { c, r -> dialCenter = c; dialRadius = r },
                    onSlotGeometry = { slotCenter = it },
                    onTune = onTune,
                    onPins = onPins,
                    onRamFree = onRamFree,
                    onSetup = onSetup,
                    clipboard = clipboard,
                    toast = toast,
                    serial = serial,
                    skin = skin,
                    clack = clack,
                )
                IronFormFactor.LANDSCAPE -> BenchLandscape(
                    ui = ui,
                    ramFraction = ramFraction,
                    swapFraction = swapFraction,
                    ramUsedGb = ramUsedGb,
                    ramTotalGb = ramTotalGb,
                    tickTxt = tickTxt,
                    tickLed = tickLed,
                    phase = phase,
                    stampText = stampText,
                    windUp = windUp,
                    workOrder = workOrder,
                    active = active,
                    onPurge = ::purge,
                    onReset = { phase = BenchPhase.IDLE },
                    onDialGeometry = { c, r -> dialCenter = c; dialRadius = r },
                    onSlotGeometry = { slotCenter = it },
                    onTune = onTune,
                    onPins = onPins,
                    onRamFree = onRamFree,
                    onSetup = onSetup,
                    serial = serial,
                    skin = skin,
                )
                IronFormFactor.TABLET -> BenchSplit(
                    ui = ui,
                    ramFraction = ramFraction,
                    swapFraction = swapFraction,
                    ramUsedGb = ramUsedGb,
                    ramTotalGb = ramTotalGb,
                    tickTxt = tickTxt,
                    tickLed = tickLed,
                    phase = phase,
                    stampText = stampText,
                    windUp = windUp,
                    workOrder = workOrder,
                    active = active,
                    onPurge = ::purge,
                    onReset = { phase = BenchPhase.IDLE },
                    onDialGeometry = { c, r -> dialCenter = c; dialRadius = r },
                    onSlotGeometry = { slotCenter = it },
                    onTune = onTune,
                    onPins = onPins,
                    onRamFree = onRamFree,
                    onSetup = onSetup,
                    serial = serial,
                    skin = skin,
                )
            }

            ShavingsLayer(shavings, Modifier.fillMaxSize())

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
}

@Composable
private fun BenchColumn(
    ui: BenchViewModel.Ui,
    ramFraction: Float,
    swapFraction: Float,
    ramUsedGb: Float,
    ramTotalGb: Float,
    tickTxt: String,
    tickLed: LedState,
    tickerWide: Boolean,
    onToggleTicker: () -> Unit,
    phase: BenchPhase,
    stampText: String?,
    windUp: Float,
    onWindUp: (Float) -> Unit,
    workOrder: WorkOrderData?,
    active: Boolean,
    onPurge: () -> Unit,
    onReset: () -> Unit,
    onDialGeometry: (Offset, Float) -> Unit,
    onSlotGeometry: (Offset) -> Unit,
    onTune: () -> Unit,
    onPins: () -> Unit,
    onRamFree: () -> Unit,
    onSetup: () -> Unit,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    toast: StampToastState,
    serial: String,
    skin: IronSkin,
    clack: Clack,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 120.dp.toPx() }
    val pullConnection = remember(clack, active) {
        object : NestedScrollConnection {
            var pull by mutableFloatStateOf(0f)
            private var armed = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!active) return Offset.Zero
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.3f)
                    if (pull >= thresholdPx && !armed) {
                        armed = true
                        clack.tick()
                    }
                    onWindUp((pull / thresholdPx).coerceIn(0f, 1f) * 0.12f)
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val past = pull >= thresholdPx
                pull = 0f
                armed = false
                onWindUp(0f)
                if (past) onPurge()
                return if (past) available else Velocity.Zero
            }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .nestedScroll(pullConnection),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            TickerLine(tickTxt, tickLed, collapsed = !tickerWide, onDoubleTap = onToggleTicker)
        }
        if (!ui.elevated) item {
            Spacer(Modifier.height(8.dp))
            ElevationSlip(visible = true, shake = false, onShizuku = onSetup, onRoot = onSetup)
            Spacer(Modifier.height(16.dp))
        }
        item {
            DialWithStamp(
                diameter = dialSizeFor(IronFormFactor.PHONE),
                value = ramFraction,
                energized = ui.elevated,
                boosting = phase == BenchPhase.BOOSTING,
                freedFraction = ui.freedFraction,
                windUp = windUp,
                active = active,
                valueText = "%.1f / %.1f GB".format(ramUsedGb, ramTotalGb),
                stampText = stampText,
                onGeometry = onDialGeometry,
                onLongPress = {
                    clipboard.setText(
                        AnnotatedString(
                            "RAM ${ui.mem.ramUsedMb}/${ui.mem.ramTotalMb}MB · SWAP ${ui.mem.swapUsedMb}/${ui.mem.swapTotalMb}MB"
                        )
                    )
                    clack.confirm()
                    toast.show("COPIED")
                },
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            MiniSwap(
                swapFraction = swapFraction,
                energized = ui.elevated,
                valueText = "${ui.mem.swapUsedMb} / ${ui.mem.swapTotalMb} MB",
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            BoostSlot(phase, workOrder, onPurge, onReset, Modifier.fillMaxWidth(), onSlotGeometry)
        }
        toolsItems(ui, onTune, onPins, onRamFree, onSetup, serial, skin)
    }
}

@Composable
private fun BenchLandscape(
    ui: BenchViewModel.Ui,
    ramFraction: Float,
    swapFraction: Float,
    ramUsedGb: Float,
    ramTotalGb: Float,
    tickTxt: String,
    tickLed: LedState,
    phase: BenchPhase,
    stampText: String?,
    windUp: Float,
    workOrder: WorkOrderData?,
    active: Boolean,
    onPurge: () -> Unit,
    onReset: () -> Unit,
    onDialGeometry: (Offset, Float) -> Unit,
    onSlotGeometry: (Offset) -> Unit,
    onTune: () -> Unit,
    onPins: () -> Unit,
    onRamFree: () -> Unit,
    onSetup: () -> Unit,
    serial: String,
    skin: IronSkin,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TickerLine(tickTxt, tickLed)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.weight(0.46f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialWithStamp(
                    diameter = dialSizeFor(IronFormFactor.LANDSCAPE),
                    value = ramFraction,
                    energized = ui.elevated,
                    boosting = phase == BenchPhase.BOOSTING,
                    freedFraction = ui.freedFraction,
                    windUp = windUp,
                    active = active,
                    valueText = "%.1f / %.1f GB".format(ramUsedGb, ramTotalGb),
                    stampText = stampText,
                    numerals = false,
                    onGeometry = onDialGeometry,
                )
                Spacer(Modifier.height(10.dp))
                BoostSlot(phase, workOrder, onPurge, onReset, Modifier.fillMaxWidth(), onSlotGeometry)
            }
            Spacer(Modifier.width(14.dp))
            IronSeamColumn(brass = false)
            Spacer(Modifier.width(14.dp))
            LazyColumn(
                Modifier.weight(0.54f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (!ui.elevated) item {
                    ElevationSlip(visible = true, shake = false, onShizuku = onSetup, onRoot = onSetup)
                }
                toolsItems(ui, onTune, onPins, onRamFree, onSetup, serial, skin, compact = true)
            }
        }
    }
}

@Composable
private fun BenchSplit(
    ui: BenchViewModel.Ui,
    ramFraction: Float,
    swapFraction: Float,
    ramUsedGb: Float,
    ramTotalGb: Float,
    tickTxt: String,
    tickLed: LedState,
    phase: BenchPhase,
    stampText: String?,
    windUp: Float,
    workOrder: WorkOrderData?,
    active: Boolean,
    onPurge: () -> Unit,
    onReset: () -> Unit,
    onDialGeometry: (Offset, Float) -> Unit,
    onSlotGeometry: (Offset) -> Unit,
    onTune: () -> Unit,
    onPins: () -> Unit,
    onRamFree: () -> Unit,
    onSetup: () -> Unit,
    serial: String,
    skin: IronSkin,
) {
    val seamX = (LocalIronFold.current as? IronFold.Seam)
        ?.x
        ?.takeIf { it.value.isFinite() && it > 320.dp }

    Column(Modifier.fillMaxSize()) {
        TickerLine(tickTxt, tickLed, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .then(if (seamX != null) Modifier.width(seamX) else Modifier.weight(1f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val paneW = this.maxWidth
                    val dial = minOf(paneW - 24.dp, dialSizeFor(IronFormFactor.TABLET))
                        .coerceIn(188.dp, 276.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DialWithStamp(
                            diameter = dial,
                            value = ramFraction,
                            energized = ui.elevated,
                            boosting = phase == BenchPhase.BOOSTING,
                            freedFraction = ui.freedFraction,
                            windUp = windUp,
                            active = active,
                            valueText = "%.1f / %.1f GB".format(ramUsedGb, ramTotalGb),
                            stampText = stampText,
                            onGeometry = onDialGeometry,
                        )
                        Spacer(Modifier.height(12.dp))
                        MiniSwap(
                            swapFraction = swapFraction,
                            energized = ui.elevated,
                            size = if (paneW >= 420.dp) 120.dp else 96.dp,
                            valueText = "${ui.mem.swapUsedMb} / ${ui.mem.swapTotalMb} MB",
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                BoostSlot(
                    phase, workOrder, onPurge, onReset,
                    Modifier.fillMaxWidth(0.86f).widthIn(max = 420.dp), onSlotGeometry,
                )
            }

            IronSeamColumn(brass = seamX != null)

            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            ) {
                if (!ui.elevated) item {
                    ElevationSlip(visible = true, shake = false, onShizuku = onSetup, onRoot = onSetup)
                    Spacer(Modifier.height(16.dp))
                }
                toolsItems(ui, onTune, onPins, onRamFree, onSetup, serial, skin)
            }
        }
    }
}

@Composable
private fun DialWithStamp(
    diameter: Dp,
    value: Float,
    energized: Boolean,
    boosting: Boolean,
    freedFraction: Float,
    windUp: Float,
    active: Boolean,
    valueText: String,
    stampText: String?,
    onGeometry: (Offset, Float) -> Unit,
    numerals: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    Box(
        Modifier.onGloballyPositioned {
            onGeometry(
                it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f),
                it.size.width / 2f,
            )
        }
    ) {
        InstrumentDial(
            value = value,
            energized = energized,
            boosting = boosting,
            diameter = diameter,
            numerals = numerals,
            freedFraction = freedFraction,
            over = windUp,
            active = active,
            label = "RAM",
            valueText = valueText,
            onLongPress = onLongPress,
        )
        stampText?.let {
            Box(Modifier.align(Alignment.Center)) { StampLabel(it, StampInk.Phosphor) }
        }
    }
}

@Composable
private fun MiniSwap(
    swapFraction: Float,
    energized: Boolean,
    size: Dp = 96.dp,
    valueText: String,
) {
    InstrumentDial(
        value = swapFraction,
        energized = energized,
        diameter = size,
        numerals = false,
        ignition = false,
        label = "SWAP",
        valueText = valueText,
    )
}

@Composable
private fun BoostSlot(
    phase: BenchPhase,
    workOrder: WorkOrderData?,
    onPurge: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier,
    onGeometry: (Offset) -> Unit,
) {
    FlipCard(
        flipped = phase == BenchPhase.RESULT,
        modifier = modifier.onGloballyPositioned {
            onGeometry(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
        },
        front = {
            ChamferButton(
                text = if (phase == BenchPhase.BOOSTING) "PURGING…" else "BOOST · DEEP FREEZE",
                onClick = { if (phase == BenchPhase.IDLE) onPurge() },
                busy = phase == BenchPhase.BOOSTING,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        },
        back = {
            workOrder?.let { WorkOrderCard(it, onReset) }
        },
    )
}

private fun LazyListScope.toolsItems(
    ui: BenchViewModel.Ui,
    onTune: () -> Unit,
    onPins: () -> Unit,
    onRamFree: () -> Unit,
    onSetup: () -> Unit,
    serial: String,
    skin: IronSkin,
    compact: Boolean = false,
) {
    if (ui.elevated) item {
        if (!compact) Spacer(Modifier.height(12.dp))
        ToolRow(
            "Game optimisation",
            if (compact) "2 available on this kernel" else "Kernel & session parameters",
            { GaugeGlyph(skin.textDim) },
            onTune,
        )
    }
    item {
        if (!compact) Spacer(Modifier.height(8.dp))
        ToolRow("Pin Apps", "Protect apps from being frozen", { LoupeGlyph(skin.textDim) }, onPins)
    }
    item {
        if (!compact) Spacer(Modifier.height(8.dp))
        ToolRow("Pressure Room", "Force safe RAM reclaim", { RailGlyph(skin.textDim) }, onRamFree)
    }
    item {
        Spacer(Modifier.height(if (compact) 12.dp else 30.dp))
        ThermometerStrip(batteryC = ui.batteryC, cpuC = ui.cpuC)
    }
    item {
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        SerialFooter(1, "HOME", serial)
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
            RisoText(
                "PURGE COMPLETE", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900,
                modifier = Modifier.weight(1f)
            )
            StampLabel("FROZEN ${wo.apps}", StampInk.Phosphor)
        }
        Spacer(Modifier.height(14.dp))
        StatRow(
            "FREED SIZE", "+%.1f GB".format(wo.freedGb),
            sub = "RAM +%.1f · SWAP +%.1f".format(wo.freedRamGb, wo.freedSwapGb)
        )
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
