package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class StampInk(val color: Color) {
    Phosphor(Iron.Phosphor400),
    Brass(Iron.Brass400),
    Ember(Iron.Ember500),
    Signal(Iron.Signal500),
}

@Composable
fun StampLabel(
    text: String,
    ink: StampInk = StampInk.Phosphor,
    modifier: Modifier = Modifier,
    slam: Boolean = true,
    pulse: Boolean = false,
) {
    val clack = rememberClack()
    val scale = remember { Animatable(if (slam) 1.6f else 1f) }
    val rot = remember { Animatable(if (slam) -8f else -3f) }
    val alpha = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current

    LaunchedEffect(text) {
        if (slam && !reduced) {
            scale.snapTo(1.6f)
            rot.snapTo(-8f)
            launch { rot.animateTo(-3f, tween(200)) }
            scale.animateTo(1f, IronMotion.stamp())
            clack.thud()
        } else {
            scale.snapTo(1f)
            rot.snapTo(-3f)
        }
    }

    LaunchedEffect(pulse) {
        if (!pulse) {
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            alpha.animateTo(0.6f, tween(600))
            alpha.animateTo(1f, tween(600))
        }
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .rotate(rot.value)
            .semantics { contentDescription = "$text, status" }
            .ironGrain(0.12f)
    ) {
        Box(Modifier.border(2.dp, ink.color)) {
            Box(Modifier.padding(3.dp).border(1.dp, ink.color.copy(alpha = 0.6f))) {
                Text(
                    text,
                    style = IronType.Label.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.3.sp,
                        color = ink.color
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
