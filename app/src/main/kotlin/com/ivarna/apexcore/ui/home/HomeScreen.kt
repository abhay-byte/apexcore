package com.ivarna.apexcore.ui.home

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.freeze.FreezeResult
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.openPrivacyPolicy
import com.ivarna.apexcore.ui.components.SimpleMemoryDisplay
import com.ivarna.apexcore.ui.shell.State
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: State,
    backendName: String,
    lastResult: FreezeResult?,
    isPurgeAnimActive: Boolean,
    freedRamText: String,
    onPurgeAnimComplete: () -> Unit,
    onBoostClick: () -> Unit,
    onSetupClick: () -> Unit,
    onRamFreeClick: () -> Unit,
    onPinClick: () -> Unit
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

    // Refresh gauges the moment a purge result lands, so bars don't lag the FREED chip
    LaunchedEffect(lastResult) {
        if (lastResult != null) memStats = getSystemMemStats(context)
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
        val statusColor = if (state == State.BOOSTING) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        val isElevatedBackend = backendName == "Shizuku" || backendName == "Root"
        val statusText = when (state) {
            State.IDLE -> if (isElevatedBackend) "● Ready to purge bloat"
                         else "● Connect Shizuku or Root for deep freeze"
            State.BOOSTING -> "● PURGING BACKGROUND PROCESSES…"
            State.RESULT -> when {
                (lastResult?.backend ?: "") !in listOf("Shizuku", "Root") ->
                    "● Freeze blocked — connect Shizuku or Root"
                (lastResult?.killed ?: 0) == 0 && (lastResult?.freedKb ?: 0) == 0L ->
                    "● Already optimized"
                (lastResult?.killed ?: 0) == 0 ->
                    "● System fully optimized"
                else ->
                    "● Freed ${lastResult?.killed} background apps"
            }
        }
        Text(
            text = statusText.uppercase(),
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            letterSpacing = 1.sp
        )
        
        if (!isElevatedBackend && backendName != "Detecting…") {
            Spacer(modifier = Modifier.height(12.dp))
            ShizukuConnectBanner(onConnectClick = onSetupClick)
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

        Spacer(modifier = Modifier.height(20.dp))

        // RAM FREE secondary card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .clickable(enabled = state != State.BOOSTING) { onRamFreeClick() }
                .alpha(if (state == State.BOOSTING) 0.4f else 1f)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "RAM FREE",
                        color = if (state == State.BOOSTING) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f) else MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Force system reclaim",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
                Text("→", color = MaterialTheme.colorScheme.secondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PIN APPS card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .clickable(enabled = state != State.BOOSTING) { onPinClick() }
                .alpha(if (state == State.BOOSTING) 0.4f else 1f)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "PIN APPS",
                        color = if (state == State.BOOSTING) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Protect apps from being frozen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
                Text("→", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SystemDiagnosticsCard(onSetupClick = onSetupClick)

        // Always-reachable privacy link (Play User Data). SetupDialog alone is not enough:
        // that dialog is first-run / SETUP-only and easy to never open again.
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "PRIVACY POLICY",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { openPrivacyPolicy(context) }
                .padding(vertical = 8.dp, horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Shown when no elevated backend (Shizuku / Root) is ready.
 * There is no product "standard" freeze mode — deep freeze needs elevation.
 */
@Composable
fun ShizukuConnectBanner(onConnectClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onConnectClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "ELEVATION REQUIRED",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Connect Shizuku or Root for deep freeze",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "BOOST freeze is gated until Shizuku or Root is ready. No elevation means apps cannot be force-stopped on modern Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "CONNECT SHIZUKU / ROOT  →",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
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
            val pulseStrokeColor = MaterialTheme.colorScheme.primary.copy(alpha = sweepPulse.value)
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
                        color = pulseStrokeColor,
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
                    
                    // White text: purge face is dark brushed metal (not light scheme surface)
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
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
                    val boltColor = MaterialTheme.colorScheme.primary
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
                        drawPath(path = path, color = boltColor)
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

    val isElevatedResult = lastResult?.backend == "Shizuku" || lastResult?.backend == "Root"
    val isBlockedResult = !isElevatedResult && (lastResult?.killed ?: 0) == 0
    val isAlreadyOptimized = lastResult?.killed == 0 && lastResult?.freedKb == 0L && isElevatedResult
    val skipped = lastResult?.skipped ?: 0
    val failed = lastResult?.failed ?: 0
    val resultTitle = when {
        isBlockedResult -> "FREEZE BLOCKED"
        else -> "PURGE COMPLETE"
    }
    val resultSubtitle = when {
        isBlockedResult -> "Connect Shizuku or Root for deep freeze"
        isAlreadyOptimized -> "Already optimized"
        else -> "Bloat successfully cleared"
    }
    // Blocked / incomplete freeze: warning tone — not a green success checkmark
    val statusAccent = if (isBlockedResult) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = if (isBlockedResult) {
                        listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    } else {
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    }
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
                            color = statusAccent.copy(alpha = 0.15f),
                            radius = size.minDimension / 2
                        )
                        drawCircle(
                            color = statusAccent,
                            radius = size.minDimension / 2.4f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        if (isBlockedResult) {
                            // Warning "!" mark for blocked freeze
                            val cx = size.width / 2f
                            drawLine(
                                color = statusAccent,
                                start = Offset(cx, size.height * 0.28f),
                                end = Offset(cx, size.height * 0.58f),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = statusAccent,
                                radius = 1.8.dp.toPx(),
                                center = Offset(cx, size.height * 0.72f)
                            )
                        } else {
                            val path = Path().apply {
                                moveTo(size.width * 0.35f, size.height * 0.5f)
                                lineTo(size.width * 0.45f, size.height * 0.6f)
                                lineTo(size.width * 0.65f, size.height * 0.4f)
                            }
                            drawPath(
                                path = path,
                                color = statusAccent,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = resultTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resultSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "PURGE AGAIN",
                        color = MaterialTheme.colorScheme.primary,
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
                    .background(MaterialTheme.colorScheme.outlineVariant)
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
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        valueColor = MaterialTheme.colorScheme.primary,
                        delayMs = 100
                    )
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "PURGED APPS",
                        value = (lastResult?.killed ?: 0).toString(),
                        subtitle = "Force-stop success",
                        indicatorColor = MaterialTheme.colorScheme.secondary,
                        valueColor = MaterialTheme.colorScheme.onSurface,
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
                        indicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        delayMs = 200
                    )
                    // Skipped = excluded targets on elevated runs; blocked path keeps zeros
                    StatItem(
                        modifier = Modifier.weight(1f),
                        title = "SKIPPED",
                        value = skipped.toString(),
                        subtitle = when {
                            failed > 0 -> "Errors: $failed"
                            isElevatedResult -> if (skipped > 0) "Excluded targets" else "None skipped"
                            else -> "No deep freeze"
                        },
                        indicatorColor = if (skipped > 0 || failed > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        valueColor = if (skipped > 0 || failed > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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

@Composable
fun StatTile(modifier: Modifier, title: String, value: String, subtitle: String, valueColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, color = valueColor, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun SystemDiagnosticsCard(
    onSetupClick: () -> Unit
) {
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var hasShizuku by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            hasRoot = RootFreezeBackend().isReady()
            hasShizuku = ShizukuFreezeBackend().isReady()
            delay(3000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable { onSetupClick() }
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "ACCESS DIAGNOSTICS",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
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
            true -> MaterialTheme.colorScheme.primary to "ACTIVE"
            false -> ZenColors.statusInactive to "INACTIVE"
            null -> MaterialTheme.colorScheme.outline to "CHECKING…"
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
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Text(
            text = statusText,
            color = iconColor,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold
        )
    }
}

