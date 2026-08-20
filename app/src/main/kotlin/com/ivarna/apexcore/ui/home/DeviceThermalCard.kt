package com.ivarna.apexcore.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.thermal.ThermalMonitor
import com.ivarna.apexcore.thermal.ThermalSnapshot
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenColors
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DeviceThermalCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var snapshot by remember { mutableStateOf(ThermalMonitor.getSnapshot(context)) }
    var showGuide by remember { mutableStateOf(false) }

    // Live update loop every 2.5s
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ThermalMonitor.getSnapshot(context)
            delay(2500)
        }
    }

    val batteryTier = snapshot.batteryTier
    val tierColor = when (batteryTier) {
        ThermalSnapshot.BatteryTier.BEST -> scheme.primary
        ThermalSnapshot.BatteryTier.OPTIMAL -> scheme.primary
        ThermalSnapshot.BatteryTier.WARM -> scheme.secondary
        ThermalSnapshot.BatteryTier.HOT -> Color(0xFFF08C7E)
        ThermalSnapshot.BatteryTier.SEVERE_THROTTLE -> scheme.error
    }

    val shape = RoundedCornerShape(ZenDimens.roundedLg)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "THERMAL TELEMETRY",
                color = scheme.onSurfaceVariant,
                style = ZenType.label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Status Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tierColor.copy(alpha = 0.18f))
                    .border(1.dp, tierColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StatusPebble(active = true, size = 6.dp)
                    Text(
                        text = batteryTier.label.uppercase(Locale.US),
                        color = tierColor,
                        style = ZenType.caption,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .zenGlassBackground(
                    shape = shape,
                    fill = scheme.surfaceContainerLow.copy(alpha = 0.92f),
                    borderColor = scheme.outlineVariant.copy(alpha = 0.45f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 2 Compact Metric Pods (Battery & CPU)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Battery Temp Pod
                    ThermalMetricPod(
                        label = "BATTERY PACK",
                        temperature = snapshot.batteryTempCelsius,
                        subText = batteryTier.description,
                        meterProgress = ((snapshot.batteryTempCelsius - 20f) / 25f).coerceIn(0.05f, 1f),
                        accentColor = tierColor,
                        modifier = Modifier.weight(1f)
                    )

                    // 2. CPU Hotspot Pod
                    val cpuColor = when {
                        snapshot.cpuTempCelsius < 60f -> scheme.primary
                        snapshot.cpuTempCelsius < 80f -> scheme.secondary
                        else -> Color(0xFFF08C7E)
                    }
                    ThermalMetricPod(
                        label = "CPU HOTSPOT",
                        temperature = snapshot.cpuTempCelsius,
                        subText = snapshot.cpuStatusDescription,
                        meterProgress = ((snapshot.cpuTempCelsius - 30f) / 65f).coerceIn(0.05f, 1f),
                        accentColor = cpuColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Guide Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showGuide = !showGuide }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Thermal Thresholds & CPU Hotspots",
                            color = scheme.onSurfaceVariant,
                            style = ZenType.label,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = if (showGuide) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expandable Micro-Guide
                AnimatedVisibility(
                    visible = showGuide,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceContainerLowest.copy(alpha = 0.75f))
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        ThermalTierRow("<25°C", "Best", "Peak battery & system responsiveness", scheme.primary)
                        ThermalTierRow("25-30°C", "Optimal", "Standard comfortable operating zone", scheme.primary)
                        ThermalTierRow("30-35°C", "Warm", "Elevated heat — mild background load", scheme.secondary)
                        ThermalTierRow("35-40°C", "Hot", "Thermal throttling may reduce fps", Color(0xFFF08C7E))
                        ThermalTierRow("≥40°C", "Severe", "Heavy throttling to protect battery", scheme.error)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: CPU hotspot peaks surge rapidly up to 90°C+ during gaming spikes; battery temperature tracks persistent chassis heat.",
                            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                            style = ZenType.overline,
                            fontFamily = PlusJakartaSans,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalMetricPod(
    label: String,
    temperature: Float,
    subText: String,
    meterProgress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = meterProgress,
        animationSpec = tween(500),
        label = "tempMeter"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLowest.copy(alpha = 0.70f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            style = ZenType.caption,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", temperature),
                color = accentColor,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "°C",
                color = accentColor.copy(alpha = 0.8f),
                style = ZenType.body,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar meter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subText,
            color = scheme.onSurfaceVariant,
            style = ZenType.overline,
            fontFamily = PlusJakartaSans,
            maxLines = 1
        )
    }
}

@Composable
private fun ThermalTierRow(
    range: String,
    label: String,
    detail: String,
    color: Color
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = range,
            color = color,
            style = ZenType.overline,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = label,
            color = scheme.onSurface,
            style = ZenType.overline,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = detail,
            color = scheme.onSurfaceVariant,
            style = ZenType.overline,
            fontFamily = PlusJakartaSans,
            modifier = Modifier.weight(1f)
        )
    }
}