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
            Iron.Anvil950, Iron.Anvil700, Iron.Anvil800,
            Iron.Bone100, Iron.Bone500, Iron.Anvil600, Iron.Anvil500, isPaper = false
        )
        val Vellum = IronSkin(
            Iron.Bone50, Iron.Bone100, Iron.Bone50,
            Iron.Ink900, Iron.Ink600, Iron.Ink600.copy(alpha = 0.25f),
            Iron.Ink600.copy(alpha = 0.45f), isPaper = true
        )
    }
}

fun IronSkin.phosphor(): Color = if (isPaper) Iron.Phosphor600 else Iron.Phosphor400

/** High-contrast danger/status text (meets ~4.5:1 on plate surfaces). */
fun IronSkin.dangerText(): Color = if (isPaper) Iron.Signal700 else Iron.Signal300

fun IronSkin.warningText(): Color = if (isPaper) Iron.Ink600 else Iron.Brass400

fun IronSkin.successText(): Color = if (isPaper) Iron.Phosphor800 else Iron.Phosphor400

fun IronSkin.disabledText(): Color = textDim

/** Theme-aware input / select field surfaces (never mix Anvil with Ink text). */
fun IronSkin.inputSurface(): Color = if (isPaper) Iron.Bone50 else Iron.Anvil950

fun IronSkin.inputBorder(): Color = if (isPaper) Iron.Ink600.copy(alpha = 0.35f) else Iron.Anvil600

fun IronSkin.popupSurface(): Color = if (isPaper) Iron.Bone100 else Iron.Anvil800

fun IronSkin.popupBorder(): Color = if (isPaper) Iron.Ink600.copy(alpha = 0.40f) else Iron.Anvil500

fun IronSkin.selectedRow(): Color = if (isPaper) Iron.Bone300 else Iron.Anvil650

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
