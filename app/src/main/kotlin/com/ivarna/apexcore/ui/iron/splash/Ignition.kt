package com.ivarna.apexcore.ui.iron.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.onboarding.OnboardingPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Ignition(onSplashFinished: (showOnboarding: Boolean) -> Unit) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val sweep = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!reduced) {
            launch {
                sweep.animateTo(1f, tween(300, easing = LinearEasing))
                sweep.animateTo(0.45f, IronMotion.needle())
            }
        }
        appear.animateTo(1f, tween(360, easing = IronMotion.EaseWind))
        delay(550)
        onSplashFinished(!OnboardingPreferences.isOnboardingCompleted(context))
    }

    Box(Modifier.fillMaxSize().background(Iron.Anvil900).ironGrain(0.04f)) {
        Column(
            Modifier.align(Alignment.Center)
                .graphicsLayer {
                    val s = 0.96f + 0.04f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(96.dp)) {
                val r = size.minDimension / 2f
                val c6 = 6.dp.toPx()
                val c12 = 12.dp.toPx()
                drawPath(chamferPath(size, c6, c12), Iron.Anvil800)
                drawPath(
                    chamferPath(size, c6, c12),
                    Iron.Anvil600,
                    style = Stroke(1.dp.toPx())
                )
                val ring = r * 0.62f
                repeat(24) { i ->
                    val a = (i / 24f) * 240f - 210f
                    drawLine(
                        Iron.Anvil500,
                        Offset(
                            r + cos(Math.toRadians(a.toDouble())).toFloat() * ring,
                            r + sin(Math.toRadians(a.toDouble())).toFloat() * ring
                        ),
                        Offset(
                            r + cos(Math.toRadians(a.toDouble())).toFloat() * (ring + 8.dp.toPx()),
                            r + sin(Math.toRadians(a.toDouble())).toFloat() * (ring + 8.dp.toPx())
                        ),
                        1.5.dp.toPx()
                    )
                }
                val na = Math.toRadians((-210f + 240f * sweep.value).toDouble())
                drawLine(
                    Iron.Signal500, Offset(r, r),
                    Offset(r + cos(na).toFloat() * ring * 0.9f, r + sin(na).toFloat() * ring * 0.9f),
                    3.dp.toPx()
                )
                drawCircle(Iron.Brass400, 5.dp.toPx(), Offset(r, r))
            }
            Spacer(Modifier.height(20.dp))
            RisoText("APEXCORE", IronType.Display.copy(fontSize = 28.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                "FIELD-GRADE PERFORMANCE INSTRUMENTS",
                style = IronType.MonoSm,
                color = Iron.Bone500,
                letterSpacing = 2.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text("MK·II", style = IronType.MonoSm, color = Iron.Brass400)
        }
    }
}
