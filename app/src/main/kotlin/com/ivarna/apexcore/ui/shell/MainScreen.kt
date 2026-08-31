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
import com.ivarna.apexcore.ui.iron.games.FreezeOutcome
import com.ivarna.apexcore.ui.iron.games.GameLaunchCoordinator
import com.ivarna.apexcore.ui.iron.games.LaunchMatrixScreen
import com.ivarna.apexcore.ui.iron.games.ShutterOverlay
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
import com.ivarna.apexcore.ui.iron.tune.buildTuneCategories
import com.ivarna.apexcore.ui.iron.tune.TuningRoom
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val reducedMotionState = rememberUpdatedState(LocalReducedMotion.current)
    val appContext = remember(context) { context.applicationContext }

    val launchCoordinator = remember(scope, appContext) {
        GameLaunchCoordinator(
            freeze = { targetPkg, onFrozen ->
                // ALLOCATE & LAUNCH: freeze is best-effort. Without elevation we still
                // proceed to PART (same contract as GameLauncher.launch) so the game opens.
                val ready = try {
                    FreezeFramework.isReady()
                } catch (_: Throwable) {
                    false
                }
                if (!ready) {
                    onFrozen(0, 0)
                    return@GameLaunchCoordinator FreezeOutcome.Ok(0, 0)
                }
                val result = FreezeFramework.freezeAll(
                    context = appContext,
                    protectPackages = setOf(targetPkg, appContext.packageName),
                )
                if (result.backend == "blocked") {
                    onFrozen(0, 0)
                    return@GameLaunchCoordinator FreezeOutcome.Ok(0, 0)
                }
                val total = (result.killed + result.failed + result.skipped).coerceAtLeast(0)
                // One-shot framework → single 160ms tick sweep when the result lands.
                onFrozen(result.killed, total)
                FreezeOutcome.Ok(result.killed, total)
            },
            launchIntent = { pkg -> GameLauncher.fireIntent(appContext, pkg) },
            attachRail = { pkg -> GameLauncher.attachRail(appContext, pkg) },
            reducedMotion = { reducedMotionState.value },
            scope = scope,
        )
    }

    var gearTab by rememberSaveable { mutableStateOf(GearTab.HOME) }
    var ironSlot by rememberSaveable { mutableStateOf(IronSlot.NONE) }
    var showBackendSheet by rememberSaveable { mutableStateOf(false) }
    var showSetupSheet by rememberSaveable { mutableStateOf(false) }
    var showPinSheet by rememberSaveable { mutableStateOf(false) }
    var showAddGameSheet by rememberSaveable { mutableStateOf(false) }
    var showReplayManual by rememberSaveable { mutableStateOf(false) }

    var shizukuStatus by remember { mutableStateOf(KeyStatus()) }
    var rootStatus by remember { mutableStateOf(KeyStatus()) }

    // ── SINGLE status/nav bar writer for the MAIN stage ──
    // Vellum = white header/footer + white system bars (dark icons for contrast).
    // Graphite = dark header/footer + dark system bars (light icons).
    val view = androidx.compose.ui.platform.LocalView.current
    val barSkin = ironSkin()
    // User request: top/bottom white on Vellum. Paper => light bars (white bg, dark icons).
    // If user truly wants white icons on white, set lightBars=false, but dark icons are required for readability.
    val lightBars = barSkin.isPaper
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        view.context.findActivity()?.window?.let { w ->
            val c = androidx.core.view.WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = lightBars
            c.isAppearanceLightNavigationBars = lightBars
            // Ensure window bar colors match header/footer theme (white for Vellum)
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    w.isStatusBarContrastEnforced = false
                    w.isNavigationBarContrastEnforced = false
                }
                // For white bars on Vellum, keep dark icons (lightBars=true -> dark icons)
                // User asked "text white" – white icons need dark bar, so we preserve
                // Graphite dark bars with white icons. If they explicitly want white-on-white,
                // flip lightBars to false here.
            } catch (_: Throwable) {}
        }
    }

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
    var autoScanLoading by remember { mutableStateOf(false) }

    fun mapToCards(infos: List<GameInfo>): List<AppCardData> = infos.map { info ->
        AppCardData(
            name = info.name,
            pkg = info.pkg,
            demand = Demand.MEDIUM,
            tint = Iron.Signal500,
            icon = {
                AsyncAppIcon(pkg = info.pkg, contentDescription = info.name)
            }
        )
    }

    fun refreshGames() {
        scope.launch {
            val loaded = gameManager.load()
            gamesList = mapToCards(loaded)
        }
    }

    fun refreshAllApps() {
        scope.launch {
            allAppsLoading = true
            try {
                val apps = gameManager.listInstallableApps(context)
                allAppsList = mapToCards(apps)
            } catch (_: Throwable) {
                allAppsList = emptyList()
            } finally {
                allAppsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshGames()
        refreshAllApps()
    }

    LaunchedEffect(gearTab) {
        if (gearTab == GearTab.GAMES && allAppsList.isEmpty() && !allAppsLoading) {
            refreshAllApps()
        }
    }

    // Overlay state — polled so permission grant / external kill reflects without relaunch
    var overlayGranted by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var overlayPreview by remember { mutableStateOf(GameOverlayService.isRunning && android.provider.Settings.canDrawOverlays(context)) }
    var railSize by remember { mutableStateOf<RailSize>(RailSize.M) }
    var railOpacity by remember { mutableFloatStateOf(0.94f) }
    var railEdge by remember { mutableStateOf<RailEdge>(RailEdge.LEFT) }

    LaunchedEffect(Unit) {
        val s = prefs.getString("hud_size", "M")
        railSize = when (s) { "S" -> RailSize.S; "L" -> RailSize.L; else -> RailSize.M }
        railOpacity = prefs.getFloat("hud_opacity", 0.94f)
        railEdge = if (prefs.getString("hud_edge", "LEFT") == "RIGHT") RailEdge.RIGHT else RailEdge.LEFT
    }
    // Continuous poll: keep HUD toggle / permission pill in sync when user returns from Settings or system kills service
    LaunchedEffect(Unit) {
        while (true) {
            val granted = android.provider.Settings.canDrawOverlays(context)
            val running = GameOverlayService.isRunning
            if (granted != overlayGranted) overlayGranted = granted
            // When permission is revoked, preview must show as not running; when granted, reflect service truth
            val desiredPreview = running && granted
            if (desiredPreview != overlayPreview) overlayPreview = desiredPreview
            // If not granted but somehow overlayPreview was true (stale), force false
            if (!granted && overlayPreview) overlayPreview = false
            kotlinx.coroutines.delay(1000)
        }
    }

    // Tune state
    val tuneManager = remember { com.ivarna.apexcore.tune.TuneManager.get(context) }
    var tuneCategories by remember { mutableStateOf<List<TuneCategoryUi>>(emptyList()) }
    var isTuneProbing by remember { mutableStateOf(false) }
    var tuneProbeError by remember { mutableStateOf<String?>(null) }
    var refreshTuneJob by remember { mutableStateOf<Job?>(null) }
    val tuneSessionActive by tuneManager.sessionActive.collectAsState()
    val sessionVerifiedCount by tuneManager.verifiedAppliedCount.collectAsState()
    val tunePrefs = remember { com.ivarna.apexcore.tune.TunePrefs(context) }
    val tunePresetManager = remember { com.ivarna.apexcore.tune.TunePresetManager(tuneManager) }
    var selectedTuneGamePkg by remember { mutableStateOf<String?>(null) }
    var presetReport by remember { mutableStateOf<com.ivarna.apexcore.tune.TunePresetReport?>(null) }
    var isApplyingMaxPerf by remember { mutableStateOf(false) }
    var showMaxLockAck by remember { mutableStateOf(false) }
    var pendingAckId by remember { mutableStateOf<com.ivarna.apexcore.tune.TuneId?>(null) }
    var pendingAckChecked by remember { mutableStateOf(false) }
    var pendingAckRaw by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gamesList) {
        if (selectedTuneGamePkg == null) selectedTuneGamePkg = gamesList.firstOrNull()?.pkg
        else if (gamesList.none { it.pkg == selectedTuneGamePkg }) {
            selectedTuneGamePkg = gamesList.firstOrNull()?.pkg
        }
    }

    fun refreshTune(pkgOverride: String? = selectedTuneGamePkg, force: Boolean = false) {
        refreshTuneJob?.cancel()
        refreshTuneJob = scope.launch {
            isTuneProbing = true
            tuneProbeError = null
            try {
                val caps = try {
                    tuneManager.refreshCapabilitiesSync(force)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.e("MainScreen", "Tuning capability probe failed", t)
                    tuneProbeError = t.message?.takeIf { it.isNotBlank() } ?: "Capability probe failed"
                    tuneManager.capabilities.value
                }
                val selectedPkg = pkgOverride
                val grouped = buildTuneCategories(
                    caps = caps,
                    selectedPkg = selectedPkg,
                    sessionPkg = tunePrefs.getSessionPkg(),
                    probeFailure = tuneProbeError,
                    gameModeCapability = { pkg -> tuneManager.gameModeCapability(pkg) },
                    intentOf = { id -> tuneManager.intent(id) },
                    onToggle = { id, checked, raw ->
                        if (checked &&
                            (id == com.ivarna.apexcore.tune.TuneId.CPU_LOCK_MAX ||
                                id == com.ivarna.apexcore.tune.TuneId.GPU_LOCK_MAX) &&
                            !tunePrefs.isMaxLockAcked()
                        ) {
                            pendingAckId = id
                            pendingAckChecked = checked
                            pendingAckRaw = raw
                            showMaxLockAck = true
                        } else {
                            tuneManager.setIntent(id, com.ivarna.apexcore.tune.TuneValue(checked, raw))
                            refreshTune(pkgOverride)
                        }
                    },
                    onEnumSelect = { id, token ->
                        tuneManager.setIntent(id, com.ivarna.apexcore.tune.TuneValue(true, token))
                        refreshTune(pkgOverride)
                    },
                    onSliderChange = { id, v ->
                        // Commit-only: TuningRoom Slider calls this onValueChangeFinished only.
                        tuneManager.setIntent(id, com.ivarna.apexcore.tune.TuneValue(true, v.toString()))
                    },
                )
                tuneCategories = grouped
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e("MainScreen", "Tuning category refresh failed", t)
                tuneProbeError = t.message?.takeIf { it.isNotBlank() } ?: "Capability probe failed"
                tuneCategories = buildTuneCategories(
                    caps = tuneManager.capabilities.value,
                    selectedPkg = pkgOverride,
                    sessionPkg = tunePrefs.getSessionPkg(),
                    probeFailure = tuneProbeError,
                    gameModeCapability = { pkg -> tuneManager.gameModeCapability(pkg) },
                    intentOf = { id -> tuneManager.intent(id) },
                    onToggle = { _, _, _ -> },
                    onEnumSelect = { _, _ -> },
                    onSliderChange = { _, _ -> },
                )
            } finally {
                isTuneProbing = false
            }
        }
    }

    LaunchedEffect(ironSlot) {
        if (ironSlot == IronSlot.TUNE) {
            refreshTune()
        }
    }
    LaunchedEffect(selectedTuneGamePkg) {
        // A report for package A must not remain after switching to package B.
        if (presetReport != null &&
            presetReport!!.gamePackage.isNotBlank() &&
            presetReport!!.gamePackage != selectedTuneGamePkg
        ) {
            presetReport = null
        }
        if (ironSlot == IronSlot.TUNE) refreshTune(selectedTuneGamePkg)
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

    IronConfirmDialog(
        visible = showMaxLockAck,
        title = "High-power disclosure",
        body = "Maximum clocks can increase heat and battery drain. ApexCore keeps Android/kernel thermal protection enabled and will release max locks if the device reaches severe thermal stress.",
        confirmLabel = "Acknowledge",
        dismissLabel = "Cancel",
        severity = IronDialogSeverity.Warning,
        onDismiss = { showMaxLockAck = false; pendingAckId = null },
        onConfirm = {
            tunePrefs.setMaxLockAcked(true)
            showMaxLockAck = false
            val ackId = pendingAckId
            if (ackId != null) {
                tuneManager.setIntent(ackId, com.ivarna.apexcore.tune.TuneValue(pendingAckChecked, pendingAckRaw))
                refreshTune()
            } else {
                // Preset ack — retry preset
                val pkg = selectedTuneGamePkg
                if (pkg != null && !isApplyingMaxPerf) {
                    scope.launch {
                        isApplyingMaxPerf = true
                        try {
                            val report = tunePresetManager.applyMaximumPerformance(pkg)
                            presetReport = report
                            refreshTune(pkg)
                            toast.show("${report.applied}/${report.requested} verified")
                        } finally {
                            isApplyingMaxPerf = false
                        }
                    }
                }
            }
            pendingAckId = null
        },
    )

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
                onPins = {
                    if (gearTab != GearTab.GAMES) {
                        gearTab = GearTab.GAMES
                        scope.launch {
                            // Let GearTabTransition (240ms) finish before heavy pin sheet composition
                            delay(260)
                            showPinSheet = true
                        }
                    } else {
                        showPinSheet = true
                    }
                },
                onRamFree = { ironSlot = IronSlot.PRESSURE },
                onSetup = { showSetupSheet = true },
                toast = toast,
                active = gearTab == GearTab.HOME && !showReplayManual
            )
        },
        shutterOverlay = {
            ShutterOverlay(
                state = launchCoordinator.state,
                onCancel = launchCoordinator::cancel,
            )
        },
        games = {
            LaunchMatrixScreen(
                games = gamesList,
                allApps = allAppsList,
                allLoading = allAppsLoading,
                coordinator = launchCoordinator,
                onAdd = {
                    showAddGameSheet = true
                },
                onPin = { showPinSheet = true },
                onAutoScan = {
                    if (autoScanLoading) return@LaunchMatrixScreen
                    scope.launch {
                        autoScanLoading = true
                        try {
                            val before = gameManager.load().size
                            // First try category-based auto-detect (CATEGORY_GAME / isGame meta)
                            gameManager.acceptDetected(context)
                            var loaded = gameManager.load()
                            var added = loaded.size - before
                            // Fallback: if no category games, bulk-load all launchable user apps so the button always populates
                            if (added == 0) {
                                val all = gameManager.listInstallableApps(context)
                                val new = all.filter { a -> loaded.none { it.pkg == a.pkg } }
                                if (new.isNotEmpty()) {
                                    gameManager.addAll(new)
                                    loaded = gameManager.load()
                                    added = loaded.size - before
                                }
                            }
                            gamesList = mapToCards(loaded)
                            // Keep ALL APPS rail in sync
                            if (allAppsList.isEmpty()) refreshAllApps()
                            if (added > 0) toast.show("AUTO SCAN: $added GAMES ADDED")
                            else toast.show("NO NEW GAMES FOUND")
                        } catch (_: Throwable) {
                            toast.show("SCAN FAILED")
                        } finally {
                            autoScanLoading = false
                        }
                    }
                },
                autoScanning = autoScanLoading,
                onRemove = { card ->
                    gameManager.remove(card.pkg)
                    refreshGames()
                },
                addSheet = {
                    if (showAddGameSheet) {
                        var installedApps by remember { mutableStateOf<List<PickerApp>>(emptyList()) }
                        LaunchedEffect(allAppsList, allAppsLoading) {
                            if (allAppsList.isNotEmpty()) {
                                installedApps = allAppsList.map { card ->
                                    PickerApp(
                                        name = card.name,
                                        pkg = card.pkg,
                                        icon = { AsyncAppIcon(pkg = card.pkg, contentDescription = card.name) }
                                    )
                                }
                            } else if (!allAppsLoading) {
                                val src = gameManager.listInstallableApps(context)
                                installedApps = src.map { app ->
                                    PickerApp(
                                        name = app.name,
                                        pkg = app.pkg,
                                        icon = { AsyncAppIcon(pkg = app.pkg, contentDescription = app.name) }
                                    )
                                }
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
                        // React to allAppsList becoming available to avoid duplicate PM scan
                        LaunchedEffect(allAppsList, allAppsLoading) {
                            if (allAppsList.isNotEmpty()) {
                                allInstalled = allAppsList.map { card ->
                                    PickerApp(
                                        name = card.name,
                                        pkg = card.pkg,
                                        icon = { AsyncAppIcon(pkg = card.pkg, contentDescription = card.name) }
                                    )
                                }
                            } else if (!allAppsLoading) {
                                val fetched = gameManager.listInstallableApps(context)
                                allInstalled = fetched.map { app ->
                                    PickerApp(
                                        name = app.name,
                                        pkg = app.pkg,
                                        icon = { AsyncAppIcon(pkg = app.pkg, contentDescription = app.name) }
                                    )
                                }
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
                    if (on) {
                        if (!android.provider.Settings.canDrawOverlays(context)) {
                            overlayGranted = false
                            overlayPreview = false
                            // Guard — don't pretend to start; prompt permission
                            try {
                                android.widget.Toast.makeText(context, "Grant draw-over permission first", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {}
                            // Re-check after toast
                        } else {
                            val started = GameOverlayService.start(context, "")
                            overlayPreview = started && android.provider.Settings.canDrawOverlays(context)
                            if (!started) {
                                try {
                                    android.widget.Toast.makeText(context, "Overlay permission required", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (_: Throwable) {}
                                overlayGranted = android.provider.Settings.canDrawOverlays(context)
                            }
                        }
                    } else {
                        overlayPreview = false
                        GameOverlayService.stop(context)
                        overlayGranted = android.provider.Settings.canDrawOverlays(context)
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
                    sessionApplied = sessionVerifiedCount,
                    isProbing = isTuneProbing,
                    onProbe = { refreshTune(force = true) },
                    onBack = { ironSlot = IronSlot.NONE },
                    selectedGamePkg = selectedTuneGamePkg,
                    gameOptions = gamesList.map { it.pkg to it.name },
                    onGamePkgSelect = { pkg ->
                        if (pkg != selectedTuneGamePkg) presetReport = null
                        selectedTuneGamePkg = pkg
                        refreshTune(pkg, force = true)
                    },
                    onMaximumPerformance = {
                        if (isApplyingMaxPerf) return@TuningRoom
                        val pkg = selectedTuneGamePkg?.takeIf { it.isNotBlank() } ?: run {
                            toast.show("SELECT A GAME FIRST")
                            return@TuningRoom
                        }
                        if (!tunePrefs.isMaxLockAcked()) {
                            // Show ack for preset as well
                            pendingAckId = null // preset ack marker
                            showMaxLockAck = true
                            // store pkg for retry after ack
                            selectedTuneGamePkg = pkg
                            return@TuningRoom
                        }
                        scope.launch {
                            isApplyingMaxPerf = true
                            try {
                                val report = tunePresetManager.applyMaximumPerformance(pkg)
                                presetReport = report
                                refreshTune(pkg)
                                toast.show("${report.applied}/${report.requested} verified")
                            } finally {
                                isApplyingMaxPerf = false
                            }
                        }
                    },
                    presetReport = presetReport,
                    isApplyingMaxPerf = isApplyingMaxPerf,
                    probeError = tuneProbeError,
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
