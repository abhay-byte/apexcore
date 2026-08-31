package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class IronDialogSeverity {
    Normal,
    Warning,
    Danger,
}

/**
 * Iron-native confirm dialog. Uses Compose [Dialog] for the window, renders an Iron plate
 * with ChamferButton actions — never Material TextButton / AlertDialog purple.
 */
@Composable
fun IronConfirmDialog(
    visible: Boolean,
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    severity: IronDialogSeverity = IronDialogSeverity.Normal,
) {
    if (!visible) return
    val skin = ironSkin()
    val accent = when (severity) {
        IronDialogSeverity.Normal -> Iron.Brass400
        IronDialogSeverity.Warning -> skin.warningText()
        IronDialogSeverity.Danger -> skin.dangerText()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            IronSurface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                padding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(accent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        title,
                        style = IronType.Title.copy(fontSize = 18.sp),
                        color = skin.text,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(body, style = IronType.Body, color = skin.textDim)
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = skin.hairline, thickness = 1.dp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChamferButton(
                        text = dismissLabel.uppercase(),
                        onClick = onDismiss,
                        tall = false,
                        variant = ChamferVariant.Outline,
                    )
                    ChamferButton(
                        text = confirmLabel.uppercase(),
                        onClick = onConfirm,
                        tall = false,
                        variant = ChamferVariant.Primary,
                    )
                }
            }
        }
    }
}
