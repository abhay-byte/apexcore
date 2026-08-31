package com.ivarna.apexcore.ui.iron.tune

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ivarna.apexcore.ui.iron.shell.IronSeamColumn
import com.ivarna.apexcore.ui.iron.window.IronFormFactor
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow

/* ═══ §7.8 TUNING ROOM ═══════════════════════════════════════════════ */

data class TuneOptionUi(
    val key: String,
    val title: String,
    val description: String,
    val available: Boolean,
    val reason: String?,
    val kind: com.ivarna.apexcore.tune.TuneControlKind = com.ivarna.apexcore.tune.TuneControlKind.SWITCH,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
    val enumOptions: List<String> = emptyList(),
    val selectedEnum: String? = null,
    val onEnumSelect: (String) -> Unit = {},
    val sliderRange: IntRange? = null,
    val sliderValue: Int? = null,
    val onSliderChange: (Int) -> Unit = {},
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
    selectedGamePkg: String? = null,
    gameOptions: List<Pair<String, String>> = emptyList(),
    onGamePkgSelect: ((String) -> Unit)? = null,
    onMaximumPerformance: (() -> Unit)? = null,
    presetReport: com.ivarna.apexcore.tune.TunePresetReport? = null,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val density = LocalDensity.current
    val skin = ironSkin()
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

    val thresholdPx = with(density) { 96.dp.toPx() }
    // Keep pull as plain var inside connection – avoid mutableState recomposition on every scroll delta (stutter source)
    val probeConnection = remember(thresholdPx) {
        object : NestedScrollConnection {
            var pull = 0f
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .nestedScroll(probeConnection)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackArrow(skin.textDim, onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    "TUNING ROOM",
                    style = IronType.Display.copy(fontSize = 14.sp, letterSpacing = 0.15.sp),
                    color = skin.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                if (isProbing) {
                    LoadingNeedle()
                } else {
                    ChamferButton("PROBE", onProbe, tall = false, variant = ChamferVariant.Outline)
                }
            }

            Text("Real kernel & session tuning.", style = IronType.Body, color = skin.text)
            Text(
                "Capability-gated parameters safely applied during game sessions and restored on exit.",
                style = IronType.Caption, color = skin.textDim
            )
            Text(
                "High power: CPU/GPU max locks can increase heat and battery use; unsupported controls stay off.",
                style = IronType.Caption, color = skin.textDim
            )
            Spacer(Modifier.height(10.dp))

            if (gameOptions.isNotEmpty() && onGamePkgSelect != null) {
                Text("Target game for Game Mode", style = IronType.Label, color = skin.textDim)
                Spacer(Modifier.height(6.dp))
                GamePicker(selectedGamePkg, gameOptions, onGamePkgSelect)
                Spacer(Modifier.height(10.dp))
            }

            if (onMaximumPerformance != null) {
                MaximumPerformanceCard(onMaximumPerformance, presetReport, enabled = !isProbing)
                Spacer(Modifier.height(10.dp))
            }

            if (sessionActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StampLabel("SESSION ACTIVE", StampInk.Signal, pulse = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "LIVE · $sessionApplied APPLIED · %02d:%02d".format(sessionElapsedS / 60, sessionElapsedS % 60),
                        style = IronType.Mono, color = ironSkin().phosphor()
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Hoist sorting out of LazyColumn to avoid re-sort on every scroll frame (stutter)
            val sortedCategories = remember(categories) {
                categories.sortedByDescending { it.availableCount }
            }
            val form = LocalIronWindow.current.form
            if (form == IronFormFactor.PHONE) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(
                        count = sortedCategories.size,
                        key = { i -> sortedCategories[i].name },
                        contentType = { "category" }
                    ) { idx ->
                        val cat = sortedCategories[idx]
                        DrawerHeader(cat.name, cat.availableCount)
                        Spacer(Modifier.height(8.dp))
                        CategoryPlate(cat, skin)
                        Spacer(Modifier.height(20.dp))
                    }
                    item(key = "footer", contentType = "footer") {
                        TuneFooter(serial)
                    }
                }
            } else {
                var catIdx by remember { mutableIntStateOf(0) }
                val safeCat = catIdx.coerceIn(0, (sortedCategories.size - 1).coerceAtLeast(0))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        sortedCategories.forEachIndexed { i, c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickableNoIndication { clack.tick(); catIdx = i },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(if (i == safeCat) 24.dp else 0.dp)
                                        .background(Iron.Brass400)
                                )
                                Spacer(Modifier.width(12.dp))
                                EngravedText(c.name, IronType.Label, color = skin.text)
                                Spacer(Modifier.width(8.dp))
                                Text("${c.availableCount}", style = IronType.MonoSm, color = skin.textDim)
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    IronSeamColumn(brass = false)
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (sortedCategories.isNotEmpty()) {
                            item {
                                CategoryPlate(sortedCategories[safeCat], skin)
                            }
                        }
                        item { TuneFooter(serial) }
                    }
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
private fun CategoryPlate(cat: TuneCategoryUi, skin: IronSkin) {
    if (skin.isPaper) {
        PaperPlate(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(16.dp),
            withShadow = false,
        ) {
            cat.options.forEachIndexed { i, opt ->
                TuneRow(opt)
                if (i < cat.options.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = skin.hairline, thickness = 1.dp)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    } else {
        EngravedPlate(Modifier.fillMaxWidth()) {
            cat.options.forEachIndexed { i, opt ->
                TuneRow(opt)
                if (i < cat.options.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = skin.hairline, thickness = 1.dp)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun TuneFooter(serial: String) {
    Spacer(Modifier.height(16.dp))
    PaperPlate(withShadow = false) {
        Text(
            "Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.",
            style = IronType.Caption, color = Iron.Ink600
        )
    }
    SerialFooter(7, "TUNE", serial)
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
        EngravedText(name, IronType.Label, color = ironSkin().textDim)
        Spacer(Modifier.width(10.dp))
        Text("$available AVAILABLE", style = IronType.MonoSm, color = ironSkin().textDim)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = ironSkin().hairline, thickness = 1.dp)
    }
}

@Composable
private fun TuneRow(opt: TuneOptionUi) {
    val skin = ironSkin()
    when (opt.kind) {
        com.ivarna.apexcore.tune.TuneControlKind.ENUM -> {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (!opt.available) Modifier.alpha(0.55f) else Modifier)
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = skin.text)
                        Text(
                            opt.reason ?: opt.description,
                            style = IronType.Caption,
                            color = if (opt.reason != null) Iron.Ember500 else skin.textDim
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    MachinedToggle(opt.checked, opt.onToggle, enabled = opt.available)
                }
                Spacer(Modifier.height(8.dp))
                if (opt.enumOptions.isEmpty()) {
                    Text(
                        if (!opt.available) opt.reason ?: "Not available on this kernel"
                        else "No common governor — no selection available",
                        style = IronType.Caption, color = Iron.Ember500
                    )
                } else {
                    EnumSelector(
                        selected = opt.selectedEnum,
                        options = opt.enumOptions,
                        enabled = opt.checked && opt.available,
                        onSelect = opt.onEnumSelect
                    )
                    if (opt.selectedEnum != null && opt.available) {
                        Text(
                            "Selected: ${opt.selectedEnum}",
                            style = IronType.MonoSm, color = skin.textDim,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        com.ivarna.apexcore.tune.TuneControlKind.SLIDER -> {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (!opt.available) Modifier.alpha(0.55f) else Modifier)
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = skin.text)
                        Text(
                            opt.reason ?: opt.description,
                            style = IronType.Caption,
                            color = if (opt.reason != null) Iron.Ember500 else skin.textDim
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    MachinedToggle(opt.checked, opt.onToggle, enabled = opt.available)
                }
                val range = opt.sliderRange ?: 0..100
                val initial = opt.sliderValue ?: range.first
                var sliderPos by remember(opt.key, initial) { mutableFloatStateOf(initial.toFloat()) }
                // Keep slider synced if external intent changes (e.g., after probe).
                LaunchedEffect(opt.sliderValue) {
                    opt.sliderValue?.let { if (it.toFloat() != sliderPos) sliderPos = it.toFloat() }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Slider(
                    value = sliderPos,
                    onValueChange = { v -> sliderPos = v },
                    onValueChangeFinished = { opt.onSliderChange(sliderPos.toInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = opt.checked && opt.available
                )
                Text(
                    "${sliderPos.toInt()}",
                    style = IronType.MonoSm, color = skin.textDim,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        else -> {
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
                    Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = skin.text)
                    Text(
                        opt.reason ?: opt.description,
                        style = IronType.Caption,
                        color = if (opt.reason != null) Iron.Ember500 else skin.textDim
                    )
                }
                Spacer(Modifier.width(12.dp))
                MachinedToggle(opt.checked, opt.onToggle, enabled = opt.available)
            }
        }
    }
}

@Composable
private fun EnumSelector(
    selected: String?,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    val skin = ironSkin()
    if (options.isEmpty()) return
    if (options.size <= 4) {
        val idx = options.indexOf(selected).takeIf { it >= 0 } ?: 0
        MachinedSegment(
            options = options,
            selected = idx,
            onSelect = { i -> if (enabled) onSelect(options[i]) },
            modifier = Modifier.fillMaxWidth().then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
        )
    } else {
        var expanded by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Iron.Anvil950, IronShape.Slot)
                    .border(1.dp, Iron.Anvil600, IronShape.Slot)
                    .then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(10.dp)
            ) {
                androidx.compose.material3.Text(
                    selected ?: options.first(),
                    style = IronType.Label,
                    color = skin.text
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(opt, style = IronType.Label) },
                        onClick = { expanded = false; onSelect(opt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GamePicker(
    selectedPkg: String?,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val skin = ironSkin()
    val selectedLabel = options.find { it.first == selectedPkg }?.second ?: "Select game"
    Box {
        PaperPlate(
            modifier = Modifier.fillMaxWidth().then(if (selectedPkg == null) Modifier else Modifier),
            padding = PaddingValues(12.dp),
            withShadow = false
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .let { m ->
                        m
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedLabel, style = IronType.Label, color = skin.text, modifier = Modifier.weight(1f))
                ChamferButton("CHANGE", onClick = { expanded = true }, tall = false, variant = ChamferVariant.Outline)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (pkg, name) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name, style = IronType.Body) },
                    onClick = { expanded = false; onSelect(pkg) }
                )
            }
        }
    }
}

@Composable
private fun MaximumPerformanceCard(
    onClick: () -> Unit,
    report: com.ivarna.apexcore.tune.TunePresetReport?,
    enabled: Boolean
) {
    EngravedPlate(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Maximum Performance", style = IronType.Title.copy(fontSize = 15.sp), color = ironSkin().text)
                Text(
                    "OEM Game Mode + CPU/GPU performance governor + max locks (verified only)",
                    style = IronType.Caption, color = ironSkin().textDim
                )
                if (report != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${report.applied}/${report.requested} verified",
                        style = IronType.MonoSm,
                        color = if (report.partial) Iron.Ember500 else Iron.Signal500
                    )
                    report.components.forEach { c ->
                        Text(
                            "${c.id.name}: ${if (c.verified) "verified" else c.reason}",
                            style = IronType.Caption, color = if (c.verified) ironSkin().textDim else Iron.Ember500
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            ChamferButton("APPLY", onClick = onClick, enabled = enabled, tall = false)
        }
    }
}
