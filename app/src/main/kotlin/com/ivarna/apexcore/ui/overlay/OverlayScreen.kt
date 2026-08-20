package com.ivarna.apexcore.ui.overlay

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.games.GameOverlayService
import com.ivarna.apexcore.ui.components.GlassCard
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType
import kotlinx.coroutines.delay

/** Deprecated but valid for own-package service on API 34+; used only as prefs truth check. */
private fun isOverlayServiceRunningFallback(context: Context): Boolean = try {
    @Suppress("DEPRECATION")
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.getRunningServices(Int.MAX_VALUE).any { it.service.className == GameOverlayService::class.java.name }
} catch (_: Throwable) {
    false
}

@Composable
fun OverlayScreen(context: Context = LocalContext.current) {
    var hasPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var testOverlayActive by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val prefs = remember { context.getSharedPreferences("apexcore", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        while (true) {
            hasPermission = android.provider.Settings.canDrawOverlays(context)
            val prefRunning = prefs.getBoolean(GameOverlayService.PREF_OVERLAY_RUNNING, false)
            val running = GameOverlayService.isRunning ||
                (prefRunning && hasPermission && isOverlayServiceRunningFallback(context))
            // External kill / system stop: service dead but prefs still true → clear drift
            if (!running && prefRunning) {
                prefs.edit()
                    .remove(GameOverlayService.PREF_OVERLAY_RUNNING)
                    .remove(GameOverlayService.PREF_OVERLAY_PKG)
                    .apply()
                testOverlayActive = false
            } else {
                testOverlayActive = running
            }
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tactical HUD ambient — grid, scan, radar (not vines)
        OverlayHudBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ZenDimens.containerPadding)
                .verticalScroll(rememberScrollState())
        ) {
        // Clearance: status bar + floating frosted top bar
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(ZenDimens.topBarClearance))
        Spacer(modifier = Modifier.height(ZenDimens.elementGap))
        Text(
            text = "HUD Overlay",
            color = scheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configure floating gameplay monitor",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Permission card — solid glass + StatusPebble
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusPebble(active = hasPermission)
                Text(
                    text = if (hasPermission) "PERMISSION GRANTED" else "ACTION REQUIRED",
                    color = if (hasPermission) scheme.primary else scheme.secondary,
                    style = ZenType.overline,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(ZenDimens.base))
            Text(
                text = if (hasPermission) {
                    "ApexCore has permission to render the performance HUD on top of other games."
                } else {
                    "To display the real-time FPS & memory monitor during gaming, please grant the Draw Over Apps permission."
                },
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 18.sp
            )
            if (!hasPermission) {
                Spacer(modifier = Modifier.height(ZenDimens.elementGap))
                Button(
                    onClick = {
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GRANT PERMISSION",
                        style = ZenType.label,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "HUD OVERLAY",
            color = scheme.onSurface,
            style = ZenType.body,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = PlusJakartaSans
        )
        Spacer(modifier = Modifier.height(12.dp))

        GlassCard {
            Text(
                text = "Launch a preview overlay to check placement, transparency, and drag gestures.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        GameOverlayService.start(context, context.packageName)
                        testOverlayActive = true
                    },
                    enabled = hasPermission && !testOverlayActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        disabledContainerColor = scheme.outlineVariant,
                        disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.72f)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "START",
                        style = ZenType.label,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
                OutlinedButton(
                    onClick = {
                        GameOverlayService.stop(context)
                        testOverlayActive = false
                    },
                    enabled = testOverlayActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = scheme.secondary,
                        disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.72f)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "STOP",
                        style = ZenType.label,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
        // Clearance for floating bottom-nav island
        Spacer(modifier = Modifier.height(ZenDimens.bottomNavClearance))
        }
    }
}