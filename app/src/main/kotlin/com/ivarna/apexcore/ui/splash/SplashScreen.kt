package com.ivarna.apexcore.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.R
import com.ivarna.apexcore.ui.onboarding.OnboardingPreferences
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenType
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: (showOnboarding: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var startAnimation by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.78f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1300)
        val isCompleted = OnboardingPreferences.isOnboardingCompleted(context)
        onSplashFinished(!isCompleted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Soft ambient aura
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(logoScale)
                .alpha(contentAlpha * 0.45f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.35f),
                            scheme.primaryContainer.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(logoScale)
                .alpha(contentAlpha)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_no_bg),
                contentDescription = "ApexCore Logo",
                modifier = Modifier.size(110.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ApexCore",
                style = ZenType.hero,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ZEN PERFORMANCE ENGINE",
                style = ZenType.label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                letterSpacing = 2.5.sp
            )
        }
    }
}