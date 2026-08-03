package com.ivarna.apexcore.ui.shell

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ivarna.apexcore.R
import com.ivarna.apexcore.SetupDialog
import com.ivarna.apexcore.SetupDialogHelper
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.FreezeResult
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.games.GamesScreen
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.games.WhitelistPickerDialog
import com.ivarna.apexcore.ram.RamFreeScreen
import com.ivarna.apexcore.ui.home.HomeScreen
import com.ivarna.apexcore.ui.overlay.OverlayScreen
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(gameManager: GameManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember { mutableStateOf(State.IDLE) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var backendName by remember { mutableStateOf("Detecting…") }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Layout Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Unified Top Bar
            if (showRamFree) {
                // RamFreeScreen has its own chrome — just add a spacer for status bars
                Spacer(modifier = Modifier.height(0.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("APEX", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CORE", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    
                    // Global backend dropdown
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
                            coroutineScope.launch {
                                try {
                                    FreezeFramework.detect()
                                } catch (_: Throwable) {}
                            }
                        },
                        onOpenSetup = { showSetupDialog = true }
                    )
                }
            }

            // Page Content
            if (showRamFree) {
                RamFreeScreen(
                    onBack = { showRamFree = false },
                    modifier = Modifier.weight(1f)
                )
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        if (targetState == Tab.HOME) {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                        } else if (initialState == Tab.HOME) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                        } else if (targetState == Tab.GAMES) {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
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
                    }
                }
            }

            // Fixed Bottom Navigation Bar
            AnimatedVisibility(visible = !showRamFree) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f))
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavBarItem(
                            label = "BOOST",
                            icon = Icons.Default.Home,
                            isActive = currentTab == Tab.HOME,
                            onClick = { currentTab = Tab.HOME }
                        )
                        NavBarItem(
                            label = "GAMES",
                            icon = Icons.Default.PlayArrow,
                            isActive = currentTab == Tab.GAMES,
                            onClick = { currentTab = Tab.GAMES }
                        )
                        NavBarItem(
                            label = "OVERLAY",
                            icon = Icons.Default.Settings,
                            isActive = currentTab == Tab.OVERLAY,
                            onClick = { currentTab = Tab.OVERLAY }
                        )
                    }
                }
            }
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
    }
}

@Composable
fun NavBarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    val indicatorBackground by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(indicatorBackground)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Column(
            modifier = Modifier.height(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans,
                    letterSpacing = 0.5.sp
                )
            }
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

    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isElevated) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                .clickable { onToggleDropdown() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayName,
                color = if (isElevated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontSize = 9.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { onToggleDropdown() },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shizuku", color = if (currentPref == "shizuku") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (currentPref == "shizuku") FontWeight.Bold else FontWeight.Normal)
                        Text(
                            when (dropdownReadiness["shizuku"]) {
                                true -> "Ready"
                                false -> "Not available"
                                else -> "Checking…"
                            },
                            color = when (dropdownReadiness["shizuku"]) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
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
                        Text("Root", color = if (currentPref == "root") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (currentPref == "root") FontWeight.Bold else FontWeight.Normal)
                        Text(
                            when (dropdownReadiness["root"]) {
                                true -> "Ready"
                                false -> "Not available"
                                else -> "Checking…"
                            },
                            color = when (dropdownReadiness["root"]) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
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

