package com.ivarna.apexcore.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.SetupDialog
import com.ivarna.apexcore.SetupDialogHelper
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.FreezeResult
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.games.GamesScreen
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.games.WhitelistPickerDialog
import com.ivarna.apexcore.ram.RamFreeScreen
import com.ivarna.apexcore.ui.home.HomeScreen
import com.ivarna.apexcore.ui.onboarding.OnboardingScreen
import com.ivarna.apexcore.ui.overlay.OverlayScreen
import com.ivarna.apexcore.ui.settings.SettingsScreen
import com.ivarna.apexcore.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    gameManager: GameManager,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    lightTankBg: Boolean = true,
    onLightTankBgChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember { mutableStateOf(State.IDLE) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var backendName by remember { mutableStateOf("Detecting…") }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }
    var showOnboardingReplay by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<FreezeResult?>(null) }
    var showRamFree by remember { mutableStateOf(false) }
    var globalBackendPref by remember {
        mutableStateOf(
            context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
                .getString("preferred_backend", null)?.takeIf { it == "shizuku" || it == "root" }
        )
    }
    var showGlobalDropdown by remember { mutableStateOf(false) }
    var detectionDone by remember { mutableStateOf(false) }

    // Purge animation states
    var isPurgeAnimActive by remember { mutableStateOf(false) }
    var freedRamText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Cold-start: migrate legacy "standard" pref → needs setup; apply preferred backend
        val prefs = context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
        var prefBackend = prefs.getString("preferred_backend", null)
        if (prefBackend == "standard") {
            prefs.edit().remove("preferred_backend").apply()
            prefBackend = null
        }
        val preferredName = when (prefBackend) {
            "shizuku" -> "Shizuku"
            "root" -> "Root"
            else -> null
        }
        FreezeFramework.setPreferredBackend(preferredName)
        // FPS stack follows same mode (Root / Shizuku / Auto)
        FpsStack.get(context).syncPreferredBackend(prefBackend)

        val backend = FreezeFramework.detect()
        detectionDone = true
        backendName = backend?.name ?: "SETUP REQUIRED"
        val setupPrefs = context.getSharedPreferences(SetupDialogHelper.PREFS, Context.MODE_PRIVATE)
        if (!setupPrefs.getBoolean(SetupDialogHelper.KEY_SHOWN, false) && backend == null) {
            showSetupDialog = true
        }
    }

    val activeBackend by FreezeFramework.activeBackend.collectAsState(initial = null)
    LaunchedEffect(activeBackend) {
        // Elevated→null mid-session (revoke / grant loss) must flip the chip back to
        // setup; before the first probe settles, keep "Detecting…" (no flash).
        val backend = activeBackend
        when {
            backend != null -> backendName = backend.name
            detectionDone -> backendName = "SETUP REQUIRED"
        }
    }

    // Shared HazeState — content uses .haze(), chrome uses .hazeChild() (finalbenchmark pattern)
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Content is the blur source — edge-to-edge (incl. under status bar + top chrome)
        // so frost has real pixels to sample. Screens reserve top/bottom clearance.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        ) {
            if (showRamFree) {
                RamFreeScreen(
                    onBack = { showRamFree = false },
                    modifier = Modifier.weight(1f)
                )
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        // Nav order: HOME → GAMES → OVERLAY → SETTINGS (left → right)
                        val goingRight = targetState.ordinal > initialState.ordinal
                        if (goingRight) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            state = state,
                            backendName = backendName,
                            lastResult = lastResult,
                            isPurgeAnimActive = isPurgeAnimActive,
                            freedRamText = freedRamText,
                            lightTankBg = lightTankBg,
                            onPurgeAnimComplete = {
                                isPurgeAnimActive = false
                                state = State.RESULT
                            },
                            onBoostClick = {
                                if (state == State.BOOSTING || isPurgeAnimActive) return@HomeScreen
                                if (state == State.RESULT) {
                                    state = State.IDLE
                                    freedRamText = ""
                                    return@HomeScreen
                                }
                                coroutineScope.launch {
                                    if (!FreezeFramework.isReady()) {
                                        // Decision E: no freezeAll without Shizuku/Root — setup instead
                                        showSetupDialog = true
                                        return@launch
                                    }
                                    state = State.BOOSTING
                                    isPurgeAnimActive = true
                                    val result = FreezeFramework.freezeAll(context)
                                    lastResult = result
                                    val freedMb = result.freedKb / 1024f
                                    val swapFreedMb = result.swapFreedKb / 1024f
                                    freedRamText = if (swapFreedMb > 0f) {
                                        "+%d MB RAM (+%d MB Swap)".format(freedMb.toInt(), swapFreedMb.toInt())
                                    } else {
                                        "+%d MB".format(freedMb.toInt())
                                    }
                                }
                            },
                            onSetupClick = { showSetupDialog = true },
                            onRamFreeClick = { showRamFree = true },
                            onPinClick = { showPinPicker = true }
                        )
                        Tab.GAMES -> GamesScreen(gameManager = gameManager)
                        Tab.OVERLAY -> OverlayScreen()
                        Tab.SETTINGS -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            lightTankBg = lightTankBg,
                            onLightTankBgChange = onLightTankBgChange,
                            activeBackendName = backendName,
                            preferredBackend = globalBackendPref,
                            onSetupClick = { showSetupDialog = true },
                            onShowOnboarding = { showOnboardingReplay = true }
                        )
                    }
                }
            }
        }

        // Frosted top bar + status bar — absolute top, full-bleed haze strip
        AnimatedVisibility(
            visible = !showRamFree,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            ZenTopBar(
                hazeState = hazeState,
                backendChip = {
                    GlobalBackendDropdown(
                        currentPref = globalBackendPref,
                        backendName = backendName,
                        showDropdown = showGlobalDropdown,
                        onToggleDropdown = { showGlobalDropdown = !showGlobalDropdown },
                        onSelectPref = { pref ->
                            globalBackendPref = pref
                            context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
                                .edit().putString("preferred_backend", pref).apply()
                            showGlobalDropdown = false
                            val preferredName = when (pref) {
                                "shizuku" -> "Shizuku"
                                "root" -> "Root"
                                else -> null
                            }
                            FreezeFramework.setPreferredBackend(preferredName)
                            // FPS privilege mode tracks top-bar selection
                            FpsStack.get(context).syncPreferredBackend(pref)
                            coroutineScope.launch {
                                try {
                                    FreezeFramework.detect()
                                } catch (_: Throwable) {}
                            }
                        },
                        onOpenSetup = { showSetupDialog = true }
                    )
                }
            )
        }

        // Frosted bottom island — overlays content; screens reserve bottomNavClearance
        AnimatedVisibility(
            visible = !showRamFree,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            ZenBottomNav(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                hazeState = hazeState
            )
        }

        if (showSetupDialog && FreezeFramework.resolver() != null) {
            SetupDialog(resolver = FreezeFramework.resolver()!!, onDismiss = { showSetupDialog = false })
        }

        if (showPinPicker) {
            WhitelistPickerDialog(
                gameManager = gameManager,
                onDismiss = { showPinPicker = false }
            )
        }

        if (showOnboardingReplay) {
            OnboardingScreen(
                onFinish = { showOnboardingReplay = false },
                isReplay = true
            )
        }
    }
}

@Composable
fun GlobalBackendDropdown(
    currentPref: String?,
    backendName: String,
    showDropdown: Boolean,
    onToggleDropdown: () -> Unit,
    onSelectPref: (String) -> Unit,
    onOpenSetup: () -> Unit
) {
    var dropdownReadiness by remember { mutableStateOf<Map<String, Boolean?>>(emptyMap()) }

    LaunchedEffect(showDropdown) {
        if (showDropdown) {
            dropdownReadiness = mapOf(
                "shizuku" to ShizukuFreezeBackend().isReady(),
                "root" to RootFreezeBackend().isReady()
            )
        }
    }

    val displayName = when (currentPref) {
        "shizuku" -> "SHIZUKU"
        "root" -> "ROOT"
        else -> if (backendName == "Shizuku" || backendName == "Root") backendName.uppercase() else "SETUP"
    }
    // Chip color follows real backend: no elevation = warning; Shizuku/Root = elevated
    val isElevated = backendName == "Shizuku" || backendName == "Root"

    val scheme = MaterialTheme.colorScheme
    Box {
        // High-contrast chip: primaryContainer fill + onPrimaryContainer text when elevated
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (isElevated) scheme.primaryContainer
                    else scheme.secondaryContainer
                )
                .clickable { onToggleDropdown() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayName,
                color = if (isElevated) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                fontSize = 9.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { onToggleDropdown() },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Shizuku",
                            color = if (currentPref == "shizuku") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = if (currentPref == "shizuku") FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            when (dropdownReadiness["shizuku"]) {
                                true -> "Ready"
                                false -> "Not available"
                                else -> "Checking…"
                            },
                            color = when (dropdownReadiness["shizuku"]) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                },
                onClick = {
                    if (dropdownReadiness["shizuku"] == true) onSelectPref("shizuku")
                    else onOpenSetup()
                }
            )

            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Root",
                            color = if (currentPref == "root") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = if (currentPref == "root") FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            when (dropdownReadiness["root"]) {
                                true -> "Ready"
                                false -> "Not available"
                                else -> "Checking…"
                            },
                            color = when (dropdownReadiness["root"]) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                },
                onClick = {
                    if (dropdownReadiness["root"] == true) onSelectPref("root")
                    else onOpenSetup()
                }
            )
        }
    }
}

