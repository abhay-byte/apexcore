package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TickerLine(
    text: String,
    led: LedState,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val skin = ironSkin()
    Row(
        modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap?.invoke() }) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedDot(led)
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(
            !collapsed,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.weight(1f)
        ) {
            AnimatedContent(
                text,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "ticker"
            ) { t ->
                Text(
                    t,
                    style = IronType.Mono,
                    color = skin.text,
                    maxLines = 1,
                    overflow = if (reduced) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (reduced) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
        Text("  ▸▸", style = IronType.MonoSm, color = skin.textDim)
    }
}
