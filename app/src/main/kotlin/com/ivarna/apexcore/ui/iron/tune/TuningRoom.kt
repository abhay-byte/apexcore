package com.ivarna.apexcore.ui.iron.tune

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*

/* ═══ §7.8 TUNING ROOM ═══════════════════════════════════════════════ */

data class TuneOptionUi(
    val key: String,
    val title: String,
    val description: String,
    val available: Boolean,
    val reason: String?,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

data class TuneCategoryUi(val name: String, val options: List<TuneOptionUi>) {
    val availableCount: Int get() = options.count { it.available }
}

@Composable
fun TuningRoom(
    categories: List<TuneCategoryUi>,
    sessionActive: Boolean,
    sessionElapsedS: Int,
    sessionApplied: Int,
    isProbing: Boolean,
    onProbe: () -> Unit,
    onBack: () -> Unit,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val density = LocalDensity.current
    val running = sessionActive

    val view = LocalView.current
    DisposableEffect(running) {
        val window = view.context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = running) { progress ->
        try {
            progress.collect { scrub = it.progress }
            onBack()
        } catch (_: Throwable) {
            scrub = 0f
        }
    }

    var pull by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 96.dp.toPx() }
    val probeConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.2f)
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val past = pull >= thresholdPx
                pull = 0f
                if (past) {
                    onProbe()
                    repeat(3) { clack.tick() }
                }
                return if (past) available else Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(probeConnection)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackArrow(Iron.Bone300, onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    "TUNING ROOM",
                    style = IronType.Display.copy(fontSize = 22.sp),
                    color = Iron.Bone100,
                    modifier = Modifier.weight(1f)
                )
                if (isProbing) {
                    LoadingNeedle()
                } else {
                    ChamferButton("PROBE", onProbe, tall = false, variant = ChamferVariant.Outline)
                }
            }

            Text("Real kernel & session tuning.", style = IronType.Body, color = Iron.Bone300)
            Text(
                "Capability-gated parameters safely applied during game sessions and restored on exit.",
                style = IronType.Caption, color = Iron.Bone500
            )
            Spacer(Modifier.height(10.dp))

            if (sessionActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StampLabel("SESSION ACTIVE", StampInk.Signal, pulse = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "LIVE · $sessionApplied APPLIED · %02d:%02d".format(sessionElapsedS / 60, sessionElapsedS % 60),
                        style = IronType.Mono, color = Iron.Phosphor400
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(Modifier.weight(1f)) {
                val sorted = categories.sortedByDescending { it.availableCount }
                sorted.forEach { cat ->
                    item(key = cat.name) {
                        DrawerHeader(cat.name, cat.availableCount)
                        Spacer(Modifier.height(8.dp))
                        EngravedPlate(Modifier.fillMaxWidth()) {
                            cat.options.forEachIndexed { i, opt ->
                                TuneRow(opt)
                                if (i < cat.options.lastIndex) {
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                item {
                    PaperPlate {
                        Text(
                            "Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.",
                            style = IronType.Caption, color = Iron.Ink600
                        )
                    }
                    SerialFooter(7, "TUNE", serial)
                }
            }
        }

        if (scrub > 0.01f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PaperPlate(Modifier.alpha(scrub)) {
                    RisoText("END SESSION?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900)
                    Text("Release to end and restore kernel parameters.", style = IronType.Caption, color = Iron.Ink600)
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(name: String, available: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp, 4.dp)
                .background(Iron.Brass400)
        )
        Spacer(Modifier.width(8.dp))
        EngravedText(name, IronType.Label, color = Iron.Bone300)
        Spacer(Modifier.width(10.dp))
        Text("$available AVAILABLE", style = IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Iron.Anvil600, thickness = 1.dp)
    }
}

@Composable
private fun TuneRow(opt: TuneOptionUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(if (!opt.available) Modifier.alpha(0.55f) else Modifier)
        ) {
            Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = Iron.Bone100)
            Text(
                opt.reason ?: opt.description,
                style = IronType.Caption,
                color = if (opt.reason != null) Iron.Ember500 else Iron.Bone500
            )
        }
        Spacer(Modifier.width(12.dp))
        MachinedToggle(opt.checked, opt.onToggle, enabled = opt.available)
    }
}
