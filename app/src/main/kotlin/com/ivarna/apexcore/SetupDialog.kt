package com.ivarna.apexcore

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.launch

object SetupDialogHelper {
    const val KEY_SHOWN = "setup_shown_v1"
    const val PREFS = "apexcore"
}

const val PRIVACY_POLICY_URL = "https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md"

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
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.40f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                    .clickable(enabled = false) {} // block click propagation
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "SYSTEM ACCESS CONFIG",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Deep freeze (BOOST) requires Shizuku or Root access.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                // 2. Root access (full width)
                OptionCard(
                    title = "Root access",
                    sub = "Direct su shell execution.",
                    cta = "GRANT ROOT",
                    isRecommended = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    coroutineScope.launch {
                        resolver.invalidate()
                        val backend = FreezeFramework.detect()
                        if (backend != null) {
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Root permission not found yet", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Privacy policy link (Play User Data policy requires an in-app link)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable { openPrivacyPolicy(context) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "PRIVACY POLICY",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dismiss — no "Standard mode" fallback exists for freeze (Decision E)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NOT NOW",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans,
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
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)))
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    }
    
    val borderBrush = if (isRecommended) {
        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
    } else {
        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
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
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sub,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = cta + "  →",
            color = if (isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSans
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
        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api&hl=en&pli=1")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(play) } catch (_: Throwable) {}
}

/** Opens the public privacy policy in the browser (Play User Data — must be always reachable). */
fun openPrivacyPolicy(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(PRIVACY_POLICY_URL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {}
}
