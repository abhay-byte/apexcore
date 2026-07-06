package com.apexcore.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apexcore.app.freeze.FreezeBackendResolver
import com.apexcore.app.freeze.FreezeFramework
import com.apexcore.app.ui.theme.*
import kotlinx.coroutines.launch

object SetupDialogHelper {
    const val KEY_SHOWN = "setup_shown_v1"
    const val PREFS = "apexcore"
}

@Composable
fun SetupDialog(
    resolver: FreezeBackendResolver,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        onDispose {
            val prefs = context.getSharedPreferences(SetupDialogHelper.PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(SetupDialogHelper.KEY_SHOWN, true).apply()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceGlass)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderGlass, RoundedCornerShape(28.dp))
                    .clickable(enabled = false) {} // block click propagation
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "SYSTEM ACCESS CONFIG",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a mode to enable deep process freezing.",
                    color = TextBody,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Bento Layout
                // 1. Shizuku (Full Width, Recommended)
                OptionCard(
                    title = "Shizuku Service",
                    sub = "Uses wireless debugging API. Faster and does not require root access.",
                    cta = "CONFIGURE SHIZUKU",
                    badge = "RECOMMENDED",
                    isRecommended = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    openShizuku(context)
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Root & Accessibility (Side-by-side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OptionCard(
                        title = "Root access",
                        sub = "Direct su shell execution.",
                        cta = "GRANT ROOT",
                        isRecommended = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch {
                            resolver.invalidate()
                            val backend = FreezeFramework.detect()
                            if (backend.priority < 99) {
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Root permission not found yet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    OptionCard(
                        title = "Accessibility",
                        sub = "Automates app settings UI.",
                        cta = "OPEN SETTINGS",
                        isRecommended = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        openAccessibilitySettings(context)
                        onDismiss()
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Skip / Cached Only Action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "USE CACHED-ONLY MODE",
                        color = AccentPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    sub: String,
    cta: String,
    isRecommended: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    val bgBrush = if (isRecommended) {
        Brush.verticalGradient(listOf(AccentPrimary.copy(alpha = 0.15f), AccentSecondary.copy(alpha = 0.05f)))
    } else {
        Brush.verticalGradient(listOf(BgDark, BgDark))
    }
    
    val borderBrush = if (isRecommended) {
        Brush.horizontalGradient(listOf(AccentPrimary.copy(alpha = 0.6f), AccentSecondary.copy(alpha = 0.4f)))
    } else {
        Brush.horizontalGradient(listOf(BorderGlass, BorderGlass))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush)
            .border(1.dp, borderBrush, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = TextTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentSuccess.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        color = AccentSuccess,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sub,
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = cta + "  →",
            color = if (isRecommended) AccentPrimary else TextTitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun openShizuku(context: Context) {
    val pm = context.packageManager
    val candidates = listOf("moe.shizuku.manager", "moe.shizuku.api")
    for (pkg in candidates) {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {}
        }
    }
    val play = Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.manager")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(play) } catch (_: Throwable) {}
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {}
}
