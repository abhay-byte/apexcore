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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.random.Random

/* ── LocalPaperSurfaces: §10 — metal chassis, swap only content surfaces ── */
val LocalPaperSurfaces = staticCompositionLocalOf { false }

/* ── Riso audit: §1.5 rule 1 — exactly one riso per screen ── */
val LocalRisoCount = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
inline fun IronScreen(name: String, crossinline content: @Composable () -> Unit) {
    val count = LocalRisoCount.current
    remember(name) { count.intValue = 0; true }
    content()
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

    // §9 — cap font scale at the spec ceiling (1.3×)
    val d = LocalDensity.current
    val capped = remember(d, d.fontScale) {
        if (d.fontScale > 1.3f) Density(d.density, fontScale = 1.3f) else d
    }

    // §4.4 — auto-reduce when system animator scale == 0
    val systemReduced = remember {
        Settings.Global.getFloat(
            ctx.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    // status/nav bar icon tint per finish
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        ctx.findActivity()?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            val lightBars = finish == IronFinish.VELLUM
            c.isAppearanceLightStatusBars = lightBars
            c.isAppearanceLightNavigationBars = lightBars
        }
    }

    CompositionLocalProvider(
        LocalIronFinish provides finish,
        LocalPaperSurfaces provides (finish == IronFinish.VELLUM || paperInserts),
        LocalReducedMotion provides (reducedMotionOverride ?: systemReduced),
        LocalRisoCount provides mutableIntStateOf(0),
        LocalDensity provides capped,
    ) { content() }
}

/* ── Errata #1/#2: fixed grain — one ImageBitmap, Compose shader ── */
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

fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier = this.drawWithCache {
    val brush = ShaderBrush(ImageShader(Grain.image, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = alpha)
    }
}

/** §2.3 — tablet: content column max 480dp, centered. */
@Composable
fun IronContentFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 480.dp)
    ) { content() }
}
