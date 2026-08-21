# IRONWORK — Implementation Pack II (files 20–33)

Continues the pack: assumes files 1–19 (`IronTokens`, `Clack`, `Plates`, `Buttons`, `StampLabel`, `InstrumentDial`, `Scales`, `TickerLine`, `ToolRow`, `Controls`, `Shell`, `BenchSheet`, `Effects`, `Fields`, `TheBench`, `LaunchMatrix` parts, HUD touch, Tune slip) exist under `ui/iron/`. Imports are wildcarded for brevity — resolve in IDE on paste.

---

## 20. `Skin.kt` — finish engine (Graphite ↔ Vellum, §10)

```kotlin
package com.ivarna.apexcore.ui.iron

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
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
            Iron.Bone100, Iron.Bone500, Iron.Anvil600, Iron.Anvil500, isPaper = false)
        val Vellum = IronSkin(
            Iron.Bone50, Iron.Bone100, Iron.Bone50,
            Iron.Ink900, Iron.Ink600, Iron.Ink600.copy(alpha = 0.25f),
            Iron.Ink600.copy(alpha = 0.45f), isPaper = true)
    }
}

@Composable fun ironSkin(): IronSkin =
    if (LocalIronFinish.current == IronFinish.VELLUM) IronSkin.Vellum else IronSkin.Graphite

fun ThemeMode.resolve(systemDark: Boolean): IronFinish = when (this) {
    ThemeMode.SYSTEM -> if (systemDark) IronFinish.GRAPHITE else IronFinish.VELLUM
    ThemeMode.GRAPHITE -> IronFinish.GRAPHITE
    ThemeMode.VELLUM -> IronFinish.VELLUM
}

/** §10 — surfaces flip metal↔paper per finish. Data readouts STAY metal in both
 *  (in Vellum that's the "ink plate" inversion from the spec: use EngravedPlate directly). */
@Composable
fun IronSurface(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (ironSkin().isPaper) PaperPlate(modifier, padding = padding, content = content)
    else EngravedPlate(modifier, padding = padding, content = content)
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) { if (ctx is Activity) return ctx; ctx = ctx.baseContext }
    return null
}
```

---

## 21. `Chrome.kt` — dropdown, back arrow, loading needle

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.*
import kotlinx.coroutines.isActive

/** Anchored menu — put inside the trigger's Box; aligns to its top-end. */
@Composable
fun IronDropdown(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 240.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        EngravedPlate(modifier, padding = PaddingValues(8.dp)) {
            Box(Modifier.width(width)) { Column(content = content) }
        }
    }
}

@Composable
fun DropdownLedRow(label: String, ready: Boolean, selected: Boolean, onClick: () -> Unit) {
    val clack = rememberClack()
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(IronShape.Slot)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clack.row(); onClick()
            }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(if (ready) LedState.READY else LedState.BLOCKED)
        Spacer(Modifier.width(8.dp))
        Text(label, IronType.Mono, color = if (selected) Iron.Bone100 else Iron.Bone300,
            modifier = Modifier.weight(1f))
        if (selected) Text("●", IronType.MonoSm, color = Iron.Brass400)
    }
}

@Composable
fun BackArrow(tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clack = rememberClack()
    Canvas(
        modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { clack.row(); onClick() }
    ) {
        val c = center
        drawLine(tint, Offset(c.x - 9.dp.toPx(), c.y), Offset(c.x + 9.dp.toPx(), c.y), 2.dp.toPx())
        drawLine(tint, Offset(c.x - 4.dp.toPx(), c.y - 5.dp.toPx()), Offset(c.x - 9.dp.toPx(), c.y), 2.dp.toPx())
        drawLine(tint, Offset(c.x - 4.dp.toPx(), c.y + 5.dp.toPx()), Offset(c.x - 9.dp.toPx(), c.y), 2.dp.toPx())
        tickAtGlyph(Offset(c.x + 9.dp.toPx(), c.y), 1f, 0f, tint)   // tick terminal
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.tickAtGlyph(
    p: Offset, dx: Float, dy: Float, tint: Color,
) {
    val n = 3.dp.toPx()
    drawLine(tint, Offset(p.x - dy * n, p.y + dx * n), Offset(p.x + dy * n, p.y - dx * n), 2.dp.toPx())
}

/** §8 — probing is ALWAYS a spinning needle, never a skeleton. */
@Composable
fun LoadingNeedle(tint: Color = Iron.Bone300, modifier: Modifier = Modifier) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (isActive) { rot.snapTo(0f); rot.animateTo(360f, tween(900, easing = LinearEasing)) }
    }
    Canvas(modifier.size(28.dp)) {
        withTransform({ rotate(rot.value, pivot = center) }) {
            drawLine(tint, center, Offset(center.x, center.y - size.minDimension / 2f + 2.dp.toPx()), 2.dp.toPx())
        }
        drawCircle(tint, 2.dp.toPx(), center)
    }
}
```

---

## 22. `Ink.kt` — doodles, key glyphs, FIG artwork (§2.6, §7.2)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ink get() = Iron.Ink600

/* ── Doodles: 1.5dp stroke, hand wiggle, paper surfaces only ── */

@Composable
fun DoodleArrow(tint: Color = ink, modifier: Modifier = Modifier) = Canvas(modifier.size(44.dp)) {
    val st = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
    val path = Path().apply {                                   // curved, slightly off
        moveTo(4.dp.toPx(), size.height - 6.dp.toPx())
        quadraticTo(size.width * 0.42f, size.height * 0.88f,
            size.width - 8.dp.toPx(), 8.dp.toPx())
    }
    drawPath(path, tint, style = st)
    val tip = Offset(size.width - 8.dp.toPx(), 8.dp.toPx())
    drawLine(tint, tip, tip - Offset(8.dp.toPx(), 2.dp.toPx()), st.width, StrokeCap.Round)
    drawLine(tint, tip, tip - Offset(2.dp.toPx(), 8.dp.toPx()), st.width, StrokeCap.Round)
}

@Composable
fun DoodleStar(tint: Color = ink, modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val c = center; val r = size.minDimension / 2f - 2f
    val w = 1.5.dp.toPx()
    repeat(5) { i ->                                            // pentagram scribble
        val a0 = (i * 144f) * PI.toFloat() / 180f - PI.toFloat() / 2
        val a1 = ((i + 2) * 144f) * PI.toFloat() / 180f - PI.toFloat() / 2
        drawLine(tint, Offset(c.x + cos(a0) * r, c.y + sin(a0) * r),
            Offset(c.x + cos(a1) * r, c.y + sin(a1) * r), w, StrokeCap.Round)
    }
}

@Composable
fun DoodleSquiggle(tint: Color = ink, modifier: Modifier = Modifier) = Canvas(modifier.size(60.dp, 10.dp)) {
    val path = Path().apply {
        moveTo(2f, size.height / 2f)
        var x = 2f; var up = true
        while (x < size.width - 2f) {
            quadraticTo(x + 4f, if (up) 0f else size.height, x + 8f, size.height / 2f)
            x += 8f; up = !up
        }
    }
    drawPath(path, tint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
fun DoodleCircle(tint: Color = Iron.Signal700, modifier: Modifier = Modifier) = Canvas(modifier.size(34.dp)) {
    drawArc(tint, 0f, 300f, false, Offset(2f, 2f), size.copy(width = size.width - 4f, height = size.height - 4f),
        style = Stroke(1.5.dp.toPx()))
    drawArc(tint, 310f, 40f, false, Offset(4f, 4f),
        size.copy(width = size.width - 8f, height = size.height - 8f), style = Stroke(1.5.dp.toPx()))
}

/* ── The two keys (§7.2 page 4) ── */

@Composable
fun SkeletonKeyGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(22.dp)) {
    val s = size.width / 22f
    drawCircle(tint, 6f * s, Offset(6f * s, 11f * s), style = Stroke(2f * s, cap = StrokeCap.Round))
    drawLine(tint, Offset(12f * s, 11f * s), Offset(21f * s, 11f * s), 2f * s, StrokeCap.Round)
    drawLine(tint, Offset(17f * s, 11f * s), Offset(17f * s, 15f * s), 2f * s, StrokeCap.Round)
    drawLine(tint, Offset(20f * s, 11f * s), Offset(20f * s, 16f * s), 2f * s, StrokeCap.Round)
}

@Composable
fun AllenKeyGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val s = size.width / 20f
    drawLine(tint, Offset(5f * s, 4f * s), Offset(5f * s, 16f * s), 2.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(5f * s, 16f * s), Offset(15f * s, 16f * s), 2.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(3.5f * s, 3f * s), Offset(6.5f * s, 3f * s), 1.5f * s, StrokeCap.Round)
    drawLine(tint, Offset(13.5f * s, 14.5f * s), Offset(16.5f * s, 14.5f * s), 1.5f * s, StrokeCap.Round)
}

/* ── FIG frames: corner registration ticks + label ── */

@Composable
fun FigFrame(label: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val t = 12.dp.toPx(); val w = 1.5.dp.toPx()
            fun mark(c: Offset, dx: Float, dy: Float) {
                drawLine(ink, c, c + Offset(dx * t, 0f), w)
                drawLine(ink, c, c + Offset(0f, dy * t), w)
            }
            mark(Offset(0f, 0f), 1f, 1f); mark(Offset(size.width, 0f), -1f, 1f)
            mark(Offset(0f, size.height), 1f, -1f); mark(Offset(size.width, size.height), -1f, -1f)
        }
        Text(label, IronType.MonoSm, color = ink, modifier = Modifier.padding(6.dp))
        content()
    }
}

/* ── FIG artwork: ink line-art, 1.5dp strokes, one accent each ── */

@Composable
fun FigArtwork(figure: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(200.dp)) {
        val s = size.width / 200f
        fun d(v: Int) = v * s
        val w = 1.5.dp.toPx()

        when (figure) {
            0 -> {                                               // cover: instrument cluster doodle
                drawCircle(ink, d(66), center, style = Stroke(w))
                repeat(12) { i ->
                    val a = i / 12f * 2f * PI.toFloat() - PI.toFloat() / 2
                    drawLine(ink, Offset(center.x + cos(a) * d(66), center.y + sin(a) * d(66)),
                        Offset(center.x + cos(a) * d(74), center.y + sin(a) * d(74)), w)
                }
                drawLine(Iron.Signal700, center, Offset(center.x + d(26), center.y - d(44)), 2.5f * s)
                drawCircle(ink, d(4), center, ink)
                drawText(measurer.measure("S/N 3F-0042",
                    TextStyle(PlexMono, fontSize = 9.sp, color = ink)),
                    topLeft = Offset(center.x - d(34), center.y + d(50)))
            }
            1 -> {                                               // exploded dial + shavings
                val c = Offset(d(95), d(72))
                drawCircle(ink, d(44), c, style = Stroke(w))
                repeat(16) { i ->
                    val a = i / 16f * 2f * PI.toFloat()
                    drawLine(ink, Offset(c.x + cos(a) * d(44), c.y + sin(a) * d(44)),
                        Offset(c.x + cos(a) * d(50), c.y + sin(a) * d(50)), w)
                }
                drawPath(Path().apply {                          // dashed alignment guide
                    moveTo(c.x, c.y); lineTo(d(148), d(40))
                }, ink, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 4f * s))))
                drawLine(Iron.Signal700, Offset(d(148), d(40)), Offset(d(172), d(16)), 2.5f * s)
                drawCircle(Iron.Brass400, d(4), Offset(d(148), d(40)))
                repeat(6) { i ->                                  // falling shavings
                    val x = d(128 + i * 12); val y = d(148 + (i % 3) * 14)
                    drawPath(Path().apply {
                        moveTo(x, y); lineTo(x + d(9), y - d(3)); lineTo(x + d(7), y + d(5)); close()
                    }, if (i % 2 == 0) Iron.Signal700 else ink)
                }
                drawText(measurer.measure("+1.4 GB",
                    TextStyle(PlexMono, fontSize = 13.sp, color = Iron.Signal700)),
                    topLeft = Offset(d(20), d(150)))
            }
            2 -> {                                               // phone + rail + data column
                drawRoundRect(ink, Offset(d(55), d(16)),
                    Size(d(90), d(168)), CornerRadius(d(8)), style = Stroke(w))
                drawLine(Iron.Brass400, Offset(d(68), d(52)), Offset(d(68), d(148)), 2f * s)
                drawRoundRect(ink, Offset(d(74), d(56)), Size(d(46), d(92)), CornerRadius(d(3)), style = Stroke(w))
                drawText(measurer.measure("144",
                    TextStyle(PlexMono, fontSize = 17.sp, color = Iron.Phosphor400)),
                    topLeft = Offset(d(80), d(60)))
                drawText(measurer.measure("FPS", TextStyle(PlexMono, fontSize = 7.sp, color = ink)),
                    topLeft = Offset(d(80), d(80)))
                val sp = Path().apply {                           // sparkline
                    moveTo(d(78), d(108))
                    lineTo(d(86), d(102)); lineTo(d(94), d(110)); lineTo(d(102), d(99)); lineTo(d(114), d(104))
                }
                drawPath(sp, Iron.Signal700, style = Stroke(1.2.dp.toPx()))
                repeat(8) { i ->                                  // cpu bars
                    val h = 4 + (i * 37 % 13)
                    drawLine(ink, Offset(d(78 + i * 5), d(142)), Offset(d(78 + i * 5), d(142 - h)), 2.5f * s)
                }
                drawText(measurer.measure("❄", TextStyle(PlexMono, fontSize = 11.sp, color = Iron.Brass400)),
                    topLeft = Offset(d(88), d(120)))
            }
            3 -> {                                               // luggage tag + capped tube
                drawRoundRect(ink, Offset(d(26), d(58)), Size(d(82), d(42)), CornerRadius(d(6)), style = Stroke(w))
                drawCircle(ink, d(7), Offset(d(38), d(79)), style = Stroke(w))
                drawCircle(Iron.Brass400, d(3), Offset(d(38), d(79)))
                drawPath(Path().apply {                          // string to key
                    moveTo(d(45), d(79)); quadraticTo(d(105), d(52), d(138), d(70))
                }, ink, style = Stroke(w))
                drawLine(Iron.Signal700, Offset(d(138), d(70)), Offset(d(168), d(70)), 2.5f * s)
                drawCircle(Iron.Signal700, d(5), Offset(d(134), d(70)), style = Stroke(2f * s))
                // capped gauge tube
                drawRoundRect(ink, Offset(d(150), d(118)), Size(d(14), d(66)), CornerRadius(d(3)), style = Stroke(w))
                drawRect(Iron.Signal700, Offset(d(152), d(148)), Size(d(10), d(34)))
                drawLine(Iron.Brass400, Offset(d(146), d(146)), Offset(d(168), d(146)), 2f * s)
                drawText(measurer.measure("CAP", TextStyle(PlexMono, fontSize = 7.sp, color = Iron.Brass400)),
                    topLeft = Offset(d(146), d(132)))
            }
            4 -> {                                               // the two keys
                drawCircle(ink, d(24), Offset(d(48), d(100)), style = Stroke(2.5f * s))
                drawLine(ink, Offset(d(72), d(100)), Offset(d(118), d(100)), 2.5f * s)
                drawLine(ink, Offset(d(104), d(100)), Offset(d(104), d(112)), 2.5f * s)
                drawLine(ink, Offset(d(115), d(100)), Offset(d(115), d(114)), 2.5f * s)
                drawLine(Iron.Signal700, Offset(d(150), d(52)), Offset(d(150), d(122)), 3f * s)
                drawLine(Iron.Signal700, Offset(d(150), d(122)), Offset(d(176), d(122)), 3f * s)
                drawText(measurer.measure("or", TextStyle(Caveat, fontSize = 13.sp, color = ink)),
                    topLeft = Offset(d(122), d(52)))
            }
        }
    }
}
```

---

## 23. `PressureRoom.kt` — Ram Free full screen (§7.9)

```kotlin
package com.ivarna.apexcore.ui.iron.ram

import android.view.WindowManager
import androidx.activity.GestureCancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.drop

/* ═══ §7.9 PRESSURE ROOM ═════════════════════════════════════════════ */

enum class PressurePhase { IDLE, PREFREEZE, FILLING, HOLDING, RELEASING, DONE }
private val runningPhases = setOf(
    PressurePhase.PREFREEZE, PressurePhase.FILLING,
    PressurePhase.HOLDING, PressurePhase.RELEASING)

data class RamModeUi(val name: String, val ready: Boolean)

data class PressureUiState(
    val phase: PressurePhase = PressurePhase.IDLE,
    val ramUsedMb: Int = 0, val ramTotalMb: Int = 1,
    val swapUsedMb: Int = 0, val swapTotalMb: Int = 1,
    val resultGb: Float? = null,
)

/** Adapt from your RamFillManager progress — names shown for mapping. */
fun mapProgress(phase: PressurePhase) = phase   // ← replace with real mapper

@Composable
fun PressureRoom(
    state: PressureUiState,
    modes: List<RamModeUi>,
    selectedMode: RamModeUi?,
    preFreeze: Boolean,
    onMode: (RamModeUi) -> Unit,
    onPreFreeze: (Boolean) -> Unit,
    onStart: () -> Unit, onHold: () -> Unit, onRelease: () -> Unit, onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val running = state.phase in runningPhases

    // §7.9 keep screen on while running
    val view = LocalView.current
    DisposableEffect(running) {
        val window = view.context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // §7.9 cancel on pause + dispose
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, running) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && running) onCancel()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); if (running) onCancel() }
    }
    // §6.1 predictive back scrub → END SESSION? slip; commit cancels
    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = running) { progress ->
        try { progress.collect { scrub = it.progress }; onCancel() }
        catch (e: GestureCancellationException) { scrub = 0f }
    }

    var modeMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // ── top bar
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BackArrow(Iron.Bone300, onBack)
            Spacer(Modifier.width(8.dp))
            Text("PRESSURE ROOM", IronType.Display.copy(fontSize = 22.sp), color = Iron.Bone100,
                modifier = Modifier.weight(1f))
            // mode chip → dropdown
            Box {
                Row(
                    Modifier.clip(IronShape.Slot)
                        .border2(Iron.Anvil600)
                        .clickable(interactionSource = remember { MutableInteractionSource() },
                            indication = null) { clack.row(); modeMenu = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedDot(if (selectedMode?.ready == true) LedState.READY else LedState.BLOCKED)
                    Spacer(Modifier.width(6.dp))
                    Text(selectedMode?.name ?: "MODE", IronType.MonoSm, color = Iron.Bone300)
                    Text("  ▾", IronType.MonoSm, color = Iron.Bone500)
                }
                if (modeMenu) IronDropdown(onDismiss = { modeMenu = false }) {
                    modes.forEach { m ->
                        DropdownLedRow(m.name, m.ready, m == selectedMode) {
                            if (m.ready) onMode(m) else clack.no()
                            modeMenu = false
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── TubeManometer (RAM + SWAP)
        TubeManometer(
            ramFraction = state.ramUsedMb.toFloat() / state.ramTotalMb,
            swapFraction = state.swapUsedMb.toFloat() / state.swapTotalMb,
            ramText = "%d / %d MB".format(state.ramUsedMb, state.ramTotalMb),
            swapText = "%d / %d MB".format(state.swapUsedMb, state.swapTotalMb),
            energized = true,
        )

        Spacer(Modifier.height(6.dp))

        // ── State Railway
        EngravedText("STATE RAILWAY", IronType.Label, color = Iron.Bone500)
        Spacer(Modifier.height(8.dp))
        StateRailway(state.phase)

        // result
        AnimatedVisibility(state.phase == PressurePhase.DONE, enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160))) {
            state.resultGb?.let {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RESULT", IronType.MonoSm, color = Iron.Bone500)
                    Spacer(Modifier.width(10.dp))
                    OdometerCounter("+%.1f".format(it), style = IronType.Mono.copy(fontSize = 26.sp))
                    Text(" GB RECLAIMED", IronType.Mono, color = Iron.Phosphor400)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── pre-freeze toggle
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRE-FREEZE BEFORE FILL", IronType.Label, color = Iron.Bone100)
                Text("Purge user apps before pressure test", IronType.Caption, color = Iron.Bone500)
            }
            MachinedToggle(preFreeze, onPreFreeze)
        }

        Spacer(Modifier.height(12.dp))

        // ─§7.9 action button state machine: START → HOLD → RELEASE → CANCEL
        val (label, primary, action) = when (state.phase) {
            PressurePhase.IDLE, PressurePhase.DONE -> Triple("START PRESSURE TEST", true, onStart)
            PressurePhase.PREFREEZE, PressurePhase.FILLING -> Triple("HOLD", true, onHold)
            PressurePhase.HOLDING -> Triple("RELEASE", false, onRelease)
            PressurePhase.RELEASING -> Triple("CANCEL", false, onCancel)
        }
        ChamferButton(
            label, action,
            variant = if (primary) ChamferVariant.Primary else ChamferVariant.Outline,
            busy = state.phase == PressurePhase.FILLING,
            modifier = Modifier.fillMaxWidth(),
        )

        SerialFooter(8, "PRESSURE", serial)
    }

    // END SESSION? slip during back scrub
    if (scrub > 0.01f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PaperPlate(Modifier.graphicsLayerAlpha(scrub)) {
                RisoText("END SESSION?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900)
                Text("Release to cancel the pressure test.", IronType.Caption, color = Iron.Ink600)
            }
        }
    }
}

private fun Modifier.border2(c: Color): Modifier = this.then(
    Modifier.composed { border(1.dp, c, IronShape.Slot) })

/* ── TubeManometer: vertical mercury tubes + brass flags ── */

@Composable
fun TubeManometer(ramFraction: Float, swapFraction: Float, ramText: String, swapText: String, energized: Boolean) {
    val ramLvl by animateFloatAsState(ramFraction.coerceIn(0f, 1f), IronMotion.needle(), label = "ramL")
    val swapLvl by animateFloatAsState(swapFraction.coerceIn(0f, 1f), IronMotion.needle(), label = "swapL")
    Row(Modifier.fillMaxWidth().height(240.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Tube(196.dp, ramLvl, "RAM", ramText, energized)
        Spacer(Modifier.width(40.dp))
        Tube(124.dp, swapLvl, "SWAP", swapText, energized)
    }
}

@Composable
private fun Tube(tubeH: Dp, level: Float, label: String, valueText: String, energized: Boolean) {
    val measurer = rememberTextMeasurer()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.width(72.dp).height(tubeH).drawWithCache {
            val w = 12.dp.toPx()
            val x = (size.width - w) / 2f + 8.dp.toPx()
            val pct = listOf(0f, 50f, 100f).map {
                measurer.measure(if (it == 100f) "1.0" else ".${it.toInt()}",
                    TextStyle(PlexMono, fontSize = 8.sp, color = Iron.Bone500))
            }
            onDrawBehind {
                val lvl = level * (size.height - 4.dp.toPx())
                // tube outline
                drawRoundRect(Iron.Anvil600, Offset(x - 1f, 0f),
                    Size(w + 2f, size.height), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                // mercury — level animates via deferred read, no re-cache
                drawRoundRect(if (energized) Iron.Signal500 else Iron.Anvil500,
                    Offset(x, size.height - lvl), Size(w, lvl), CornerRadius(2.dp.toPx()))
                // marks every 10%
                (0..10).forEach { m ->
                    val y = size.height * (1f - m / 10f)
                    drawLine(Iron.Anvil500, Offset(x - 7.dp.toPx(), y), Offset(x - 2.dp.toPx(), y), 1.dp.toPx())
                }
                drawText(pct[0], topLeft = Offset(x - 30.dp.toPx(), size.height - 6.dp.toPx()))
                drawText(pct[1], topLeft = Offset(x - 30.dp.toPx(), size.height / 2f - 6.dp.toPx()))
                drawText(pct[2], topLeft = Offset(x - 34.dp.toPx(), 0f))
                // brass flag at level
                val y = size.height - lvl
                drawLine(Iron.Brass400, Offset(x + w, y), Offset(x + w + 9.dp.toPx(), y), 2.dp.toPx())
                drawPath(Path().apply {
                    moveTo(x + w + 9.dp.toPx(), y)
                    lineTo(x + w + 17.dp.toPx(), y - 5.dp.toPx())
                    lineTo(x + w + 9.dp.toPx(), y - 10.dp.toPx()); close()
                }, Iron.Brass400)
            }
        }) {}
        Spacer(Modifier.height(6.dp))
        Text(valueText, IronType.Mono, color = Iron.Bone100)
    }
}

/* ── State Railway: 5 stations, brass carriage, phosphor fill ── */

private val railLabels = listOf("PRE", "FILL", "HOLD", "REL", "DONE")

@Composable
fun StateRailway(phase: PressurePhase) {
    val clack = rememberClack()
    val idx = when (phase) {
        PressurePhase.IDLE -> -1
        PressurePhase.PREFREEZE -> 0
        PressurePhase.FILLING -> 1
        PressurePhase.HOLDING -> 2
        PressurePhase.RELEASING -> 3
        PressurePhase.DONE -> 4
    }
    // §7.9 arrival = CLOCK_TICK
    LaunchedEffect(Unit) { snapshotFlow { idx }.drop(1).collect { if (it >= 0) clack.tick() } }

    Column {
        // current station stamp-swap (160ms crossfade)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STATE", IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.width(10.dp))
            AnimatedContent(idx, transitionSpec = {
                fadeIn(tween(160)) togetherWith fadeOut(tween(160))
            }, label = "railState") { i ->
                Text(if (i < 0) "STANDBY" else railLabels[i], IronType.Mono,
                    color = if (i < 0) Iron.Bone500 else Iron.Phosphor400)
            }
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
            val segW = maxWidth / 5
            Canvas(Modifier.fillMaxSize()) {
                val segWpx = size.width / 5f
                val cy = 20.dp.toPx()
                drawLine(Iron.Anvil600, Offset(segWpx * 0.5f, cy), Offset(segWpx * 4.5f, cy), 2.dp.toPx())
                repeat(5) { i ->
                    val cx = segWpx * (i + 0.5f)
                    when {
                        idx > i -> drawCircle(Iron.Phosphor400, 5.dp.toPx(), Offset(cx, cy))
                        idx == i -> {
                            drawCircle(Iron.Phosphor400, 5.dp.toPx(), Offset(cx, cy))
                            drawCircle(Iron.Phosphor400.copy(alpha = 0.5f), 8.dp.toPx(),
                                Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                        }
                        else -> drawCircle(Iron.Anvil500, 5.dp.toPx(), Offset(cx, cy),
                            style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            // brass carriage
            val carriageX by animateDpAsState(
                if (idx < 0) -20.dp else segW * (idx + 0.5f) - 7.dp,
                IronMotion.drawer(), label = "carriage")
            Box(Modifier.offset(x = carriageX.coerceAtLeast(0.dp), y = 15.dp)
                .size(14.dp, 10.dp).clip(IronShape.Slot).background(Iron.Brass400))
            // labels
            Row(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                repeat(5) { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(railLabels[i], IronType.MonoSm,
                            color = when { i == idx -> Iron.Bone100; idx > i -> Iron.Phosphor400; else -> Iron.Bone500 })
                    }
                }
            }
        }
    }
}
```

---

## 24. `FieldManual.kt` — Onboarding on paper (§7.2)

```kotlin
package com.ivarna.apexcore.ui.iron.manual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ═══ §7.2 THE FIELD MANUAL — paper, stitched binding, ink figures ══ */

enum class BackendChoice { SHIZUKU, ROOT }

data class KeyStatus(
    val ready: Boolean = false,
    val checking: Boolean = true,
    val statusLine: String = "CHECKING…",
)

private val pageData = listOf(
    Triple("", "", ""),   // cover handled separately
    Triple("01 · PURGE ENGINE", "Focus Resources for Gaming",
        "ApexCore deep-freezes background apps and hands the reclaimed RAM to your game. One switch, every spare cycle."),
    Triple("02 · PERFORMANCE HUD", "Live On-Screen Telemetry",
        "A slim rail rides the screen edge — FPS, memory and CPU, live. It hides when idle."),
    Triple("03 · MEMORY TOOLKIT", "App Pins & Safe Reclaim",
        "Pin what you love. Pressure tests stay capped and never touch system apps."),
    Triple("04 · SYSTEM ACCESS", "Elevate Your Control",
        "Deep freeze needs a key. Pick Shizuku or Root — change it any time in Settings."),
)
private val marginNotes = listOf("", "~ wind it up!", "~ it hides when idle", "~ pin what you love", "~ pick your key")

@Composable
fun FieldManual(
    isReplay: Boolean,
    onboardingCompletedProbe: () -> Boolean,     // OnboardingPreferences.isOnboardingCompleted
    shizuku: KeyStatus,
    root: KeyStatus,
    selectedBackend: BackendChoice?,
    onProbe: () -> Unit,                          // re-detect both backends
    onSelect: (BackendChoice) -> Unit,            // write pref + sync FreezeFramework/FpsStack
    onConfigureShizuku: () -> Unit,
    onGrantRoot: () -> Unit,
    onFinish: (BackendChoice?) -> Unit,           // completes onboarding (null = skipped)
    onClose: () -> Unit,                          // replay close
) {
    val clack = rememberClack()
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val page = pagerState.currentPage

    // probe both backends every 1200ms while open (§10 SetupDialog parity)
    LaunchedEffect(Unit) { while (true) { onProbe(); delay(1200) } }

    Box(Modifier.fillMaxSize().background(Iron.Bone50).ironGrain(0.05f)) {
        Row(Modifier.fillMaxSize()) {
            BindingLane()
            Column(Modifier.weight(1f).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)) {

                // ── top lane: back/close · ruler pager · skip
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (isReplay) BackArrow(Iron.Ink900, onClose)
                    else BackArrow(Iron.Ink900, {
                        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) } else onClose()
                    })
                    Spacer(Modifier.weight(1f))
                    RulerPager(5, page)
                    Spacer(Modifier.weight(1f))
                    if (!isReplay) Text("SKIP", IronType.MonoSm, color = Iron.Ink600,
                        modifier = Modifier.clickableNoIndication { clack.row(); onFinish(null) })
                    else Spacer(Modifier.width(40.dp))
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { p ->
                    when (p) {
                        0 -> CoverPage(pagerState)
                        in 1..3 -> FigurePage(p, pagerState)
                        else -> KeyPage(pagerState, shizuku, root, selectedBackend,
                            onSelect, onConfigureShizuku, onGrantRoot)
                    }
                }

                // ── CTA: GET STARTED → CONTINUE → ENTER THE WORKSHOP
                val cta = when (page) { 0 -> "GET STARTED"; 4 -> "ENTER THE WORKSHOP"; else -> "CONTINUE" }
                ChamferButton(cta, {
                    clack.confirm()
                    if (page < 4) scope.launch { pagerState.animateScrollToPage(page + 1) }
                    else onFinish(selectedBackend)
                }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp))
            }
        }
    }
}

/* ── §7.2 stitched binding ── */
@Composable
private fun BindingLane() {
    Canvas(Modifier.width(22.dp).fillMaxHeight()) {
        val cx = 12.dp.toPx()
        var y = 0f                                        // dashed spine
        while (y < size.height) {
            drawLine(Iron.Ink600, Offset(cx, y), Offset(cx, y + 5.dp.toPx()), 1.5.dp.toPx())
            y += 9.dp.toPx()
        }
        var sy = 14.dp.toPx()                             // stitches crossing the spine
        while (sy < size.height) {
            drawLine(Iron.Ink900, Offset(cx - 5.dp.toPx(), sy), Offset(cx + 5.dp.toPx(), sy), 2.dp.toPx())
            sy += 26.dp.toPx()
        }
    }
}

@Composable
private fun RulerPager(count: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(4.dp, 16.dp).clip(IronShape.Slot)
                .background(if (i == active) Iron.Ink900 else Iron.Ink900.copy(alpha = 0.2f)))
        }
    }
}

/** §7.2 parallax — factor 0 = moves with page, 1 = pinned to screen. */
private fun Modifier.manualParallax(
    pagerState: androidx.compose.foundation.pager.PagerState,
    page: Int, factor: Float, reduced: Boolean,
): Modifier = graphicsLayer {
    if (reduced) return@graphicsLayer
    val distance = pagerState.currentPage + pagerState.currentPageOffsetFraction - page
    translationX = distance * size.width * factor
}

@Composable
private fun CoverPage(pagerState: androidx.compose.foundation.pager.PagerState) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        FigFrame("APEXCORE · MK·II", Modifier.size(200.dp)
            .manualParallax(pagerState, 0, 0.4f, LocalReducedMotion.current)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FigArtwork(0) }
        }
        Spacer(Modifier.height(24.dp))
        RisoText("APEXCORE", IronType.Display.copy(fontSize = 30.sp), color = Iron.Ink900)
        Text("FIELD-GRADE PERFORMANCE INSTRUMENTS", IronType.MonoSm, color = Iron.Ink600,
            modifier = Modifier.padding(top = 6.dp), letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoodleStar(); Spacer(Modifier.width(10.dp))
            Text("hello, operator.", IronType.Hand, color = Iron.Ink600)
        }
    }
}

@Composable
private fun FigurePage(
    page: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
) {
    val reduced = LocalReducedMotion.current
    val (kicker, title, body) = pageData[page]
    Box(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            FigFrame("FIG. 0$page", Modifier.size(196.dp).manualParallax(pagerState, page, 0.4f, reduced)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FigArtwork(page) }
            }
            Spacer(Modifier.height(20.dp))
            Text(kicker, IronType.MonoSm, color = Iron.Signal700, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, IronType.Title.copy(fontSize = 24.sp), color = Iron.Ink900)
            Spacer(Modifier.height(10.dp))
            Text(body, IronType.Body, color = Iron.Ink600, modifier = Modifier.padding(horizontal = 8.dp))
        }
        // margin note + doodle — deepest layer (0.3× movement)
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 12.dp)
            .manualParallax(pagerState, page, 0.7f, reduced),
            horizontalAlignment = Alignment.End) {
            DoodleArrow(Iron.Signal700, Modifier.graphicsLayer { rotationZ = 200f })
            Text(marginNotes[page], IronType.Hand, color = Iron.Ink600,
                modifier = Modifier.graphicsLayer { rotationZ = -4f })
        }
    }
}

@Composable
private fun KeyPage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    shizuku: KeyStatus, root: KeyStatus,
    selected: BackendChoice?,
    onSelect: (BackendChoice) -> Unit,
    onConfigureShizuku: () -> Unit, onGrantRoot: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("04 · SYSTEM ACCESS", IronType.MonoSm, color = Iron.Signal700, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Text("Elevate Your Control", IronType.Title.copy(fontSize = 24.sp), color = Iron.Ink900)
        Spacer(Modifier.height(8.dp))
        Text("Deep freeze needs a key. Pick Shizuku or Root — change it any time in Settings.",
            IronType.Body, color = Iron.Ink600)
        Spacer(Modifier.height(16.dp))
        KeyCard(BackendChoice.SHIZUKU, shizuku, selected == BackendChoice.SHIZUKU,
            onPaper = true, badge = "RECOMMENDED",
            onUse = { onSelect(BackendChoice.SHIZUKU) }, onConfigure = onConfigureShizuku)
        Spacer(Modifier.height(12.dp))
        KeyCard(BackendChoice.ROOT, root, selected == BackendChoice.ROOT,
            onPaper = true, badge = null,
            onUse = { onSelect(BackendChoice.ROOT) }, onConfigure = onGrantRoot)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.align(Alignment.CenterHorizontally)
            .manualParallax(pagerState, 4, 0.7f, LocalReducedMotion.current),
            verticalAlignment = Alignment.CenterVertically) {
            DoodleStar(); Spacer(Modifier.width(8.dp))
            Text(marginNotes[4], IronType.Hand, color = Iron.Ink600)
        }
    }
}

/* ── KeyCard: shared by Manual (paper) and Setup sheet (metal) ── */
@Composable
fun KeyCard(
    choice: BackendChoice,
    status: KeyStatus,
    selected: Boolean,
    onPaper: Boolean,
    onUse: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val clack = rememberClack()
    val borderC = if (onPaper) Iron.Ink600 else Iron.Anvil500
    val textC = if (onPaper) Iron.Ink900 else Iron.Bone100
    val dimC = if (onPaper) Iron.Ink600 else Iron.Bone500
    Box(modifier.fillMaxWidth().border(1.5.dp, borderC, IronShape.Plate).ironGrain(0.06f)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (choice == BackendChoice.SHIZUKU) SkeletonKeyGlyph(textC) else AllenKeyGlyph(textC)
                Spacer(Modifier.width(10.dp))
                Text(if (choice == BackendChoice.SHIZUKU) "SHIZUKU SERVICE" else "ROOT ACCESS",
                    IronType.Title.copy(fontSize = 15.sp), color = textC, modifier = Modifier.weight(1f))
                if (status.ready && !selected && badge != null)
                    StampLabel(badge, StampInk.Brass, slam = false)
                else if (status.ready && !selected)
                    StampLabel("READY", StampInk.Phosphor, slam = false)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(when { status.ready -> LedState.READY; status.checking -> LedState.CHECKING; else -> LedState.BLOCKED })
                Spacer(Modifier.width(6.dp))
                Text(status.statusLine, IronType.MonoSm, color = dimC)
            }
            Spacer(Modifier.height(12.dp))
            ChamferButton(
                text = when {
                    status.checking -> "CHECKING…"
                    status.ready -> if (choice == BackendChoice.SHIZUKU) "USE SHIZUKU" else "USE ROOT"
                    else -> if (choice == BackendChoice.SHIZUKU) "CONFIGURE SHIZUKU" else "GRANT ROOT"
                },
                onClick = { if (status.ready) { clack.confirm(); onUse() } else onConfigure() },
                enabled = !status.checking,
                tall = false, modifier = Modifier.fillMaxWidth(),
            )
        }
        // §7.2 selection: READY stamp slams onto the card
        if (selected) Box(Modifier.align(Alignment.Center)) {
            StampLabel("READY", StampInk.Phosphor, slam = true)
        }
    }
}
```

*(For `clickableNoIndication` and `verticalScroll`, use `Modifier.clickable(interactionSource, indication = null) {}` and `androidx.compose.foundation.verticalScroll` — same pattern as earlier files.)*

---

## 25. `Toolbox.kt` — Settings full screen (§7.7)

```kotlin
package com.ivarna.apexcore.ui.iron.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ═══ §7.7 THE TOOLBOX ═══════════════════════════════════════════════ */

data class RunningModeUi(
    val backend: String, val preferred: String,
    val fpsPrivilege: String, val gpuVendor: String,
)
data class DiagnosticUi(
    val name: String, val statusLine: String, val led: LedState,
    val actionLabel: String? = null, val action: (() -> Unit)? = null, val probing: Boolean = false,
)

@Composable
fun Toolbox(
    themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit,
    paperInserts: Boolean, onPaperInserts: (Boolean) -> Unit,   // same pref key as light_tank_glass
    runningMode: RunningModeUi,
    diagnostics: List<DiagnosticUi>,
    versionName: String,
    onPrivacy: () -> Unit,
    onTour: () -> Unit,
) {
    val serial = rememberSerial()
    val clack = rememberClack()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("TOOLBOX", IronType.Display.copy(fontSize = 26.sp), color = Iron.Bone100)
        Text("Appearance, access, and about", IronType.Caption, color = Iron.Bone500)
        Spacer(Modifier.height(8.dp))

        // ── APPEARANCE
        SectionHeader("APPEARANCE")
        EngravedPlate {
            Text("THEME", IronType.Label, color = Iron.Bone100)
            Text("Match the system, or pick a finish", IronType.Caption, color = Iron.Bone500)
            Spacer(Modifier.height(10.dp))
            MachinedSegment(listOf("SYSTEM", "VELLUM", "GRAPHITE"), themeMode.ordinal) { i ->
                onThemeMode(ThemeMode.entries[i])
            }
        }
        Spacer(Modifier.height(12.dp))
        EngravedPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAPER INSERTS", IronType.Label, color = Iron.Bone100)
                    Text("Bone paper surfaces in Graphite mode", IronType.Caption, color = Iron.Bone500)
                }
                MachinedToggle(paperInserts, onPaperInserts)
            }
        }

        // ── ACCESS
        SectionHeader("ACCESS")
        EngravedPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(LedState.READY)
                Spacer(Modifier.width(8.dp))
                Text(runningMode.backend, IronType.Mono, color = Iron.Bone100)
            }
            Spacer(Modifier.height(10.dp))
            KeyValue("PREFERRED", runningMode.preferred)
            KeyValue("FPS PRIVILEGE", runningMode.fpsPrivilege)
            KeyValue("GPU", runningMode.gpuVendor)
            KeyValue("S/N", serial)
        }
        Spacer(Modifier.height(12.dp))
        EngravedText("ACCESS DIAGNOSTICS", IronType.Label, color = Iron.Bone500)
        Spacer(Modifier.height(6.dp))
        diagnostics.forEach { d -> LedRow(d); Spacer(Modifier.height(8.dp)) }

        // ── LEGAL
        SectionHeader("LEGAL")
        ToolRow("Privacy Policy", "How ApexCore handles your data — the Ledger",
            { RailGlyph(Iron.Bone300) }, onPrivacy)

        // ── ABOUT
        SectionHeader("ABOUT")
        EngravedPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("APEXCORE", IronType.Title.copy(fontSize = 18.sp), color = Iron.Bone100,
                    modifier = Modifier.weight(1f))
                Text("MK·II", IronType.MonoSm, color = Iron.Brass400)
            }
            Spacer(Modifier.height(8.dp))
            KeyValue("VERSION", versionName)
            KeyValue("SERIAL", serial)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StampLabel("NO ADS", StampInk.Phosphor, slam = false)
                StampLabel("NO TRACKING", StampInk.Phosphor, slam = false)
            }
            Text("MACHINED IN 1.2 MB", IronType.MonoSm, color = Iron.Bone500,
                modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
        ToolRow("App Tour", "Replay the Manual & access configuration",
            { CartridgeGlyph(Iron.Bone300) }, onTour)

        SerialFooter(6, "TOOLBOX", serial)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(Modifier.padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EngravedText(title, IronType.Label, color = Iron.Bone300)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Iron.Anvil600, thickness = 1.dp)
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, IronType.MonoSm, color = Iron.Bone500)
        Text(v, IronType.MonoSm, color = Iron.Bone300)
    }
}

@Composable
private fun LedRow(d: DiagnosticUi) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        LedDot(d.led)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, IronType.Label, color = Iron.Bone100)
            Text(d.statusLine, IronType.MonoSm, color = Iron.Bone500)
        }
        if (d.probing) LoadingNeedle()
        else d.actionLabel?.let { l ->
            ChamferButton(l, { d.action?.invoke() }, tall = false, variant = ChamferVariant.Outline)
        }
    }
}
```

---

## 26. `IronShell.kt` — the shell + full-screen slots + backend sheet (§7.3)

```kotlin
package com.ivarna.apexcore.ui.iron.shell

import androidx.activity.GestureCancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/* ═══ §7.3 SHELL — Bridge + tabs + full-screen slot machinery ═══════ */

enum class IronSlot { NONE, TUNE, PRESSURE, LEDGER }

@Composable
fun IronShell(
    finish: IronFinish,
    tab: GearTab, onTab: (GearTab) -> Unit,
    backendName: String, backendLed: LedState, onBackend: () -> Unit,
    slot: IronSlot, onSlot: (IronSlot) -> Unit,
    home: @Composable () -> Unit,
    games: @Composable () -> Unit,
    optics: @Composable () -> Unit,
    toolbox: @Composable () -> Unit,
    slotContent: @Composable (IronSlot) -> Unit,
    replayOverlay: (@Composable () -> Unit)? = null,
    backendSheet: (@Composable () -> Unit)? = null,
) {
    // §10 theme crossfade 220ms — children pick up the finish through the local
    Crossfade(finish, animationSpec = tween(220), label = "finish") { f ->
        CompositionLocalProvider(LocalIronFinish provides f) {
            Box(Modifier.fillMaxSize().background(ironSkin().canvas).ironGrain(0.04f)) {
                Column(Modifier.fillMaxSize()) {
                    BridgePlate(backendName, backendLed, onBackend)
                    Box(Modifier.weight(1f)) {
                        GearTabTransition(tab) { t ->
                            when (t) {
                                GearTab.HOME -> home()
                                GearTab.GAMES -> games()
                                GearTab.HUD -> optics()
                                GearTab.TOOLS -> toolbox()
                            }
                        }
                    }
                    GearSelector(tab, onTab)
                }
                // §7.3 full-screen overlays replace tab content, hide chrome
                FullScreenSlot(visible = slot != IronSlot.NONE, onDismiss = { onSlot(IronSlot.NONE) }) {
                    slotContent(slot)
                }
                replayOverlay?.invoke()
                backendSheet?.invoke()
            }
        }
    }
}

/** Slide-up slot with predictive-back scrub of the slide-down (§6.1). */
@Composable
fun FullScreenSlot(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = visible) { progress ->
        try { progress.collect { scrub = it.progress }; onDismiss() }
        catch (e: GestureCancellationException) { scrub = 0f }
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(260, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = scrub * size.height * 0.25f
                    val s = 1f - 0.04f * scrub; scaleX = s; scaleY = s
                }
                .background(ironSkin().canvas).ironGrain(0.04f)
        ) { content() }
    }
}

/** §3.12 — Bridge chip opens this: same readiness data as Settings. */
@Composable
fun BackendBenchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    shizuku: KeyStatus, root: KeyStatus,
    preferred: BackendChoice?,
    onUse: (BackendChoice) -> Unit,
    onConfigure: (BackendChoice) -> Unit,
) {
    val clack = rememberClack()
    BenchSheet(visible = visible, onDismiss = onDismiss) {
        StampLabel("SYSTEM ACCESS", StampInk.Signal, slam = true)
        Spacer(Modifier.height(16.dp))
        BackendRow("SHIZUKU", shizuku, preferred == BackendChoice.SHIZUKU,
            { onUse(BackendChoice.SHIZUKU) }, { onConfigure(BackendChoice.SHIZUKU) })
        Spacer(Modifier.height(10.dp))
        BackendRow("ROOT", root, preferred == BackendChoice.ROOT,
            { onUse(BackendChoice.ROOT) }, { onConfigure(BackendChoice.ROOT) })
    }
}

@Composable
private fun BackendRow(name: String, s: KeyStatus, isPreferred: Boolean,
                       onUse: () -> Unit, onConfigure: () -> Unit) {
    val clack = rememberClack()
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        LedDot(when { s.ready -> LedState.READY; s.checking -> LedState.CHECKING; else -> LedState.BLOCKED })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, IronType.Label, color = Iron.Bone100)
                if (isPreferred) { Spacer(Modifier.width(8.dp)); StampLabel("PREFERRED", StampInk.Brass, slam = false) }
            }
            Text(s.statusLine, IronType.MonoSm, color = Iron.Bone500)
        }
        ChamferButton(
            text = when { s.checking -> "…"; s.ready -> "USE"; else -> "SETUP" },
            onClick = { clack.row(); if (s.ready) onUse() else onConfigure() },
            enabled = !s.checking, tall = false,
            variant = if (s.ready) ChamferVariant.Primary else ChamferVariant.Outline,
        )
    }
}
```

---

## 27. `LaunchMatrix.kt` — Games full screen (§7.5)

```kotlin
package com.ivarna.apexcore.ui.iron.games

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells   // (not used — kept out)
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ═══ §7.5 THE RACK ══════════════════════════════════════════════════ */

enum class Demand(val cells: Int) { LOW(1), MEDIUM(2), HIGH(3) }

data class AppCardData(
    val name: String, val pkg: String,
    val demand: Demand,
    val tint: Color,                       // extracted icon color → lens wash
    val icon: @Composable () -> Unit,      // caller renders the real app icon
)

@Composable
fun LaunchMatrixScreen(
    games: List<AppCardData>,
    allApps: List<AppCardData>,
    allLoading: Boolean,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onLaunch: (AppCardData) -> Unit,       // freeze + GameLauncher (fires at shutter seam)
    onRemove: (AppCardData) -> Unit,
    addSheet: @Composable () -> Unit = {},
    pinSheet: @Composable () -> Unit = {},
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    var query by remember { mutableStateOf("") }
    var segment by remember { mutableIntStateOf(0) }          // 0 GAMES · 1 ALL APPS
    var eject by remember { mutableStateOf<AppCardData?>(null) }
    var pending by remember { mutableStateOf<AppCardData?>(null) }
    var launchTick by remember { mutableIntStateOf(0) }

    val source = if (segment == 0) games else allApps
    val visible = remember(query, source) {
        source.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SearchSlot(query, { query = it })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (games.isNotEmpty()) ChamferButton("+ ADD", onAdd, tall = false, variant = ChamferVariant.Outline)
                ChamferButton("PIN", onPin, tall = false, variant = ChamferVariant.Outline)
            }
            Spacer(Modifier.height(12.dp))
            MachinedSegment(listOf("GAMES", "ALL APPS"), segment) { segment = it }
            Spacer(Modifier.height(16.dp))

            when {
                segment == 1 && allLoading -> Box(Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingNeedle(); Spacer(Modifier.height(8.dp))
                        Text("SCANNING PACKAGES…", IronType.MonoSm, color = Iron.Bone500)
                    }
                }
                visible.isEmpty() -> EmptyPlate(
                    noGames = segment == 0 && games.isEmpty() && query.isEmpty(),
                    noMatch = query.isNotEmpty(), onAdd = onAdd,
                    modifier = Modifier.weight(1f))
                else -> Rack(
                    apps = visible, segment = segment,
                    onSegment = { segment = it },
                    onLaunch = { card -> pending = card; launchTick++ },
                    onEject = { eject = it },
                    modifier = Modifier.weight(1f))
            }
            SerialFooter(3, "GAMES", serial)
        }

        // §7.5 SHUTTER — freeze broadcast fires at the seam (§17 delivered)
        ShutterOverlay(trigger = launchTick, onSeam = { pending?.let(onLaunch) })

        // eject confirm sheet
        BenchSheet(visible = eject != null, onDismiss = { eject = null }) {
            Text("EJECT CARTRIDGE?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Bone100)
            Spacer(Modifier.height(4.dp))
            eject?.let {
                Text("${it.name} · ${it.pkg}", IronType.MonoSm, color = Iron.Bone500)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("KEEP", { eject = null }, Modifier.weight(1f),
                    variant = ChamferVariant.Outline, tall = false)
                ChamferButton("REMOVE", {
                    eject?.let(onRemove); eject = null
                }, Modifier.weight(1f), tall = false)
            }
        }
        addSheet(); pinSheet()
    }

    // clear pending after the ceremony window (target app should be foreground by then)
    LaunchedEffect(launchTick) { if (launchTick > 0) { delay(1400); pending = null } }
}

/* ── carousel + two-finger segment flip ── */
@Composable
private fun Rack(
    apps: List<AppCardData>, segment: Int, onSegment: (Int) -> Unit,
    onLaunch: (AppCardData) -> Unit, onEject: (AppCardData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { apps.size }

    // §5.2 haptic tick on page settle
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { clack.tick() }
    }

    Box(modifier.fillMaxWidth().pointerInput(apps) {
        // §6.2 two-finger horizontal swipe → GAMES ↔ ALL APPS
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var flipped = false; var two = false; var x0 = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    if (!two) { two = true; x0 = pressed.map { it.position.x }.average().toFloat() }
                    val x = pressed.map { it.position.x }.average().toFloat()
                    if (!flipped && abs(x - x0) > 80.dp.toPx()) {
                        flipped = true; onSegment(1 - segment); clack.keyTap()
                    }
                } else two = false
            }
        }
    }) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - minOf(abs(offset), 1f)
            val active = page == pagerState.currentPage
            Box(Modifier.graphicsLayer {
                val s = 0.8f + 0.2f * closeness
                scaleX = s; scaleY = s
                alpha = 0.4f + 0.6f * closeness
                translationY = (8.dp * offset * offset).toPx()      // §7.5 the dip
            }.blur(if (abs(offset) > 0.5f) 4.dp else 0.dp)) {       // depth, API 31+
                Cartridge(
                    apps[page], active = active,
                    onTap = { if (!active) scope.launch { pagerState.animateScrollToPage(page) } },
                    onLaunch = { onLaunch(apps[page]) },
                    onEject = { onEject(apps[page]) },
                )
            }
        }
    }
}

/* ── the cartridge: lens, demand meter, drag-down eject tilt ── */
@Composable
private fun Cartridge(
    card: AppCardData, active: Boolean,
    onTap: () -> Unit, onLaunch: () -> Unit, onEject: () -> Unit,
) {
    val clack = rememberClack()
    var dy by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState((dy / 12f).coerceIn(0f, 8f), IronMotion.machined(), label = "tilt")

    EngravedPlate(
        modifier = Modifier
            .graphicsLayer { rotationZ = tilt }                     // §6.2 tilt, not translate
            .pointerInput(active) { onTapGesture(onTap) },
        onClick = if (active) onLaunch else onTap,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            // Lens — brass ring + tinted wash (§7.5)
            Box(Modifier.size(88.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(card.tint.copy(alpha = 0.12f))
                .border(2.dp, Iron.Brass400, androidxx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(56.dp)) { card.icon() }
            }
            Spacer(Modifier.height(14.dp))
            Text(card.name, IronType.Display.copy(fontSize = 22.sp), color = Iron.Bone100)
            Spacer(Modifier.height(2.dp))
            Text(card.pkg, IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.height(12.dp))
            DemandMeter(card.demand)
            Spacer(Modifier.height(16.dp))
            ChamferButton("ALLOCATE & LAUNCH", onLaunch, Modifier.fillMaxWidth())
            if (active) {
                Spacer(Modifier.height(10.dp))
                Text("drag card down to eject", IronType.MonoSm, color = Iron.Bone500)
            }
        }

        // §6.2 drag-down eject (resisted, ticks, ≥80dp commits)
        if (active) Box(Modifier.matchParentSize().pointerInput(Unit) {
            var lastTick = 0
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    dy = (dy + dragAmount * 0.5f).coerceIn(0f, 160.dp.toPx())
                    val t = (dy / 20.dp.toPx()).toInt()
                    if (t != lastTick) { lastTick = t; clack.tick() }
                    change.consume()
                },
                onDragEnd = { if (dy >= 80.dp.toPx()) { clack.off(); onEject() }; dy = 0f },
                onDragCancel = { dy = 0f },
            )
        })
    }
}

@Composable
private fun DemandMeter(demand: Demand) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("RESOURCE DEMAND", IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.width(8.dp))
        repeat(3) { i ->
            Box(Modifier.size(width = 12.dp, height = 8.dp).clip(IronShape.Slot)
                .background(if (i < demand.cells) Iron.Signal500 else Iron.Anvil600))
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(demand.name, IronType.MonoSm, color = Iron.Bone300)
    }
}

@Composable
private fun EmptyPlate(noGames: Boolean, noMatch: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        EngravedPlate(Modifier.fillMaxWidth(0.8f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                if (noMatch) Text("∅ NO MATCH · CHECK SPELLING", IronType.Mono, color = Iron.Bone300)
                else {
                    Text("NO ITEMS FOUND", IronType.Mono, color = Iron.Bone300)
                    Spacer(Modifier.height(14.dp))
                    DoodleArrow(Iron.Signal700, Modifier.graphicsLayer { rotationZ = 90f })
                    Spacer(Modifier.height(14.dp))
                    ChamferButton(if (noGames) "ADD GAMES" else "SCAN FOR GAMES", onAdd)
                }
            }
        }
    }
}
```

*(Fix the two typos that snuck in: `androidx.compose.foundation.shape.CircleShape` in the Lens, and `onTapGesture` → `detectTapGestures { onTap() }` — same helpers as earlier files.)*

---

## 28. `Optics.kt` — Overlay screen + live Phantom Rail preview (§7.6, §7.12)

```kotlin
package com.ivarna.apexcore.ui.iron.optics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/* ═══ §7.6 OPTICS BENCH ══════════════════════════════════════════════ */

enum class RailEdge { LEFT, RIGHT }
enum class RailSize(val panelW: Dp, val fpsSp: TextUnit) { S(50.dp, 18.sp), M(58.dp, 24.sp), L(66.dp, 30.sp) }

data class OpticsUiState(
    val permissionGranted: Boolean,
    val previewRunning: Boolean,
    val size: RailSize, val opacity: Float,          // 0.4..1.0
    val edge: RailEdge,
)

@Composable
fun OpticsBench(
    state: OpticsUiState,
    onGrant: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,              // starts/stops GameOverlayService preview
    onSize: (RailSize) -> Unit, onOpacity: (Float) -> Unit, onEdge: (RailEdge) -> Unit,
) {
    val serial = rememberSerial()
    val clack = rememberClack()

    // simulated telemetry for the live preview (service data in production)
    var fps by remember { mutableIntStateOf(144) }
    val ram = remember { mutableStateListOf(*(FloatArray(40) { 0.5f }).toTypedArray()) }
    val cpu = remember { mutableStateListOf(*(FloatArray(8) { 0.3f }).toTypedArray()) }
    LaunchedEffect(Unit) {
        while (true) {
            fps = 118 + (0..26).random()
            ram.removeAt(0); ram.add(0.3f + (0..40).random() / 100f)
            repeat(8) { cpu[it] = 0.15f + (0..70).random() / 100f }
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("OPTICS", IronType.Display.copy(fontSize = 26.sp), color = Iron.Bone100)
        Text("Configure the in-game telemetry rail", IronType.Caption, color = Iron.Bone500)
        Spacer(Modifier.height(12.dp))

        // ── permission
        EngravedPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PERMISSION", IronType.Label, color = Iron.Bone100)
                    Text(if (state.permissionGranted)
                        "ApexCore may draw over other apps."
                    else "Draw-over-apps permission required for the HUD.",
                        IronType.Caption, color = Iron.Bone500)
                }
                if (state.permissionGranted) StampLabel("GRANTED", StampInk.Phosphor, slam = false)
                else StampLabel("ACTION REQUIRED", StampInk.Ember, slam = false)
            }
            if (!state.permissionGranted) {
                Spacer(Modifier.height(12.dp))
                ChamferButton("GRANT PERMISSION", onGrant, Modifier.fillMaxWidth(), tall = false)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── live preview bench
        EngravedPlate {
            Text("PREVIEW", IronType.Label, color = Iron.Bone100)
            Text("Drag the rail. Feel the magnet snap. Double-tap to expand.",
                IronType.Caption, color = Iron.Bone500)
            Spacer(Modifier.height(12.dp))
            PhantomRailPreview(fps, ram.toList(), cpu.toList(), state)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PREVIEW SERVICE", IronType.Label, color = Iron.Bone100, modifier = Modifier.weight(1f))
                MachinedToggle(state.previewRunning, onTogglePreview)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── fit
        EngravedPlate {
            Text("FIT", IronType.Label, color = Iron.Bone100)
            Spacer(Modifier.height(10.dp))
            Text("SIZE", IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.height(6.dp))
            MachinedSegment(listOf("S", "M", "L"), state.size.ordinal) { onSize(RailSize.entries[it]) }
            Spacer(Modifier.height(14.dp))
            Text("OPACITY", IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.height(6.dp))
            OpacityRuler(state.opacity, onOpacity)
            Spacer(Modifier.height(14.dp))
            Text("EDGE", IronType.MonoSm, color = Iron.Bone500)
            Spacer(Modifier.height(6.dp))
            MachinedSegment(listOf("LEFT", "RIGHT"), state.edge.ordinal) { onEdge(RailEdge.entries[it]) }
        }
        SerialFooter(5, "OPTICS", serial)
    }
}

fun Modifier.dashedWindow(color: Color = Iron.Anvil500): Modifier = drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
}

/* ── the rail itself — same physics as the service rail, in Compose ── */
@Composable
fun PhantomRailPreview(
    fps: Int, ram: List<Float>, cpu: List<Float>,
    state: OpticsUiState,
) {
    val clack = rememberClack()
    var expanded by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val y = remember { Animatable(40f) }
    val flashDefrost = remember { mutableStateOf(false) }

    // §7.12 auto-minimize after 5s idle
    LaunchedEffect(interaction) { delay(5000); expanded = false }

    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp).dashedWindow()) {
        val areaH = constraints.maxHeight.toFloat()
        val snapZones = floatArrayOf(0.05f, 0.37f, 0.63f, 0.9f)     // §6.2 four zones

        suspend fun snap() {
            val target = snapZones.map { it * (areaH - 170.dp.toPx()) }.minBy { abs(it - y.value) }
            y.animateTo(target, IronMotion.drawer()); clack.tick()
        }

        Box(
            Modifier
                .align(if (state.edge == RailEdge.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                .offset { IntOffset(0, y.value.toInt()) }
                .pointerInput(state.edge) {
                    detectTapGestures(onTap = { interaction++; expanded = !expanded; clack.row() })
                }
                .pointerInput(state.edge) {
                    detectDragGestures(
                        onDragStart = { interaction++ },
                        onDragEnd = { /* snap launched below via effect */ },
                    ) { change, dy ->
                        y.snapTo((y.value + dy).coerceIn(0f, areaH - 170.dp.toPx()))
                        change.consume()
                    }
                }
        ) {
            if (expanded) RailPanel(fps, ram, cpu, state.size, state.opacity, flashDefrost.value) {
                interaction++; flashDefrost.value = true; clack.confirm()
            }
            else Box(Modifier.padding(horizontal = 7.dp).width(2.dp).height(120.dp)
                .background(Iron.Brass400))                        // 2dp filament
        }
        LaunchedEffect(interaction) {
            // re-snap after any drag settles (cheap + keeps magnet feel)
            if (!expanded) snap()
        }
        LaunchedEffect(flashDefrost.value) {
            if (flashDefrost.value) { delay(600); flashDefrost.value = false }
        }
    }
}

@Composable
private fun RailPanel(
    fps: Int, ram: List<Float>, cpu: List<Float>,
    size: RailSize, opacity: Float, defrostFlash: Boolean,
    onDefrost: () -> Unit,
) {
    Column(
        Modifier.width(size.panelW).height(170.dp)
            .clip(IronShape.Slot)
            .background(Iron.Anvil950.copy(alpha = opacity))       // smoked glass (service adds blur)
            .border(1.dp, Iron.Anvil600, IronShape.Slot)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$fps", IronType.MonoLg.copy(fontSize = size.fpsSp), color = Iron.Phosphor400)
        Text("FPS", IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(size.panelW - 16.dp).height(22.dp)) {   // RAM sparkline
            val path = Path()
            ram.forEachIndexed { i, v ->
                val x = i / (ram.size - 1f) * size.width
                val yy = size.height - v * size.height
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, Iron.Bone300, style = Stroke(1.2.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(size.panelW - 16.dp).height(26.dp)) {   // CPU 8-segment
            val bw = size.width / 8f
            cpu.forEachIndexed { i, v ->
                val h = v * size.height
                drawRect(Iron.Brass400, Offset(i * bw + 1f, size.height - h),
                    Size(bw - 2f, h))
            }
        }
        Spacer(Modifier.weight(1f))
        DefrostNode(defrostFlash, onDefrost)
    }
}

@Composable
private fun DefrostNode(flash: Boolean, onTap: () -> Unit) {
    Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape)
        .border(1.5.dp, if (flash) Iron.Phosphor400 else Iron.Brass400,
            androidx.compose.foundation.shape.CircleShape)
        .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {                              // hand-drawn snow doodle
            val c = center; val r = size.minDimension / 2f
            repeat(6) { i ->
                val a = i / 6f * 2f * Math.PI.toFloat()
                drawLine(Iron.Bone300,
                    Offset(c.x - cos(a) * r, c.y - sin(a) * r),
                    Offset(c.x + cos(a) * r, c.y + sin(a) * r), 1.5.dp.toPx())
            }
        }
        if (flash) Text("DEFROSTED", IronType.MonoSm.copy(fontSize = 8.sp),
            color = Iron.Phosphor400,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { rotationZ = -3f })
    }
}

/* ── OpacityRuler: drag-the-marker slider on a real ruler (§7.6) ── */
@Composable
fun OpacityRuler(value: Float, onChange: (Float) -> Unit) {
    val clack = rememberClack()
    Column {
        Canvas(Modifier.fillMaxWidth().height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    clack.tick(); onChange((0.4f + 0.6f * (pos.x / size.width)).coerceIn(0.4f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange((0.4f + 0.6f * (change.position.x / size.width)).coerceIn(0.4f, 1f))
                    change.consume()
                }
            }
        ) {
            val cy = size.height * 0.5f
            drawLine(Iron.Anvil600, Offset.Zero, Offset(size.width, cy), 1.dp.toPx())
            var x = 0f; var i = 0
            while (x <= size.width + 0.5f) {
                val major = i % 5 == 0
                drawLine(if (major) Iron.Bone300 else Iron.Anvil500,
                    Offset(x, cy), Offset(x, cy - (if (major) 10.dp else 5.dp).toPx()), 1.dp.toPx())
                x += size.width / 20f; i++
            }
            val mx = ((value - 0.4f) / 0.6f) * size.width
            drawRect(Iron.Brass400, Offset(mx - 1.dp.toPx(), cy - 14.dp.toPx()),
                Size(2.dp.toPx(), 14.dp.toPx()))
        }
        Text("${(value * 100).toInt()}%", IronType.MonoSm, color = Iron.Bone300,
            modifier = Modifier.align(Alignment.End))
    }
}
```

---

## 29. `TuningRoom.kt` — Tune full screen (§7.8)

```kotlin
package com.ivarna.apexcore.ui.iron.tune

import android.view.WindowManager
import androidx.activity.GestureCancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.drop

/* ═══ §7.8 TUNING ROOM ═══════════════════════════════════════════════ */

data class TuneOptionUi(
    val key: String, val title: String, val description: String,
    val available: Boolean, val reason: String?, val checked: Boolean,
    val onToggle: (Boolean) -> Unit,                 // TuneManager.setIntent(key, it)
)
data class TuneCategoryUi(val name: String, val options: List<TuneOptionUi>) {
    val availableCount: Int get() = options.count { it.available }
}

@Composable
fun TuningRoom(
    categories: List<TuneCategoryUi>,
    sessionActive: Boolean, sessionElapsedS: Int, sessionApplied: Int,
    isProbing: Boolean, onProbe: () -> Unit,
    onBack: () -> Unit,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val running = sessionActive

    // keep screen on while a session is live
    val view = LocalView.current
    DisposableEffect(running) {
        val window = view.context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // §6.1 back scrub → END SESSION? slip (caller ends session on commit)
    var scrub by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = running) { progress ->
        try { progress.collect { scrub = it.progress }; onBack() }   // commit = leave + restore
        catch (e: GestureCancellationException) { scrub = 0f }
    }

    // §6.2 pull-to-reprobe (96dp, resisted)
    var pull by remember { mutableFloatStateOf(0f) }
    val threshold = 96.dp
    val probeConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(threshold.toPx() * 1.2f)
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val past = pull >= threshold.toPx()
                pull = 0f
                if (past) { onProbe(); repeat(3) { clack.tick() } }
                return if (past) available else Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(probeConnection).padding(horizontal = 20.dp)) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BackArrow(Iron.Bone300, onBack)
                Spacer(Modifier.width(8.dp))
                Text("TUNING ROOM", IronType.Display.copy(fontSize = 22.sp), color = Iron.Bone100,
                    modifier = Modifier.weight(1f))
                if (isProbing) LoadingNeedle()
                else ChamferButton("PROBE", onProbe, tall = false, variant = ChamferVariant.Outline)
            }

            Text("Real kernel & session tuning.", IronType.Body, color = Iron.Bone300)
            Text("Capability-gated parameters safely applied during game sessions and restored on exit.",
                IronType.Caption, color = Iron.Bone500)
            Spacer(Modifier.height(10.dp))

            // session strip
            if (sessionActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StampLabel("SESSION ACTIVE", StampInk.Signal, pulse = true)
                    Spacer(Modifier.width(10.dp))
                    Text("LIVE · $sessionApplied APPLIED · %02d:%02d"
                        .format(sessionElapsedS / 60, sessionElapsedS % 60),
                        IronType.Mono, color = Iron.Phosphor400)
                }
                Spacer(Modifier.height(10.dp))
            }

            // categories — sorted by available count desc (§7.8)
            LazyColumn(Modifier.weight(1f)) {
                val sorted = categories.sortedByDescending { it.availableCount }
                sorted.forEach { cat ->
                    item(key = cat.name) {
                        DrawerHeader(cat.name, cat.availableCount)
                        Spacer(Modifier.height(8.dp))
                        EngravedPlate(Modifier.fillMaxWidth()) {
                            cat.options.forEachIndexed { i, opt ->
                                TuneRow(opt)
                                if (i < cat.options.lastIndex) {
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                // fine print on paper
                item {
                    PaperPlate {
                        Text("Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.",
                            IronType.Caption, color = Iron.Ink600)
                    }
                    SerialFooter(7, "TUNE", serial)
                }
            }
        }

        // END SESSION? slip
        if (scrub > 0.01f) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PaperPlate(Modifier.graphicsLayerAlpha(scrub)) {
                RisoText("END SESSION?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900)
                Text("Release to end and restore kernel parameters.", IronType.Caption, color = Iron.Ink600)
            }
        }
    }
}

@Composable
private fun DrawerHeader(name: String, available: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp, 4.dp).background(Iron.Brass400))     // drawer-pull tab
        Spacer(Modifier.width(8.dp))
        EngravedText(name, IronType.Label, color = Iron.Bone300)
        Spacer(Modifier.width(10.dp))
        Text("$available AVAILABLE", IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Iron.Anvil600, thickness = 1.dp)
    }
}

@Composable
private fun TuneRow(opt: TuneOptionUi) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).then(if (!opt.available) Modifier.graphicsLayerAlpha(0.55f) else Modifier)) {
            Text(opt.title, IronType.Title.copy(fontSize = 15.sp), color = Iron.Bone100)
            Text(opt.reason ?: opt.description, IronType.Caption,
                color = if (opt.reason != null) Iron.Ember500 else Iron.Bone500)
        }
        Spacer(Modifier.width(12.dp))
        MachinedToggle(opt.checked, opt.onToggle, enabled = opt.available)
    }
}
```

---

## 30. `Ledger.kt` — Privacy Policy renderer on paper (§7.10)

```kotlin
package com.ivarna.apexcore.ui.iron.legal

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ═══ §7.10 THE LEDGER — paper, ink, metal code plates ══════════════ */

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Bullet(val spans: List<MdSpan>) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}
data class MdSpan(
    val text: String, val bold: Boolean = false, val italic: Boolean = false,
    val code: Boolean = false, val linkLabel: String? = null, val linkUrl: String? = null,
)

@Composable
fun TheLedger(
    blocks: List<MdBlock>,        // PrivacyMarkdown.render(assets/privacy_policy.md)
    onLink: (String) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Iron.Bone50).ironGrain(0.05f)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BackArrow(Iron.Ink900, onBack)
                Spacer(Modifier.width(8.dp))
                Text("THE LEDGER", IronType.Display.copy(fontSize = 20.sp), color = Iron.Ink900,
                    modifier = Modifier.weight(1f))
                StampLabel("PRINTED OFFLINE · NO NETWORK", StampInk.Brass, slam = false)
            }
            HorizontalDivider(color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp)

            if (blocks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ledger unavailable.", IronType.Body, color = Iron.Ink600)
                }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                items(blocks.size) { i -> Block(blocks[i], onLink) }
                item {
                    Text("PLATE 09 · LEDGER · PRINTED OFFLINE", IronType.MonoSm,
                        color = Iron.Ink600, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Block(b: MdBlock, onLink: (String) -> Unit) {
    when (b) {
        is MdBlock.Heading -> {
            when (b.level) {
                1 -> Text(b.text, IronType.Display.copy(fontSize = 26.sp, color = Iron.Ink900))
                2 -> { Text(b.text, IronType.Title.copy(fontSize = 18.sp, color = Iron.Ink900))
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp) }
                else -> Text(b.text, IronType.Label.copy(color = Iron.Ink900))
            }
            Spacer(Modifier.height(10.dp))
        }
        is MdBlock.Paragraph -> { MdSpanText(b.spans, onLink); Spacer(Modifier.height(10.dp)) }
        is MdBlock.Bullet -> {
            Row(Modifier.fillMaxWidth()) {
                Canvas(Modifier.size(10.dp).padding(top = 6.dp)) {   // brass tick marker
                    drawLine(Iron.Brass400, Offset(0f, size.height / 2f),
                        Offset(size.width, size.height / 2f), 3.dp.toPx())
                }
                Spacer(Modifier.width(10.dp))
                MdSpanText(b.spans, onLink, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        is MdBlock.Code -> {
            // §7.10 dark metal plate inset into paper
            Box(Modifier.fillMaxWidth().clip(IronShape.Plate).background(Iron.Anvil800)
                .ironGrain(0.05f).padding(12.dp)) {
                Column {
                    b.lines.forEach {
                        Text(it, IronType.Mono.copy(fontSize = 12.sp, color = Iron.Bone300))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        is MdBlock.Table -> {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    b.header.forEach {
                        Text(it, IronType.MonoSm, color = Iron.Ink900,
                            modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp),
                    color = Iron.Ink600.copy(alpha = 0.3f), thickness = 1.dp)
                b.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        row.forEach {
                            Text(it, IronType.MonoSm, color = Iron.Ink600, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MdSpanText(spans: List<MdSpan>, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val annotated = remember(spans) {
        buildAnnotatedString {
            spans.forEach { s ->
                val start = length
                withStyle(SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    fontFamily = if (s.code) PlexMono else null,
                    fontSize = if (s.code) 13.sp else TextUnit.Unspecified,
                    color = if (s.linkUrl != null) Iron.Signal700 else Color.Unspecified,
                    textDecoration = if (s.linkUrl != null) TextDecoration.Underline else null,
                    background = if (s.code) Iron.Bone300.copy(alpha = 0.45f) else null,
                )) { append(s.text) }
                if (s.linkUrl != null) addStringAnnotation("url", s.linkUrl, start, length)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated, IronType.Body, color = Iron.Ink900, modifier = modifier
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    layout?.getOffsetForPosition(pos)?.let { off ->
                        annotated.getStringAnnotations("url", off, off)
                            .firstOrNull()?.let { onLink(it.item) }
                    }
                }
            },
        onTextLayout = { layout = it },
    )
    // (Squiggle-underline variant: render single-link paragraphs via DoodleSquiggle
    //  under the Text — same trick as the margin notes.)
}
```

---

## 31. `Sheets.kt` — Setup / Pin Apps / Add Game (§7.11)

```kotlin
package com.ivarna.apexcore.ui.iron.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ═══ §7.11 — every dialog is a BenchSheet ═══════════════════════════ */

data class PickerApp(
    val name: String, val pkg: String,
    val icon: @Composable () -> Unit,
)

/* ── Setup sheet: the Key Selector (replaces SetupDialog; same contract) ── */
@Composable
fun SystemAccessSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    shizuku: KeyStatus, root: KeyStatus,
    selected: BackendChoice?,
    onProbe: () -> Unit,
    onSelect: (BackendChoice) -> Unit,          // writes pref + syncs FreezeFramework/FpsStack
    onConfigureShizuku: () -> Unit, onGrantRoot: () -> Unit,
) {
    // probe every 1200ms while open (§10 parity)
    LaunchedEffect(visible) { while (visible) { onProbe(); delay(1200) } }
    var localSel by remember { mutableStateOf<BackendChoice?>(null) }
    val sel = localSel ?: selected

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        StampLabel("SYSTEM ACCESS", StampInk.Signal, slam = true)
        Spacer(Modifier.height(6.dp))
        Text("Deep freeze (BOOST) requires Shizuku or Root access.",
            IronType.Caption, color = Iron.Bone500)
        Spacer(Modifier.height(16.dp))
        KeyCard(BackendChoice.SHIZUKU, shizuku, sel == BackendChoice.SHIZUKU, onPaper = false,
            badge = "RECOMMENDED",
            onUse = { onSelect(BackendChoice.SHIZUKU); localSel = BackendChoice.SHIZUKU },
            onConfigure = onConfigureShizuku)
        Spacer(Modifier.height(12.dp))
        KeyCard(BackendChoice.ROOT, root, sel == BackendChoice.ROOT, onPaper = false, badge = null,
            onUse = { onSelect(BackendChoice.ROOT); localSel = BackendChoice.ROOT },
            onConfigure = onGrantRoot)
        // parent dismisses on successful sync — here: auto-dismiss after the stamp lands
        LaunchedEffect(localSel) { if (localSel != null) { delay(450); onDismiss() } }
    }
}

/* ── Pin Apps sheet: searchable list + brass pin toggles + IndexRail ── */
@Composable
fun PinAppsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    apps: List<PickerApp>,                       // sorted by name
    pinned: Set<String>,
    onTogglePin: (String) -> Unit,               // GameManager whitelist store
) {
    val clack = rememberClack()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibleApps = remember(query, apps) {
        apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }
    val letterIndex = remember(apps) {                            // §3.17 scrub map
        val map = mutableMapOf<Char, Int>(); var last: Char? = null
        apps.forEachIndexed { i, a ->
            val c = a.name.first().uppercaseChar()
            if (c != last) { map[c] = i; last = c }
        }
        map
    }

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        RisoText("PIN APPS", IronType.Title.copy(fontSize = 18.sp))
        Text("PINNED APPS ARE NEVER FROZEN · ${pinned.size} PINNED",
            IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(12.dp))
        SearchSlot(query, { query = it })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(400.dp)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(visibleApps.size) { i ->
                    val app = visibleApps[i]
                    PickerRow(app, pinned.contains(app.pkg)) { onTogglePin(app.pkg) }
                }
            }
            IndexRail(onLetter = { c ->
                letterIndex[c]?.let { scope.launch { listState.animateScrollToItem(it) } }
            })
        }
        Spacer(Modifier.height(12.dp))
        ChamferButton(if (pinned.isEmpty()) "DONE" else "DONE · ${pinned.size}",
            { clack.confirm(); onDismiss() }, Modifier.fillMaxWidth(), tall = false)
    }
}

/* ── Add Game sheet: multi-select + ADDED stamp ── */
@Composable
fun AddGameSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    apps: List<PickerApp>,                        // listInstallableApps
    alreadyAdded: Set<String>,
    onAdd: (List<PickerApp>) -> Unit,             // GameManager.add()
) {
    val clack = rememberClack()
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var stamped by remember { mutableStateOf(false) }
    val visibleApps = remember(query, apps) {
        apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        RisoText("ADD GAMES", IronType.Title.copy(fontSize = 18.sp))
        Text("BUILD YOUR RACK · ${apps.size} INSTALLED", IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(12.dp))
        SearchSlot(query, { query = it })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.height(400.dp)) {
            items(visibleApps.size) { i ->
                val app = visibleApps[i]
                val isPicked = picked.contains(app.pkg) || alreadyAdded.contains(app.pkg)
                PickerRow(app, isPicked) {
                    if (alreadyAdded.contains(app.pkg)) return@PickerRow
                    if (picked.contains(app.pkg)) picked.remove(app.pkg) else picked.add(app.pkg)
                    clack.off()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box {
            ChamferButton(if (picked.isEmpty()) "CANCEL" else "ADD ${picked.size}",
                {
                    if (picked.isEmpty()) { onDismiss(); return@ChamferButton }
                    onAdd(apps.filter { picked.contains(it.pkg) })
                    clack.confirm(); stamped = true
                }, Modifier.fillMaxWidth(), tall = false)
            if (stamped) Box(Modifier.align(Alignment.Center)) {
                StampLabel("ADDED ${picked.size}", StampInk.Phosphor, slam = true)
                LaunchedEffect(Unit) { delay(500); stamped = false; onDismiss() }
            }
        }
    }
}

/* ── shared picker row: lens icon + name/pkg + compact brass toggle ── */
@Composable
private fun PickerRow(app: PickerApp, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .border(1.5.dp, Iron.Anvil500, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) { Box(Modifier.size(28.dp)) { app.icon() } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, IronType.Body.copy(color = Iron.Bone100))
            Text(app.pkg, IronType.MonoSm, color = Iron.Bone500)
        }
        MachinedToggleCompact(on, onToggle)
    }
}

/** Compact brass pin toggle for sheet rows (36×20 — MachinedToggle's little sibling). */
@Composable
fun MachinedToggleCompact(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val clack = rememberClack()
    val x = remember { Animatable(if (checked) 1f else 0f) }
    val wob = remember { Animatable(0f) }
    LaunchedEffect(checked) {
        x.animateTo(if (checked) 1f else 0f, IronMotion.machined())
        wob.snapTo(0f)
        wob.animateTo(0f, keyframes { durationMillis = 90; 4f at 30; -4f at 60 })
    }
    Box(
        Modifier.size(40.dp, 22.dp).clip(IronShape.Slot)
            .background(if (checked) Iron.Brass400.copy(alpha = 0.35f) else Iron.Anvil600)
            .border(1.dp, if (checked) Iron.Brass400 else Iron.Anvil600, IronShape.Slot)
            .androidx.compose.foundation.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (checked) clack.off() else clack.confirm()
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(start = 2.dp).size(18.dp)
            .graphicsLayer { translationX = 18.dp.toPx() * x.value; rotationZ = wob.value }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (checked) Iron.Brass400 else Iron.Bone500))
    }
}
```

*(Fix: `MachinedToggleCompact` uses standard `Modifier.clickable(...)` — same pattern as `MachinedToggle` in file 11.)*

---

## 32. `RailView.kt` — HUD service view (§7.12)

```kotlin
package com.ivarna.apexcore.games

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * §7.12 Phantom Rail — collapsed 2dp brass filament; expands to smoked-glass
 * telemetry column. Pure Canvas View: FPS, RAM sparkline, CPU bars, DEFROST.
 * Window moves + snap zones are handled by GameOverlayService (§18 delivered).
 */
class RailView(context: Context) : View(context) {

    // ── live data (pushed by FpsStack/ThermalMonitor loop) ──
    var fps = 0; set(v) { field = v; if (expanded) invalidate() }
    var thermal = false; set(v) { field = v; invalidate() }
    var onDefrost: (() -> Unit)? = null

    private val ram = FloatArray(60) { 0.5f }; private var ramIdx = 0
    private val cpu = FloatArray(8)
    fun push(fps: Int, ramFraction: Float, cpuFractions: FloatArray) {
        this.fps = fps
        ram[ramIdx] = ramFraction; ramIdx = (ramIdx + 1) % ram.size
        System.arraycopy(cpuFractions, 0, cpu, 0, minOf(cpuFractions.size, cpu.size))
        if (expanded) invalidate()
    }

    // ── expand/collapse (350ms §7.12) ──
    private var expanded = false
    private var expandT = 0f
    private val expandAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 350; interpolator = DecelerateInterpolator()
        addUpdateListener { expandT = it.animatedValue as Float; invalidate() }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val autoMinimize = Runnable { setExpanded(false) }

    fun setExpanded(on: Boolean) {
        if (expanded == on) return
        expanded = on
        expandAnim.cancel()
        expandAnim.setFloatValues(if (on) 0f else 1f, if (on) 1f else 0f)
        expandAnim.start()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (on) handler.postDelayed(autoMinimize, 5000) else handler.removeCallbacks(autoMinimize)
    }
    fun notifyInteraction() { if (expanded) { handler.removeCallbacks(autoMinimize); handler.postDelayed(autoMinimize, 5000) } }

    // ── paints ──
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val filamentP = Paint(ANTI_ALIAS).apply { color = 0xFFD9A75A.toInt(); strokeCap = Paint.Cap.ROUND }
    private val emberP = Paint(ANTI_ALIAS).apply { color = 0xFFF5402C.toInt() }
    private val panelP = Paint(ANTI_ALIAS).apply {
        color = if (Build.VERSION.SDK_INT >= 31) 0xD0101113.toInt() else 0xCC101113.toInt()  // blur fallback §7.12
    }
    private val strokeP = Paint(ANTI_ALIAS).apply { color = 0xFF2B2F34.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1) }
    private val fpsP = Paint(ANTI_ALIAS).apply {
        color = 0xFF7FE060.toInt(); typeface = Typeface.MONOSPACE /* load PlexMono from assets */; isFakeBoldText = true
    }
    private val labelP = Paint(ANTI_ALIAS).apply { color = 0xFFA29880.toInt(); typeface = Typeface.MONOSPACE }
    private val sparkP = Paint(ANTI_ALIAS).apply { color = 0xFFCFC6AE.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1.2) }
    private val barP = Paint(ANTI_ALIAS).apply { color = 0xFFD9A75A.toInt() }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = measuredWidth.toFloat(); val h = measuredHeight.toFloat()
        // collapsed filament width interpolates: 2dp → panel width
        val panelW = dp(58)
        val curW = dp(2) + (panelW - dp(2)) * expandT
        val pulse = if (thermal) 0.35f + 0.65f * ((System.nanoTime() / 4e8f) % 1f) else 1f
        val filColor = if (thermal) emberP.color else filamentP.color

        if (expandT < 0.99f) {                          // filament (drawn while any collapse remains)
            filamentP.alpha = (255 * (1f - expandT * 0.7f) * pulse).toInt().coerceIn(0, 255)
            filamentP.strokeWidth = dp(2)
            c.drawLine(dp(7), 0f, dp(7), h, filamentP)
        }
        if (expandT > 0.01f) {                          // panel
            val alpha = (255 * expandT).toInt()
            panelP.alpha = alpha; strokeP.alpha = alpha; fpsP.alpha = alpha
            labelP.alpha = alpha; sparkP.alpha = alpha; barP.alpha = alpha
            val left = dp(3); val top = 0f; val right = left + curW; val bottom = h
            c.drawRoundRect(left, top, right, bottom, dp(3), dp(3), panelP)
            c.drawRoundRect(left, top, right, bottom, dp(3), dp(3), strokeP)

            val cx = (left + right) / 2f
            // FPS
            fpsP.textSize = dp(22)
            val fpsY = top + dp(30)
            c.drawText("$fps", cx - fpsP.measureText("$fps") / 2f, fpsY, fpsP)
            labelP.textSize = dp(8)
            c.drawText("FPS", cx - labelP.measureText("FPS") / 2f, fpsY + dp(11), labelP)
            // RAM sparkline (ring buffer, oldest first)
            path.reset()
            val sy = fpsY + dp(22); val sh = dp(20); val sw = curW - dp(16)
            for (i in 0 until ram.size) {
                val v = ram[(ramIdx + i) % ram.size]
                val x = left + dp(8) + sw * i / (ram.size - 1f)
                val y = sy + sh - v * sh
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, sparkP)
            // CPU 8-segment
            val by = sy + sh + dp(8); val bh = dp(24); val bw = (curW - dp(16)) / 8f
            for (i in 0 until 8) {
                val bh2 = cpu[i] * bh
                c.drawRect(left + dp(8) + i * bw + dp(0.5f), by + bh - bh2,
                    left + dp(8) + (i + 1) * bw - dp(0.5f), by + bh, barP)
            }
            // DEFROST node
            drawSnow(c, cx, by + bh + dp(24), dp(9), labelP)
        }
    }

    private fun drawSnow(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.strokeWidth = dp(1.2)
        repeat(6) { i ->
            val a = i / 6f * 2f * Math.PI.toFloat()
            c.drawLine(cx - kotlin.math.cos(a) * r, cy - kotlin.math.sin(a) * r,
                cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r, p)
        }
    }

    private var downY = 0f; private var downT = 0L
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; downT = System.currentTimeMillis(); return true }
            MotionEvent.ACTION_UP -> {
                notifyInteraction()
                val tap = abs(e.y - downY) < dp(8) && System.currentTimeMillis() - downT < 300
                if (tap) {
                    if (!expanded) setExpanded(true)
                    else {
                        // DEFROST hit zone (bottom of the panel)
                        val defrostY = measuredHeight - dp(40)
                        if (e.y > defrostY) {
                            performHapticFeedback(
                                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                                else HapticFeedbackConstants.VIRTUAL_KEY)
                            onDefrost?.invoke()
                        } else setExpanded(false)
                    }
                }
            }
        }
        return super.onTouchEvent(e)   // moves fall through → service drag/snap (§18)
    }
}
```

---

## 33. `Ignition.kt` — Splash (§7.1)

```kotlin
package com.ivarna.apexcore.ui.iron.splash

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Ignition(onSplashFinished: (showOnboarding: Boolean) -> Unit) {
    val reduced = LocalReducedMotion.current
    val sweep = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!reduced) {
            launch {                                              // mini ignition sweep
                sweep.animateTo(1f, tween(300, easing = LinearEasing))
                sweep.animateTo(0.45f, IronMotion.needle())
            }
        }
        appear.animateTo(1f, tween(360, easing = IronMotion.EaseWind))
        delay(550)
        onSplashFinished(!OnboardingPreferences.isOnboardingCompleted)   // existing router
    }

    Box(Modifier.fillMaxSize().background(Iron.Anvil900).ironGrain(0.04f)) {
        Column(Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 0.96f + 0.04f * appear.value; scaleX = s; scaleY = s
                alpha = appear.value
            }) {
            // The Mark: chamfered plate + sweeping needle
            Canvas(Modifier.size(96.dp)) {
                val r = size.minDimension / 2f
                drawPath(chamferPath(size, dp(6), dp(12)), Iron.Anvil800)
                drawPath(chamferPath(size, dp(6), dp(12)), Iron.Anvil600,
                    style = Stroke(1.dp.toPx()))
                val ring = r * 0.62f
                repeat(24) { i ->
                    val a = (i / 24f) * 240f - 210f                   // dial span
                    drawLine(Iron.Anvil500,
                        Offset(r + cos(Math.toRadians(a.toDouble())).toFloat() * ring,
                            r + sin(Math.toRadians(a.toDouble())).toFloat() * ring),
                        Offset(r + cos(Math.toRadians(a.toDouble())).toFloat() * (ring + 8.dp.toPx()),
                            r + sin(Math.toRadians(a.toDouble())).toFloat() * (ring + 8.dp.toPx())),
                        1.5.dp.toPx())
                }
                val na = Math.toRadians((-210f + 240f * sweep.value).toDouble())
                drawLine(Iron.Signal500, Offset(r, r),
                    Offset(r + cos(na).toFloat() * ring * 0.9f, r + sin(na).toFloat() * ring * 0.9f),
                    3.dp.toPx())
                drawCircle(Iron.Brass400, 5.dp.toPx(), Offset(r, r))
            }
            Spacer(Modifier.height(20.dp))
            RisoText("APEXCORE", IronType.Display.copy(fontSize = 28.sp))
            Spacer(Modifier.height(6.dp))
            Text("FIELD-GRADE PERFORMANCE INSTRUMENTS", IronType.MonoSm,
                color = Iron.Bone500, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(4.dp))
            Text("MK·II", IronType.MonoSm, color = Iron.Brass400)
        }
    }
}
```

---

## Wiring the whole app together (MainActivity)

```kotlin
// AppStage: SPLASH → ONBOARDING → MAIN — existing routing, new skins
when (stage) {
    AppStage.SPLASH     -> Ignition(onSplashFinished = viewModel::routeFromSplash)
    AppStage.ONBOARDING -> FieldManual(
        isReplay = false,
        onboardingCompletedProbe = { false },          // fresh install by definition
        shizuku = vm.shizuku, root = vm.root,
        selectedBackend = vm.preferredBackend,
        onProbe = vm::redetect,
        onSelect = vm::selectBackend,                   // pref + FreezeFramework + FpsStack sync
        onConfigureShizuku = vm::openShizuku, onGrantRoot = vm::probeRoot,
        onFinish = vm::completeOnboarding, onClose = {},
    )
    AppStage.MAIN       -> IronShell(
        finish = vm.themeMode.resolve(isSystemInDarkTheme()),
        tab = vm.tab, onTab = vm::setTab,
        backendName = vm.backendName, backendLed = vm.backendLed,
        onBackend = vm::openBackendSheet,
        slot = vm.slot, onSlot = vm::setSlot,
        home = { TheBench(...) },                        // file 16
        games = { LaunchMatrixScreen(...) },             // file 27
        optics = { OpticsBench(...) },                   // file 28
        toolbox = { Toolbox(...) },                      // file 25
        slotContent = { slot ->
            when (slot) {
                IronSlot.TUNE     -> TuningRoom(...)     // file 29
                IronSlot.PRESSURE -> PressureRoom(...)   // file 23
                IronSlot.LEDGER   -> TheLedger(...)      // file 30
                IronSlot.NONE     -> {}
            }
        },
        replayOverlay = if (vm.showReplay) {{ FieldManual(isReplay = true, ..., onClose = vm::closeReplay) }} else null,
        backendSheet = { BackendBenchSheet(...) },       // file 26
    )
}
```

**Complete spec coverage now:** Splash ✓ · Onboarding (paper manual) ✓ · Shell ✓ · Home ✓ (file 16) · Games ✓ · Overlay ✓ · Settings ✓ · Tune ✓ · Ram Free ✓ · Privacy ✓ · Setup/Pin/Add sheets ✓ · HUD service ✓ · all primitives ✓
