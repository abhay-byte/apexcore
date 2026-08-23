package com.ivarna.apexcore.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.freeze.WhitelistStore
import com.ivarna.apexcore.games.GameInfo
import com.ivarna.apexcore.games.GameLauncher
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.games.GameOverlayService
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.iron.games.AppCardData
import com.ivarna.apexcore.ui.iron.games.Demand
import com.ivarna.apexcore.ui.iron.games.LaunchMatrixScreen
import com.ivarna.apexcore.ui.iron.home.BenchViewModel
import com.ivarna.apexcore.ui.iron.home.TheBench
import com.ivarna.apexcore.ui.iron.legal.MdBlock
import com.ivarna.apexcore.ui.iron.legal.TheLedger
import com.ivarna.apexcore.ui.iron.manual.FieldManual
import com.ivarna.apexcore.ui.iron.overlay.OpticsBench
import com.ivarna.apexcore.ui.iron.overlay.OpticsUiState
import com.ivarna.apexcore.ui.iron.overlay.RailEdge
import com.ivarna.apexcore.ui.iron.overlay.RailSize
import com.ivarna.apexcore.ui.iron.ram.PressurePhase
import com.ivarna.apexcore.ui.iron.ram.PressureRoom
import com.ivarna.apexcore.ui.iron.ram.PressureUiState
import com.ivarna.apexcore.ui.iron.ram.RamModeUi
import com.ivarna.apexcore.ui.iron.settings.DiagnosticUi
import com.ivarna.apexcore.ui.iron.settings.RunningModeUi
import com.ivarna.apexcore.ui.iron.settings.Toolbox
import com.ivarna.apexcore.ui.iron.shell.BackendBenchSheet
import com.ivarna.apexcore.ui.iron.shell.GearTab
import com.ivarna.apexcore.ui.iron.shell.IronShell
import com.ivarna.apexcore.ui.iron.shell.IronSlot
import com.ivarna.apexcore.ui.iron.sheets.AddGameSheet
import com.ivarna.apexcore.ui.iron.sheets.PinAppsSheet
import com.ivarna.apexcore.ui.iron.sheets.SystemAccessSheet
import com.ivarna.apexcore.ui.iron.tune.TuneCategoryUi
import com.ivarna.apexcore.ui.iron.tune.TuneOptionUi
import com.ivarna.apexcore.ui.iron.tune.TuningRoom
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    gameManager: GameManager,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    lightTankBg: Boolean = true,
    onLightTankBgChange: (Boolean) -> Unit = {},
    mechanicalMotion: String = "auto",
    onMechanicalMotionChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val benchVm: BenchViewModel = viewModel()
    val benchUi by benchVm.ui.collectAsState()
    val toast = rememberStampToast()

    var gearTab by rememberSaveable { mutableStateOf(GearTab.HOME) }
    var ironSlot by rememberSaveable { mutableStateOf(IronSlot.NONE) }
    var showBackendSheet by rememberSaveable { mutableStateOf(false) }
    var showSetupSheet by rememberSaveable { mutableStateOf(false) }
    var showPinSheet by rememberSaveable { mutableStateOf(false) }
    var showAddGameSheet by rememberSaveable { mutableStateOf(false) }
    var showReplayManual by rememberSaveable { mutableStateOf(false) }

    var shizukuStatus by remember { mutableStateOf(KeyStatus()) }
    var rootStatus by remember { mutableStateOf(KeyStatus()) }

    fun probeKeys() {
        scope.launch {
            val sReady = try { ShizukuFreezeBackend().isReady() } catch (_: Throwable) { false }
            shizukuStatus = KeyStatus(
                ready = sReady,
                checking = false,
                statusLine = if (sReady) "Connected · wireless debugging" else "Service not running"
            )
            val rReady = try { RootFreezeBackend().isReady() } catch (_: Throwable) { false }
            rootStatus = KeyStatus(
                ready = rReady,
                checking = false,
                statusLine = if (rReady) "su granted" else "su not granted"
            )
            benchVm.redetect()
        }
    }

    LaunchedEffect(Unit) {
        probeKeys()
    }

    val prefs = remember { context.getSharedPreferences("apexcore", Context.MODE_PRIVATE) }
    val prefStr = prefs.getString("preferred_backend", null)
    val preferredBackend = when (prefStr) {
        "shizuku" -> BackendChoice.SHIZUKU
        "root" -> BackendChoice.ROOT
        else -> null
    }

    // Games state
    var gamesList by remember { mutableStateOf<List<AppCardData>>(emptyList()) }
    var allAppsList by remember { mutableStateOf<List<AppCardData>>(emptyList()) }
    var allAppsLoading by remember { mutableStateOf(false) }

    fun refreshGames() {
        scope.launch {
            val loaded = gameManager.load()
            gamesList = loaded.map { info ->
                AppCardData(
                    name = info.name,
                    pkg = info.pkg,
                    demand = Demand.MEDIUM,
                    tint = Iron.Signal500,
                    icon = {
                        val iconDrawable = try {
                            context.packageManager.getApplicationIcon(info.pkg)
                        } catch (_: Throwable) { null }
                        if (iconDrawable != null) {
                            val dr = com.google.accompanist.drawablepainter.rememberDrawablePainter(iconDrawable)
                            Image(
                                painter = dr,
                                contentDescription = info.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshGames()
    }

    // Overlay state
    var overlayGranted by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var overlayPreview by remember { mutableStateOf(GameOverlayService.isRunning) }
    var railSize by remember { mutableStateOf<RailSize>(RailSize.M) }
    var railOpacity by remember { mutableFloatStateOf(0.94f) }
    var railEdge by remember { mutableStateOf<RailEdge>(RailEdge.LEFT) }

    LaunchedEffect(Unit) {
        overlayGranted = android.provider.Settings.canDrawOverlays(context)
        val s = prefs.getString("hud_size", "M")
        railSize = when (s) { "S" -> RailSize.S; "L" -> RailSize.L; else -> RailSize.M }
        railOpacity = prefs.getFloat("hud_opacity", 0.94f)
        railEdge = if (prefs.getString("hud_edge", "LEFT") == "RIGHT") RailEdge.RIGHT else RailEdge.LEFT
    }

    // Tune state
    val tuneManager = remember { com.ivarna.apexcore.tune.TuneManager.get(context) }
    var tuneCategories by remember { mutableStateOf<List<TuneCategoryUi>>(emptyList()) }
    var isTuneProbing by remember { mutableStateOf(false) }
    val tuneSessionActive by tuneManager.sessionActive.collectAsState()

    fun refreshTune() {
        scope.launch {
            isTuneProbing = true
            tuneManager.refreshCapabilities()
            val caps = tuneManager.capabilities.value
            val specs = com.ivarna.apexcore.tune.TuneSpecs.all
            val grouped = com.ivarna.apexcore.tune.TuneCategory.entries.map { cat ->
                val catSpecs = specs.filter { it.category == cat }
                val options = catSpecs.map { spec ->
                    val cap = caps[spec.id]
                    val isAvail = cap?.available == true
                    val isChecked = tuneManager.intent(spec.id).on
                    TuneOptionUi(
                        key = spec.id.name,
                        title = spec.title,
                        description = spec.description,
                        available = isAvail,
                        reason = if (!isAvail) "Not available on kernel" else null,
                        checked = isChecked,
                        onToggle = { checked ->
                            tuneManager.setIntent(spec.id, com.ivarna.apexcore.tune.TuneValue(checked))
                            refreshTune()
                        }
                    )
                }
                TuneCategoryUi(name = cat.name, options = options)
            }
            tuneCategories = grouped
            isTuneProbing = false
        }
    }

    LaunchedEffect(ironSlot) {
        if (ironSlot == IronSlot.TUNE) {
            refreshTune()
        }
    }

    // Pressure Room State
    var pressureState by remember {
        mutableStateOf(
            PressureUiState(
                phase = PressurePhase.IDLE,
                ramUsedMb = benchUi.mem.ramUsedMb,
                ramTotalMb = benchUi.mem.ramTotalMb,
                swapUsedMb = benchUi.mem.swapUsedMb,
                swapTotalMb = benchUi.mem.swapTotalMb
            )
        )
    }
    LaunchedEffect(benchUi.mem) {
        if (pressureState.phase == PressurePhase.IDLE) {
            pressureState = pressureState.copy(
                ramUsedMb = benchUi.mem.ramUsedMb,
                ramTotalMb = benchUi.mem.ramTotalMb,
                swapUsedMb = benchUi.mem.swapUsedMb,
                swapTotalMb = benchUi.mem.swapTotalMb
            )
        }
    }
    var preFreeze by remember { mutableStateOf(true) }
    val ramModes = remember { listOf(RamModeUi("STANDARD", true), RamModeUi("AGGRESSIVE", true)) }
    var selectedRamMode by remember { mutableStateOf<RamModeUi?>(ramModes.first()) }

    // Privacy Policy raw text — parsed only while the Ledger slot is open (§5.1 lazy parse)
    val ledgerBlocks = remember(ironSlot) {
        if (ironSlot == IronSlot.LEDGER) parsePrivacyBlocks(context) else emptyList()
    }

    fun openShizukuApp() {
        val pm = context.packageManager
        val candidates = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager", "moe.shizuku.api")
        var launched = false
        for (pkg in candidates) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                try {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    launched = true
                    break
                } catch (_: Throwable) {}
            }
        }
        if (!launched) {
            val play = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(play) } catch (_: Throwable) {}
        }
    }

    IronShell(
        tab = gearTab,
        onTab = { gearTab = it },
        backendName = benchUi.backendName,
        backendLed = benchUi.backendLed,
        onBackend = { showBackendSheet = true },
        slot = ironSlot,
        onSlot = { ironSlot = it },
        home = {
            TheBench(
                ui = benchUi,
                onBoost = { benchVm.boost(context) },
                onTune = { ironSlot = IronSlot.TUNE },
                onPins = { showPinSheet = true },
                onRamFree = { ironSlot = IronSlot.PRESSURE },
                onSetup = { showSetupSheet = true },
                toast = toast,
                active = gearTab == GearTab.HOME && !showReplayManual
            )
        },
        games = {
            LaunchMatrixScreen(
                games = gamesList,
                allApps = allAppsList,
                allLoading = allAppsLoading,
                onAdd = {
                    showAddGameSheet = true
                },
                onPin = { showPinSheet = true },
                onLaunch = { card ->
                    scope.launch {
                        GameLauncher.launch(context, card.pkg)
                    }
                },
                onRemove = { card ->
                    gameManager.remove(card.pkg)
                    refreshGames()
                },
                addSheet = {
                    if (showAddGameSheet) {
                        var installedApps by remember { mutableStateOf<List<PickerApp>>(emptyList()) }
                        LaunchedEffect(Unit) {
                            val apps = gameManager.listInstallableApps(context)
                            installedApps = apps.map { app ->
                                PickerApp(
                                    name = app.name,
                                    pkg = app.pkg,
                                    icon = {
                                        val dr = try { context.packageManager.getApplicationIcon(app.pkg) } catch (_: Throwable) { null }
                                        if (dr != null) {
                                            Image(
                                                painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(dr),
                                                contentDescription = app.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        AddGameSheet(
                            visible = true,
                            onDismiss = { showAddGameSheet = false },
                            apps = installedApps,
                            alreadyAdded = gamesList.map { it.pkg }.toSet(),
                            onAdd = { added ->
                                gameManager.addAll(added.map { app -> GameInfo(app.pkg, app.name, false) })
                                refreshGames()
                            }
                        )
                    }
                },
                pinSheet = {
                    if (showPinSheet) {
                        var allInstalled by remember { mutableStateOf<List<PickerApp>>(emptyList()) }
                        LaunchedEffect(Unit) {
                            val apps = gameManager.listInstallableApps(context)
                            allInstalled = apps.map { app ->
                                PickerApp(
                                    name = app.name,
                                    pkg = app.pkg,
                                    icon = {
                                        val dr = try { context.packageManager.getApplicationIcon(app.pkg) } catch (_: Throwable) { null }
                                        if (dr != null) {
                                            Image(
                                                painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(dr),
                                                contentDescription = app.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        var pinnedSet by remember { mutableStateOf(WhitelistStore.allPinned(context)) }
                        PinAppsSheet(
                            visible = true,
                            onDismiss = { showPinSheet = false },
                            apps = allInstalled,
                            pinned = pinnedSet,
                            onTogglePin = { pkg ->
                                val nowPinned = !pinnedSet.contains(pkg)
                                WhitelistStore.setPinned(context, pkg, nowPinned)
                                pinnedSet = WhitelistStore.allPinned(context)
                            }
                        )
                    }
                }
            )
        },
        optics = {
            OpticsBench(
                active = gearTab == GearTab.HUD && !showReplayManual,
                state = OpticsUiState(
                    permissionGranted = overlayGranted,
                    previewRunning = overlayPreview,
                    size = railSize,
                    opacity = railOpacity,
                    edge = railEdge
                ),
                onGrant = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { context.startActivity(intent) } catch (_: Throwable) {}
                },
                onTogglePreview = { on ->
                    overlayPreview = on
                    if (on) {
                        GameOverlayService.start(context, context.packageName)
                    } else {
                        GameOverlayService.stop(context)
                    }
                },
                onSize = { s ->
                    railSize = s
                    prefs.edit().putString("hud_size", s.name).apply()
                },
                onOpacity = { o ->
                    railOpacity = o
                    prefs.edit().putFloat("hud_opacity", o).apply()
                },
                onEdge = { e ->
                    railEdge = e
                    prefs.edit().putString("hud_edge", e.name).apply()
                }
            )
        },
        toolbox = {
            Toolbox(
                themeMode = themeMode,
                onThemeMode = onThemeModeChange,
                paperInserts = lightTankBg,
                onPaperInserts = onLightTankBgChange,
                mechanicalMotion = mechanicalMotion,
                onMechanicalMotion = onMechanicalMotionChange,
                runningMode = RunningModeUi(
                    backend = benchUi.backendName,
                    preferred = preferredBackend?.name ?: "AUTO",
                    fpsPrivilege = "PRIVILEGED",
                    gpuVendor = "AUTO"
                ),
                diagnostics = listOf(
                    DiagnosticUi(
                        name = "SHIZUKU",
                        statusLine = shizukuStatus.statusLine,
                        led = if (shizukuStatus.ready) LedState.READY else LedState.BLOCKED,
                        actionLabel = if (!shizukuStatus.ready) "SETUP" else null,
                        action = { openShizukuApp() }
                    ),
                    DiagnosticUi(
                        name = "ROOT",
                        statusLine = rootStatus.statusLine,
                        led = if (rootStatus.ready) LedState.READY else LedState.BLOCKED,
                        actionLabel = "PROBE",
                        action = { probeKeys() }
                    )
                ),
                versionName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4" } catch (_: Throwable) { "1.4" },
                onPrivacy = { ironSlot = IronSlot.LEDGER },
                onTour = { showReplayManual = true }
            )
        },
        slotContent = { slot ->
            when (slot) {
                IronSlot.TUNE -> TuningRoom(
                    categories = tuneCategories,
                    sessionActive = tuneSessionActive,
                    sessionElapsedS = 0,
                    sessionApplied = tuneCategories.sumOf { it.options.count { opt -> opt.checked } },
                    isProbing = isTuneProbing,
                    onProbe = { refreshTune() },
                    onBack = { ironSlot = IronSlot.NONE }
                )
                IronSlot.PRESSURE -> PressureRoom(
                    state = pressureState,
                    modes = ramModes,
                    selectedMode = selectedRamMode,
                    preFreeze = preFreeze,
                    onMode = { selectedRamMode = it },
                    onPreFreeze = { preFreeze = it },
                    onStart = {
                        scope.launch {
                            pressureState = pressureState.copy(phase = PressurePhase.FILLING)
                            val res = FreezeFramework.freezeAll(context)
                            pressureState = pressureState.copy(
                                phase = PressurePhase.DONE,
                                resultGb = (res.freedKb + res.swapFreedKb) / (1024f * 1024f)
                            )
                        }
                    },
                    onHold = {
                        pressureState = pressureState.copy(phase = PressurePhase.HOLDING)
                    },
                    onRelease = {
                        pressureState = pressureState.copy(phase = PressurePhase.DONE)
                    },
                    onCancel = {
                        pressureState = pressureState.copy(phase = PressurePhase.IDLE)
                    },
                    onBack = { ironSlot = IronSlot.NONE }
                )
                IronSlot.LEDGER -> TheLedger(
                    blocks = ledgerBlocks,
                    onLink = { url ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
                    },
                    onBack = { ironSlot = IronSlot.NONE }
                )
                IronSlot.NONE -> {}
            }
        },
        replayOverlay = if (showReplayManual) {
            {
                FieldManual(
                    isReplay = true,
                    onboardingCompletedProbe = { true },
                    shizuku = shizukuStatus,
                    root = rootStatus,
                    selectedBackend = preferredBackend,
                    onProbe = { probeKeys() },
                    onSelect = { choice ->
                        val key = if (choice == BackendChoice.SHIZUKU) "shizuku" else "root"
                        prefs.edit().putString("preferred_backend", key).apply()
                        FreezeFramework.setPreferredBackend(if (choice == BackendChoice.SHIZUKU) "Shizuku" else "Root")
                        FpsStack.get(context).syncPreferredBackend(key)
                        probeKeys()
                    },
                    onConfigureShizuku = { openShizukuApp() },
                    onGrantRoot = { probeKeys() },
                    onFinish = { showReplayManual = false },
                    onClose = { showReplayManual = false }
                )
            }
        } else null,
        backendSheet = {
            if (showBackendSheet) {
                BackendBenchSheet(
                    visible = true,
                    onDismiss = { showBackendSheet = false },
                    shizuku = shizukuStatus,
                    root = rootStatus,
                    preferred = preferredBackend,
                    onUse = { choice ->
                        val key = if (choice == BackendChoice.SHIZUKU) "shizuku" else "root"
                        prefs.edit().putString("preferred_backend", key).apply()
                        FreezeFramework.setPreferredBackend(if (choice == BackendChoice.SHIZUKU) "Shizuku" else "Root")
                        FpsStack.get(context).syncPreferredBackend(key)
                        probeKeys()
                        showBackendSheet = false
                    },
                    onConfigure = { choice ->
                        showBackendSheet = false
                        if (choice == BackendChoice.SHIZUKU) {
                            openShizukuApp()
                        } else {
                            probeKeys()
                        }
                    }
                )
            }
            if (showSetupSheet) {
                SystemAccessSheet(
                    visible = true,
                    onDismiss = { showSetupSheet = false },
                    shizuku = shizukuStatus,
                    root = rootStatus,
                    selected = preferredBackend,
                    onProbe = { probeKeys() },
                    onSelect = { choice ->
                        val key = if (choice == BackendChoice.SHIZUKU) "shizuku" else "root"
                        prefs.edit().putString("preferred_backend", key).apply()
                        FreezeFramework.setPreferredBackend(if (choice == BackendChoice.SHIZUKU) "Shizuku" else "Root")
                        FpsStack.get(context).syncPreferredBackend(key)
                        probeKeys()
                    },
                    onConfigureShizuku = { openShizukuApp() },
                    onGrantRoot = { probeKeys() }
                )
            }
        }
    )
}

private fun parsePrivacyBlocks(context: Context): List<MdBlock> {
    return try {
        val lines = context.assets.open("privacy_policy.md").bufferedReader().use { it.readLines() }
        val blocks = mutableListOf<MdBlock>()
        var inCode = false
        val codeLines = mutableListOf<String>()
        val tableRows = mutableListOf<List<String>>()

        fun flushCode() {
            if (codeLines.isNotEmpty()) {
                blocks.add(MdBlock.CodeBlock(codeLines.toList()))
                codeLines.clear()
            }
        }

        fun flushTable() {
            if (tableRows.isNotEmpty()) {
                val header = tableRows.first()
                val rows = if (tableRows.size > 1) tableRows.drop(1) else emptyList()
                blocks.add(MdBlock.Table(header, rows))
                tableRows.clear()
            }
        }

        fun parseSpans(text: String): List<com.ivarna.apexcore.ui.iron.legal.MdSpan> {
            // simple span parser: bold **text**, italic *text*, code `text`, link [label](url)
            val spans = mutableListOf<com.ivarna.apexcore.ui.iron.legal.MdSpan>()
            var remaining = text
            val regex = Regex("""(\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))""")
            var lastIdx = 0
            regex.findAll(text).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIdx) {
                    spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(text.substring(lastIdx, start)))
                }
                val bold = match.groups[2]?.value
                val italic = match.groups[3]?.value
                val code = match.groups[4]?.value
                val linkLabel = match.groups[5]?.value
                val linkUrl = match.groups[6]?.value

                when {
                    bold != null -> spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(bold, bold = true))
                    italic != null -> spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(italic, italic = true))
                    code != null -> spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(code, code = true))
                    linkLabel != null && linkUrl != null -> spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(linkLabel, linkLabel = linkLabel, linkUrl = linkUrl))
                    else -> spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(match.value))
                }
                lastIdx = end
            }
            if (lastIdx < text.length) {
                spans.add(com.ivarna.apexcore.ui.iron.legal.MdSpan(text.substring(lastIdx)))
            }
            return if (spans.isEmpty()) listOf(com.ivarna.apexcore.ui.iron.legal.MdSpan(text)) else spans
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    flushTable()
                    inCode = true
                }
                continue
            }

            if (inCode) {
                codeLines.add(line)
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // separator line check |---|
                if (trimmed.replace("|", "").replace("-", "").replace(":", "").isBlank()) {
                    continue
                }
                val cols = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                tableRows.add(cols)
                continue
            } else {
                flushTable()
            }

            if (trimmed.isBlank()) {
                continue
            }

            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                val headingText = trimmed.drop(level).trim()
                blocks.add(MdBlock.Heading(level, headingText))
                continue
            }

            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                val bulletText = trimmed.drop(2).trim()
                blocks.add(MdBlock.Bullet(parseSpans(bulletText)))
                continue
            }

            blocks.add(MdBlock.Paragraph(parseSpans(trimmed)))
        }
        flushCode()
        flushTable()
        blocks
    } catch (_: Throwable) {
        emptyList<MdBlock>()
    }
}
