package com.ivarna.apexcore.ui.iron

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.random.Random

val LocalPaperSurfaces = staticCompositionLocalOf { false }

val LocalRisoCount = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
fun IronScreen(name: String, content: @Composable () -> Unit) {
    val count = remember(name) { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalRisoCount provides count) {
        content()
    }
}

@Composable
fun IronTheme(
    themeMode: ThemeMode,
    paperInserts: Boolean,
    reducedMotionOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val finish = themeMode.resolve(isSystemInDarkTheme())

    val d = LocalDensity.current
    val capped = remember(d, d.fontScale) {
        if (d.fontScale > 1.3f) Density(d.density, fontScale = 1.3f) else d
    }

    val systemReduced = remember {
        Settings.Global.getFloat(
            ctx.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val reduced = systemReduced || (reducedMotionOverride == true)

    // NOTE: status/nav bar tint is owned by the visible surface (IronShell / Ignition /
    // FieldManual), not here — a second writer here kept re-darkening icons in Graphite.

    CompositionLocalProvider(
        LocalIronFinish provides finish,
        LocalPaperSurfaces provides (finish == IronFinish.VELLUM || paperInserts),
        LocalReducedMotion provides reduced,
        LocalRisoCount provides mutableIntStateOf(0),
        LocalDensity provides capped,
    ) { content() }
}

object Grain {
    val image: ImageBitmap by lazy {
        val s = 128
        val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
        val px = IntArray(s * s)
        val rnd = Random(42)
        for (i in px.indices) {
            val v = 120 + rnd.nextInt(136)
            px[i] = android.graphics.Color.rgb(v, v, v)
        }
        bmp.setPixels(px, 0, s, 0, 0, s, s)
        bmp.asImageBitmap()
    }
}

internal fun Modifier.ironGrainInternal(alpha: Float, paper: Boolean): Modifier = this.drawWithCache {
    val brush = ShaderBrush(ImageShader(Grain.image, TileMode.Repeated, TileMode.Repeated))
    val blend = if (paper) BlendMode.Multiply else BlendMode.Screen
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = alpha, blendMode = blend)
    }
}

@Composable
fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier {
    val paper = ironSkin().isPaper
    return ironGrainInternal(alpha, paper)
}

@Composable
fun IronContentFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 480.dp)
    ) { content() }
}
