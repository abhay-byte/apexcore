package com.ivarna.apexcore.ram

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivarna.apexcore.R
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RamFreeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val manager = remember { RamFillerManager(context) }
    val fillProgress by manager.progress.collectAsState()
    var memStats by remember { mutableStateOf(getSystemMemStats(context)) }

    var mode by remember { mutableStateOf(RamFillMode.STANDARD) }
    var preFreeze by remember { mutableStateOf(true) }
    var showModeDropdown by remember { mutableStateOf(false) }
    var modeReadiness by remember { mutableStateOf<Map<RamFillMode, Boolean?>>(emptyMap()) }

    // Periodic memory stat refresh
    LaunchedEffect(Unit) {
        while (true) {
            memStats = getSystemMemStats(context)
            delay(1000)
        }
    }

    // Detect mode readiness when dropdown opens
    LaunchedEffect(showModeDropdown) {
        if (showModeDropdown) {
            val readiness = mutableMapOf<RamFillMode, Boolean?>()
            for (m in RamFillMode.entries) {
                readiness[m] = try { m.isReady() } catch (_: Throwable) { false }
            }
            modeReadiness = readiness
        }
    }

    // Cancel on pause / dispose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                manager.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.cancel()
        }
    }

    // System back handling
    BackHandler {
        manager.cancel()
        onBack()
    }

    val isRunning = fillProgress !is RamFillProgress.Done
    val isPreFreezing = fillProgress is RamFillProgress.PreFreeze
    val isFilling = fillProgress is RamFillProgress.Filling
    val isHolding = fillProgress is RamFillProgress.Holding
    val isReleasing = fillProgress is RamFillProgress.Releasing

    // Keep screen on while running
    LaunchedEffect(isRunning) {
        val window = (context as? android.app.Activity)?.window
        if (isRunning) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val glassShape = RoundedCornerShape(ZenDimens.roundedLg)
    val glassFill = scheme.surfaceContainerLowest.copy(alpha = 0.92f)
    val glassBorder = scheme.outlineVariant.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // --- Top bar: ArrowBack (AutoMirrored) — never ASCII ← ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = ZenDimens.containerPadding, vertical = ZenDimens.elementGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .zenGlassBackground(
                        shape = RoundedCornerShape(ZenDimens.roundedMd),
                        fill = glassFill,
                        borderColor = glassBorder
                    )
                    .clickable {
                        manager.cancel()
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(ZenDimens.elementGap))

            Text(
                text = "Ram Free",
                color = scheme.onSurface,
                style = ZenType.display.copy(fontWeight = FontWeight.SemiBold)
            )

            if (mode != RamFillMode.STANDARD && !isRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .zenGlassBackground(
                            shape = RoundedCornerShape(50),
                            fill = scheme.primary.copy(alpha = 0.12f),
                            borderColor = scheme.primary.copy(alpha = 0.25f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode.displayName,
                        color = scheme.primary,
                        style = ZenType.overline,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Body ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZenDimens.containerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Soft pressure gauge
            RamPressureGauge(
                ramFraction = if (memStats.ramTotalKb > 0)
                    memStats.ramUsedKb.toFloat() / memStats.ramTotalKb else 0f,
                swapFraction = if (memStats.swapTotalKb > 0)
                    memStats.swapUsedKb.toFloat() / memStats.swapTotalKb else 0f,
                fillProgress = fillProgress
            )

            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            // Live readouts (glass chips)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadoutRow(
                    ramUsedKb = memStats.ramUsedKb,
                    ramTotalKb = memStats.ramTotalKb,
                    swapUsedKb = memStats.swapUsedKb,
                    swapTotalKb = memStats.swapTotalKb
                )
                val memAvailKb = memStats.ramTotalKb - memStats.ramUsedKb
                ReadoutChip(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Mem available",
                    value = "%.0f MB".format(memAvailKb / 1024f),
                    accent = scheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode — Standard only (Shizuku/Root future extras)
            Text(
                "Mode: fill is always Standard. Extras coming for Shizuku/Root.",
                color = scheme.onSurfaceVariant,
                style = ZenType.label,
                fontFamily = PlusJakartaSans,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )

            // Soft mode controls (glass)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zenGlassBackground(
                                shape = glassShape,
                                fill = glassFill,
                                borderColor = glassBorder
                            )
                            .clickable(enabled = !isRunning) { showModeDropdown = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "MODE",
                                    color = scheme.onSurfaceVariant,
                                    style = ZenType.caption,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    mode.displayName,
                                    color = scheme.onSurface,
                                    style = ZenType.bodySm,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = PlusJakartaSans
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showModeDropdown,
                        onDismissRequest = { showModeDropdown = false },
                        modifier = Modifier.background(scheme.surfaceContainerLowest)
                    ) {
                        RamFillMode.entries.forEach { m ->
                            val ready = modeReadiness[m]
                            val available = ready == true || m == RamFillMode.STANDARD
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            m.displayName,
                                            color = if (m == mode) scheme.primary
                                            else if (available) scheme.onSurface
                                            else scheme.onSurfaceVariant,
                                            style = ZenType.bodySm,
                                            fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = PlusJakartaSans
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            when {
                                                m == RamFillMode.STANDARD -> "Always ready"
                                                ready == null -> "Checking…"
                                                ready -> "Ready"
                                                else -> "Not available"
                                            },
                                            color = when {
                                                m == RamFillMode.STANDARD || ready == true -> scheme.primary
                                                ready == null -> scheme.onSurfaceVariant
                                                else -> scheme.secondary
                                            },
                                            style = ZenType.overline,
                                            fontFamily = PlusJakartaSans
                                        )
                                    }
                                },
                                onClick = {
                                    if (available) mode = m
                                    showModeDropdown = false
                                },
                                enabled = available
                            )
                        }
                    }
                }

                // Pre-freeze toggle (soft glass)
                Box(
                    modifier = Modifier
                        .zenGlassBackground(
                            shape = glassShape,
                            fill = if (preFreeze) scheme.primary.copy(alpha = 0.12f) else glassFill,
                            borderColor = if (preFreeze) scheme.primary.copy(alpha = 0.3f) else glassBorder
                        )
                        .clickable(enabled = !isRunning) { preFreeze = !preFreeze }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PRE-PURGE",
                            color = scheme.onSurfaceVariant,
                            style = ZenType.caption,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (preFreeze) "ON" else "OFF",
                            color = if (preFreeze) scheme.primary else scheme.onSurfaceVariant,
                            style = ZenType.bodySm,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress / status area
            RamFillProgressSection(fillProgress = fillProgress)

            Spacer(modifier = Modifier.height(24.dp))

            // Soft primary CTA
            RamFillActionButton(
                isRunning = isRunning || isPreFreezing || isFilling || isHolding || isReleasing,
                onClick = {
                    coroutineScope.launch {
                        manager.run(mode = mode, preFreeze = preFreeze)
                    }
                },
                onCancel = { manager.cancel() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Footer
            Text(
                text = "Force system reclaim · 90% safe cap · no system kills",
                color = scheme.onSurfaceVariant,
                style = ZenType.label,
                fontFamily = PlusJakartaSans,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun RamPressureGauge(
    ramFraction: Float,
    swapFraction: Float,
    fillProgress: RamFillProgress
) {
    val scheme = MaterialTheme.colorScheme
    val isFilling = fillProgress is RamFillProgress.Filling
    val isHolding = fillProgress is RamFillProgress.Holding
    val isRunning = isFilling || isHolding || fillProgress is RamFillProgress.PreFreeze
    val isReleasing = fillProgress is RamFillProgress.Releasing

    val pulseAlpha by rememberInfiniteTransition(label = "gauge_pulse").animateFloat(
        initialValue = 0.55f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Soft scheme colors: sage while calm/release, warm secondary while working
    val barColor = when {
        isReleasing -> scheme.primary
        isRunning -> scheme.secondary
        else -> scheme.primary
    }

    val currentRamFraction = if (isFilling) {
        val f = fillProgress as RamFillProgress.Filling
        f.ramUsagePercent.coerceIn(0f, 1f)
    } else ramFraction

    val currentSwapFraction = if (isFilling) {
        val f = fillProgress as RamFillProgress.Filling
        f.swapUsagePercent.coerceIn(0f, 1f)
    } else swapFraction

    val animRamFraction by animateFloatAsState(currentRamFraction, tween(300), label = "ram")
    // Animated for parity with pre-restyle; dual-arc not drawn in v1
    @Suppress("UNUSED_VARIABLE")
    val animSwapFraction by animateFloatAsState(currentSwapFraction, tween(300), label = "swap")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(188.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = scheme.surfaceContainer.copy(alpha = 0.85f)
            val ceilingMarkColor = scheme.tertiary.copy(alpha = 0.75f)
            val arcPrimary = barColor
            val arcSoft = scheme.primaryContainer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 14.dp.toPx()
                val arcSize = Size(size.width * 0.82f, size.height * 0.82f)
                val arcOffset = Offset(
                    (size.width - arcSize.width) / 2,
                    (size.height - arcSize.height) / 2
                )

                // Soft track
                drawArc(
                    color = trackColor,
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = arcOffset, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Soft gradient-like dual pass: soft underlay + primary arc
                val ramSweep = animRamFraction * 270f
                if (ramSweep > 0f) {
                    drawArc(
                        color = arcSoft.copy(alpha = 0.35f),
                        startAngle = 135f, sweepAngle = ramSweep, useCenter = false,
                        topLeft = arcOffset, size = arcSize,
                        style = Stroke(width = strokeW + 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = arcPrimary.copy(alpha = if (isRunning && !isFilling) pulseAlpha else 0.92f),
                        startAngle = 135f, sweepAngle = ramSweep, useCenter = false,
                        topLeft = arcOffset, size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // 90% ceiling mark
                val markAngle = 135f + 270f * 0.90f
                val markRad = Math.toRadians(markAngle.toDouble())
                val cx = size.width / 2
                val cy = size.height / 2
                val outerR = arcSize.width / 2
                val markOuterX = cx + outerR * Math.cos(markRad)
                val markOuterY = cy + outerR * Math.sin(markRad)
                val markInnerX = cx + (outerR - strokeW) * Math.cos(markRad)
                val markInnerY = cy + (outerR - strokeW) * Math.sin(markRad)

                drawLine(
                    color = ceilingMarkColor,
                    start = Offset(markInnerX.toFloat(), markInnerY.toFloat()),
                    end = Offset(markOuterX.toFloat(), markOuterY.toFloat()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Bold center readouts
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f%%".format(currentRamFraction * 100),
                    color = if (currentRamFraction >= 0.85f) scheme.secondary else scheme.onSurface,
                    style = ZenType.hero,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        isFilling -> "Filling"
                        isHolding -> "Holding"
                        isReleasing -> "Releasing"
                        fillProgress is RamFillProgress.PreFreeze -> "Purging"
                        else -> "Ready"
                    },
                    color = if (isRunning) scheme.secondary.copy(alpha = pulseAlpha)
                    else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReadoutRow(
    ramUsedKb: Long, ramTotalKb: Long,
    swapUsedKb: Long, swapTotalKb: Long
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReadoutChip(
            modifier = Modifier.weight(1f),
            label = "Ram used",
            value = "%.0f / %.0f MB".format(ramUsedKb / 1024f, ramTotalKb / 1024f),
            accent = scheme.primary
        )
        ReadoutChip(
            modifier = Modifier.weight(1f),
            label = "Swap used",
            value = if (swapTotalKb > 0) "%.0f / %.0f MB".format(swapUsedKb / 1024f, swapTotalKb / 1024f) else "0 / 0 MB",
            accent = scheme.secondary
        )
    }
}

@Composable
private fun ReadoutChip(
    modifier: Modifier,
    label: String,
    value: String,
    accent: Color
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedMd)
    Box(
        modifier = modifier
            .zenGlassBackground(
                shape = shape,
                fill = scheme.surfaceContainerLowest.copy(alpha = 0.92f),
                borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
            )
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = scheme.onSurfaceVariant,
                style = ZenType.overline,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = accent,
                style = ZenType.bodySm,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProgressGlassCard(
    borderAccent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zenGlassBackground(
                shape = shape,
                fill = scheme.surfaceContainerLowest.copy(alpha = 0.92f),
                borderColor = (borderAccent ?: scheme.outlineVariant).copy(alpha = if (borderAccent != null) 0.35f else 0.6f)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun RamFillProgressSection(
    fillProgress: RamFillProgress
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = fillProgress,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "progress"
    ) { progress ->
        when (progress) {
            is RamFillProgress.PreFreeze -> {
                ProgressGlassCard(borderAccent = scheme.secondary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPebble(active = true, size = 10.dp)
                        Text(
                            "Pre-freezing",
                            color = scheme.secondary,
                            style = ZenType.body,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Purging background apps…",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PlusJakartaSans
                    )
                }
            }

            is RamFillProgress.Filling -> {
                ProgressGlassCard(borderAccent = scheme.secondary) {
                    Text(
                        "Allocating memory",
                        color = scheme.secondary,
                        style = ZenType.body,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${progress.allocatedMb} MB · RAM ${"%.0f".format(progress.ramUsagePercent * 100)}%",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PlusJakartaSans
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Chunks: ${progress.chunkCount}",
                        color = scheme.onSurfaceVariant,
                        style = ZenType.label,
                        fontFamily = PlusJakartaSans
                    )
                }
            }

            is RamFillProgress.Holding -> {
                ProgressGlassCard(borderAccent = scheme.secondary) {
                    Text(
                        "Holding peak",
                        color = scheme.secondary,
                        style = ZenType.body,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${progress.remainingMs}ms",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PlusJakartaSans
                    )
                }
            }

            is RamFillProgress.Releasing -> {
                ProgressGlassCard(borderAccent = scheme.primary) {
                    Text(
                        "Releasing buffers",
                        color = scheme.primary,
                        style = ZenType.body,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Returning memory to system…",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = PlusJakartaSans
                    )
                }
            }

            is RamFillProgress.Done -> {
                val result = progress.result
                val totalFreedMb = (result.freedKb + result.swapFreedKb) / 1024

                if (result.cancelled) {
                    ProgressGlassCard {
                        Text(
                            "Cancelled",
                            color = scheme.onSurfaceVariant,
                            style = ZenType.body,
                            fontFamily = PlusJakartaSans
                        )
                    }
                } else if (result.stopReason == StopReason.RAM_CAP && result.chunkCount == 0) {
                    ProgressGlassCard {
                        Text(
                            "Already at 90% safe cap",
                            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                            style = ZenType.body,
                            fontFamily = PlusJakartaSans
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "System memory already near ceiling",
                            color = scheme.onSurfaceVariant,
                            style = ZenType.label,
                            fontFamily = PlusJakartaSans
                        )
                    }
                } else if (result.stopReason == StopReason.BUDGET && result.peakRamPercent < 0.70f) {
                    ProgressGlassCard(borderAccent = scheme.secondary) {
                        Text(
                            "Pressure limited",
                            color = scheme.secondary,
                            style = ZenType.body,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Process budget · peak ${result.peakAllocatedMb}MB alloc",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = PlusJakartaSans
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "heapMax: ${result.heapMaxMb}MB · ${result.chunkCount} chunks",
                            color = scheme.onSurfaceVariant,
                            style = ZenType.label,
                            fontFamily = PlusJakartaSans
                        )
                        if (totalFreedMb > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Freed %d MB".format(totalFreedMb),
                                    color = scheme.primary,
                                    style = ZenType.body,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (totalFreedMb == 0L && result.totalDurationMs > 0 && result.stopReason != StopReason.BUDGET) {
                    ProgressGlassCard {
                        Text(
                            "No reclaim detected",
                            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                            style = ZenType.body,
                            fontFamily = PlusJakartaSans
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "${result.stopReason} · %d MB · %ds".format(result.peakAllocatedMb, result.totalDurationMs / 1000),
                            color = scheme.onSurfaceVariant,
                            style = ZenType.label,
                            fontFamily = PlusJakartaSans
                        )
                    }
                } else {
                    val noteText = when (result.stopReason) {
                        StopReason.RAM_CAP -> "%d MB · %ds · RAM %.0f%%".format(
                            result.peakAllocatedMb, result.totalDurationMs / 1000, result.peakRamPercent * 100)
                        StopReason.TIMEOUT -> "timeout · %d MB".format(result.peakAllocatedMb)
                        StopReason.OOM -> "OOM · %d MB".format(result.peakAllocatedMb)
                        StopReason.BUDGET -> "budget · %d MB · %.0f%% RAM".format(
                            result.peakAllocatedMb, result.peakRamPercent * 100)
                        StopReason.CANCEL -> "cancelled"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zenGlassBackground(
                                shape = RoundedCornerShape(ZenDimens.roundedLg),
                                fill = scheme.primary.copy(alpha = 0.08f),
                                borderColor = scheme.primary.copy(alpha = 0.28f)
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "+%d MB".format(totalFreedMb),
                            color = scheme.primary,
                            style = ZenType.hero,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            noteText,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamFillActionButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp) // pebble-like
    val pulseAlpha by rememberInfiniteTransition(label = "btn_pulse").animateFloat(
        initialValue = 0.65f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "btn_pulse"
    )

    val fillBrush = if (isRunning) {
        Brush.verticalGradient(
            listOf(
                scheme.secondary.copy(alpha = 0.12f),
                scheme.secondaryContainer.copy(alpha = 0.35f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(scheme.primary, scheme.primaryContainer)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(fillBrush, shape)
            .border(
                width = 1.dp,
                color = if (isRunning) scheme.secondary.copy(alpha = 0.35f) else scheme.primary.copy(alpha = 0.4f),
                shape = shape
            )
            .clickable { if (isRunning) onCancel() else onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isRunning) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_water_drop),
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = if (isRunning) "Cancel" else "Free RAM",
                color = if (isRunning) scheme.secondary.copy(alpha = pulseAlpha) else scheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}