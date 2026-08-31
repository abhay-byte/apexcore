package com.ivarna.apexcore.ui.iron.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.onboarding.OnboardingPreferences
import com.ivarna.apexcore.ui.theme.ApexBrandIcon
import kotlinx.coroutines.delay

@Composable
fun Ignition(onSplashFinished: (showOnboarding: Boolean) -> Unit) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            appear.animateTo(1f, tween(200))
            delay(200)
        } else {
            appear.animateTo(1f, tween(360, easing = IronMotion.EaseWind))
            delay(550)
        }
        onSplashFinished(!OnboardingPreferences.isOnboardingCompleted(context))
    }

    IronScreen("IGNITION") {
    val skin = ironSkin()
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val a = view.context.findActivity()
        a?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = skin.isPaper
            c.isAppearanceLightNavigationBars = skin.isPaper
        }
    }
    Box(Modifier.fillMaxSize().background(skin.canvas).ironGrain(0.05f)) {
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
            ApexBrandIcon(size = 96.dp)
            Spacer(Modifier.height(20.dp))
            RisoText("APEXCORE", IronType.Display.copy(fontSize = 28.sp))
            Spacer(Modifier.height(6.dp))
            Text(
                "FIELD-GRADE PERFORMANCE INSTRUMENTS",
                style = IronType.MonoSm,
                color = skin.textDim,
                letterSpacing = 2.5.sp
            )
        }
    }
    }
}
