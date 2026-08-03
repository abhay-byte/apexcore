package com.ivarna.apexcore.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.util.GpuVendor
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.openPrivacyPolicy
import com.ivarna.apexcore.ui.components.GlassCard
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.theme.LocalZenSemantics
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ThemeMode
import com.ivarna.apexcore.ui.theme.ZenDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    lightTankBg: Boolean,
    onLightTankBgChange: (Boolean) -> Unit,
    activeBackendName: String = "Detecting…",
    preferredBackend: String? = null,
    onSetupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val fpsStack = remember { FpsStack.get(context) }
    val privilegeMode by fpsStack.privilegeModeStore.mode.collectAsState()
    var gpuVendor by remember { mutableStateOf(GpuVendor.UNKNOWN) }

    LaunchedEffect(Unit) {
        gpuVendor = withContext(Dispatchers.IO) {
            fpsStack.gpuVendor()
        }
    }

    val versionLabel = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val name = info.versionName ?: "1.0"
            "v$name"
        } catch (_: PackageManager.NameNotFoundException) {
            "v1.0"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Calm zen ambient — aurora, pebbles, constellation (not vines / HUD)
        SettingsZenBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ZenDimens.containerPadding)
                .verticalScroll(rememberScrollState())
        ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(ZenDimens.topBarClearance))
        Spacer(modifier = Modifier.height(ZenDimens.elementGap))

        Text(
            text = "Settings",
            color = scheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Appearance, access, and about",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Appearance ──────────────────────────────────────────────
        Text(
            text = "APPEARANCE",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        GlassCard(organicStyle = 1, organicSizeScale = 1.2f, organicAlpha = 0.75f) {
            Text(
                text = "Theme",
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose light, dark, or match your system",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            ThemeSegmentedControl(
                selected = themeMode,
                onSelect = onThemeModeChange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(organicStyle = 5, organicSizeScale = 1.1f, organicAlpha = 0.7f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Light tank glass",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Keep RAM / SWAP tanks light frosted even in dark mode",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = lightTankBg,
                    onCheckedChange = onLightTankBgChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary,
                        uncheckedThumbColor = scheme.outline,
                        uncheckedTrackColor = scheme.surfaceContainerHighest
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Access ──────────────────────────────────────────────────
        Text(
            text = "ACCESS",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        ActiveModeCard(
            activeBackendName = activeBackendName,
            preferredBackend = preferredBackend,
            privilegeMode = privilegeMode,
            gpuVendor = gpuVendor,
            onSetupClick = onSetupClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        SystemDiagnosticsCard(onSetupClick = onSetupClick)

        Spacer(modifier = Modifier.height(28.dp))

        // ── Legal ───────────────────────────────────────────────────
        Text(
            text = "LEGAL",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        GlassCard(
            onClick = { openPrivacyPolicy(context) },
            organicStyle = 7,
            organicSizeScale = 1.05f,
            organicAlpha = 0.65f
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Policy",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "How Apex Core handles your data",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── About ───────────────────────────────────────────────────
        Text(
            text = "ABOUT",
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        GlassCard(organicStyle = 3, organicSizeScale = 1.05f, organicAlpha = 0.65f) {
            Text(
                text = "Apex Core",
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Version $versionLabel",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(ZenDimens.bottomNavClearance))
        }
    }
}

@Composable
private fun ActiveModeCard(
    activeBackendName: String,
    preferredBackend: String?,
    privilegeMode: PrivilegeMode,
    gpuVendor: GpuVendor,
    onSetupClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isElevated = activeBackendName.equals("Shizuku", ignoreCase = true) ||
        activeBackendName.equals("Root", ignoreCase = true)
    val activeLabel = when {
        isElevated -> activeBackendName.uppercase()
        activeBackendName.contains("SETUP", ignoreCase = true) -> "SETUP"
        activeBackendName.contains("Detect", ignoreCase = true) -> "…"
        else -> activeBackendName.uppercase()
    }
    val preferredLabel = when (preferredBackend?.lowercase()) {
        "root" -> "Root"
        "shizuku" -> "Shizuku"
        else -> "Auto"
    }
    // Boost uses the same elevated freeze backend as Purge Engine
    val boostBackend = if (isElevated) activeBackendName else "Blocked"
    val privilegeShort = when (privilegeMode) {
        PrivilegeMode.AUTO -> "Auto"
        PrivilegeMode.ROOT -> "Root"
        PrivilegeMode.SHIZUKU -> "Shizuku"
        PrivilegeMode.STANDARD -> "Standard"
    }
    // Games always use SF first (factualstats); DMA only for non-game UI under root.
    val fpsPathShort = when (privilegeMode) {
        PrivilegeMode.ROOT -> when (gpuVendor) {
            GpuVendor.ADRENO -> "Games: SF · UI: Adreno"
            GpuVendor.MALI -> "Games: SF · UI: Mali"
            GpuVendor.UNKNOWN -> "Games: SF · UI: daemon"
        }
        PrivilegeMode.SHIZUKU -> "SF elevated → gfxinfo"
        PrivilegeMode.STANDARD -> "SF / gfxinfo"
        PrivilegeMode.AUTO -> when (gpuVendor) {
            GpuVendor.ADRENO -> "Auto · SF (games) / Adreno"
            GpuVendor.MALI -> "Auto · SF (game) / Mali"
            GpuVendor.UNKNOWN -> "Auto · SF (game) / daemon"
        }
    }

    GlassCard(onClick = onSetupClick, organicStyle = 2, organicSizeScale = 1.2f, organicAlpha = 0.7f) {
        // Header: title + live status chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RUNNING MODE",
                color = scheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPebble(active = isElevated, size = 8.dp)
                ModeChip(
                    text = activeLabel,
                    emphasized = isElevated
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact 2×2 metric grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactMetric(
                label = "Boost",
                value = boostBackend,
                modifier = Modifier.weight(1f)
            )
            CompactMetric(
                label = "Preferred",
                value = preferredLabel,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactMetric(
                label = "FPS mode",
                value = privilegeShort,
                modifier = Modifier.weight(1f)
            )
            CompactMetric(
                label = "GPU",
                value = gpuVendor.shortName,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Single-line path footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ZenDimens.roundedMd))
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FPS",
                color = scheme.primary,
                fontSize = 10.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fpsPathShort,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ZenDimens.roundedMd))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = scheme.onSurface,
            fontSize = 13.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ModeChip(
    text: String,
    emphasized: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (emphasized) scheme.primaryContainer
                else scheme.secondaryContainer.copy(alpha = 0.85f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (emphasized) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SystemDiagnosticsCard(
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

    GlassCard(onClick = onSetupClick, organicStyle = 6, organicSizeScale = 1.15f, organicAlpha = 0.7f) {
        Text(
            text = "ACCESS DIAGNOSTICS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun DiagnosticRow(
    label: String,
    status: Boolean?,
    description: String
) {
    val scheme = MaterialTheme.colorScheme
    val zen = LocalZenSemantics.current
    val (statusColor, statusText) = when (status) {
        true -> scheme.primary to "ACTIVE"
        false -> zen.statusInactive to "INACTIVE"
        null -> scheme.outline to "CHECKING…"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPebble(active = status, size = 10.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ThemeSegmentedControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedMd)
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val active = selected == mode
            val chipShape = RoundedCornerShape(ZenDimens.roundedSm + 2.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(chipShape)
                    .background(
                        if (active) scheme.primary else scheme.primary.copy(alpha = 0f)
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}
