package com.ivarna.apexcore.ui.overlay

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.games.GameOverlayService
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OverlayScreen(context: Context = LocalContext.current) {
    var hasPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var testOverlayActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            hasPermission = android.provider.Settings.canDrawOverlays(context)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "HUD OVERLAY",
            color = TextTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Configure floating gameplay monitor",
            color = TextMuted,
            fontSize = 12.sp
        )
        
        Spacer(modifier = Modifier.height(28.dp))

        // Permission Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (hasPermission) AccentSuccess.copy(alpha = 0.1f) else AccentWarning.copy(alpha = 0.1f))
                .border(1.dp, if (hasPermission) AccentSuccess.copy(alpha = 0.4f) else AccentWarning.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = if (hasPermission) "PERMISSION GRANTED" else "ACTION REQUIRED",
                    color = if (hasPermission) AccentSuccess else AccentWarning,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasPermission) "ApexCore has permission to render the performance HUD on top of other games."
                           else "To display the real-time FPS & memory monitor during gaming, please grant the Draw Over Apps permission.",
                    color = TextBody,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentWarning)
                            .clickable {
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
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("GRANT PERMISSION", color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Test HUD Controls
        Text(
            text = "TEST HUD OVERLAY",
            color = TextTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Launch a dummy monitor to test placement, transparency, and drag gestures directly on this screen.",
                    color = TextBody,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (testOverlayActive) BorderGlass else AccentPrimary)
                            .clickable(enabled = hasPermission && !testOverlayActive) {
                                GameOverlayService.start(context, context.packageName)
                                testOverlayActive = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "START TEST HUD",
                            color = if (testOverlayActive) TextMuted else TextTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (testOverlayActive) AccentWarning.copy(alpha=0.2f) else BorderGlass)
                            .clickable(enabled = testOverlayActive) {
                                GameOverlayService.stop(context)
                                testOverlayActive = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "STOP TEST HUD",
                            color = if (testOverlayActive) AccentWarning else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

