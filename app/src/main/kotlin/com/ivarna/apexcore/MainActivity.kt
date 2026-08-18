package com.ivarna.apexcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.ui.onboarding.OnboardingScreen
import com.ivarna.apexcore.ui.shell.MainScreen
import com.ivarna.apexcore.ui.splash.SplashScreen
import com.ivarna.apexcore.ui.theme.ApexCoreTheme
import com.ivarna.apexcore.ui.theme.ThemeMode
import com.ivarna.apexcore.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

enum class AppStage {
    SPLASH,
    ONBOARDING,
    MAIN
}

class MainActivity : ComponentActivity() {
    private val gameManager by lazy { GameManager(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreezeFramework.init(this)
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky {
                FreezeFramework.resolver()?.invalidate()
                lifecycleScope.launch {
                    try {
                        FreezeFramework.detect()
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var appStage by remember { mutableStateOf(AppStage.SPLASH) }
            var themeMode by remember { mutableStateOf(ThemePreferences.get(this@MainActivity)) }
            var lightTankBg by remember {
                mutableStateOf(ThemePreferences.getLightTankBg(this@MainActivity))
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = themeMode.resolveDark(systemDark)

            ApexCoreTheme(darkTheme = darkTheme) {
                AnimatedContent(
                    targetState = appStage,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
                    },
                    label = "AppStageTransition"
                ) { stage ->
                    when (stage) {
                        AppStage.SPLASH -> {
                            SplashScreen(
                                onSplashFinished = { showOnboarding ->
                                    appStage = if (showOnboarding) AppStage.ONBOARDING else AppStage.MAIN
                                }
                            )
                        }
                        AppStage.ONBOARDING -> {
                            OnboardingScreen(
                                onFinish = {
                                    appStage = AppStage.MAIN
                                }
                            )
                        }
                        AppStage.MAIN -> {
                            MainScreen(
                                gameManager = gameManager,
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    ThemePreferences.set(this@MainActivity, mode)
                                },
                                lightTankBg = lightTankBg,
                                onLightTankBgChange = { enabled ->
                                    lightTankBg = enabled
                                    ThemePreferences.setLightTankBg(this@MainActivity, enabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        FreezeFramework.resolver()?.invalidate()
        // Re-detect so chip/banner refresh immediately after granting Shizuku/Root
        lifecycleScope.launch {
            FreezeFramework.detect()
        }
    }
}
