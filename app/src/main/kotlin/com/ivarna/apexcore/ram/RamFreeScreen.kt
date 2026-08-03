package com.ivarna.apexcore.ram

import android.content.Context
import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivarna.apexcore.getSystemMemStats
import com.ivarna.apexcore.ui.theme.*
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
    val isDone = fillProgress is RamFillProgress.Done

    val doneResult = (fillProgress as? RamFillProgress.Done)?.result

    // Keep screen on while running
    LaunchedEffect(isRunning) {
        val window = (context as? android.app.Activity)?.window
        if (isRunning) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top Bar with back arrow ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back arrow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable {
                        manager.cancel()
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "RAM FREE",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            if (mode != RamFillMode.STANDARD && !isRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = mode.displayName.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 8.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- Body ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Pressure gauge
            RamPressureGauge(
                ramFraction = if (memStats.ramTotalKb > 0)
                    memStats.ramUsedKb.toFloat() / memStats.ramTotalKb else 0f,
                swapFraction = if (memStats.swapTotalKb > 0)
                    memStats.swapUsedKb.toFloat() / memStats.swapTotalKb else 0f,
                fillProgress = fillProgress
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live readouts
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
                    label = "MEM AVAILABLE",
                    value = "%.0f MB".format(memAvailKb / 1024f),
                    accent = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode — Standard only (Shizuku/Root future extras)
            Text(
                "Mode: fill is always Standard. Extras coming for Shizuku/Root.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 9.sp,
                fontFamily = PlusJakartaSans,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )

            // Mode dropdown + pre-freeze toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Backend dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable(enabled = !isRunning) { showModeDropdown = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("MODE", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 9.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(mode.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("▼", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 10.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = showModeDropdown,
                        onDismissRequest = { showModeDropdown = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
                                            color = if (m == mode) MaterialTheme.colorScheme.primary else if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                            fontSize = 13.sp,
                                            fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal
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
                                                m == RamFillMode.STANDARD || ready == true -> MaterialTheme.colorScheme.primary
                                                ready == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                                else -> MaterialTheme.colorScheme.secondary
                                            },
                                            fontSize = 10.sp,
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

                // Pre-freeze toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (preFreeze) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, if (preFreeze) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(enabled = !isRunning) { preFreeze = !preFreeze }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PRE-PURGE", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 9.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (preFreeze) "ON" else "OFF",
                            color = if (preFreeze) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress / status area
            RamFillProgressSection(
                fillProgress = fillProgress,
                doneResult = doneResult
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary CTA
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // navigationBarsPadding for gesture nav
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
    val isFilling = fillProgress is RamFillProgress.Filling
    val isHolding = fillProgress is RamFillProgress.Holding
    val isRunning = isFilling || isHolding || fillProgress is RamFillProgress.PreFreeze
    val isReleasing = fillProgress is RamFillProgress.Releasing

    val pulseAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulse"
    )

    val barColor = when {
        isReleasing -> MaterialTheme.colorScheme.primary // cryo blue on release
        isRunning -> MaterialTheme.colorScheme.secondary // heat orange while filling
        else -> MaterialTheme.colorScheme.primary
    }

    val currentRamFraction = if (isFilling) {
        val f = fillProgress as RamFillProgress.Filling
        f.ramUsagePercent.coerceIn(0f, 1f)
    } else ramFraction

    val currentSwapFraction = if (isFilling) {
        val f = fillProgress as RamFillProgress.Filling
        f.swapUsagePercent.coerceIn(0f, 1f)
    } else swapFraction

    // Animate bar progress smoothly
    val animRamFraction by animateFloatAsState(currentRamFraction, tween(300), label = "ram")
    val animSwapFraction by animateFloatAsState(currentSwapFraction, tween(300), label = "swap")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular gauge
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceContainerLowest
            val ceilingMarkColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 16.dp.toPx()
                val topLeft = Offset(strokeW / 2, strokeW / 2)
                val size = Size(size.width - strokeW, size.height - strokeW)
                val arcSize = Size(size.width * 0.85f, size.height * 0.85f)
                val arcOffset = Offset(
                    (size.width - arcSize.width) / 2 + strokeW / 2,
                    (size.height - arcSize.height) / 2 + strokeW / 2
                )

                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = arcOffset, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // RAM arc
                val ramSweep = animRamFraction * 270f
                drawArc(
                    color = barColor.copy(alpha = if (isRunning && !isFilling) pulseAlpha else 0.9f),
                    startAngle = 135f, sweepAngle = ramSweep, useCenter = false,
                    topLeft = arcOffset, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // 90% ceiling mark
                val markAngle = 135f + 270f * 0.90f
                val markRad = Math.toRadians(markAngle.toDouble())
                val markOuterX = size.width / 2 + strokeW / 2 + (arcSize.width / 2) * Math.cos(markRad)
                val markOuterY = size.height / 2 + strokeW / 2 + (arcSize.height / 2) * Math.sin(markRad)
                val markInnerX = size.width / 2 + strokeW / 2 + (arcSize.width / 2 - strokeW) * Math.cos(markRad)
                val markInnerY = size.height / 2 + strokeW / 2 + (arcSize.height / 2 - strokeW) * Math.sin(markRad)

                drawLine(
                    color = ceilingMarkColor,
                    start = Offset(markInnerX.toFloat(), markInnerY.toFloat()),
                    end = Offset(markOuterX.toFloat(), markOuterY.toFloat()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f%%".format(currentRamFraction * 100),
                    color = if (currentRamFraction >= 0.85f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = when {
                        isFilling -> "FILLING"
                        isHolding -> "HOLDING"
                        isReleasing -> "RELEASING"
                        fillProgress is RamFillProgress.PreFreeze -> "PURGING"
                        fillProgress is RamFillProgress.Done -> "READY"
                        else -> "READY"
                    },
                    color = if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReadoutChip(
            modifier = Modifier.weight(1f),
            label = "RAM USED",
            value = "%.0f / %.0f MB".format(ramUsedKb / 1024f, ramTotalKb / 1024f),
            accent = MaterialTheme.colorScheme.primary
        )
        ReadoutChip(
            modifier = Modifier.weight(1f),
            label = "SWAP USED",
            value = if (swapTotalKb > 0) "%.0f / %.0f MB".format(swapUsedKb / 1024f, swapTotalKb / 1024f) else "0 / 0 MB",
            accent = MaterialTheme.colorScheme.secondary
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 9.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 12.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RamFillProgressSection(
    fillProgress: RamFillProgress,
    doneResult: RamFillResult?
) {
    AnimatedContent(
        targetState = fillProgress,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "progress"
    ) { progress ->
        when (progress) {
            is RamFillProgress.PreFreeze -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("● PRE-FREEZING", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Purging background apps…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            is RamFillProgress.Filling -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ALLOCATING MEMORY",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${progress.allocatedMb} MB · RAM ${"%.0f".format(progress.ramUsagePercent * 100)}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = PlusJakartaSans
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Chunks: ${progress.chunkCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }

            is RamFillProgress.Holding -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HOLDING PEAK", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${progress.remainingMs}ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                    }
                }
            }

            is RamFillProgress.Releasing -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RELEASING BUFFERS", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Returning memory to system…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            is RamFillProgress.Done -> {
                val result = progress.result
                val totalFreedMb = (result.freedKb + result.swapFreedKb) / 1024

                if (result.cancelled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancelled", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 14.sp, fontFamily = PlusJakartaSans)
                    }
                } else if (result.stopReason == StopReason.RAM_CAP && result.chunkCount == 0) {
                    // Already at safe cap before any alloc
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Already at 90% safe cap",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 14.sp,
                                fontFamily = PlusJakartaSans
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "System memory already near ceiling",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                } else if (result.stopReason == StopReason.BUDGET && result.peakRamPercent < 0.70f) {
                    // Honest BUDGET — pressure limited by process budget
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "PRESSURE LIMITED",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 14.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Process budget · peak ${result.peakAllocatedMb}MB alloc",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "heapMax: ${result.heapMaxMb}MB · ${result.chunkCount} chunks",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans
                            )
                            if (totalFreedMb > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "+%d MB freed".format(totalFreedMb),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (totalFreedMb == 0L && result.totalDurationMs > 0 && result.stopReason != StopReason.BUDGET) {
                    // Honest zero
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No reclaim detected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 14.sp,
                                fontFamily = PlusJakartaSans
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${result.stopReason} · %d MB · %ds".format(result.peakAllocatedMb, result.totalDurationMs / 1000),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                } else {
                    // Result card
                    val noteText = when (result.stopReason) {
                        StopReason.RAM_CAP -> "%d MB · %ds · RAM %.0f%%".format(
                            result.peakAllocatedMb, result.totalDurationMs / 1000, result.peakRamPercent * 100)
                        StopReason.TIMEOUT -> "timeout · %d MB".format(result.peakAllocatedMb)
                        StopReason.OOM -> "OOM · %d MB".format(result.peakAllocatedMb)
                        StopReason.BUDGET -> "budget · %d MB · %.0f%% RAM".format(
                            result.peakAllocatedMb, result.peakRamPercent * 100)
                        StopReason.CANCEL -> "cancelled"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "+%d MB".format(totalFreedMb),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 32.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                noteText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
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
    val pulseAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.6f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "btn_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary
            )
            .border(
                1.dp,
                if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                RoundedCornerShape(16.dp)
            )
            .clickable { if (isRunning) onCancel() else onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isRunning) "CANCEL" else "FREE RAM",
            color = if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.onPrimary,
            fontSize = 16.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
    }
}
