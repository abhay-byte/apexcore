package com.ivarna.apexcore.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.R
import com.ivarna.apexcore.ui.iron.ThemeMode
import com.ivarna.apexcore.ui.iron.ironSkin

/**
 * Official ApexCore mark — Graphite (dark plate) vs Vellum (light plate).
 * In-app surfaces follow [ironSkin]; launcher / splash follow [ThemeMode] + system night.
 */
object ThemeBrand {
    private const val ALIAS_GRAPHITE = "com.ivarna.apexcore.LauncherGraphite"
    private const val ALIAS_VELLUM = "com.ivarna.apexcore.LauncherVellum"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDisable: Runnable? = null

    fun logoRes(isPaper: Boolean): Int =
        if (isPaper) R.drawable.ic_app_logo_vellum else R.drawable.ic_app_logo_graphite

    /**
     * Real device night mode — always from [Context.getApplicationContext], never the
     * activity base wrapped by [wrapContextForTheme]. A Vellum-locked wrap forces
     * UI_MODE_NIGHT_NO; reading that for ThemeMode.SYSTEM incorrectly keeps light/Vellum
     * even when the phone is in dark mode.
     */
    fun isSystemDark(context: Context): Boolean {
        val cfg = context.applicationContext.resources.configuration
        return (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun wantsVellumIcon(context: Context, mode: ThemeMode = ThemePreferences.get(context)): Boolean {
        return when (mode) {
            ThemeMode.VELLUM -> true
            ThemeMode.GRAPHITE -> false
            ThemeMode.SYSTEM -> !isSystemDark(context)
        }
    }

    /**
     * Swap launcher activity-alias so home-screen icon matches Graphite/Vellum.
     *
     * Disabling the alias that launched the current task can tear the activity down
     * (OEM launchers ignore DONT_KILL_APP). Enable the target first, then disable the
     * previous alias after a short delay once both are live.
     */
    fun syncLauncherIcon(context: Context, mode: ThemeMode = ThemePreferences.get(context)) {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val vellum = wantsVellumIcon(appCtx, mode)
        val enable = ComponentName(appCtx, if (vellum) ALIAS_VELLUM else ALIAS_GRAPHITE)
        val disable = ComponentName(appCtx, if (vellum) ALIAS_GRAPHITE else ALIAS_VELLUM)

        pendingDisable?.let { mainHandler.removeCallbacks(it) }
        pendingDisable = null

        setAliasEnabled(pm, enable, enabled = true)

        val disableTask = Runnable {
            pendingDisable = null
            setAliasEnabled(pm, disable, enabled = false)
        }
        pendingDisable = disableTask
        // Defer disable so the task is not pinned to a component we are about to turn off.
        mainHandler.postDelayed(disableTask, 750L)
    }

    private fun setAliasEnabled(pm: PackageManager, component: ComponentName, enabled: Boolean) {
        val state = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            val current = pm.getComponentEnabledSetting(component)
            if (current == state) return
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        } catch (_: Throwable) {
            // Alias missing on older installs mid-upgrade — ignore.
        }
    }

    /**
     * Force night resources for splash/adaptive FG when theme is locked to Graphite/Vellum.
     * SYSTEM leaves the device uiMode alone.
     */
    fun wrapContextForTheme(base: Context): Context {
        val mode = ThemePreferences.get(base)
        val night = when (mode) {
            ThemeMode.GRAPHITE -> true
            ThemeMode.VELLUM -> false
            ThemeMode.SYSTEM -> return base
        }
        val config = Configuration(base.resources.configuration)
        val rest = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        config.uiMode = rest or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(config)
    }
}

@Composable
fun ApexBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val paper = ironSkin().isPaper
    Image(
        painter = painterResource(ThemeBrand.logoRes(paper)),
        contentDescription = "ApexCore",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
