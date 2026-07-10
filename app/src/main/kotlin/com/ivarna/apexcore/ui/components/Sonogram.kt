package com.ivarna.apexcore.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.State
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SimpleMemoryDisplay(
    ramUsedKb: Long,
    ramTotalKb: Long,
    swapUsedKb: Long,
    swapTotalKb: Long,
    state: State,
    isPurgeAnimActive: Boolean,
    actualFreedMb: Float,
    freedRamText: String,
    onPurgeAnimComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ramUsedGb = ramUsedKb / (1024f * 1024f)
    val ramTotalGb = ramTotalKb / (1024f * 1024f)
    val ramFraction = if (ramTotalKb > 0) (ramUsedKb.toFloat() / ramTotalKb).coerceIn(0f, 1f) else 0f

    val swapUsedGb = swapUsedKb / (1024f * 1024f)
    val swapTotalGb = swapTotalKb / (1024f * 1024f)
    val swapFraction = if (swapTotalKb > 0) (swapUsedKb.toFloat() / swapTotalKb).coerceIn(0f, 1f) else 0f

    val animatedRamProgress by animateFloatAsState(
        targetValue = ramFraction,
        animationSpec = tween(600, easing = EaseInOutQuad),
        label = "ram_progress"
    )
    val animatedSwapProgress by animateFloatAsState(
        targetValue = swapFraction,
        animationSpec = tween(600, easing = EaseInOutQuad),
        label = "swap_progress"
    )

    // Pulse animation during active purging
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Handle completion timeout when actualFreedMb arrives
    LaunchedEffect(isPurgeAnimActive, actualFreedMb) {
        if (isPurgeAnimActive && actualFreedMb >= 0f && freedRamText.isNotEmpty()) {
            delay(1200) // Hold simple freed result for 1.2s
            onPurgeAnimComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- RAM SECTION ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAM",
                    color = AccentPrimary,
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "%.2f GB / %.2f GB".format(ramUsedGb, ramTotalGb),
                    color = TextTitle,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }

            // RAM Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderGlass, RoundedCornerShape(9.dp))
            ) {
                val barColor = if (isPurgeAnimActive && actualFreedMb < 0f) {
                    AccentPrimary.copy(alpha = pulseAlpha)
                } else {
                    AccentPrimary
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedRamProgress)
                        .background(barColor)
                )
            }
        }

        // --- SWAP SECTION ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SWAP",
                    color = AccentWarning,
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (swapTotalKb > 0) "%.2f GB / %.2f GB".format(swapUsedGb, swapTotalGb) else "0.00 GB / 0.00 GB",
                    color = TextTitle,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
            }

            // SWAP Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderGlass, RoundedCornerShape(9.dp))
            ) {
                val barColor = if (isPurgeAnimActive && actualFreedMb < 0f) {
                    AccentWarning.copy(alpha = pulseAlpha)
                } else {
                    AccentWarning
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedSwapProgress)
                        .background(barColor)
                )
            }
        }

        // --- SIMPLE OPTIMIZATION INDICATOR / RESULT ---
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
                        text = "● OPTIMIZING MEMORY…",
                        color = AccentPrimary.copy(alpha = pulseAlpha),
                        fontSize = 13.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else if (freedRamText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentPrimary.copy(alpha = 0.15f))
                            .border(1.dp, AccentPrimary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ FREED $freedRamText",
                            color = AccentPrimary,
                            fontSize = 14.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}



