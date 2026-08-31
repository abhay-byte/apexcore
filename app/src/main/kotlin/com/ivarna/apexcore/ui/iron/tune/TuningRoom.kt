package com.ivarna.apexcore.ui.iron.tune

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
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
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TunePresetComponent
import com.ivarna.apexcore.tune.TunePresetReport
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
    presetReport: TunePresetReport? = null,
    isApplyingMaxPerf: Boolean = false,
    probeError: String? = null,
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

            // Hoist sorting out of LazyColumn to avoid re-sort on every scroll frame (stutter)
            val sortedCategories = remember(categories) {
                categories.sortedByDescending { it.availableCount }
            }
            val form = LocalIronWindow.current.form
            // Intro / Game Mode / Max Perf scroll with categories — only title chrome stays fixed.
            if (form == IronFormFactor.PHONE) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item(key = "scroll-header", contentType = "header") {
                        TuningRoomScrollHeader(
                            skin = skin,
                            probeError = probeError,
                            selectedGamePkg = selectedGamePkg,
                            gameOptions = gameOptions,
                            onGamePkgSelect = onGamePkgSelect,
                            onMaximumPerformance = onMaximumPerformance,
                            presetReport = presetReport,
                            isProbing = isProbing,
                            isApplyingMaxPerf = isApplyingMaxPerf,
                            sessionActive = sessionActive,
                            sessionElapsedS = sessionElapsedS,
                            sessionApplied = sessionApplied,
                        )
                    }
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
                        item(key = "scroll-header", contentType = "header") {
                            TuningRoomScrollHeader(
                                skin = skin,
                                probeError = probeError,
                                selectedGamePkg = selectedGamePkg,
                                gameOptions = gameOptions,
                                onGamePkgSelect = onGamePkgSelect,
                                onMaximumPerformance = onMaximumPerformance,
                                presetReport = presetReport,
                                isProbing = isProbing,
                                isApplyingMaxPerf = isApplyingMaxPerf,
                                sessionActive = sessionActive,
                                sessionElapsedS = sessionElapsedS,
                                sessionApplied = sessionApplied,
                            )
                        }
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
    val titleColor = if (opt.available) skin.text else skin.textDim
    val bodyColor = skin.textDim
    val reasonColor = skin.dangerText()
    when (opt.kind) {
        com.ivarna.apexcore.tune.TuneControlKind.ENUM -> {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = titleColor)
                        Text(
                            opt.reason ?: opt.description,
                            style = IronType.Caption,
                            color = if (opt.reason != null) reasonColor else bodyColor
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
                        style = IronType.Caption, color = reasonColor
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
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = titleColor)
                        Text(
                            opt.reason ?: opt.description,
                            style = IronType.Caption,
                            color = if (opt.reason != null) reasonColor else bodyColor
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
                IronSlider(
                    value = sliderPos,
                    onValueChange = { v -> sliderPos = v },
                    onValueChangeFinished = { opt.onSliderChange(sliderPos.toInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = opt.checked && opt.available,
                )
                IronSliderReadout(
                    text = "${sliderPos.toInt()}",
                    emphasized = opt.checked && opt.available,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
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
                Column(Modifier.weight(1f)) {
                    Text(opt.title, style = IronType.Title.copy(fontSize = 15.sp), color = titleColor)
                    Text(
                        opt.reason ?: opt.description,
                        style = IronType.Caption,
                        color = if (opt.reason != null) reasonColor else bodyColor
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
    if (options.isEmpty()) return
    if (options.size <= 4) {
        val idx = options.indexOf(selected).takeIf { it >= 0 } ?: 0
        MachinedSegment(
            options = options,
            selected = idx,
            onSelect = { i -> if (enabled) onSelect(options[i]) },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        IronSelectField(
            value = selected ?: options.first(),
            options = options.map { IronSelectOption(key = it, label = it) },
            onSelect = { opt -> onSelect(opt.key) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
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
    val clack = rememberClack()
    val selectedLabel = when {
        options.isEmpty() -> "No games added"
        selectedPkg.isNullOrBlank() -> "Select a game"
        else -> options.find { it.first == selectedPkg }?.second ?: "Select a game"
    }
    Box {
        IronSurface(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    selectedLabel,
                    style = IronType.Label,
                    color = skin.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (options.isNotEmpty()) {
                    ChamferButton(
                        "CHANGE",
                        onClick = {
                            clack.tick()
                            expanded = true
                        },
                        tall = false,
                        variant = ChamferVariant.Outline,
                    )
                }
            }
        }
        IronDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            selectedKey = selectedPkg,
            options = options.map { (pkg, name) -> IronSelectOption(key = pkg, label = name) },
            onSelect = { opt ->
                expanded = false
                onSelect(opt.key)
            },
        )
    }
}

private fun TuneId.userFacingLabel(): String = when (this) {
    TuneId.GAME_MODE_PERFORMANCE -> "Game mode"
    TuneId.CPU_GOVERNOR -> "CPU governor"
    TuneId.CPU_LOCK_MAX -> "CPU max lock"
    TuneId.GPU_GOVERNOR -> "GPU governor"
    TuneId.GPU_LOCK_MAX -> "GPU max lock"
    else -> name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private data class PresetRowUi(
    val label: String,
    val status: String,
    val color: androidx.compose.ui.graphics.Color,
)

@Composable
private fun presetRowUi(c: TunePresetComponent): PresetRowUi {
    val skin = ironSkin()
    return when {
        c.verified -> PresetRowUi(c.id.userFacingLabel(), "VERIFIED", skin.successText())
        !c.supported -> {
            val detail = c.reason
                .replace("Needs Root for this kernel", "ROOT REQUIRED", ignoreCase = true)
                .replace("not probed", "NOT PROBED", ignoreCase = true)
                .uppercase()
            PresetRowUi(c.id.userFacingLabel(), "UNAVAILABLE · $detail", skin.warningText())
        }
        c.reason.equals("requested", ignoreCase = true) ->
            PresetRowUi(c.id.userFacingLabel(), "REQUESTED", skin.warningText())
        else ->
            PresetRowUi(c.id.userFacingLabel(), "FAILED · ${c.reason.uppercase()}", skin.dangerText())
    }
}

@Composable
private fun TuningRoomScrollHeader(
    skin: IronSkin,
    probeError: String?,
    selectedGamePkg: String?,
    gameOptions: List<Pair<String, String>>,
    onGamePkgSelect: ((String) -> Unit)?,
    onMaximumPerformance: (() -> Unit)?,
    presetReport: TunePresetReport?,
    isProbing: Boolean,
    isApplyingMaxPerf: Boolean,
    sessionActive: Boolean,
    sessionElapsedS: Int,
    sessionApplied: Int,
) {
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

    if (!probeError.isNullOrBlank()) {
        IronSurface(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(12.dp),
        ) {
            Text("CAPABILITY PROBE FAILED", style = IronType.Label, color = skin.dangerText())
            Text(
                "Controls shown unavailable. Tap PROBE to retry.",
                style = IronType.Caption,
                color = skin.textDim,
            )
            Text(probeError, style = IronType.MonoSm, color = skin.textDim)
        }
        Spacer(Modifier.height(10.dp))
    }

    if (onGamePkgSelect != null) {
        Text("Target game for Game Mode", style = IronType.Label, color = skin.textDim)
        Spacer(Modifier.height(6.dp))
        GamePicker(selectedGamePkg, gameOptions, onGamePkgSelect)
        Spacer(Modifier.height(10.dp))
    }

    if (onMaximumPerformance != null) {
        val hasTarget = !selectedGamePkg.isNullOrBlank() && gameOptions.any { it.first == selectedGamePkg }
        // Bind report to current target so a prior package's result cannot linger.
        val boundReport = presetReport?.takeIf {
            it.gamePackage.isBlank() || it.gamePackage == selectedGamePkg
        }
        MaximumPerformanceCard(
            onClick = onMaximumPerformance,
            report = boundReport,
            enabled = !isProbing && !isApplyingMaxPerf && hasTarget,
            applying = isApplyingMaxPerf,
            hasTarget = hasTarget,
        )
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
}

@Composable
private fun MaximumPerformanceCard(
    onClick: () -> Unit,
    report: TunePresetReport?,
    enabled: Boolean,
    applying: Boolean,
    hasTarget: Boolean,
) {
    val skin = ironSkin()
    EngravedPlate(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Maximum Performance", style = IronType.Title.copy(fontSize = 15.sp), color = skin.text)
                Text(
                    "OEM Game Mode + CPU/GPU performance governor + max locks",
                    style = IronType.Caption, color = skin.textDim
                )
                if (applying) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "APPLYING PRESET…",
                        style = IronType.MonoSm,
                        color = skin.phosphor(),
                    )
                } else if (!hasTarget) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add or select a game to use Maximum Performance.",
                        style = IronType.Caption,
                        color = skin.textDim,
                    )
                }
                if (report != null && !applying) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${report.applied} / ${report.requested} SUPPORTED ACTIONS VERIFIED",
                        style = IronType.MonoSm,
                        color = if (report.partial) skin.warningText() else skin.successText(),
                    )
                    Spacer(Modifier.height(6.dp))
                    report.components.forEach { c ->
                        val row = presetRowUi(c)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                row.label.uppercase(),
                                style = IronType.Caption,
                                color = skin.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                row.status,
                                style = IronType.MonoSm,
                                color = row.color,
                            )
                        }
                    }
                }
            }
            if (applying) {
                Box(
                    Modifier
                        .height(44.dp)
                        .widthIn(min = 72.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingNeedle(tint = skin.phosphor())
                }
            } else {
                ChamferButton("APPLY", onClick = onClick, enabled = enabled, tall = false)
            }
        }
    }
}
