package com.ivarna.apexcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.games.GameManager
import com.ivarna.apexcore.ui.iron.BackendChoice
import com.ivarna.apexcore.ui.iron.IronTheme
import com.ivarna.apexcore.ui.iron.KeyStatus
import com.ivarna.apexcore.ui.iron.ThemeMode
import com.ivarna.apexcore.ui.iron.manual.FieldManual
import com.ivarna.apexcore.ui.iron.splash.Ignition
import com.ivarna.apexcore.ui.iron.window.IronFold
import com.ivarna.apexcore.ui.iron.window.IronWindowBox
import com.ivarna.apexcore.ui.onboarding.OnboardingPreferences
import com.ivarna.apexcore.ui.shell.MainScreen
import com.ivarna.apexcore.ui.theme.ThemeBrand
import com.ivarna.apexcore.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

enum class AppStage {
    SPLASH,
    ONBOARDING,
    MAIN
}

class MainActivity : ComponentActivity() {
    private val gameManager by lazy { GameManager(this) }
    private val foldState = mutableStateOf<IronFold>(IronFold.None)
    /** Launcher-alias sync deferred to onStop so disabling the launch component cannot tear down this task. */
    private var pendingLauncherSync = false

    override fun attachBaseContext(newBase: android.content.Context) {
        // Align night resources (splash FG/BG) with locked Graphite/Vellum before inflate.
        super.attachBaseContext(ThemeBrand.wrapContextForTheme(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeBrand.syncLauncherIcon(this)
        FreezeFramework.init(this)
        windowInfoFlow()
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

        setContent {
            var appStageOrdinal by rememberSaveable { mutableIntStateOf(AppStage.SPLASH.ordinal) }
            val appStage = AppStage.entries[appStageOrdinal]
            var themeModeOrdinal by rememberSaveable {
                mutableIntStateOf(ThemePreferences.get(this@MainActivity).ordinal)
            }
            val themeMode = when (themeModeOrdinal) {
                1 -> ThemeMode.VELLUM
                2 -> ThemeMode.GRAPHITE
                else -> ThemeMode.SYSTEM
            }
            var paperInserts by rememberSaveable {
                mutableStateOf(ThemePreferences.getLightTankBg(this@MainActivity))
            }
            var mechanicalMotion by rememberSaveable {
                mutableStateOf(ThemePreferences.getMechanicalMotion(this@MainActivity))
            }
            val motionOverride = when (mechanicalMotion) {
                "full" -> false
                "reduced" -> true
                else -> null
            }

            var shizukuStatus by remember { mutableStateOf(KeyStatus()) }
            var rootStatus by remember { mutableStateOf(KeyStatus()) }
            val fold by foldState

            fun probeBackends() {
                lifecycleScope.launch {
                    val sReady = try { ShizukuFreezeBackend().isReady() } catch (_: Throwable) { false }
                    shizukuStatus = KeyStatus(
                        ready = sReady,
                        checking = false,
                        statusLine = if (sReady) "Connected · wireless debugging" else "Service not running"
                    )
                    val rReady = try { RootFreezeBackend().isReady() } catch (_: Throwable) { false }
                    rootStatus = KeyStatus(
                        ready = rReady,
                        checking = false,
                        statusLine = if (rReady) "su granted" else "su not granted"
                    )
                }
            }

            LaunchedEffect(Unit) {
                probeBackends()
            }

            IronTheme(
                themeMode = themeMode,
                paperInserts = paperInserts,
                reducedMotionOverride = motionOverride
            ) {
                IronWindowBox(fold = fold) {
                    AnimatedContent(
                        targetState = appStage,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
                        },
                        label = "AppStageTransition"
                    ) { stage ->
                        when (stage) {
                            AppStage.SPLASH -> {
                                Ignition(
                                    onSplashFinished = { showOnboarding ->
                                        appStageOrdinal = if (showOnboarding) AppStage.ONBOARDING.ordinal else AppStage.MAIN.ordinal
                                    }
                                )
                            }
                            AppStage.ONBOARDING -> {
                                val prefs = getSharedPreferences("apexcore", MODE_PRIVATE)
                                val prefStr = prefs.getString("preferred_backend", null)
                                val selectedBackend = when (prefStr) {
                                    "shizuku" -> BackendChoice.SHIZUKU
                                    "root" -> BackendChoice.ROOT
                                    else -> null
                                }

                                FieldManual(
                                    isReplay = false,
                                    onboardingCompletedProbe = { OnboardingPreferences.isOnboardingCompleted(this@MainActivity) },
                                    shizuku = shizukuStatus,
                                    root = rootStatus,
                                    selectedBackend = selectedBackend,
                                    onProbe = { probeBackends() },
                                    onSelect = { choice ->
                                        val key = if (choice == BackendChoice.SHIZUKU) "shizuku" else "root"
                                        prefs.edit().putString("preferred_backend", key).apply()
                                        FreezeFramework.setPreferredBackend(if (choice == BackendChoice.SHIZUKU) "Shizuku" else "Root")
                                        FpsStack.get(this@MainActivity).syncPreferredBackend(key)
                                        probeBackends()
                                    },
                                    onConfigureShizuku = {
                                        val pm = packageManager
                                        val candidates = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager", "moe.shizuku.api")
                                        var launched = false
                                        for (pkg in candidates) {
                                            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                                            if (intent != null) {
                                                try {
                                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    startActivity(intent)
                                                    launched = true
                                                    break
                                                } catch (_: Throwable) {}
                                            }
                                        }
                                        if (!launched) {
                                            val play = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try { startActivity(play) } catch (_: Throwable) {}
                                        }
                                    },
                                    onGrantRoot = {
                                        probeBackends()
                                    },
                                    onFinish = {
                                        OnboardingPreferences.setOnboardingCompleted(this@MainActivity, true)
                                        appStageOrdinal = AppStage.MAIN.ordinal
                                    },
                                    onClose = {
                                        appStageOrdinal = AppStage.MAIN.ordinal
                                    }
                                )
                            }
                            AppStage.MAIN -> {
                                MainScreen(
                                    gameManager = gameManager,
                                    themeMode = ThemeMode.entries[themeModeOrdinal],
                                    onThemeModeChange = { mode ->
                                        themeModeOrdinal = mode.ordinal
                                        ThemePreferences.set(this@MainActivity, mode)
                                        // Defer alias swap — disabling the alias that launched us
                                        // finishes this activity on many OEMs (looks like a crash).
                                        pendingLauncherSync = true
                                    },
                                    lightTankBg = paperInserts,
                                    onLightTankBgChange = { enabled ->
                                        paperInserts = enabled
                                        ThemePreferences.setLightTankBg(this@MainActivity, enabled)
                                    },
                                    mechanicalMotion = mechanicalMotion,
                                    onMechanicalMotionChange = { value ->
                                        mechanicalMotion = value
                                        ThemePreferences.setMechanicalMotion(this@MainActivity, value)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun windowInfoFlow() = lifecycleScope.launch {
        WindowInfoTracker.getOrCreate(this@MainActivity)
            .windowLayoutInfo(this@MainActivity)
            .collect { info -> foldState.value = parseFold(info) }
    }

    private fun parseFold(info: WindowLayoutInfo): IronFold {
        val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            ?: return IronFold.None
        val density = resources.displayMetrics.density
        fun dp(px: Int) = (px / density).dp
        val r = f.bounds
        return IronFold.Seam(
            x = if (f.orientation == FoldingFeature.Orientation.VERTICAL) dp(r.centerX()) else null,
            y = if (f.orientation == FoldingFeature.Orientation.HORIZONTAL) dp(r.centerY()) else null,
            separating = f.isSeparating,
        )
    }

    override fun onResume() {
        super.onResume()
        FreezeFramework.resolver()?.invalidate()
        lifecycleScope.launch {
            FreezeFramework.detect()
        }
    }

    override fun onStop() {
        if (pendingLauncherSync) {
            pendingLauncherSync = false
            ThemeBrand.syncLauncherIcon(this)
        }
        super.onStop()
    }
}
