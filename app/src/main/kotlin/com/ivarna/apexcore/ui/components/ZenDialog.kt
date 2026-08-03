package com.ivarna.apexcore.ui.components

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ivarna.apexcore.ui.theme.LocalZenSemantics

/**
 * Zen modal shell: true window backdrop blur (API 31+) + soft theme scrim.
 *
 * Never uses [androidx.compose.material3.ColorScheme.inverseSurface] for the dim —
 * that paints white wash in dark mode and harsh black in light. Scrim is always a
 * deep ink tint at low alpha so light stays airy and dark stays deep, with blur
 * carrying the glass feel when the platform supports it.
 */
@Composable
fun ZenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalZenSemantics.current.isDark
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            // We own dim + blur; kill the default heavy black platform dim.
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            if (supportsBlur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = BLUR_BEHIND_RADIUS_PX
                window.attributes = attrs
            }
        }

        // Soft ink scrim — lower when blur is active so frost shows through.
        val scrim = if (isDark) {
            Color.Black.copy(alpha = if (supportsBlur) 0.28f else 0.52f)
        } else {
            // Deep teal ink (matches Zen dark surface), not pure black — soft veil over light UI.
            Color(0xFF0C171B).copy(alpha = if (supportsBlur) 0.18f else 0.32f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Blocks scrim-dismiss when the user taps the sheet.
 * Call from a @Composable scope so [interactionSource] is stable.
 */
@Composable
fun Modifier.zenDialogSheet(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {}
    )
}

private const val BLUR_BEHIND_RADIUS_PX = 48
