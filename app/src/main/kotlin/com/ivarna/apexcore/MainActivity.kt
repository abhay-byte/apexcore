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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
            // Config-change survives without recreate, but process death still needs
            // Saveable. Enums are stored as ordinals (no custom Saver needed).
            var appStageOrdinal by rememberSaveable { mutableIntStateOf(AppStage.SPLASH.ordinal) }
            val appStage = AppStage.entries[appStageOrdinal]
            var themeModeOrdinal by rememberSaveable {
                mutableIntStateOf(ThemePreferences.get(this@MainActivity).ordinal)
            }
            val themeMode = ThemeMode.entries[themeModeOrdinal]
            var lightTankBg by rememberSaveable {
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
                                    appStageOrdinal = if (showOnboarding) AppStage.ONBOARDING.ordinal else AppStage.MAIN.ordinal
                                }
                            )
                        }
                        AppStage.ONBOARDING -> {
                            OnboardingScreen(
                                onFinish = {
                                    appStageOrdinal = AppStage.MAIN.ordinal
                                }
                            )
                        }
                        AppStage.MAIN -> {
                            MainScreen(
                                gameManager = gameManager,
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    themeModeOrdinal = mode.ordinal
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
