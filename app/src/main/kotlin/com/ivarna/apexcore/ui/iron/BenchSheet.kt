package com.ivarna.apexcore.ui.iron

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.ui.iron.window.IronWidth
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow

/**
 * §3.14 BenchSheet — bottom sheet on phone, side sheet on tablet/rail widths.
 * Predictive back SCRUBS the dismiss.
 */
@Composable
fun BenchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalIronWindow.current.width >= IronWidth.MEDIUM) {
        IronSideSheet(visible, onDismiss, modifier, content)
    } else {
        IronBottomSheet(visible, onDismiss, modifier, content)
    }
}

@Composable
fun IronBottomSheet(
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

    val sheetBg = ironSkin().plate
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(240, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
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
                    .background(sheetBg)
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

@Composable
fun IronSideSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clack = rememberClack()
    var scrub by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { scrub = it.progress }
            onDismiss()
        } catch (_: Throwable) {
            scrub = 0f
        }
    }

    val skin = ironSkin()
    val sheetBg = skin.plate
    val railBg = if (skin.isPaper) Iron.Bone50 else Iron.Anvil900
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(240, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
        modifier = modifier,
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

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = minOf(440.dp, maxWidth * 0.84f)
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(width)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = 1f - 0.08f * scrub
                            scaleX = s
                            scaleY = s
                            alpha = 1f - scrub
                            translationX = dragX + scrub * size.width * 0.25f
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dx ->
                                    dragX = (dragX + dx).coerceAtLeast(0f)
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (dragX > 120.dp.toPx()) {
                                        clack.off()
                                        onDismiss()
                                    }
                                    dragX = 0f
                                },
                            )
                        },
                ) {
                    Box(
                        Modifier
                            .width(12.dp)
                            .fillMaxHeight()
                            .background(railBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(4.dp, 32.dp).background(Iron.Brass400))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .background(sheetBg)
                            .ironGrain(0.04f)
                            .padding(20.dp)
                            .navigationBarsPadding(),
                        content = content,
                    )
                }
            }
        }
    }
}
