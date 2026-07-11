package com.ivarna.apexcore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.FreezeResult
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.freeze.AccessibilityFreezeBackend
import com.ivarna.apexcore.games.GamesScreen
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.games.GameOverlayService
import com.ivarna.apexcore.ui.components.SimpleMemoryDisplay
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val gameManager by lazy { GameManager(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreezeFramework.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ApexCoreTheme {
                MainScreen(gameManager)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        FreezeFramework.resolver()?.invalidate()
    }
}

enum class State { IDLE, BOOSTING, RESULT }
enum class Tab { HOME, GAMES, OVERLAY }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(gameManager: GameManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember { mutableStateOf(State.IDLE) }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var backendName by remember { mutableStateOf("Detecting…") }
    var showSetupDialog by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<FreezeResult?>(null) }

    // Purge animation states
    var isPurgeAnimActive by remember { mutableStateOf(false) }
    var freedRamText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val backend = FreezeFramework.detect()
        backendName = backend.name
        val prefs = context.getSharedPreferences(SetupDialogHelper.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SetupDialogHelper.KEY_SHOWN, false) && backendName == "cached only") {
            showSetupDialog = true
        }
    }

    val activeBackend by FreezeFramework.activeBackend.collectAsState(initial = null)
    LaunchedEffect(activeBackend) {
        backendName = activeBackend?.name ?: "Detecting…"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Main Layout Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Unified Top Bar
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
                    Text("APEX", color = TextTitle, fontSize = 16.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CORE", color = AccentPrimary, fontSize = 16.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                
                // Mode indicator / Setup toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (backendName == "cached only") AccentWarning.copy(alpha = 0.15f) else AccentSuccess.copy(alpha = 0.15f))
                        .clickable { showSetupDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = backendName.uppercase(),
                        color = if (backendName == "cached only") AccentWarning else AccentSuccess,
                        fontSize = 9.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Page Content with Smooth Transition
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
                            state = State.BOOSTING
                            isPurgeAnimActive = true
                            coroutineScope.launch {
                                val result = FreezeFramework.freezeAll(context)
                                lastResult = result
                                val freedMb = result.freedKb / 1024f
                                freedRamText = if (freedMb >= 1024) {
                                    "+%.2f GB".format(freedMb / 1024f)
                                } else {
                                    "+%d MB".format(freedMb.toInt())
                                }
                            }
                        },
                        onSetupClick = { showSetupDialog = true }
                    )
                    Tab.GAMES -> GamesScreen(gameManager = gameManager)
                    Tab.OVERLAY -> OverlayScreen()
                }
            }

            // Fixed Bottom Navigation Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard.copy(alpha = 0.95f))
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderGlass)
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

        if (showSetupDialog && FreezeFramework.resolver() != null) {
            SetupDialog(resolver = FreezeFramework.resolver()!!, onDismiss = { showSetupDialog = false })
        }
    }
}

data class MemStats(
    val ramUsedKb: Long,
    val ramTotalKb: Long,
    val swapUsedKb: Long,
    val swapTotalKb: Long
)

fun getSystemMemStats(context: Context): MemStats {
    var ramTotal = 0L; var ramAvail = 0L
    var swapTotal = 0L; var swapFree = 0L
    try {
        java.io.File("/proc/meminfo").useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("MemTotal:") -> ramTotal = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("MemAvailable:") -> ramAvail = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("SwapTotal:") -> swapTotal = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") -> swapFree = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                }
            }
        }
    } catch (_: Throwable) {}
    if (ramTotal == 0L) {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            ramTotal = memInfo.totalMem / 1024
            ramAvail = memInfo.availMem / 1024
        } catch (_: Throwable) {}
    }
    val ramUsed = (ramTotal - ramAvail).coerceAtLeast(0)
    val swapUsed = (swapTotal - swapFree).coerceAtLeast(0)
    return MemStats(ramUsed, ramTotal, swapUsed, swapTotal)
}

fun getSystemRamKb(context: Context): Pair<Long, Long> {
    val stats = getSystemMemStats(context)
    return stats.ramUsedKb to stats.ramTotalKb
}

@Composable
fun HomeScreen(
    state: State,
    backendName: String,
    lastResult: FreezeResult?,
    isPurgeAnimActive: Boolean,
    freedRamText: String,
    onPurgeAnimComplete: () -> Unit,
    onBoostClick: () -> Unit,
    onSetupClick: () -> Unit
) {
    val context = LocalContext.current
    var memStats by remember { mutableStateOf(getSystemMemStats(context)) }
    val actualFreedMb = if (lastResult != null && freedRamText.isNotEmpty()) (lastResult.freedKb / 1024f) else -1f

    // Periodically update memory stats
    LaunchedEffect(state) {
        memStats = getSystemMemStats(context)
        while (state == State.IDLE || state == State.RESULT) {
            delay(3000)
            memStats = getSystemMemStats(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Simple Memory Display (RAM + SWAP bars)
        SimpleMemoryDisplay(
            ramUsedKb = memStats.ramUsedKb,
            ramTotalKb = memStats.ramTotalKb,
            swapUsedKb = memStats.swapUsedKb,
            swapTotalKb = memStats.swapTotalKb,
            state = state,
            isPurgeAnimActive = isPurgeAnimActive,
            actualFreedMb = actualFreedMb,
            freedRamText = freedRamText,
            onPurgeAnimComplete = onPurgeAnimComplete,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(28.dp))
        
        // Status updates description
        val statusColor = if (state == State.BOOSTING) AccentWarning else TextMuted
        val statusText = when (state) {
            State.IDLE -> "● Ready to purge bloat"
            State.BOOSTING -> "● PURGING BACKGROUND PROCESSES…"
            State.RESULT -> if ((lastResult?.killed ?: 0) == 0) "● System fully optimized" 
                            else "● Freed ${lastResult?.killed} background apps"
        }
        Text(
            text = statusText.uppercase(),
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = JetBrainsMono,
            letterSpacing = 1.sp
        )
        
        if (backendName == "cached only") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "> CONFIGURE ELEVATED ACCESS",
                color = AccentWarning,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                modifier = Modifier
                    .clickable { onSetupClick() }
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Combined Action and Result Card
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(400)) togetherWith fadeOut(tween(300))
            },
            label = "action_card_transition"
        ) { targetState ->
            if (targetState == State.RESULT) {
                UnifiedResultCard(
                    lastResult = lastResult,
                    onClick = onBoostClick
                )
            } else {
                MainActionCard(state = targetState, onClick = onBoostClick)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SystemDiagnosticsCard(onSetupClick = onSetupClick)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FreedTextAnimationOverlay(
    text: String,
    trigger: Boolean,
    isLocked: Boolean,
    onComplete: () -> Unit
) {
    val offsetAnim = remember { Animatable(180f) }
    val scaleAnim = remember { Animatable(0.4f) }
    val alphaAnim = remember { Animatable(0f) }

    // Trigger initial rise when trigger turns true
    LaunchedEffect(trigger) {
        if (!trigger) {
            alphaAnim.snapTo(0f)
            return@LaunchedEffect
        }
        offsetAnim.snapTo(180f)
        scaleAnim.snapTo(0.4f)
        alphaAnim.snapTo(0f)

        // Phase 1: Shoot up and overshoot scale
        val jobOffset = launch {
            offsetAnim.animateTo(0f, tween(400, easing = EaseOutBack))
        }
        val jobScale = launch {
            scaleAnim.animateTo(1.05f, tween(400, easing = EaseOutBack))
            scaleAnim.animateTo(1.0f, tween(150))
        }
        val jobAlpha = launch {
            alphaAnim.animateTo(1f, tween(250, easing = EaseOutQuad))
        }
        jobOffset.join()
        jobScale.join()
        jobAlpha.join()
    }

    // Trigger mechanical click pulse when isLocked becomes true and hold 1.5s then complete
    LaunchedEffect(isLocked) {
        if (isLocked && trigger) {
            scaleAnim.animateTo(1.12f, tween(100, easing = EaseOutQuad))
            scaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))

            delay(1500) // Exactly 1.5s center display per docs/design.md

            val jobSettle = launch {
                offsetAnim.animateTo(-60f, tween(300, easing = EaseInQuad))
            }
            val jobFade = launch {
                alphaAnim.animateTo(0f, tween(300))
            }
            jobSettle.join()
            jobFade.join()

            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AccentPrimary,
            fontSize = 32.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .offset(y = offsetAnim.value.dp)
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MainActionCard(state: State, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Tactile 3D button compress effects
    val buttonOffsetY by animateDpAsState(
        targetValue = if (isPressed) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "button_offset_y"
    )
    val shadowOffsetY by animateDpAsState(
        targetValue = if (isPressed) 3.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shadow_offset_y"
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 0.45f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "shadow_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "infinite_transitions")
    
    val breathScaleState = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )
    val breathScale = if (state == State.IDLE) breathScaleState.value else 1f

    val sweepPulse = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep_pulse"
    )

    // Brushed metal texture simulated via multi-stop linear gradient
    val brushedMetalGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2E3440),
            Color(0xFF4C566A),
            Color(0xFF2E3440),
            Color(0xFF5E677A),
            Color(0xFF2E3440)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(breathScale),
        contentAlignment = Alignment.Center
    ) {
        // --- 1. Drop Shadow ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = shadowOffsetY)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent)
                    )
                )
        )

        // --- 2. Metal Bezel Lip ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 8.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(32.dp)
                )
        )

        // --- 3. Purge Button Brushed Metal Face ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = buttonOffsetY)
                .clip(RoundedCornerShape(32.dp))
                .background(brushedMetalGradient)
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()
                // Top glass highlights
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    style = Stroke(width = strokeWidth),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx(), 32.dp.toPx())
                )

                if (state == State.BOOSTING) {
                    drawRoundRect(
                        color = AccentPrimary.copy(alpha = sweepPulse.value),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx(), 32.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    val title = if (state == State.BOOSTING) "PURGING SYSTEM..." else "PURGE ENGINE"
                    val subtitle = if (state == State.BOOSTING) "FREEZING BACKGROUND SERVICES" else "TRIGGER SYSTEM CLEAN & OPTIMIZE"
                    
                    Text(
                        text = title,
                        color = TextTitle,
                        fontSize = 20.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextBody,
                        fontSize = 11.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    )
                }

                // Lightning Purge Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.58f, 0f)
                            lineTo(size.width * 0.15f, size.height * 0.55f)
                            lineTo(size.width * 0.48f, size.height * 0.55f)
                            lineTo(size.width * 0.40f, size.height)
                            lineTo(size.width * 0.85f, size.height * 0.45f)
                            lineTo(size.width * 0.52f, size.height * 0.45f)
                            close()
                        }
                        drawPath(path = path, color = Color.Black.copy(alpha = 0.25f))
                        drawPath(path = path, color = AccentPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedResultCard(
    lastResult: FreezeResult?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "press_scale"
    )

    val isZero = lastResult?.killed == 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .background(SurfaceCard)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(AccentPrimary.copy(alpha = 0.6f), AccentSecondary.copy(alpha = 0.6f))
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        drawCircle(
                            color = AccentSuccess.copy(alpha = 0.15f),
                            radius = size.minDimension / 2
                        )
                        drawCircle(
                            color = AccentSuccess,
                            radius = size.minDimension / 2.4f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        val path = Path().apply {
                            moveTo(size.width * 0.35f, size.height * 0.5f)
                            lineTo(size.width * 0.45f, size.height * 0.6f)
                            lineTo(size.width * 0.65f, size.height * 0.4f)
                        }
                        drawPath(
                            path = path,
                            color = AccentSuccess,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PURGE COMPLETE",
                            color = TextTitle,
                            fontSize = 14.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isZero) "Memory fully purified" else "Bloat successfully cleared",
                            color = TextBody,
                            fontFamily = Inter,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentPrimary.copy(alpha = 0.12f))
                        .border(1.dp, AccentPrimary.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "PURGE AGAIN",
                        color = AccentPrimary,
                        fontSize = 9.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGlass)
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "FREED SIZE",
                        value = if (isZero) "0 MB" else {
                            val freedMb = (lastResult?.freedKb ?: 0L) / 1024f
                            if (freedMb >= 1024) "%.2f GB".format(freedMb / 1024f) else "%d MB".format(freedMb.toInt())
                        },
                        subtitle = "RAM reclaimed",
                        indicatorColor = AccentPrimary,
                        valueColor = AccentPrimary,
                        delayMs = 100
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "PURGED APPS",
                        value = (lastResult?.killed ?: 0).toString(),
                        subtitle = "Processes killed",
                        indicatorColor = AccentWarning,
                        valueColor = TextTitle,
                        delayMs = 150
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "DURATION",
                        value = if (lastResult != null) "${lastResult.durationMs / 1000f}s" else "—",
                        subtitle = "Purge execution",
                        indicatorColor = TextMuted,
                        valueColor = TextTitle,
                        delayMs = 200
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "ERRORS",
                        value = (lastResult?.failed ?: 0).toString(),
                        subtitle = "Skipped processes",
                        indicatorColor = if ((lastResult?.failed ?: 0) > 0) AccentWarning else TextMuted,
                        valueColor = if ((lastResult?.failed ?: 0) > 0) AccentWarning else TextBody,
                        delayMs = 250
                    )
                }
            }
        }
    }
}


@Composable
fun StatItem(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    indicatorColor: Color,
    valueColor: Color,
    delayMs: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "stat_alpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "stat_translation"
    )

    Row(
        modifier = modifier
            .graphicsLayer(
                alpha = alpha,
                translationY = translationY
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = TextBody,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun StatTile(modifier: Modifier, title: String, value: String, subtitle: String, valueColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, color = valueColor, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, color = TextBody, fontSize = 12.sp)
    }
}

@Composable
fun OverlayScreen(context: Context = LocalContext.current) {
    var hasPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var testOverlayActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            hasPermission = android.provider.Settings.canDrawOverlays(context)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "HUD OVERLAY",
            color = TextTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Configure floating gameplay monitor",
            color = TextMuted,
            fontSize = 12.sp
        )
        
        Spacer(modifier = Modifier.height(28.dp))

        // Permission Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (hasPermission) AccentSuccess.copy(alpha = 0.1f) else AccentWarning.copy(alpha = 0.1f))
                .border(1.dp, if (hasPermission) AccentSuccess.copy(alpha = 0.4f) else AccentWarning.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = if (hasPermission) "PERMISSION GRANTED" else "ACTION REQUIRED",
                    color = if (hasPermission) AccentSuccess else AccentWarning,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasPermission) "ApexCore has permission to render the performance HUD on top of other games."
                           else "To display the real-time FPS & memory monitor during gaming, please grant the Draw Over Apps permission.",
                    color = TextBody,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentWarning)
                            .clickable {
                                try {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(intent)
                                } catch (_: Throwable) {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("GRANT PERMISSION", color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Test HUD Controls
        Text(
            text = "TEST HUD OVERLAY",
            color = TextTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Launch a dummy monitor to test placement, transparency, and drag gestures directly on this screen.",
                    color = TextBody,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (testOverlayActive) BorderGlass else AccentPrimary)
                            .clickable(enabled = hasPermission && !testOverlayActive) {
                                GameOverlayService.start(context, context.packageName)
                                testOverlayActive = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "START TEST HUD",
                            color = if (testOverlayActive) TextMuted else TextTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (testOverlayActive) AccentWarning.copy(alpha=0.2f) else BorderGlass)
                            .clickable(enabled = testOverlayActive) {
                                GameOverlayService.stop(context)
                                testOverlayActive = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "STOP TEST HUD",
                            color = if (testOverlayActive) AccentWarning else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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
        targetValue = if (isActive) AccentPrimary else TextMuted,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    val indicatorBackground by animateColorAsState(
        targetValue = if (isActive) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent,
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
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun SystemDiagnosticsCard(
    onSetupClick: () -> Unit
) {
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var hasShizuku by remember { mutableStateOf<Boolean?>(null) }
    var hasAccessibility by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            hasRoot = RootFreezeBackend().isReady()
            hasShizuku = ShizukuFreezeBackend().isReady()
            hasAccessibility = AccessibilityFreezeBackend().isReady()
            delay(3000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .clickable { onSetupClick() }
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "ACCESS DIAGNOSTICS",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            DiagnosticRow(
                label = "Root Access Presence",
                status = hasRoot,
                description = "Checks if direct 'su' command is available"
            )
            Spacer(modifier = Modifier.height(12.dp))
            DiagnosticRow(
                label = "Shizuku Service Connection",
                status = hasShizuku,
                description = "Checks if Shizuku binder is running & authorized"
            )
            Spacer(modifier = Modifier.height(12.dp))
            DiagnosticRow(
                label = "Manual Torture (Accessibility)",
                status = hasAccessibility,
                description = "Checks if force-stop automation is enabled"
            )
        }
    }
}

@Composable
fun DiagnosticRow(
    label: String,
    status: Boolean?,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (iconColor, statusText) = when (status) {
            true -> AccentSuccess to "ACTIVE"
            false -> AccentWarning to "INACTIVE"
            null -> TextMuted to "CHECKING…"
        }
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(iconColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = TextBody,
                fontSize = 11.sp
            )
        }
        Text(
            text = statusText,
            color = iconColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
