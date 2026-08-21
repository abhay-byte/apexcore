package com.ivarna.apexcore.ui.iron

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class IronFinish { GRAPHITE, VELLUM }
enum class ThemeMode { SYSTEM, VELLUM, GRAPHITE }

val LocalIronFinish = staticCompositionLocalOf { IronFinish.GRAPHITE }

data class IronSkin(
    val canvas: Color, val plate: Color, val platePressed: Color,
    val text: Color, val textDim: Color, val hairline: Color, val tick: Color,
    val isPaper: Boolean,
) {
    companion object {
        val Graphite = IronSkin(
            Iron.Anvil900, Iron.Anvil700, Iron.Anvil800,
            Iron.Bone100, Iron.Bone500, Iron.Anvil600, Iron.Anvil500, isPaper = false
        )
        val Vellum = IronSkin(
            Iron.Bone50, Iron.Bone100, Iron.Bone50,
            Iron.Ink900, Iron.Ink600, Iron.Ink600.copy(alpha = 0.25f),
            Iron.Ink600.copy(alpha = 0.45f), isPaper = true
        )
    }
}

@Composable
fun ironSkin(): IronSkin =
    if (LocalIronFinish.current == IronFinish.VELLUM) IronSkin.Vellum else IronSkin.Graphite

fun ThemeMode.resolve(systemDark: Boolean): IronFinish = when (this) {
    ThemeMode.SYSTEM -> if (systemDark) IronFinish.GRAPHITE else IronFinish.VELLUM
    ThemeMode.GRAPHITE -> IronFinish.GRAPHITE
    ThemeMode.VELLUM -> IronFinish.VELLUM
}

@Composable
fun IronSurface(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalPaperSurfaces.current) PaperPlate(modifier, padding = padding, content = content)
    else EngravedPlate(modifier, padding = padding, content = content)
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
