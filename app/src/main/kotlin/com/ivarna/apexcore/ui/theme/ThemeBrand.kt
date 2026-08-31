package com.ivarna.apexcore.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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

    fun logoRes(isPaper: Boolean): Int =
        if (isPaper) R.drawable.ic_app_logo_vellum else R.drawable.ic_app_logo_graphite

    fun wantsVellumIcon(context: Context, mode: ThemeMode = ThemePreferences.get(context)): Boolean {
        return when (mode) {
            ThemeMode.VELLUM -> true
            ThemeMode.GRAPHITE -> false
            ThemeMode.SYSTEM -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night != Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /** Swap launcher activity-alias so home-screen icon matches Graphite/Vellum. */
    fun syncLauncherIcon(context: Context, mode: ThemeMode = ThemePreferences.get(context)) {
        val pm = context.packageManager
        val vellum = wantsVellumIcon(context, mode)
        setAliasEnabled(pm, ComponentName(context, ALIAS_VELLUM), enabled = vellum)
        setAliasEnabled(pm, ComponentName(context, ALIAS_GRAPHITE), enabled = !vellum)
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
