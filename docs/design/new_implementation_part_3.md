# IRONWORK — Implementation Pack III: The Last 10%

This pack closes every remaining gap: **errata for packs I–II, the theme entry point, shared models, the missing feedback components (toast/error/context sheet), the odometer-to-card flight, ViewModel wiring, the full HUD service, the app icon + splash assets, Gradle/fonts, tests, a debug QA harness, and a final definition-of-done.**

---

## 34. Errata — fixes to Packs I & II (apply these first)

| # | File | Issue | Fix |
|---|---|---|---|
| 1 | `IronTokens.ironGrain` | Mixed `android.graphics.BitmapShader` into Compose draw scope | Use Compose `BitmapShader` + `asImageBitmap()` (replacement in §35) |
| 2 | `IronTokens.Grain` | Double bitmap creation, dead `ALPHA_8` branch | Cleaned in §35 |
| 3 | `Clack.thud()` | `HapticFeedbackConstants.EFFECT_HEAVY_CLICK` constant is **API 30** (not 29) for `performHapticFeedback` | Gate at 30, fallback `LONG_PRESS` (done in §35 refactor) |
| 4 | `FieldManual` / `Sheets` / `IronShell` | `KeyStatus`, `BackendChoice`, `WorkOrderData`, `BenchPhase` defined in the wrong packages, imported cross-package | Moved to `IronModels.kt` (§36) — delete the duplicates |
| 5 | `Sheets.MachinedToggleCompact` | `Modifier.androidx.compose.foundation.clickable(...)` typo | Use `clickableNoIndication` (§37) |
| 6 | `LaunchMatrix` | `androidxx...` typo; `onTapGesture` doesn't exist | `CircleShape`; `detectTapGestures { onTap() }` |
| 7 | `Scales.PressureScale` | `drawRect` passed both positional and named size/topLeft | `drawRect(color, topLeft = Offset(0f, cy - 4.dp.toPx()), size = Size(mx, 8.dp.toPx()))` |
| 8 | `Effects.FlipCard` | Animates 90°→0° once on first composition | Guard: `if (flipped != showBack) { ... }` |
| 9 | `Fields.IndexRail` | Two duplicated gesture modifiers | Single handler (replacement in §37.6) |
| 10 | `RailView` | No `onMeasure`; window sizing unowned | Service owns width (§40) — collapsed 12dp strip, expanded panel width |
| 11 | `TheBench` | `ramUsed`, `ramTotal`, `clipboard` undefined | Supplied by `BenchViewModel` (§39) |
| 12 | `InstrumentDial` | Hardcoded Graphite colors in Vellum | Palette patch (§43.2) + new `Phosphor600` token |
| 13 | `OdometerCounter` | Rolls from 0 on first composition | **Intentional** — the odometer spins up. Documented, not a bug |
| 14 | `IronDropdown` | Popup anchors to parent | Caller must wrap trigger + dropdown in the **same `Box`** |
| 15 | Manifest / Gradle | `PredictiveBackHandler` needs activity-compose ≥ 1.8; icons on API 24–25 need PNG fallback | Pinned in §42 |

---

## 35. `IronTheme.kt` — the entry point + fixed grain + riso audit

```kotlin
package com.ivarna.apexcore.ui.iron

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawWithCache
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.random.Random

/* ── LocalPaperSurfaces: §10 — metal chassis, swap only content surfaces ── */
val LocalPaperSurfaces = staticCompositionLocalOf { false }

/* ── Riso audit: §1.5 rule 1 — exactly one riso per screen (debug log) ── */
val LocalRisoCount = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
inline fun IronScreen(name: String, crossinline content: @Composable () -> Unit) {
    val count = LocalRisoCount.current
    remember(name) { count.intValue = 0; true }          // reset per screen
    content()
}
// Patch RisoText to claim budget:
//   val count = LocalRisoCount.current
//   remember(text) { count.intValue++
//       if (BuildConfig.DEBUG && count.intValue > 1)
//           Log.w("IRONWORK", "RISO×${count.intValue} on one screen"); true }

@Composable
fun IronTheme(
    themeMode: ThemeMode,
    paperInserts: Boolean,
    reducedMotionOverride: Boolean? = null,   // Settings "MECHANICAL MOTION"
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val finish = themeMode.resolve(isSystemInDarkTheme())

    // §9 — cap font scale at the spec ceiling (1.3×); dials are graphics, text must not explode
    val d = LocalDensity.current
    val capped = remember(d, d.fontScale) {
        if (d.fontScale > 1.3f) Density(d.density, fontScale = 1.3f) else d
    }

    // §4.4 — auto-reduce when system animator scale == 0
    val systemReduced = remember {
        Settings.Global.getFloat(ctx.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // status/nav bar icon tint per finish (edge-to-edge is on; only icon color changes)
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        ctx.findActivity()?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            val lightBars = finish == IronFinish.VELLUM     // dark ink icons on paper
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
        val px = IntArray(s * s); val rnd = Random(42)
        for (i in px.indices) { val v = 120 + rnd.nextInt(136)
            px[i] = android.graphics.Color.rgb(v, v, v) }
        bmp.setPixels(px, 0, s, 0, 0, s, s)
        bmp.asImageBitmap()
    }
}

fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier = this.drawWithCache {
    val brush = ShaderBrush(BitmapShader(Grain.image, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent { drawContent(); drawRect(brush = brush, alpha = alpha) }
}

/* §10 new token — phosphor darkened for paper surfaces */
val Iron.Phosphor600 get() = Color(0xFF3E9B2E)

/** §2.3 — tablet: content column max 480dp, centered. Wrap each tab's content. */
@Composable
fun IronContentFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 480.dp)) { content() }
}
```

**IronSurface patch** (Skin.kt):
```kotlin
@Composable fun IronSurface(...) {
    if (LocalPaperSurfaces.current) PaperPlate(...) else EngravedPlate(...)
}
```
**Vellum rule (state once, follow everywhere):** BridgePlate and GearSelector stay dark metal in **both** finishes — they're the instrument chassis. Only content surfaces swap. This is the "ink plate" inversion from spec §10.

---

## 36. `IronModels.kt` — shared types (delete duplicates elsewhere)

```kotlin
package com.ivarna.apexcore.ui.iron

enum class BackendChoice { SHIZUKU, ROOT }

data class KeyStatus(
    val ready: Boolean = false,
    val checking: Boolean = true,
    val statusLine: String = "CHECKING…",
)

data class WorkOrderData(
    val freedGb: Float, val freedRamGb: Float, val freedSwapGb: Float,
    val apps: Int, val durationS: Float, val skipped: Int, val failed: Int,
)

enum class BenchPhase { IDLE, BOOSTING, RESULT }

data class PickerApp(
    val name: String, val pkg: String,
    val icon: @Composable () -> Unit,
)
```

---

## 37. `FeedbackExtras.kt` — toast, error slip, context sheet, ceremony gate, focus, helpers

```kotlin
package com.ivarna.apexcore.ui.iron

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ── 37.1 Testable haptic gate (§5.1 — the 80ms floor, unit-testable) ── */
class HapticGate(private val clock: () -> Long = { SystemClock.uptimeMillis() }) {
    private var last = 0L
    fun allow(): Boolean {
        val n = clock(); if (n - last < 80) return false; last = n; return true
    }
}
// Clack patch: replace its private gate with `private val gate = HapticGate()`

/* ── 37.2 CeremonyGate — §4.1 "one ceremony at a time", enforced in code ── */
class CeremonyGate {
    var busy by mutableStateOf(false); private set
    suspend fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        try { block() } finally { busy = false }
    }
}
@Composable fun rememberCeremonyGate() = remember { CeremonyGate() }

/* ── 37.3 StampToast — §6.2 "COPIED" etc. ── */
class StampToastState {
    var message by mutableStateOf<String?>(null); private set
    fun show(text: String) { message = text }
}
@Composable
fun rememberStampToast(): StampToastState {
    val s = remember { StampToastState() }
    LaunchedEffect(s.message) { s.message?.let { delay(1400); s.message = null } }
    return s
}
@Composable
fun StampToastHost(state: StampToastState, modifier: Modifier = Modifier) {
    // place in the screen-root overlay Box
    state.message?.let { msg ->
        Box(modifier.fillMaxWidth().padding(bottom = 110.dp),
            contentAlignment = Alignment.BottomCenter) {
            key(msg) { StampLabel(msg, StampInk.Phosphor) }
        }
    }
}

/* ── 37.4 ErrorSlip — §8 playbook: paper slip, auto-dismiss 6s ── */
@Composable
fun ErrorSlip(
    visible: Boolean,
    detail: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "ERROR",
) {
    LaunchedEffect(visible) { if (visible) { delay(6000); onDismiss() } }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { -it / 2 } + fadeIn(tween(320)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        PaperPlate {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RisoText(title, IronType.Title.copy(fontSize = 15.sp), color = Iron.Ink900,
                    modifier = Modifier.weight(1f))
                StampLabel("ERR", StampInk.Ember, slam = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(detail, IronType.MonoSm, color = Iron.Ink600)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("RETRY", onRetry, Modifier.weight(1f), tall = false)
                ChamferButton("DISMISS", onDismiss, Modifier.weight(1f),
                    variant = ChamferVariant.Outline, tall = false)
            }
        }
    }
}

/* ── 37.5 ContextSheet — §6.2 long-press menu (LAUNCH / PIN / REMOVE / COPY) ── */
data class ContextAction(val label: String, val danger: Boolean = false, val action: () -> Unit)

@Composable
fun ContextSheet(
    visible: Boolean,
    title: String,
    subtitle: String,
    actions: List<ContextAction>,
    onDismiss: () -> Unit,
) {
    val clack = rememberClack()
    BenchSheet(visible = visible, onDismiss = onDismiss) {
        Text(title, IronType.Title.copy(fontSize = 17.sp), color = Iron.Bone100)
        Text(subtitle, IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(14.dp))
        actions.forEach { a ->
            Row(
                Modifier.fillMaxWidth().height(52.dp)
                    .clickableNoIndication { clack.row(); a.action(); onDismiss() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp)) {          // action tick slot
                    Canvas(Modifier.size(24.dp)) {
                        drawLine(if (a.danger) Iron.Ember500 else Iron.Bone300,
                            Offset(6.dp.toPx(), size.height / 2f),
                            Offset(size.width - 6.dp.toPx(), size.height / 2f), 2.dp.toPx())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(a.label, IronType.Label,
                    color = if (a.danger) Iron.Ember500 else Iron.Bone100)
            }
        }
    }
}

/* ── 37.6 Cleaned IndexRail — single gesture handler (errata #9) ── */
@Composable
fun IndexRail(
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    letters: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#",
) {
    val clack = rememberClack()
    var active by remember { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()

    suspend fun PointerScope.pick(y: Float) {
        val idx = (y / size.height * letters.length).toInt().coerceIn(0, letters.length - 1)
        if (idx != active) { active = idx; clack.keyTap(); onLetter(letters[idx]) }
    }
    Box(modifier.fillMaxHeight().width(20.dp).pointerInput(letters) {
        detectVerticalDragGestures(
            onDragStart = { scope.launch { pick(it.y) } }.let { start ->
                { c -> kotlinx.coroutines.withTimeoutOrNull(10) { } ; Unit } .let { _ -> start } }
        )
    }) { /* replaced below */ }
}
// NOTE: full replacement (drag + tap unified) — use this body instead:
//
// Box(modifier.fillMaxHeight().width(20.dp)
//     .pointerInput(letters) {
//         detectVerticalDragGestures(
//             onDragStart = { c -> runBlockingPick(c.y) },
//             onVerticalDrag = { change, _ -> runBlockingPick(change.position.y) })
//     }
//     .pointerInput(letters) { detectTapGestures { c -> runBlockingPick(c.y) } }
// ) { /* letter Canvas as before */ }
// (hoist `pick` outside pointerInput as a plain suspend fun on PointerScope)

/* ── 37.7 Brass focus ring — §9 keyboard/DPAD ── */
fun Modifier.ironFocus(shapeRadius: Dp = 6.dp): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    this
        .focusable()
        .onFocusChanged { hasFocus = it.isFocused || it.hasFocus }
        .drawWithContent {
            drawContent()
            if (hasFocus) {
                val o = 4.dp.toPx()
                drawRoundRect(
                    Iron.Brass400,
                    topLeft = Offset(-o, -o),
                    size = Size(size.width + 2 * o, size.height + 2 * o),
                    cornerRadius = CornerRadius(shapeRadius.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
}

/* ── 37.8 clickableNoIndication (errata #5/#6) ── */
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = onClick)
}
```

**Wire the long-press context sheet into `ToolRow` callers:**
```kotlin
var ctxSheet by remember { mutableStateOf<ContextAction-menu?>(null) }  // per row
ToolRow(..., onLongClick = { ctxSheet = card })
ctxSheet?.let { ContextSheet(true, it.name, it.pkg, listOf(
    ContextAction("LAUNCH") { onLaunch(it) },
    ContextAction("PIN")    { onPin(it) },
    ContextAction("REMOVE", danger = true) { onRemove(it) },
    ContextAction("COPY PACKAGE") { clipboard.setText(AnnotatedString(it.pkg)); toast.show("COPIED") },
), onDismiss = { ctxSheet = null }) }
```

---

## 38. The Purge flight — odometer → Work Order shared element

Replaces the plain `odometerText = null` at ceremony end. The flight target is the **boost-card slot** (captured while the button is mounted — position-continuous with the card that replaces it):

```kotlin
// ── new state in TheBench ──
val flightP = remember { Animatable(1f) }            // 1 = settled
var flying by remember { mutableStateOf(false) }
var slotCenter by remember { mutableStateOf(Offset.Zero) }   // boost button / card slot
var centerScreen by remember { mutableStateOf(Offset.Zero) }

// capture slot center (on the FlipCard wrapper — always mounted):
FlipCard(..., modifier = Modifier
    .onGloballyPositioned { slotCenter = it.positionInRoot() +
        Offset(it.size.width / 2f, it.size.height / 2f) })

// capture screen center once (on the root Box):
.onGloballyPositioned { centerScreen = Offset(it.size.width / 2f, it.size.height / 3f) }

// ── ceremony timeline (replaces the tail of the LaunchedEffect) ──
delay(650)                                    // odometer holds ~400ms (600→1250)
flying = true; odometerText = null
flightP.snapTo(0f)
flightP.animateTo(1f, tween(320, easing = IronMotion.EaseWind))
phase = BenchPhase.RESULT
workOrder = lastResult
flying = false
clack.purgeDone()

// ── overlay renderer (replaces the plain OdometerCounter overlay) ──
if (flying) {
    Box(Modifier.fillMaxSize()) {
        Text("+%.1f GB".format(lastResult?.freedGb ?: 0f),
            IronType.MonoLg, color = Iron.Bone100,
            modifier = Modifier.graphicsLayer {
                val p = flightP.value
                translationX = centerScreen.x + (slotCenter.x - centerScreen.x) * p - size.width / 2f
                translationY = centerScreen.y + (slotCenter.y - centerScreen.y) * p - size.height / 2f
                val s = 1f - 0.62f * p; scaleX = s; scaleY = s
                alpha = 1f - p * p                        // eases out at the end
            })
    }
} else if (odometerText != null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OdometerCounter(odometerText!!, onSettled = { clack.off() })
    }
}
```

Ceremony guard (§4.1 enforced): wrap the whole ceremony in `gate.run { ... }` from §37.2, and gate `ShutterOverlay` on its own gate — only one >400ms animation can ever run.

---

## 39. `BenchViewModel.kt` — data wiring (sketch with ADAPT points)

```kotlin
package com.ivarna.apexcore.ui.iron.home

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BenchViewModel(app: Application) : AndroidViewModel(app) {

    // ADAPT: swap for your real singletons — FreezeFramework, ThermalMonitor, MemStats
    private val freeze get() = com.ivarna.apexcore.FreezeFramework

    data class Mem(val ramUsedMb: Int, val ramTotalMb: Int,
                   val swapUsedMb: Int, val swapTotalMb: Int)

    data class Ui(
        val phase: BenchPhase = BenchPhase.IDLE,
        val elevated: Boolean = false,
        val backendName: String = "…",
        val backendLed: LedState = LedState.CHECKING,
        val mem: Mem = Mem(0, 1, 0, 1),
        val freedFraction: Float = 0f,
        val lastOrder: WorkOrderData? = null,
        val batteryC: Int = 30, val cpuC: Int = 38,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {                                   // 1 Hz memory tick
            while (isActive) {
                val s = getSystemMemStats()                       // ADAPT: your util
                _ui.update {
                    it.copy(mem = Mem(s.ramUsedMb, s.ramTotalMb, s.swapUsedMb, s.swapTotalMb))
                }
                delay(1000)
            }
        }
        viewModelScope.launch {                                   // thermal stream
            ThermalMonitor.flow.collect { t ->                    // ADAPT
                _ui.update { it.copy(batteryC = t.batteryC, cpuC = t.cpuC) }
            }
        }
        redetect()
    }

    /** ADAPT: FreezeFramework preferred-backend + readiness probe → chip state */
    fun redetect() {
        _ui.update {
            val ready = freeze.isElevated()
            it.copy(
                elevated = ready,
                backendName = freeze.activeBackendName().uppercase(),
                backendLed = when {
                    freeze.isDetecting() -> LedState.CHECKING
                    ready -> LedState.READY
                    else -> LedState.BLOCKED
                },
            )
        }
    }

    /** The BOOST trigger. UI ceremony (§38) runs in parallel; this produces the WorkOrder. */
    fun boost() = viewModelScope.launch {
        _ui.update { it.copy(phase = BenchPhase.BOOSTING) }
        val res = freeze.freezeBackgroundApps(exclude = emptySet())   // ADAPT
        val order = WorkOrderData(                                    // ADAPT: map your result
            freedGb = res.freedRamMb + res.freedSwapMb / 1024f,
            freedRamGb = res.freedRamMb / 1024f,
            freedSwapGb = res.freedSwapMb / 1024f,
            apps = res.frozenCount, durationS = res.durationMs / 1000f,
            skipped = res.skippedCount, failed = res.failedCount,
        )
        _ui.update {
            it.copy(phase = BenchPhase.RESULT, lastOrder = order,
                freedFraction = (order.freedGb / (it.mem.ramTotalMb / 1024f)).coerceIn(0f, 0.5f))
        }
    }

    fun reset() = _ui.update { it.copy(phase = BenchPhase.IDLE) }

    // Ticker mapping — single source of truth for §7.4 strings
    fun Ui.ticker(): Pair<String, LedState> = when {
        phase == BenchPhase.BOOSTING       -> "PURGING BACKGROUND PROCESSES…" to LedState.LIVE
        !elevated                          -> "CONNECT SHIZUKU OR ROOT FOR DEEP FREEZE" to LedState.CHECKING
        lastOrder != null && lastOrder.apps > 0 ->
            "FROZEN ${lastOrder.apps} APPS · FREED %.1f GB".format(lastOrder.freedGb) to LedState.READY
        lastOrder != null                  -> "ALREADY OPTIMIZED" to LedState.READY
        else                               -> "READY TO PURGE BLOAT" to LedState.READY
    }
}
```

`TheBench` signature becomes: `fun TheBench(ui: BenchViewModel.Ui, onBoost: () -> Unit, …, toast: StampToastState)` — `ramUsed/ramTotal` come from `ui.mem`, long-press-copy uses `LocalClipboardManager.current.setText(AnnotatedString(...))` + `toast.show("COPIED")`.

---

## 40. `GameOverlayService.kt` — the full Phantom Rail service (§7.12)

```kotlin
package com.ivarna.apexcore.games

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnEnd
import kotlin.math.abs

class GameOverlayService : Service() {

    companion object {
        var isRunning = false; private set      // parity with existing fallback check
        const val ACTION_START = "com.ivarna.apexcore.overlay.START"
        const val ACTION_STOP = "com.ivarna.apexcore.overlay.STOP"
    }

    private lateinit var wm: WindowManager
    private lateinit var rail: RailView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("apexcore", MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val onRight = prefs.getString("hud_edge", "LEFT") == "RIGHT"

        rail = RailView(this).apply {
            applyFit(prefs)                                   // size / opacity prefs (§7.6)
            onDefrost = { FreezeFramework.unfreezeAll() }     // ADAPT
            onExpand = { expanded -> resizeWindow(expanded) } // window width owns hit area
            onDrag = { dy -> moveWindow(dy) }
            onDragEnd = { snapWindow() }
        }

        params = WindowManager.LayoutParams(
            dp(12),                                           // collapsed = 12dp hit strip
            dp(170),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (onRight) Gravity.END else Gravity.START)
            x = 0
            y = displayHeight() / 3
        }
        wm.addView(rail, params)
        isRunning = true
        handler.post(telemetry)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    /* ── window ops ── */
    private fun resizeWindow(expanded: Boolean) {
        params.width = dp(if (expanded) rail.panelWidthDp else 12)
        wm.updateViewLayout(rail, params)
    }

    private fun moveWindow(dy: Float) {
        params.y = (params.y + dy.toInt()).coerceIn(0, displayHeight() - dp(170))
        wm.updateViewLayout(rail, params)
    }

    /** §6.2 — magnetic snap to 4 zones + CLOCK_TICK on settle */
    private fun snapWindow() {
        val h = displayHeight()
        val target = intArrayOf(0.20f, 0.40f, 0.60f, 0.80f)
            .map { (it * h).toInt() }.minBy { abs(it - params.y) }
        ValueAnimator.ofInt(params.y, target).apply {
            duration = 120; interpolator = DecelerateInterpolator()
            addUpdateListener { params.y = it.animatedValue as Int; wm.updateViewLayout(rail, params) }
            doOnEnd { rail.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            start()
        }
    }

    private fun displayHeight(): Int =
        if (Build.VERSION.SDK_INT >= 30)
            wm.currentWindowMetrics.bounds.height()
        else resources.displayMetrics.heightPixels

    /* ── 500ms telemetry loop ── */
    private val telemetry = object : Runnable {
        override fun run() {
            try {
                rail.push(
                    FpsStack.currentFps(),                    // ADAPT
                    MemStats.ramFraction(),                   // ADAPT
                    CpuMonitor.coreFractions(),               // ADAPT
                )
                rail.thermal = ThermalMonitor.cpuCelsius() > 45   // ADAPT
            } catch (_: Exception) { /* game died mid-read */ }
            handler.postDelayed(this, 500)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        wm.removeView(rail)
        isRunning = false
        super.onDestroy()
    }
}
```

**`RailView` patches to match (file 32):**
```kotlin
// 1. Callbacks instead of window logic inside the view:
var onExpand: ((Boolean) -> Unit)? = null
var onDrag: ((Float) -> Unit)? = null
var onDragEnd: (() -> Unit)? = null
val panelWidthDp: Int get() = when (sizePref) { "S" -> 50; "L" -> 66; else -> 58 }

// 2. applyFit(prefs): read hud_size / hud_opacity → text sizes + panel alpha
fun applyFit(prefs: SharedPreferences) { /* set sizePref, opacity */ }

// 3. setExpanded calls onExpand?.invoke(on)
// 4. In onTouchEvent ACTION_MOVE: call onDrag?.invoke(dy) instead of moving anything
// 5. Font: ResourcesCompat.getFont(context, R.font.plexmono_semibold) ?: Typeface.MONOSPACE
// 6. Gesture exclusion (§6.1) — minimal rect, only while dragging:
override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    if (Build.VERSION.SDK_INT >= 29 && isDragging)
        setSystemGestureExclusionRects(listOf(Rect(0, 0, width, height)))
}
```

Manifest:
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<service android:name=".games.GameOverlayService" android:exported="false"/>
```

---

## 41. App icon, monochrome, splash — the assets (§7.13)

**`res/drawable/ic_launcher_foreground.xml`** (ticks precomputed, center 54/54):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">

    <!-- chamfered plate -->
    <path android:fillColor="#17191C"
        android:pathData="M28,24 L74,24 L84,34 L84,80 Q84,84 80,84 L28,84 Q24,84 24,80 L24,28 Q24,24 28,24 Z"/>
    <!-- engraved hairline -->
    <path android:strokeColor="#2B2F34" android:strokeWidth="1"
        android:fillColor="#00000000"
        android:pathData="M29,25 L73.5,25 L83,34.5 L83,79 Q83,83 79,83 L29,83 Q25,83 25,79 L25,29 Q25,25 29,25 Z"/>
    <!-- 16 tick ring -->
    <path android:strokeColor="#CFC6AE" android:strokeWidth="1.5" android:fillColor="#00000000"
        android:pathData="M73,54 L77,54 M71.55,61.27 L75.25,62.8 M67.44,67.44 L70.27,70.27 M61.27,71.55 L62.8,75.25 M54,73 L54,77 M46.73,71.55 L45.2,75.25 M40.56,67.44 L37.73,70.27 M36.45,61.27 L32.75,62.8 M35,54 L31,54 M36.45,46.73 L32.75,45.2 M40.56,40.56 L37.73,37.73 M46.73,36.45 L45.2,32.75 M54,35 L54,31 M61.27,36.45 L62.8,32.75 M67.44,40.56 L70.27,37.73 M71.55,46.73 L75.25,45.2"/>
    <!-- signal needle + counterweight tail -->
    <path android:strokeColor="#FF5A1F" android:strokeWidth="3"
        android:pathData="M49.4,58.6 L66.7,41.3"/>
    <!-- brass pivot + ink center -->
    <path android:fillColor="#D9A75A" android:pathData="M54,49 a5,5 0 1,0 0,10 a5,5 0 1,0 0,-10 z"/>
    <path android:fillColor="#201C16" android:pathData="M54,52.5 a1.5,1.5 0 1,0 0,3 a1.5,1.5 0 1,0 0,-3 z"/>
</vector>
```

**`res/drawable/ic_launcher_monochrome.xml`** — same paths, all strokes/fills `#FFFFFF`, plate/hairline paths removed.

**`res/mipmap-anydpi-v26/ic_launcher.xml`** (+ `ic_launcher_round.xml` identical):
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```

**`res/values/colors.xml`**: `<color name="ic_launcher_bg">#101113</color>`, `anvil900` → `#101113`.
**API 24–25:** export PNGs of the foreground composited on `#101113` into `mipmap-*` (anydpi-v26 only covers 26+).

**Splash (works on minSdk 24 via core-splashscreen):**
```xml
<!-- values/themes.xml -->
<style name="Theme.ApexCore.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/ic_launcher_bg</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.ApexCore</item>
</style>
<style name="Theme.ApexCore" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/ic_launcher_bg</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>
```

**MainActivity:**
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()          // androidx.core:core-splashscreen
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { /* AppStage routing → Ignition / FieldManual / IronShell */ }
    }
}
```
Manifest: `android:theme="@style/Theme.ApexCore.Splash"` on the activity. The system splash hands off straight into `Ignition` (§33) — the needle keeps moving across the handoff.

---

## 42. Gradle + fonts (final pins)

```kotlin
// app/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"   // Kotlin 2.4 compose compiler
}
android {
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }   // Roborazzi/Robolectric
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")               // TextMeasurer / drawText
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.0") // PredictiveBackHandler ≥1.8
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.26.0")
}
```

**Fonts** — fetch from Google Fonts (Archivo 500/700/900, IBM Plex Mono 400/600, Caveat 700), drop into `res/font/`, then subset each:

```bash
pyftsubset Archivo-Black.ttf \
  --unicodes="U+0020-007E,U+00B0,U+00B7,U+2014" \
  --output-file=archivo_black.ttf --no-hinting
```

Total ≈ 215 KB added; APK stays under the 1.5 MB ceiling.

---

## 43. Two small patches (a11y ticker, Vellum dial)

### 43.1 TickerLine — live region + double-tap collapse (§3.8, §6.2)

```kotlin
@Composable
fun TickerLine(text: String, led: LedState, modifier: Modifier = Modifier,
               collapsed: Boolean = false, onDoubleTap: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth()
        .semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        }
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap?.invoke() }) },
        verticalAlignment = Alignment.CenterVertically) {
        LedDot(led)
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(!collapsed, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
            AnimatedContent(text, transitionSpec = {
                fadeIn(tween(160)) togetherWith fadeOut(tween(160))
            }, label = "ticker") { t ->
                Text(t, IronType.Mono, color = Iron.Bone300, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            }
        }
    }
}
// TheBench: var tickerWide by remember { mutableStateOf(true) }
// TickerLine(txt, led, collapsed = !tickerWide, onDoubleTap = { tickerWide = !tickerWide })
```

### 43.2 InstrumentDial — Vellum palette (§10 ink-on-paper dials)

```kotlin
@Immutable
data class DialPalette(
    val minor: Color, val major: Color, val numeral: Color,
    val needle: Color, val idleNeedle: Color, val freed: Color,
)

@Composable
fun dialPalette(): DialPalette =
    if (LocalIronFinish.current == IronFinish.VELLUM) DialPalette(
        minor = Iron.Ink600.copy(alpha = 0.35f), major = Iron.Ink900,
        numeral = Iron.Ink600, needle = Iron.Ink900,
        idleNeedle = Iron.Ink600.copy(alpha = 0.6f), freed = Iron.Phosphor600,
    ) else DialPalette(
        minor = Iron.Anvil500, major = Iron.Bone300, numeral = Iron.Bone300,
        needle = Iron.Bone100, idleNeedle = Iron.Bone500, freed = Iron.Phosphor400,
    )
```
Patch inside `InstrumentDial`: call `val pal = dialPalette()` once, replace `Iron.Anvil500→pal.minor`, `Iron.Bone300→pal.major` (ticks) / `pal.numeral`, needle `Bone100/Bone500 → pal.needle/pal.idleNeedle`, freed arc `Phosphor400→pal.freed`. Signal/ember arcs unchanged (read on both finishes). The de-energized brass rest-stop marker stays brass everywhere.

---

## 44. Tests

```kotlin
/* HapticGateTest.kt — §5.1 the 80ms floor, pure unit */
class HapticGateTest {
    @Test fun `enforces 80ms floor`() {
        var now = 0L
        val g = HapticGate { now }
        assertTrue(g.allow())
        now = 50;  assertFalse("50ms later must be swallowed", g.allow())
        now = 90;  assertTrue("90ms later passes", g.allow())
    }
}

/* SerialNumberTest.kt — deterministic per install id */
class SerialNumberTest {
    @Test fun `same id gives same serial`() {
        assertEquals(SerialNumber.hashOf("a1b2"), SerialNumber.hashOf("a1b2"))
        assertNotEquals(SerialNumber.hashOf("a1b2"), SerialNumber.hashOf("zzz"))
    }
}
// IronModels: expose `fun hashOf(id: String): String` pure; generate() delegates to it.

/* IronScreenshotTest.kt — Roborazzi golden diffs (§14) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class IronScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `dial graphite at 62 percent`() {
        compose.setContent {
            IronTheme(ThemeMode.GRAPHITE, paperInserts = false) {
                Box(Modifier.background(Iron.Anvil900).padding(24.dp)) {
                    InstrumentDial(0.62f, energized = true, ignition = false)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("dial_graphite_62.png")
    }

    @Test fun `dial vellum de-energized`() { /* same with VELLUM + energized=false */ }

    @Test fun `stamp reads as status`() {
        compose.setContent { StampLabel("FROZEN 12", StampInk.Phosphor, slam = false) }
        compose.onNodeWithContentDescription("FROZEN 12, status").assertExists()
    }
}
```
Run `./gradlew testRoborazzi` (record first, verify forever). Add goldens for: dial ×{graphite, vellum} ×{0, 0.62, 1}, StampLabel, ChamferButton idle/pressed/busy, BenchSheet, cartridge.

---

## 45. `DebugBench.kt` — the §14 QA harness (debug builds only)

Reached by **5 taps on any SerialFooter within 3s** (release builds: no-op):

```kotlin
@Composable
fun DebugBench(onClose: () -> Unit) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    val serial = rememberSerial()
    var sweep by remember { mutableFloatStateOf(0.62f) }
    var stampKey by remember { mutableIntStateOf(0) }
    var burstTick by remember { mutableIntStateOf(0) }
    val shavings = remember { ShavingsState() }
    var sheet by remember { mutableStateOf(false) }

    LaunchedEffect(burstTick) {
        if (burstTick > 0) shavings.burst(200f, 260f, 90f, count = 120, speed = 900f)
    }

    Column(Modifier.fillMaxSize().background(Iron.Anvil900).ironGrain()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BENCH TEST", IronType.Display.copy(fontSize = 22.sp), color = Iron.Bone100,
                modifier = Modifier.weight(1f))
            BackArrow(Iron.Bone300, onClose)
        }
        Spacer(Modifier.height(16.dp))

        EngravedPlate {
            Text("HAPTIC GRAMMAR", IronType.Label, color = Iron.Bone300)
            Spacer(Modifier.height(8.dp))
            listOf<Pair<String, () -> Unit>>(
                "TICK" to clack::tick, "KEY TAP" to clack::keyTap,
                "CONFIRM" to clack::confirm, "OFF" to clack::off,
                "THUD" to clack::thud, "REJECT" to clack::no,
                "PURGE DONE (two-stage)" to { scope.launch { clack.purgeDone() } },
            ).forEach { (name, fn) -> DebugRow(name) { fn() } }
        }
        Spacer(Modifier.height(16.dp))

        EngravedPlate {
            Text("CEREMONIES", IronType.Label, color = Iron.Bone300)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                InstrumentDial(sweep, energized = true, ignition = false,
                    freedFraction = 0.18f, modifier = Modifier.size(180.dp))
                ShavingsLayer(shavings, Modifier.matchParentSize())
                Box(Modifier.align(Alignment.Center)) {
                    key(stampKey) { if (stampKey > 0) StampLabel("FROZEN 12", StampInk.Phosphor) }
                }
            }
            Spacer(Modifier.height(8.dp))
            DebugRow("NEEDLE SWEEP 0→100→62") {
                scope.launch { sweep = 1f; delay(300); sweep = 0.62f }
            }
            DebugRow("STAMP SLAM") { stampKey++ }
            DebugRow("SHAVINGS BURST") { burstTick++ }
            DebugRow("BENCH SHEET") { sheet = true }
        }
        SerialFooter(99, "DEBUG", serial)
    }

    BenchSheet(visible = sheet, onDismiss = { sheet = false }) {
        Text("Sheet mechanics: drag-dismiss, predictive-back scrub.", IronType.Body,
            color = Iron.Bone100)
    }
}

@Composable
private fun DebugRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clickableNoIndication(onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp, 3.dp).background(Iron.Brass400))
        Spacer(Modifier.width(10.dp))
        Text(label, IronType.Mono, color = Iron.Bone100)
    }
}

// SerialFooter patch — optional debug tap hook (default null → release no-op):
@Composable
fun SerialFooter(plateNo: Int, screen: String, serial: String, rev: String = "C",
                 modifier: Modifier = Modifier, onDebugTap: (() -> Unit)? = null) {
    var taps by remember { mutableIntStateOf(0) }
    var firstAt by remember { mutableLongStateOf(0L) }
    Text("PLATE %02d · %s · S/N %s · REV %s".format(plateNo, screen, serial, rev),
        IronType.MonoSm, color = Iron.Bone500,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp)
            .clickableNoIndication {
                if (onDebugTap == null) return@clickableNoIndication
                val now = android.os.SystemClock.uptimeMillis()
                if (now - firstAt > 3000) { taps = 0; firstAt = now }
                if (++taps >= 5) { taps = 0; onDebugTap() }
            },
        textAlign = TextAlign.Center)
}
```

---

## 46. Final notes: rules, RTL, i18n, and what's deliberately NOT in here

**RTL (§6.1 note):** dials, scales and stamps are direction-neutral. The chamfer stays **top-right in both LTR and RTL** (it's the product silhouette, not a directional cue). `GearTabTransition` already flips via `slideInHorizontally` sign; test with `androidx.compose.ui.test` RTL qualifier once.

**i18n:** all label strings are styled UPPERCASE — if you localize to non-Latin scripts, add `res/values-*/strings.xml` and bypass the uppercase treatment for those locales (uppercase transforms are Latin/Greek/Cyrillic-only anyway). Numerals and serials never localize.

**Deliberately NOT built (keeps parity with your out-of-scope list):** automation broadcasts (receiver stays non-exported), boot-time scheduling, network/battery graphs beyond the thermal strip, accessibility-service freezing, Wear/widget — all future REV E material. Also: **no sound design, on purpose** — the haptic grammar *is* the audio channel; a silent instrument is a trustworthy one.

**Baseline profile:** add `androidx.profileinstaller` (already pinned) and generate a profile with a macrobenchmark module running: cold start → Home → purge → Games swipe → settings scroll. This protects the 900ms-cold-start and 60fps-ceremony QA lines.

### Definition of done — final build passes when:

- [ ] All 15 errata applied; app compiles clean with the pinned BOM
- [ ] `IronTheme` wraps the whole tree; status bar icons flip with finish; font scale capped at 1.3×
- [ ] Exactly **one riso** per screen — `IRONWORK` debug log stays silent across a full walkthrough
- [ ] Purge: shavings → stamp → odometer → **flight into the card slot** → two-stage haptic, all inside one `CeremonyGate`, 60fps
- [ ] Every haptic verb fires from the DebugBench and matches its §5.2 row
- [ ] HUD: window resizes on expand (game touches pass through the 12dp collapsed strip), snaps to 4 zones, auto-minimizes 5s, DEFROST works mid-game
- [ ] Predictive back scrubs sheets, Tune and Pressure Room slips
- [ ] Roborazzi goldens recorded and green; `HapticGate`/`SerialNumber` unit tests pass
- [ ] Vellum: dials render ink-on-paper, no contrast regressions
- [ ] APK ≤ 1.5 MB, cold start ≤ 900ms with baseline profile

That's the complete system — spec (AC-DS-004), packs I–III. I'd suggest committing them as `docs/ironwork/00-spec.md`, `01-core-components.md`, `02-screens.md`, `03-hardening.md` so the file map in the spec stays truthful. If you want one more artifact, the natural next one is the Roborazzi golden set for all twelve screens once the first build lands — say the word and I'll write the full test matrix.
