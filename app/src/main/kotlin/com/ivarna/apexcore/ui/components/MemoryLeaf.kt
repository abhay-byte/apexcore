package com.ivarna.apexcore.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.theme.LocalZenSemantics
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

enum class LeafShape { Teardrop, Diamond }

/** Teardrop: pointed tip top, bulbous base — RAM default (normative). */
val TeardropLeafShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.50f, h * 0.02f)
    cubicTo(w * 0.72f, h * 0.18f, w * 0.95f, h * 0.42f, w * 0.88f, h * 0.68f)
    cubicTo(w * 0.82f, h * 0.92f, w * 0.62f, h * 0.98f, w * 0.50f, h * 0.96f)
    cubicTo(w * 0.38f, h * 0.98f, w * 0.18f, h * 0.92f, w * 0.12f, h * 0.68f)
    cubicTo(w * 0.05f, h * 0.42f, w * 0.28f, h * 0.18f, w * 0.50f, h * 0.02f)
    close()
}

/** Diamond / rounded kite — SWAP default (normative). */
val DiamondLeafShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.50f, h * 0.04f)
    cubicTo(w * 0.70f, h * 0.22f, w * 0.92f, h * 0.40f, w * 0.92f, h * 0.55f)
    cubicTo(w * 0.92f, h * 0.72f, w * 0.70f, h * 0.90f, w * 0.50f, h * 0.96f)
    cubicTo(w * 0.30f, h * 0.90f, w * 0.08f, h * 0.72f, w * 0.08f, h * 0.55f)
    cubicTo(w * 0.08f, h * 0.40f, w * 0.30f, h * 0.22f, w * 0.50f, h * 0.04f)
    close()
}

/** Hard cap: wave path samples (acceptance). */
private const val WAVE_SAMPLES = 40

@Composable
fun MemoryLeaf(
    label: String,
    usedKb: Long,
    totalKb: Long,
    fillColor: Color,
    waveColor: Color,
    size: Dp,
    shape: LeafShape = LeafShape.Teardrop,
    isPulsing: Boolean = false,
    /** When false, only the leaf graphic is drawn (pair places metrics outside the overlap art). */
    showMetrics: Boolean = true,
    /**
     * Frosted glass shell: light white even in dark mode when true (settings toggle).
     * Matches light-mode tank readability on dark backgrounds.
     */
    lightTankBg: Boolean = true,
    modifier: Modifier = Modifier
) {
    val usedMb = usedKb / 1024f
    val fraction = if (totalKb > 0) (usedKb.toFloat() / totalKb).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "leaf_fill_$label"
    )

    val genericShape = when (shape) {
        LeafShape.Teardrop -> TeardropLeafShape
        LeafShape.Diamond -> DiamondLeafShape
    }

    // Empty glass shell — light white like light mode when [lightTankBg] is on
    val tankShell = if (lightTankBg) {
        ZenColors.surfaceContainerLowest.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(genericShape)
                .background(tankShell)
                .border(
                    width = 1.dp,
                    color = fillColor.copy(alpha = 0.35f),
                    shape = genericShape
                )
        ) {
            // Separate composable so infinite transitions only exist while pulsing.
            LeafLiquidFill(
                shape = shape,
                fillColor = fillColor,
                waveColor = waveColor,
                animatedFraction = animatedFraction,
                isPulsing = isPulsing
            )
        }

        if (showMetrics) {
            Spacer(modifier = Modifier.height(8.dp))
            LeafMetricLabels(
                label = label,
                usedMb = usedMb,
                fillColor = fillColor
            )
        }
    }
}

@Composable
private fun LeafLiquidFill(
    shape: LeafShape,
    fillColor: Color,
    waveColor: Color,
    animatedFraction: Float,
    isPulsing: Boolean
) {
    // Branch so idle leaves never subscribe to infinite transitions.
    if (isPulsing) {
        PulsingLeafLiquidFill(
            shape = shape,
            fillColor = fillColor,
            waveColor = waveColor,
            animatedFraction = animatedFraction
        )
    } else {
        StaticLeafLiquidFill(
            shape = shape,
            fillColor = fillColor,
            waveColor = waveColor,
            animatedFraction = animatedFraction,
            wavePhase = 0.4f
        )
    }
}

@Composable
private fun PulsingLeafLiquidFill(
    shape: LeafShape,
    fillColor: Color,
    waveColor: Color,
    animatedFraction: Float
) {
    val infinite = rememberInfiniteTransition(label = "leaf_wave_pulse")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pulseAlpha }
    ) {
        StaticLeafLiquidFill(
            shape = shape,
            fillColor = fillColor,
            waveColor = waveColor,
            animatedFraction = animatedFraction,
            wavePhase = wavePhase
        )
    }
}

@Composable
private fun StaticLeafLiquidFill(
    shape: LeafShape,
    fillColor: Color,
    waveColor: Color,
    animatedFraction: Float,
    wavePhase: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height
        val leafPath = Path().apply {
            when (shape) {
                LeafShape.Teardrop -> {
                    moveTo(w * 0.50f, h * 0.02f)
                    cubicTo(w * 0.72f, h * 0.18f, w * 0.95f, h * 0.42f, w * 0.88f, h * 0.68f)
                    cubicTo(w * 0.82f, h * 0.92f, w * 0.62f, h * 0.98f, w * 0.50f, h * 0.96f)
                    cubicTo(w * 0.38f, h * 0.98f, w * 0.18f, h * 0.92f, w * 0.12f, h * 0.68f)
                    cubicTo(w * 0.05f, h * 0.42f, w * 0.28f, h * 0.18f, w * 0.50f, h * 0.02f)
                    close()
                }
                LeafShape.Diamond -> {
                    moveTo(w * 0.50f, h * 0.04f)
                    cubicTo(w * 0.70f, h * 0.22f, w * 0.92f, h * 0.40f, w * 0.92f, h * 0.55f)
                    cubicTo(w * 0.92f, h * 0.72f, w * 0.70f, h * 0.90f, w * 0.50f, h * 0.96f)
                    cubicTo(w * 0.30f, h * 0.90f, w * 0.08f, h * 0.72f, w * 0.08f, h * 0.55f)
                    cubicTo(w * 0.08f, h * 0.40f, w * 0.30f, h * 0.22f, w * 0.50f, h * 0.04f)
                    close()
                }
            }
        }

        clipPath(leafPath) {
            val fillTop = h * (1f - animatedFraction)
            drawRect(
                color = fillColor.copy(alpha = 0.55f),
                topLeft = Offset(0f, fillTop),
                size = Size(w, h - fillTop)
            )
            val wavePath = buildWavePath(
                width = w,
                height = h,
                fillTop = fillTop,
                phase = wavePhase,
                amplitude = h * 0.035f,
                samples = WAVE_SAMPLES
            )
            drawPath(wavePath, color = waveColor.copy(alpha = 0.85f))
            val wave2 = buildWavePath(
                width = w,
                height = h,
                fillTop = fillTop + h * 0.02f,
                phase = wavePhase + 1.2f,
                amplitude = h * 0.025f,
                samples = WAVE_SAMPLES
            )
            drawPath(wave2, color = waveColor.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun LeafMetricLabels(
    label: String,
    usedMb: Float,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = fillColor,
            fontSize = 12.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "%.0f MB".format(usedMb),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Builds a closed wave fill path with at most [samples] points along the crest.
 * samples hard-capped at WAVE_SAMPLES for jank acceptance.
 */
private fun buildWavePath(
    width: Float,
    height: Float,
    fillTop: Float,
    phase: Float,
    amplitude: Float,
    samples: Int
): Path {
    val n = samples.coerceIn(2, WAVE_SAMPLES)
    return Path().apply {
        moveTo(0f, height)
        lineTo(0f, fillTop)
        for (i in 0..n) {
            val t = i.toFloat() / n
            val x = t * width
            val y = fillTop + sin(phase + t * 2f * PI.toFloat() * 1.5f) * amplitude
            lineTo(x, y)
        }
        lineTo(width, height)
        close()
    }
}

@Composable
fun MemoryLeafPair(
    ramUsedKb: Long,
    ramTotalKb: Long,
    swapUsedKb: Long,
    swapTotalKb: Long,
    isPurgeAnimActive: Boolean,
    actualFreedMb: Float,
    freedRamText: String,
    onPurgeAnimComplete: () -> Unit,
    /** Light frosted glass shell for tanks (settings); default on. */
    lightTankBg: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Completion contract: hold freed result 1.2s then notify parent
    LaunchedEffect(isPurgeAnimActive, actualFreedMb, freedRamText) {
        if (isPurgeAnimActive && actualFreedMb >= 0f && freedRamText.isNotEmpty()) {
            delay(1200)
            onPurgeAnimComplete()
        }
    }

    val isPulsing = isPurgeAnimActive && actualFreedMb < 0f
    val scheme = MaterialTheme.colorScheme
    val zen = LocalZenSemantics.current
    val ramUsedMb = ramUsedKb / 1024f
    val swapUsedMb = swapUsedKb / 1024f
    // With light tank glass, use light-mode liquid tints so fills match light mode
    val ramFill = if (lightTankBg) ZenColors.leafRamFill else zen.leafRamFill
    val swapFill = if (lightTankBg) ZenColors.leafSwapFill else zen.leafSwapFill
    val ramWave = if (lightTankBg) ZenColors.primary else scheme.primary
    val swapWave = if (lightTankBg) ZenColors.tertiary else scheme.tertiary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered asymmetric pair: RAM 210dp teardrop, SWAP 140dp diamond offset.
        // Metrics live OUTSIDE this box so overlapping art never collides text (F1 QA).
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(255.dp),
            contentAlignment = Alignment.Center
        ) {
            MemoryLeaf(
                label = "RAM",
                usedKb = ramUsedKb,
                totalKb = ramTotalKb,
                fillColor = ramFill,
                waveColor = ramWave,
                size = 210.dp,
                shape = LeafShape.Teardrop,
                isPulsing = isPulsing,
                showMetrics = false,
                lightTankBg = lightTankBg,
                modifier = Modifier.align(Alignment.Center)
            )
            MemoryLeaf(
                label = "SWAP",
                usedKb = swapUsedKb,
                totalKb = swapTotalKb,
                fillColor = swapFill,
                waveColor = swapWave,
                size = 140.dp,
                shape = LeafShape.Diamond,
                isPulsing = isPulsing,
                showMetrics = false,
                lightTankBg = lightTankBg,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 36.dp, y = 46.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Distinct columns under the pair — readable even when shapes overlap.
        Row(
            modifier = Modifier
                .width(280.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            LeafMetricLabels(
                label = "RAM",
                usedMb = ramUsedMb,
                fillColor = ramFill,
                modifier = Modifier.weight(1f)
            )
            LeafMetricLabels(
                label = "SWAP",
                usedMb = swapUsedMb,
                fillColor = swapFill,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = isPurgeAnimActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (actualFreedMb < 0f) {
                    Text(
                        text = "OPTIMIZING MEMORY…",
                        color = scheme.primary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else if (freedRamText.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Freed $freedRamText",
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
