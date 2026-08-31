package com.ivarna.apexcore.ui.iron.window

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IronWidth { COMPACT, MEDIUM, EXPANDED } // <600 · 600–839 · ≥840
enum class IronHeight { COMPACT, MEDIUM, EXPANDED } // <480 · 480–839 · ≥840

enum class IronFormFactor { PHONE, LANDSCAPE, TABLET }

data class IronWindow(val width: IronWidth, val height: IronHeight) {
    val form: IronFormFactor = when {
        height == IronHeight.COMPACT -> IronFormFactor.LANDSCAPE
        width == IronWidth.COMPACT -> IronFormFactor.PHONE
        else -> IronFormFactor.TABLET
    }
    val railWithLabels: Boolean get() = form == IronFormFactor.TABLET
    val split: Boolean get() = form == IronFormFactor.TABLET
}

fun ironWidthOf(w: Int): IronWidth = when {
    w < 600 -> IronWidth.COMPACT
    w < 840 -> IronWidth.MEDIUM
    else -> IronWidth.EXPANDED
}

fun ironHeightOf(h: Int): IronHeight = when {
    h < 480 -> IronHeight.COMPACT
    h < 840 -> IronHeight.MEDIUM
    else -> IronHeight.EXPANDED
}

val LocalIronWindow = staticCompositionLocalOf {
    IronWindow(IronWidth.COMPACT, IronHeight.COMPACT)
}

/** The hinge is THE SEAM — two plates meeting. x/y null when not applicable. */
sealed interface IronFold {
    data object None : IronFold
    data class Seam(val x: Dp?, val y: Dp?, val separating: Boolean) : IronFold
}

val LocalIronFold = staticCompositionLocalOf<IronFold> { IronFold.None }

/** Root measuring box — provides window + fold locals to the whole tree. */
@Composable
fun IronWindowBox(
    fold: IronFold = IronFold.None,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val win = remember(maxWidth, maxHeight) {
            IronWindow(
                ironWidthOf(maxWidth.value.toInt().coerceAtLeast(0)),
                ironHeightOf(maxHeight.value.toInt().coerceAtLeast(0)),
            )
        }
        CompositionLocalProvider(
            LocalIronWindow provides win,
            LocalIronFold provides fold,
        ) {
            content()
        }
    }
}

/** §2.3 — tablet dial scale 1.15×, phone baseline. Landscape gets a compact dial. */
fun dialSizeFor(form: IronFormFactor): Dp = when (form) {
    IronFormFactor.PHONE -> 240.dp
    IronFormFactor.LANDSCAPE -> 188.dp
    IronFormFactor.TABLET -> 276.dp
}
