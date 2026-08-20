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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.freeze.FreezeBackendResolver
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.ui.components.StatusPebble
import com.ivarna.apexcore.ui.components.ZenDialog
import com.ivarna.apexcore.ui.components.zenDialogSheet
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SetupDialogHelper {
    const val KEY_SHOWN = "setup_shown_v1"
    const val PREFS = "apexcore"
}

// Privacy policy is in-app only (private repo — no public hosting URL).
// ponytail: point at private doc hosting if Play Console ever requires an https URL.
const val PRIVACY_POLICY_URL = ""

@Composable
fun SetupDialog(
    resolver: FreezeBackendResolver,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dialogShape = RoundedCornerShape(28.dp)

    // null = checking, true = ready, false = not available
    var shizukuReady by remember { mutableStateOf<Boolean?>(null) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }

    // Probe on open + re-check while dialog is visible (user may grant in Shizuku app)
    LaunchedEffect(Unit) {
        val shizuku = ShizukuFreezeBackend()
        val root = RootFreezeBackend()
        while (true) {
            shizukuReady = try {
                shizuku.isReady()
            } catch (_: Throwable) {
                false
            }
            rootReady = try {
                root.invalidate()
                root.isReady()
            } catch (_: Throwable) {
                false
            }
            delay(1200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val prefs = context.getSharedPreferences(SetupDialogHelper.PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(SetupDialogHelper.KEY_SHOWN, true).apply()
        }
    }

    fun selectBackend(prefKey: String, displayName: String) {
        context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
            .edit().putString("preferred_backend", prefKey).apply()
        FreezeFramework.setPreferredBackend(displayName)
        FpsStack.get(context).syncPreferredBackend(prefKey)
        coroutineScope.launch {
            resolver.invalidate()
            val backend = FreezeFramework.detect()
            if (backend != null) {
                onDismiss()
            } else {
                Toast.makeText(
                    context,
                    "$displayName not ready yet — finish setup and try again",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ZenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                // Window is already sized 0.92×/max 560dp by ZenDialog; no horizontal
                // padding so the glass card fills that width exactly, inner 24dp keeps inset.
                .padding(vertical = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .zenGlassBackground(
                    shape = dialogShape,
                    fill = scheme.surfaceContainerLowest.copy(alpha = 0.96f),
                    borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                )
                .zenDialogSheet()
                .padding(ZenDimens.containerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SYSTEM ACCESS CONFIG",
                color = scheme.onSurfaceVariant,
                style = ZenType.overline,
                fontFamily = PlusJakartaSans,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(ZenDimens.base))
            Text(
                text = "Deep freeze (BOOST) requires Shizuku or Root access.",
                color = scheme.onSurfaceVariant,
                style = ZenType.bodySm,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            // 1. Shizuku
            OptionCard(
                title = "Shizuku Service",
                sub = when (shizukuReady) {
                    true -> "Connected and ready for deep freeze."
                    false -> "Uses wireless debugging API. Faster and does not require root access."
                    null -> "Checking Shizuku status…"
                },
                cta = when (shizukuReady) {
                    true -> "USE SHIZUKU"
                    false -> "CONFIGURE SHIZUKU"
                    null -> "CHECKING…"
                },
                ready = shizukuReady,
                badge = when (shizukuReady) {
                    true -> "READY"
                    false -> "RECOMMENDED"
                    null -> "…"
                },
                isRecommended = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (shizukuReady) {
                    true -> selectBackend("shizuku", "Shizuku")
                    false -> {
                        openShizuku(context)
                        // keep dialog open so status can flip to READY when user returns
                    }
                    null -> { /* still probing */ }
                }
            }

            Spacer(modifier = Modifier.height(ZenDimens.elementGap))

            // 2. Root
            OptionCard(
                title = "Root access",
                sub = when (rootReady) {
                    true -> "su granted — ready for deep freeze."
                    false -> "Direct su shell execution."
                    null -> "Checking root status…"
                },
                cta = when (rootReady) {
                    true -> "USE ROOT"
                    false -> "GRANT ROOT"
                    null -> "CHECKING…"
                },
                ready = rootReady,
                badge = when (rootReady) {
                    true -> "READY"
                    false -> null
                    null -> "…"
                },
                isRecommended = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (rootReady) {
                    true -> selectBackend("root", "Root")
                    false -> {
                        coroutineScope.launch {
                            resolver.invalidate()
                            RootFreezeBackend().invalidate()
                            val backend = FreezeFramework.detect()
                            // Re-probe after request
                            rootReady = RootFreezeBackend().isReady()
                            if (backend != null && rootReady == true) {
                                selectBackend("root", "Root")
                            } else {
                                Toast.makeText(
                                    context,
                                    "Root permission not found yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    null -> { /* still probing */ }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceContainerLow.copy(alpha = 0.9f))
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NOT NOW",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans,
                    letterSpacing = 1.sp
                )
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
    ready: Boolean?,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val isReady = ready == true

    val bgBrush = when {
        isReady -> Brush.verticalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.18f),
                scheme.primary.copy(alpha = 0.06f)
            )
        )
        isRecommended -> Brush.verticalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.15f),
                scheme.primary.copy(alpha = 0.05f)
            )
        )
        else -> Brush.verticalGradient(
            listOf(
                scheme.surfaceContainerLow.copy(alpha = 0.9f),
                scheme.surfaceContainerLow.copy(alpha = 0.9f)
            )
        )
    }

    val borderBrush = when {
        isReady -> Brush.horizontalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.75f),
                scheme.primary.copy(alpha = 0.45f)
            )
        )
        isRecommended -> Brush.horizontalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.6f),
                scheme.primary.copy(alpha = 0.4f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                scheme.outlineVariant.copy(alpha = 0.6f),
                scheme.outlineVariant.copy(alpha = 0.6f)
            )
        )
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(enabled = ready != null, onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPebble(active = ready, size = 10.dp)
                Text(
                    text = title,
                    color = scheme.onSurface,
                    style = ZenType.titleSm,
                    fontWeight = FontWeight.Bold
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isReady) scheme.primary.copy(alpha = 0.28f)
                            else scheme.primary.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .heightIn(min = 20.dp)
                        .widthIn(max = 120.dp)
                ) {
                    Text(
                        text = badge,
                        color = scheme.primary,
                        style = ZenType.micro,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sub,
            color = scheme.onSurfaceVariant,
            style = ZenType.label,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isReady) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = cta,
                color = if (isRecommended || isReady) scheme.primary else scheme.onSurface,
                style = ZenType.label,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSans
            )
            if (!isReady && ready != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isRecommended) scheme.primary else scheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun openShizuku(context: Context) {
    if (try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }) {
        try {
            rikka.shizuku.Shizuku.requestPermission(101)
            return
        } catch (_: Throwable) {}
    }
    val pm = context.packageManager
    val candidates = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager", "moe.shizuku.api")
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
    try {
        context.startActivity(play)
    } catch (_: Throwable) {}
}

/** Opens the privacy policy in the browser. No-op — the repo is private; use in-app PrivacyPolicyScreen. */
@Deprecated("Use in-app PrivacyPolicyScreen; kept for Settings fallback")
fun openPrivacyPolicy(context: Context) {
    if (PRIVACY_POLICY_URL.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(PRIVACY_POLICY_URL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {}
}