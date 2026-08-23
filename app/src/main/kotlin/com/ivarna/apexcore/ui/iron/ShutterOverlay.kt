package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

@Composable
fun ShutterOverlay(trigger: Int, onSeam: () -> Unit, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val clack = rememberClack()
    val reduced = LocalReducedMotion.current
    val gate = LocalCeremonyGate.current
    var closed by remember(trigger) { mutableStateOf(true) }
    var dim by remember(trigger) { mutableStateOf(1f) }

    LaunchedEffect(trigger) {
        gate.run {
            if (reduced) {
                dim = 1f
                delay(200)
                onSeam()
                dim = 0f
            } else {
                closed = true
                delay(200)
                clack.thud()
                onSeam()
                delay(80)
                closed = false
            }
        }
    }

    if (reduced) {
        val t by animateFloatAsState(dim, tween(200), label = "shutterDim")
        if (t > 0.01f) {
            Box(
                modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = t }
                    .background(Iron.Anvil900.copy(alpha = 0.45f))
            )
        }
        return
    }

    val t by animateFloatAsState(
        if (closed) 1f else 0f,
        tween(if (closed) 160 else 280, easing = IronMotion.EaseWind),
        label = "shutter"
    )

    if (t > 0.01f) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .graphicsLayer { translationY = -size.height * (1f - t) }
                    .background(Iron.Anvil900)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = size.height * (1f - t) }
                    .background(Iron.Anvil900)
            )
        }
    }
}
