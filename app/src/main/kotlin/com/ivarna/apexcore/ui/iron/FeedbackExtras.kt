package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* ── 37.2 CeremonyGate — §4.1 "one ceremony at a time", enforced in code ── */
class CeremonyGate {
    var busy by mutableStateOf(false)
        private set

    suspend fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        try {
            block()
        } finally {
            busy = false
        }
    }
}

@Composable
fun rememberCeremonyGate(): CeremonyGate = remember { CeremonyGate() }

/* ── 37.3 StampToast — §6.2 "COPIED" etc. ── */
class StampToastState {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) {
        message = text
    }
}

@Composable
fun rememberStampToast(): StampToastState {
    val s = remember { StampToastState() }
    LaunchedEffect(s.message) {
        if (s.message != null) {
            delay(1400)
            s.show("")
            // message becomes null via clear
        }
    }
    return s
}

@Composable
fun StampToastHost(state: StampToastState, modifier: Modifier = Modifier) {
    val msg = state.message
    if (!msg.isNullOrEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 110.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            key(msg) {
                StampLabel(msg, StampInk.Phosphor)
            }
        }
    }
}

/* ── 37.4 ErrorSlip — §8 playbook: paper slip, auto-dismiss 6s ── */
@Composable
fun ErrorSlip(
    visible: Boolean,
    detail: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "ERROR",
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(6000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { -it / 2 } + fadeIn(tween(320)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        PaperPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RisoText(
                    title,
                    IronType.Title.copy(fontSize = 15.sp),
                    color = Iron.Ink900,
                    modifier = Modifier.weight(1f)
                )
                StampLabel("ERR", StampInk.Ember, slam = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(detail, style = IronType.MonoSm, color = Iron.Ink600)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("RETRY", onRetry, Modifier.weight(1f), tall = false)
                ChamferButton("DISMISS", onDismiss, Modifier.weight(1f), variant = ChamferVariant.Outline, tall = false)
            }
        }
    }
}

/* ── 37.5 ContextSheet — §6.2 long-press menu ── */
data class ContextAction(val label: String, val danger: Boolean = false, val action: () -> Unit)

@Composable
fun ContextSheet(
    visible: Boolean,
    title: String,
    subtitle: String,
    actions: List<ContextAction>,
    onDismiss: () -> Unit,
) {
    val clack = rememberClack()
    BenchSheet(visible = visible, onDismiss = onDismiss) {
        Text(title, style = IronType.Title.copy(fontSize = 17.sp), color = Iron.Bone100)
        Text(subtitle, style = IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(14.dp))
        actions.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickableNoIndication {
                        clack.row()
                        a.action()
                        onDismiss()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                    drawLine(
                        if (a.danger) Iron.Ember500 else Iron.Bone300,
                        Offset(6.dp.toPx(), size.height / 2f),
                        Offset(size.width - 6.dp.toPx(), size.height / 2f),
                        2.dp.toPx()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    a.label,
                    style = IronType.Label,
                    color = if (a.danger) Iron.Ember500 else Iron.Bone100
                )
            }
        }
    }
}

/* ── 37.7 Brass focus ring — §9 keyboard/DPAD ── */
fun Modifier.ironFocus(shapeRadius: Dp = 6.dp): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    this
        .focusable()
        .onFocusChanged { hasFocus = it.isFocused || it.hasFocus }
        .drawWithContent {
            drawContent()
            if (hasFocus) {
                val o = 4.dp.toPx()
                drawRoundRect(
                    Iron.Brass400,
                    topLeft = Offset(-o, -o),
                    size = Size(size.width + 2 * o, size.height + 2 * o),
                    cornerRadius = CornerRadius(shapeRadius.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
}

/* ── 37.8 clickableNoIndication ── */
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
