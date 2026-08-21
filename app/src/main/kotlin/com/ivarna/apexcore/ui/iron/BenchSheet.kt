package com.ivarna.apexcore.ui.iron

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * §3.14 BenchSheet — all dialogs live here.
 * Predictive back SCRUBS the dismiss: sheet scales 1.0→0.92 and scrim fades
 * with the finger. Commit = dismiss. Cancel = spring back.
 */
@Composable
fun BenchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clack = rememberClack()
    var scrub by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { info -> scrub = info.progress }
            onDismiss()
        } catch (_: Throwable) {
            scrub = 0f
        }
    }

    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit  = slideOutVertically(tween(240, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - scrub }
                    .background(Iron.Scrim)
                    .ironGrain(0.04f)
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 1f - 0.08f * scrub
                        scaleX = s
                        scaleY = s
                        alpha = 1f - scrub
                        translationY = dragY
                    }
                    .clip(IronShape.Plate)
                    .background(Iron.Anvil800)
                    .ironGrain(0.04f)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy ->
                                dragY = (dragY + dy).coerceAtLeast(0f)
                                change.consume()
                            },
                            onDragEnd = {
                                if (dragY > 120.dp.toPx()) {
                                    clack.off()
                                    onDismiss()
                                }
                                dragY = 0f
                            }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .size(32.dp, 4.dp)
                        .background(Iron.Brass400)
                )
                Column(Modifier.padding(20.dp), content = content)
            }
        }
    }
}
