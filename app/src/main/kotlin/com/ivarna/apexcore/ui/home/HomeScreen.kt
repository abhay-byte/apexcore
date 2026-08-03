package com.ivarna.apexcore.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.freeze.FreezeResult
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.ui.components.MemoryLeafPair
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.components.zenFrostCard
import com.ivarna.apexcore.ui.shell.State
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: State,
    backendName: String,
    lastResult: FreezeResult?,
    isPurgeAnimActive: Boolean,
    freedRamText: String,
    lightTankBg: Boolean = true,
    onPurgeAnimComplete: () -> Unit,
    onBoostClick: () -> Unit,
    onSetupClick: () -> Unit,
    onRamFreeClick: () -> Unit,
    onPinClick: () -> Unit
) {
    val context = LocalContext.current
    var memStats by remember { mutableStateOf(getSystemMemStats(context)) }
    val actualFreedMb = if (lastResult != null && freedRamText.isNotEmpty()) (lastResult.freedKb / 1024f) else -1f

    // Local Haze for purge-card frost only.
    // MUST be sibling source/child (not hazeChild nested inside MainScreen's haze tree
    // sampling that same state) — nested same-state caused RenderThread SIGSEGV.
    val cardHazeState = remember { HazeState() }

    LaunchedEffect(state) {
        memStats = getSystemMemStats(context)
        while (state == State.IDLE || state == State.RESULT) {
            delay(3000)
            memStats = getSystemMemStats(context)
        }
    }

    LaunchedEffect(lastResult) {
        if (lastResult != null) memStats = getSystemMemStats(context)
    }

    val scheme = MaterialTheme.colorScheme
    val isElevatedBackend = backendName == "Shizuku" || backendName == "Root"
    // Full onSurfaceVariant — no extra alpha wash (contrast)
    val statusColor = if (state == State.BOOSTING) scheme.secondary else scheme.onSurfaceVariant
    val statusActive: Boolean? = when (state) {
        State.IDLE -> if (isElevatedBackend) true else false
        State.BOOSTING -> null
        State.RESULT -> when {
            (lastResult?.backend ?: "") !in listOf("Shizuku", "Root") -> false
            (lastResult?.killed ?: 0) == 0 && (lastResult?.freedKb ?: 0) == 0L -> true
            else -> true
        }
    }
    // Compliance C9 / C10 / C13 meanings — no ● character
    val statusText = when (state) {
        State.IDLE -> if (isElevatedBackend) "Ready to purge bloat"
        else "Connect Shizuku or Root for deep freeze"
        State.BOOSTING -> "PURGING BACKGROUND PROCESSES…"
        State.RESULT -> when {
            (lastResult?.backend ?: "") !in listOf("Shizuku", "Root") ->
                "Freeze blocked — connect Shizuku or Root"
            (lastResult?.killed ?: 0) == 0 && (lastResult?.freedKb ?: 0) == 0L ->
                "Already optimized"
            (lastResult?.killed ?: 0) == 0 ->
                "System fully optimized"
            else ->
                "Freed ${lastResult?.killed} background apps"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blur SOURCE for the result card — decorative layers only (sibling of content).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = cardHazeState)
        ) {
            // Atmosphere orbs — theme-aware (works light + dark)
            val primaryOrbColor = scheme.primaryContainer.copy(alpha = 0.14f)
            val secondaryOrbColor = scheme.secondaryContainer.copy(alpha = 0.12f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryOrbColor, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.08f),
                        radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.18f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryOrbColor, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.72f),
                        radius = size.minDimension * 0.5f
                    ),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.88f, size.height * 0.72f)
                )
            }

            // Organic vines + blooms — Home only, behind content, decorative
            HomeNatureBackground(dimmed = state == State.BOOSTING)
        }

        // Foreground UI — not inside cardHaze source; card uses hazeChild as sibling
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZenDimens.containerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clearance: status bar + floating frosted top bar
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(ZenDimens.topBarClearance))
            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            MemoryLeafPair(
                ramUsedKb = memStats.ramUsedKb,
                ramTotalKb = memStats.ramTotalKb,
                swapUsedKb = memStats.swapUsedKb,
                swapTotalKb = memStats.swapTotalKb,
                isPurgeAnimActive = isPurgeAnimActive,
                actualFreedMb = actualFreedMb,
                freedRamText = freedRamText,
                onPurgeAnimComplete = onPurgeAnimComplete,
                lightTankBg = lightTankBg,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status line: StatusPebble + text (no ●)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StatusPebble(active = statusActive, size = 10.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText.uppercase(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    letterSpacing = 1.sp
                )
            }

            if (!isElevatedBackend && backendName != "Detecting…") {
                Spacer(modifier = Modifier.height(12.dp))
                ShizukuConnectBanner(onConnectClick = onSetupClick)
            }

            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            // PebbleButton or UnifiedResultCard via AnimatedContent
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
                        cardHazeState = cardHazeState,
                        onClick = onBoostClick
                    )
                } else {
                    PebbleButton(
                        state = targetState,
                        onClick = onBoostClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            HomeAnimatedEntryRow(
                title = "RAM Free",
                subtitle = "Force system reclaim",
                icon = ZenIcons.WaterDrop,
                enabled = state != State.BOOSTING,
                flowerStyle = 0,
                onClick = onRamFreeClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            HomeAnimatedEntryRow(
                title = "Pin Apps",
                subtitle = "Protect apps from being frozen",
                icon = ZenIcons.PushPin,
                enabled = state != State.BOOSTING,
                flowerStyle = 1,
                onClick = onPinClick
            )

            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            // Dummy game-tuning toggles — UI only; gated on Shizuku / Root
            GameOptimisationToggles(
                enabled = isElevatedBackend && state != State.BOOSTING,
                elevated = isElevatedBackend
            )

            // Clearance for floating bottom-nav island (true overlay)
            Spacer(modifier = Modifier.height(ZenDimens.bottomNavClearance))
        }
    }
}

private const val GAME_OPT_PREFS = "apexcore"
private const val KEY_GPU_RENDER = "dummy_opt_gpu_render"
private const val KEY_CPU_THREAD = "dummy_opt_cpu_thread"
private const val KEY_OPENGL = "dummy_opt_opengl"
private const val KEY_KERNEL = "dummy_opt_kernel"

/**
 * Home-only dummy game optimisation toggles.
 * Interactive only when Shizuku or Root is the active elevated backend.
 * Values persist in prefs for UI continuity; no system side effects yet.
 */
@Composable
private fun GameOptimisationToggles(
    enabled: Boolean,
    elevated: Boolean
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val prefs = remember {
        context.getSharedPreferences(GAME_OPT_PREFS, Context.MODE_PRIVATE)
    }

    var gpuRender by remember {
        mutableStateOf(prefs.getBoolean(KEY_GPU_RENDER, false))
    }
    var cpuThread by remember {
        mutableStateOf(prefs.getBoolean(KEY_CPU_THREAD, false))
    }
    var openGl by remember {
        mutableStateOf(prefs.getBoolean(KEY_OPENGL, false))
    }
    var kernel by remember {
        mutableStateOf(prefs.getBoolean(KEY_KERNEL, false))
    }

    fun persist(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    val contentAlpha = if (enabled) 1f else 0.5f

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "GAME OPTIMISATION",
            color = scheme.onSurfaceVariant.copy(alpha = contentAlpha),
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp)
        )

        HomeFlowerCard(
            style = 3,
            sizeScale = 1.35f,
            flowerAlpha = 0.8f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!elevated) {
                    Text(
                        text = "Connect Shizuku or Root to unlock",
                        color = scheme.secondary,
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                DummyOptToggleRow(
                    title = "GPU render optimisation",
                    subtitle = "Prioritise frame composition for games",
                    checked = gpuRender,
                    enabled = enabled,
                    onCheckedChange = {
                        gpuRender = it
                        persist(KEY_GPU_RENDER, it)
                    }
                )
                DummyOptToggleRow(
                    title = "CPU game threading priority",
                    subtitle = "Boost game-thread scheduling",
                    checked = cpuThread,
                    enabled = enabled,
                    onCheckedChange = {
                        cpuThread = it
                        persist(KEY_CPU_THREAD, it)
                    }
                )
                DummyOptToggleRow(
                    title = "OpenGL GPU optimisation",
                    subtitle = "Tune GL driver hints for games",
                    checked = openGl,
                    enabled = enabled,
                    onCheckedChange = {
                        openGl = it
                        persist(KEY_OPENGL, it)
                    }
                )
                DummyOptToggleRow(
                    title = "Kernel game optimisation",
                    subtitle = "Kernel-level game performance hints",
                    checked = kernel,
                    enabled = enabled,
                    onCheckedChange = {
                        kernel = it
                        persist(KEY_KERNEL, it)
                    }
                )
            }
        }
    }
}

/**
 * Home entry row with looping flower décor + press / idle motion.
 * Keeps ZenEntryRow unchanged for other screens.
 */
@Composable
private fun HomeAnimatedEntryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    flowerStyle: Int,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentAlpha = if (enabled) 1f else 0.45f

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "entry_press"
    )
    val infinite = rememberInfiniteTransition(label = "entry_$title")
    val idleBreath by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400 + flowerStyle * 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "entry_breath"
    )
    val iconFloat by infinite.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800 + flowerStyle * 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_float"
    )
    val chevronNudge by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron"
    )

    val scale = if (enabled) pressScale * idleBreath else 1f

    HomeFlowerCard(
        style = flowerStyle,
        sizeScale = 1.3f,
        flowerAlpha = if (enabled) 0.85f else 0.5f,
        modifier = Modifier
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { translationY = if (enabled) iconFloat else 0f }
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.12f * contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        translationX = if (enabled) chevronNudge else 0f
                    }
            )
        }
    }
}

@Composable
private fun DummyOptToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = scheme.onSurface.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = scheme.onSurfaceVariant.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary,
                uncheckedThumbColor = scheme.outline,
                uncheckedTrackColor = scheme.surfaceContainerHighest,
                disabledCheckedThumbColor = scheme.onPrimary.copy(alpha = 0.7f),
                disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.35f),
                disabledUncheckedThumbColor = scheme.outline.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = scheme.surfaceContainerHighest.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Shown when no elevated backend (Shizuku / Root) is ready.
 * Compliance: C5–C8 locked meanings; CTA uses ArrowForward (never →).
 */
@Composable
fun ShizukuConnectBanner(onConnectClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedLg)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "banner_press"
    )
    val infinite = rememberInfiniteTransition(label = "banner_cta")
    val ctaNudge by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_nudge"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        scheme.secondary.copy(alpha = 0.20f),
                        scheme.primary.copy(alpha = 0.12f)
                    )
                )
            )
            .border(1.dp, scheme.secondary.copy(alpha = 0.45f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onConnectClick
            )
    ) {
        HomeCardFlowerDecor(
            modifier = Modifier.matchParentSize(),
            style = 1,
            sizeScale = 1.35f,
            alphaScale = 0.8f
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "ELEVATION REQUIRED",
                color = scheme.secondary,
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connect Shizuku or Root for deep freeze",
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "BOOST freeze is gated until Shizuku or Root is ready. No elevation means apps cannot be force-stopped on modern Android.",
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CONNECT SHIZUKU / ROOT",
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { translationX = ctaNudge }
                )
            }
        }
    }
}

@Composable
fun UnifiedResultCard(
    lastResult: FreezeResult?,
    cardHazeState: HazeState,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "press_scale"
    )
    val scheme = MaterialTheme.colorScheme

    val isElevatedResult = lastResult?.backend == "Shizuku" || lastResult?.backend == "Root"
    val isBlockedResult = !isElevatedResult && (lastResult?.killed ?: 0) == 0
    val isAlreadyOptimized = lastResult?.killed == 0 && lastResult?.freedKb == 0L && isElevatedResult
    val skipped = lastResult?.skipped ?: 0
    val failed = lastResult?.failed ?: 0
    // C11 / C12 / C13 locked
    val resultTitle = when {
        isBlockedResult -> "FREEZE BLOCKED"
        else -> "PURGE COMPLETE"
    }
    val resultSubtitle = when {
        isBlockedResult -> "Connect Shizuku or Root for deep freeze"
        isAlreadyOptimized -> "Already optimized"
        else -> "Bloat successfully cleared"
    }
    val statusAccent = if (isBlockedResult) scheme.secondary else scheme.primary
    val cardShape = RoundedCornerShape(32.dp)

    val infinite = rememberInfiniteTransition(label = "result_card")
    val idleBreath by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "result_breath"
    )
    val iconBob by infinite.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "result_icon"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Frost first (sibling of local haze source). Avoid wrapping hazeChild
            // in scale()/graphicsLayer — that can recurse on RenderThread.
            .zenFrostCard(
                hazeState = cardHazeState,
                surface = scheme.surfaceContainerLowest,
                shape = cardShape
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = if (isBlockedResult) {
                        listOf(scheme.secondary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.35f))
                    } else {
                        listOf(scheme.primary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.35f))
                    }
                ),
                shape = cardShape
            )
            .graphicsLayer {
                val s = scale * idleBreath
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        HomeCardFlowerDecor(
            style = 0,
            sizeScale = 1.35f,
            alphaScale = 0.8f,
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
        )
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBlockedResult) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = statusAccent,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { translationY = iconBob }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = resultTitle,
                            color = scheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resultSubtitle,
                            color = scheme.onSurfaceVariant,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, scheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "PURGE AGAIN",
                        color = scheme.primary,
                        fontSize = 9.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(scheme.outlineVariant)
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val freedMb = (lastResult?.freedKb ?: 0L) / 1024
                    val swapFreedMb = (lastResult?.swapFreedKb ?: 0L) / 1024
                    val totalFreedMb = freedMb + swapFreedMb
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "FREED SIZE",
                        value = "%d MB".format(totalFreedMb),
                        subtitle = "RAM: %d MB | Swap: %d MB · incl. cache reclaim".format(freedMb, swapFreedMb),
                        indicatorColor = scheme.primary,
                        valueColor = scheme.primary,
                        delayMs = 100
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "PURGED APPS",
                        value = (lastResult?.killed ?: 0).toString(),
                        subtitle = "Force-stop success",
                        indicatorColor = scheme.secondary,
                        valueColor = scheme.onSurface,
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
                        indicatorColor = scheme.onSurfaceVariant,
                        valueColor = scheme.onSurface,
                        delayMs = 200
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "SKIPPED",
                        value = skipped.toString(),
                        subtitle = when {
                            failed > 0 -> "Errors: $failed"
                            isElevatedResult -> if (skipped > 0) "Excluded targets" else "None skipped"
                            else -> "No deep freeze"
                        },
                        indicatorColor = if (skipped > 0 || failed > 0) scheme.secondary else scheme.onSurfaceVariant,
                        valueColor = if (skipped > 0 || failed > 0) scheme.secondary else scheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}


