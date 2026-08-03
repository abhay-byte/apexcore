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
import kotlinx.coroutines.delay

@Composable
fun OverlayScreen(context: Context = LocalContext.current) {
    var hasPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var testOverlayActive by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        while (true) {
            hasPermission = android.provider.Settings.canDrawOverlays(context)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZenDimens.containerPadding)
            .verticalScroll(rememberScrollState())
    ) {
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
            color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
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
                    fontSize = 10.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TEST HUD OVERLAY",
            color = scheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = PlusJakartaSans
        )
        Spacer(modifier = Modifier.height(12.dp))

        GlassCard {
            Text(
                text = "Launch a dummy monitor to test placement, transparency, and drag gestures directly on this screen.",
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
                        fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(ZenDimens.containerPadding))
    }
}
