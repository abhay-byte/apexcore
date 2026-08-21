package com.ivarna.apexcore.ui.iron.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DebugBench(onClose: () -> Unit) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    val serial = rememberSerial()
    var sweep by remember { mutableFloatStateOf(0.62f) }
    var stampKey by remember { mutableIntStateOf(0) }
    var burstTick by remember { mutableIntStateOf(0) }
    val shavings = remember { ShavingsState() }
    var sheet by remember { mutableStateOf(false) }

    LaunchedEffect(burstTick) {
        if (burstTick > 0) shavings.burst(200f, 260f, 90f, count = 120, speed = 900f)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Iron.Anvil900)
            .ironGrain()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BENCH TEST",
                style = IronType.Display.copy(fontSize = 22.sp),
                color = Iron.Bone100,
                modifier = Modifier.weight(1f)
            )
            BackArrow(Iron.Bone300, onClose)
        }
        Spacer(Modifier.height(16.dp))

        EngravedPlate {
            Text("HAPTIC GRAMMAR", style = IronType.Label, color = Iron.Bone300)
            Spacer(Modifier.height(8.dp))
            listOf<Pair<String, () -> Unit>>(
                "TICK" to clack::tick,
                "KEY TAP" to clack::keyTap,
                "CONFIRM" to clack::confirm,
                "OFF" to clack::off,
                "THUD" to clack::thud,
                "REJECT" to clack::no,
                "PURGE DONE (two-stage)" to { scope.launch { clack.purgeDone() } },
            ).forEach { (name, fn) ->
                DebugRow(name) { fn() }
            }
        }
        Spacer(Modifier.height(16.dp))

        EngravedPlate {
            Text("CEREMONIES", style = IronType.Label, color = Iron.Bone300)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                InstrumentDial(
                    sweep,
                    energized = true,
                    ignition = false,
                    freedFraction = 0.18f,
                    modifier = Modifier.size(180.dp)
                )
                ShavingsLayer(shavings, Modifier.matchParentSize())
                Box(Modifier.align(Alignment.Center)) {
                    key(stampKey) { if (stampKey > 0) StampLabel("FROZEN 12", StampInk.Phosphor) }
                }
            }
            Spacer(Modifier.height(8.dp))
            DebugRow("NEEDLE SWEEP 0→100→62") {
                scope.launch {
                    sweep = 1f
                    delay(300)
                    sweep = 0.62f
                }
            }
            DebugRow("STAMP SLAM") { stampKey++ }
            DebugRow("SHAVINGS BURST") { burstTick++ }
            DebugRow("BENCH SHEET") { sheet = true }
        }
        SerialFooter(99, "DEBUG", serial)
    }

    BenchSheet(visible = sheet, onDismiss = { sheet = false }) {
        Text(
            "Sheet mechanics: drag-dismiss, predictive-back scrub.",
            style = IronType.Body,
            color = Iron.Bone100
        )
    }
}

@Composable
private fun DebugRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickableNoIndication(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp, 3.dp).background(Iron.Brass400))
        Spacer(Modifier.width(10.dp))
        Text(label, style = IronType.Mono, color = Iron.Bone100)
    }
}
