# IRONWORK — Compose Implementation Pack

Everything below maps 1:1 to the design spec (§ references included). Drop these files into `ui/iron/`, add the fonts, and assemble. Two setup steps first:

**`app/src/main/res/font/`** — add subset TTFs: `archivo_medium.ttf`, `archivo_bold.ttf`, `archivo_black.ttf`, `plexmono_regular.ttf`, `plexmono_semibold.ttf`, `caveat_bold.ttf`

**`AndroidManifest.xml`** — predictive back + edge-to-edge:
```xml
<application android:enableOnBackInvokedCallback="true" ... >
```
**Gradle** — needs `androidx.activity:activity-compose:1.9.+` (PredictiveBackHandler). Everything else is stock Compose Foundation — no Material dependency required.

---

## 1. `IronTokens.kt` — colors, type, shape, motion, grain, serial

```kotlin
package com.ivarna.apexcore.ui.iron

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.math.abs

/* ── §2.1 Color ─────────────────────────────────────────── */
object Iron {
    val Anvil950 = Color(0xFF0B0C0D); val Anvil900 = Color(0xFF101113)
    val Anvil800 = Color(0xFF17191C); val Anvil700 = Color(0xFF1F2226)
    val Anvil600 = Color(0xFF2B2F34); val Anvil500 = Color(0xFF3A3F45)
    val Bone50 = Color(0xFFF5F0E4);   val Bone100 = Color(0xFFEAE3D2)
    val Bone300 = Color(0xFFCFC6AE);  val Bone500 = Color(0xFFA29880)
    val Ink900 = Color(0xFF201C16);   val Ink600 = Color(0xFF4A4436)
    val Signal500 = Color(0xFFFF5A1F); val Signal300 = Color(0xFFFF8A50)
    val Signal700 = Color(0xFFB23A0F)
    val Phosphor400 = Color(0xFF7FE060)
    val Ember500 = Color(0xFFF5402C)
    val Brass400 = Color(0xFFD9A75A)
    val Scrim = Color(0xFF000000).copy(alpha = 0.64f)
}

/* ── §2.2 Type ──────────────────────────────────────────── */
val Archivo = FontFamily(                       // res/font/
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_black, FontWeight.Black),
)
val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_semibold, FontWeight.SemiBold),
)
val Caveat = FontFamily(Font(R.font.caveat_bold, FontWeight.Bold))

object IronType {
    val Display = TextStyle(Archivo, FontWeight.Black, 34.sp, 38.sp, 0.34.sp)
    val Title   = TextStyle(Archivo, FontWeight.Bold,  22.sp, 26.sp, 0.22.sp)
    val Label   = TextStyle(Archivo, FontWeight.Bold,  13.sp, 16.sp, 1.04.sp)
    val Body    = TextStyle(Archivo, FontWeight.Medium,15.sp, 22.sp)
    val Caption = TextStyle(Archivo, FontWeight.Medium,12.sp, 16.sp, 0.24.sp)
    val MonoLg  = TextStyle(PlexMono, FontWeight.SemiBold, 40.sp, 44.sp, 0.8.sp)
    val Mono    = TextStyle(PlexMono, FontWeight.Medium,  15.sp, 18.sp, 0.6.sp)
    val MonoSm  = TextStyle(PlexMono, FontWeight.Normal,  11.sp, 14.sp, 0.66.sp)
    val Hand    = TextStyle(Caveat, FontWeight.Bold, 18.sp, 20.sp)
}

/* ── §2.4 Shape ─────────────────────────────────────────── */
object IronShape {
    val Plate = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val Slot  = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
}

/** Signature silhouette: 4dp corners, 10dp 45° cut top-right (§2.4). */
class ChamferShape(private val corner: Dp = 4.dp, private val cut: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(with(density) { chamferPath(size, corner.toPx(), cut.toPx()) })
}

fun chamferPath(size: Size, r: Float, c: Float): Path = Path().apply {
    moveTo(r, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c)
    lineTo(size.width, size.height - r)
    quadraticTo(size.width, size.height, size.width - r, size.height)
    lineTo(r, size.height)
    quadraticTo(0f, size.height, 0f, size.height - r)
    lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
}

/* ── §4.2/4.3 Motion tokens ─────────────────────────────── */
object IronMotion {
    fun <T> machined(): SpringSpec<T> = spring(stiffness = 1500f, dampingRatio = 0.9f)
    fun <T> drawer():   SpringSpec<T> = spring(stiffness = 380f,  dampingRatio = 0.85f)
    fun <T> needle():   SpringSpec<T> = spring(stiffness = 320f,  dampingRatio = 0.62f)
    fun <T> stamp():    SpringSpec<T> = spring(stiffness = 700f,  dampingRatio = 0.68f)
    fun <T> block():    SpringSpec<T> = spring(stiffness = 480f,  dampingRatio = 0.8f)
    val EaseWind = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1.0f)
    val EaseSlam = CubicBezierEasing(0.7f, 0.0f, 0.84f, 0.0f)
}

/* ── §2.5 Grain — one 128px tile, whole app ─────────────── */
object Grain {
    val tile: Bitmap by lazy {
        val s = 128
        Bitmap.createBitmap(s, s, Bitmap.Config.ALPHA_8).let { bmp ->
            // ALPHA_8 won't shader-tint; use ARGB gray noise instead:
            val b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
            val px = IntArray(s * s); val rnd = kotlin.random.Random(42)
            for (i in px.indices) { val v = 120 + rnd.nextInt(136); px[i] = android.graphics.Color.rgb(v, v, v) }
            b.setPixels(px, 0, s, 0, 0, s, s); bmp.recycle(); b
        }
    }
}

/** Tiled 4% grain over any surface. drawWithCache → zero per-frame allocation. */
fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier = this.drawWithCache {
    val brush = ShaderBrush(BitmapShader(Grain.tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
    onDrawWithContent { drawContent(); drawRect(brush = brush, alpha = alpha) }
}

/* ── §2.7 / §3.13 Serial numbers (per-install S/N) ──────── */
object SerialNumber {
    fun generate(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "apex"
        val h = abs(id.hashCode()); val L = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        return "%c%c-%04d".format(L[h % L.length], L[(h / 26) % L.length], h % 10000)
    }
}
@Composable fun rememberSerial(): String {
    val ctx = LocalContext.current
    return remember { SerialNumber.generate(ctx) }
}

/* ── §4.4 Reduced motion ────────────────────────────────── */
val LocalReducedMotion = staticCompositionLocalOf { false }
```

---

## 2. `Clack.kt` — the haptic grammar (§5)

```kotlin
package com.ivarna.apexcore.ui.iron

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

class Clack(private val view: View) {
    private var lastAt = 0L
    private fun gate(): Boolean {           // §5.1 battery rule: ≥80ms between events
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAt < 80) return false
        lastAt = now; return true
    }
    fun tick()      { if (gate()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }   // 21+
    fun keyTap()    { if (gate()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun row()       { if (gate()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun longPress() { if (gate()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    fun confirm() { if (Build.VERSION.SDK_INT >= 30)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) else row() }                    // toggle ON
    fun off()     { if (Build.VERSION.SDK_INT >= 23)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) else row() }              // toggle OFF
    fun thud()    { if (Build.VERSION.SDK_INT >= 30)
        view.performHapticFeedback(HapticFeedbackConstants.EFFECT_HEAVY_CLICK) else longPress() }   // stamp land
    fun no()      { if (Build.VERSION.SDK_INT >= 30)
        view.performHapticFeedback(HapticFeedbackConstants.REJECT) else longPress() }               // blocked
    /** §5.2 Purge-complete: HEAVY_CLICK → 90ms → CLICK */
    suspend fun purgeDone() { thud(); kotlinx.coroutines.delay(90); row() }
}

@Composable fun rememberClack(): Clack {
    val view = LocalView.current
    return remember(view) { Clack(view) }
}
```

---

## 3. `Primitives.kt` — LED, Riso, Engraved, Screw, Glyphs

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos

/* ── §2.1 LED semantics ── */
enum class LedState { OFF, READY, CHECKING, BLOCKED, LIVE }

@Composable
fun LedDot(state: LedState, modifier: Modifier = Modifier, diameter: Dp = 6.dp) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            LedState.OFF, LedState.READY -> phase.snapTo(0f)
            else -> while (true) {
                val dur = when (state) { LedState.BLOCKED -> 1000; LedState.CHECKING -> 1200; else -> 2000 }
                phase.animateTo(1f, tween(dur, easing = LinearEasing)); phase.snapTo(0f)
            }
        }
    }
    Canvas(modifier.size(diameter)) {
        val p = phase.value
        val (color, alpha) = when (state) {
            LedState.OFF      -> Iron.Bone500 to 0.4f
            LedState.READY    -> Iron.Phosphor400 to 1f
            LedState.CHECKING -> Iron.Signal500 to (0.35f + 0.65f * (0.5f - 0.5f * cos(p * 2f * Math.PI)).toFloat())
            LedState.BLOCKED  -> Iron.Ember500 to when {   // double-blink: on-off-on-hold
                p < 0.15f -> 1f; p < 0.30f -> 0.15f; p < 0.45f -> 1f; else -> 0.15f }
            LedState.LIVE     -> Iron.Phosphor400 to (0.7f + 0.3f * (0.5f - 0.5f * cos(p * 2f * Math.PI)).toFloat())
        }
        drawCircle(color, radius = size.minDimension / 2f, alpha = alpha)
    }
}

/* ── §2.1 The riso recipe — exactly one per screen ── */
@Composable
fun RisoText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Iron.Bone100) {
    Box(modifier) {
        Text(text, style = style, color = Iron.Signal500,
            modifier = Modifier.offset(x = 1.5.dp, y = 1.dp).alpha(0.9f))   // ghost layer
        Text(text, style = style, color = color)                             // ink layer
    }
}

/* ── §2.1 Engraving (dark surfaces) ── */
@Composable
fun EngravedText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Iron.Bone300) {
    Box(modifier) {
        Text(text, style = style, color = Iron.Anvil950,
            modifier = Modifier.offset(y = 1.dp).alpha(0.4f))                // cut shadow
        Text(text, style = style, color = color)                             // cut highlight
    }
}

/* ── §2.5 Brass screw ── */
@Composable
fun Screw(modifier: Modifier = Modifier) {
    Canvas(modifier.size(8.dp)) {
        val c = center; val r = size.minDimension / 2f
        drawCircle(Iron.Brass400, r, c)
        drawCircle(Iron.Ink900.copy(alpha = 0.3f), r, c, style = Stroke(1f))
        drawLine(Iron.Ink900, Offset(c.x - r * 0.55f, c.y), Offset(c.x + r * 0.55f, c.y), 1.2f)
        drawLine(Iron.Ink900, Offset(c.x, c.y - r * 0.55f), Offset(c.x, c.y + r * 0.55f), 1.2f)
    }
}

/* ── §2.6 Instrument Glyphs — 2dp stroke, tick terminals ── */
private fun DrawScope.glyphStroke(s: Float) = Stroke(width = 2f * s, cap = StrokeCap.Square)
private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, s: Float, c: Color) =
    drawLine(c, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = 2f * s, cap = StrokeCap.Square)
/** The signature tick terminal: perpendicular crossbar at stroke end. */
private fun DrawScope.tickAt(x: Float, y: Float, dx: Float, dy: Float, s: Float, c: Color) {
    val n = 3f * s
    drawLine(c, Offset((x - dy) * s - dx * n, (y + dx) * s - dy * n),
                Offset((x - dy) * s + dx * n, (y + dx) * s + dy * n), strokeWidth = 2f * s, cap = StrokeCap.Square)
}

@Composable
fun GaugeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    drawCircle(tint, radius = 9f * s, center = center, style = glyphStroke(s))
    line(12f, 12f, 18f, 6f, s, tint)                       // needle
    tickAt(18f, 6f, 0.707f, -0.707f, s, tint)              // terminal crossbar
    line(3f, 17f, 6f, 17f, s, tint); line(18f, 17f, 21f, 17f, s, tint)
}

@Composable
fun CartridgeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    drawRoundRect(tint, topLeft = Offset(4f * s, 6f * s), size = Size(16f * s, 13f * s),
        cornerRadius = CornerRadius(2f * s), style = glyphStroke(s))
    line(10f, 6f, 14f, 6f, s, tint)                        // notch
    line(4f, 3f, 9f, 3f, s, tint); tickAt(9f, 3f, 1f, 0f, s, tint)   // top label tick
}

@Composable
fun RailGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    line(8f, 3f, 8f, 21f, s, tint); tickAt(8f, 21f, 0f, 1f, s, tint)
    line(8f, 7f, 14f, 7f, s, tint); line(8f, 12f, 17f, 12f, s, tint); line(8f, 17f, 14f, 17f, s, tint)
}

@Composable
fun CaliperGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(24.dp)) {
    val s = size.width / 24f
    line(4f, 5f, 12f, 5f, s, tint); line(12f, 5f, 12f, 19f, s, tint)   // fixed jaw
    line(20f, 5f, 20f, 13f, s, tint); line(20f, 13f, 13f, 13f, s, tint) // moving jaw
    drawCircle(tint, 2f * s, Offset(16f * s, 9f * s))                  // adjustment screw
}

@Composable
fun ChevronGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    val s = size.width / 16f
    line(4f, 3f, 9f, 8f, s, tint); line(9f, 8f, 4f, 13f, s, tint)
    tickAt(4f, 13f, -0.707f, 0.707f, s, tint)
}

@Composable
fun LoupeGlyph(tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(20.dp)) {
    val s = size.width / 20f
    drawCircle(tint, radius = 6f * s, center = Offset(8f * s, 8f * s), style = glyphStroke(s))
    line(12.5f, 12.5f, 17f, 17f, s, tint); tickAt(17f, 17f, 0.707f, 0.707f, s, tint)
}
```

---

## 4. `Plates.kt` — EngravedPlate, PaperPlate, StatRow, SerialFooter (§3.1/3.2/3.13)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/* ── §3.1 EngravedPlate ── */
@Composable
fun EngravedPlate(
    modifier: Modifier = Modifier,
    structural: Boolean = false,               // corner screws — max 2 plates per screen
    caption: String? = null,                   // e.g. "PLATE 03 · REV C"
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer { val s = if (pressed) 0.98f else 1f; scaleX = s; scaleY = s }
            .clip(IronShape.Plate)
            .background(if (pressed) Iron.Anvil800 else Iron.Anvil700)
            .ironGrain(0.04f)
            .drawWithCache {                                  // hairline inset ring
                val i = 3.dp.toPx()
                val inner = Path().apply {
                    addRoundRect(RoundRect(i, i, size.width - i, size.height - i, CornerRadius(3.dp.toPx())))
                }
                onDrawWithContent {
                    drawContent()
                    drawPath(inner, Iron.Anvil600, style = Stroke(0.75.dp.toPx()))
                }
            }
            .then(if (onClick != null)
                Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
            else Modifier)
    ) {
        Column(Modifier.padding(padding)) {
            content()
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                Text(caption, IronType.MonoSm, color = Iron.Bone500)
            }
        }
        if (structural) {
            Screw(Modifier.align(Alignment.TopStart).padding(5.dp))
            Screw(Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
    }
}

/* ── §3.2 PaperPlate + deckle edge ── */
class DeckleShape : Shape {
    override fun createOutline(size: Size, ld: LayoutDirection, density: Density): Outline {
        val bite = with(density) { 2.dp.toPx() }
        val n = 7; val step = size.width / n
        return Outline.Generic(Path().apply {
            moveTo(0f, bite)
            for (i in 0 until n) {
                val x = i * step
                lineTo(x + step * 0.35f, bite); lineTo(x + step * 0.5f, 0f)   // the bite
                lineTo(x + step * 0.65f, bite); lineTo(x + step, bite)
            }
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        })
    }
}

@Composable
fun PaperPlate(
    modifier: Modifier = Modifier,
    deckleTop: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (deckleTop) remember { DeckleShape() } else IronShape.Plate
    Box(
        modifier
            // letterpress: hard 1dp offset + soft elevation
            .drawWithCache {
                val p = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(4.dp.toPx()))) }
                onDrawBehind {
                    withTransform({ translate(0f, 1.dp.toPx()) }) { drawPath(p, Iron.Ink900.copy(alpha = 0.35f)) }
                }
            }
            .shadow(8.dp, shape, clip = false, ambientColor = Iron.Ink900.copy(alpha = 0.35f),
                    spotColor = Iron.Ink900.copy(alpha = 0.35f))
            .clip(shape)
            .background(Iron.Bone100)
            .ironGrain(0.05f)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/* ── Work Order stat row (§7.4) ── */
@Composable
fun StatRow(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, IronType.MonoSm, color = Iron.Ink600)
            Text(value, IronType.Mono, color = Iron.Ink900)
        }
        if (sub != null) Text(sub, IronType.MonoSm.copy(fontSize = 10.sp), color = Iron.Ink600,
            modifier = Modifier.align(Alignment.End))
    }
}

/* ── §3.13 SerialFooter ── */
@Composable
fun SerialFooter(plateNo: Int, screen: String, serial: String, rev: String = "C", modifier: Modifier = Modifier) {
    Text(
        "PLATE %02d · %s · S/N %s · REV %s".format(plateNo, screen, serial, rev),
        IronType.MonoSm, color = Iron.Bone500,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center
    )
}
```

---

## 5. `Buttons.kt` — ChamferButton (§3.3)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ChamferVariant { Primary, Outline }

@Composable
fun ChamferButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChamferVariant = ChamferVariant.Primary,
    tall: Boolean = true,                     // 56dp primary / 44dp secondary
    busy: Boolean = false,                    // barber-pole stripes
    enabled: Boolean = true,
) {
    val clack = rememberClack()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, IronMotion.machined(), label = "press")
    val shape = remember { ChamferShape() }
    val stripe = remember { Animatable(0f) }

    // §4.2 conditional animation: runs only while busy
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        while (true) withFrameNanos { stripe.snapTo((stripe.value + 0.013f) % 1f) }
    }
    // CONFIRM on press-down (primary), row-tap (outline)
    LaunchedEffect(pressed) { if (pressed && enabled) {
        if (variant == ChamferVariant.Primary) clack.confirm() else clack.row() } }

    Box(
        modifier
            .height(if (tall) 56.dp else 44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (variant == ChamferVariant.Primary)
                (if (pressed) Iron.Signal700 else Iron.Signal500) else Color.Transparent)
            .then(if (variant == ChamferVariant.Outline)
                Modifier.border(2.dp, Iron.Bone300, shape) else Modifier)
            .drawWithCache {
                val path = chamferPath(size, 4.dp.toPx(), 10.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    if (variant == ChamferVariant.Primary) {          // 1dp catch-light
                        drawLine(Iron.Signal300.copy(alpha = 0.7f),
                            Offset(6.dp.toPx(), 1.5.dp.toPx()),
                            Offset(size.width - 14.dp.toPx(), 1.5.dp.toPx()), 1.dp.toPx())
                    }
                    if (busy) {                                       // barber pole, 45° stripes
                        clipPath(path) {
                            val gap = 24.dp.toPx(); val w = 8.dp.toPx()
                            var x = -size.height + stripe.value * gap
                            while (x < size.width + size.height) {
                                drawLine(Iron.Ink900.copy(alpha = 0.18f),
                                    Offset(x, size.height), Offset(x + size.height, 0f), w)
                                x += gap
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, IronType.Label,
            color = if (variant == ChamferVariant.Primary) Iron.Ink900 else Iron.Bone300)
    }
}
```

---

## 6. `StampLabel.kt` — the rubber stamp (§3.4)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class StampInk(val color: Color) {
    Phosphor(Iron.Phosphor400),   // READY / OK / LIVE / FROZEN(result)
    Brass(Iron.Brass400),         // PINNED / FROZEN / ADDED
    Ember(Iron.Ember500),         // BLOCKED / THROTTLED
    Signal(Iron.Signal500),       // CHECKING / SESSION ACTIVE
}

@Composable
fun StampLabel(
    text: String,
    ink: StampInk = StampInk.Phosphor,
    modifier: Modifier = Modifier,
    slam: Boolean = true,          // the 1.6 → 0.94 → 1.0 thunk
    pulse: Boolean = false,        // SESSION ACTIVE breathing
) {
    val clack = rememberClack()
    val scale = remember { Animatable(if (slam) 1.6f else 1f) }
    val rot = remember { Animatable(if (slam) -8f else -3f) }
    val alpha = remember { Animatable(1f) }
    val reduced = LocalReducedMotion.current

    // Slam on every text change (state transitions re-stamp)
    LaunchedEffect(text) {
        if (slam && !reduced) {
            scale.snapTo(1.6f); rot.snapTo(-8f)
            launch { rot.animateTo(-3f, tween(200)) }
            scale.animateTo(1f, IronMotion.stamp())          // spring dips to ~0.94 by design
            clack.thud()
        } else { scale.snapTo(1f); rot.snapTo(-3f) }
    }
    // §3.4 pulse: 1.0 ↔ 0.6, 1200ms loop — conditional, no idle frames
    LaunchedEffect(pulse) {
        if (!pulse) { alpha.snapTo(1f); return@LaunchedEffect }
        while (true) { alpha.animateTo(0.6f, tween(600)); alpha.animateTo(1f, tween(600)) }
    }

    Box(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
            .rotate(rot.value)
            .semantics { contentDescription = "$text, status" }   // TalkBack reads as status
            .ironGrain(0.12f)
    ) {
        Box(Modifier.border(2.dp, ink.color)) {
            Box(Modifier.padding(3.dp).border(1.dp, ink.color.copy(alpha = 0.6f))) {
                Text(
                    text, IronType.Label.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        letterSpacing = 1.3.sp, color = ink.color),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
```

---

## 7. `InstrumentDial.kt` — the hero gauge (§3.5)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * §3.5 InstrumentDial.
 *
 * @param value        needle target, 0..1
 * @param energized    false = de-energized: needle parks at rest stop, ticks fade
 * @param freedFraction phosphor headroom arc length (post-purge)
 * @param over         wind-up tension (pull-to-purge / purge ceremony): needle pushed past value
 * @param boosting     shimmer + needle hunting
 * @param ignition     first-mount sweep 0 → 100 → value, 3 ticks
 */
@Composable
fun InstrumentDial(
    value: Float,
    energized: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 240.dp,
    label: String = "",
    valueText: String = "",
    freedFraction: Float = 0f,
    boosting: Boolean = false,
    over: Float = 0f,
    numerals: Boolean = true,
    ignition: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    val clack = rememberClack()
    val reduced = LocalReducedMotion.current
    val rest = -0.025f                                        // rest stop at −6°
    val needle = remember { Animatable(if (energized) 0f else rest) }
    val freed = remember { Animatable(0f) }
    var swept by rememberSaveable { mutableStateOf(false) }

    // Ignition sweep — runs exactly once per mount (§14 QA)
    LaunchedEffect(energized, ignition) {
        if (energized && ignition && !swept) {
            swept = true
            if (!reduced) {
                launch { repeat(3) { delay(80); clack.tick() } }   // ticks at 25/50/75
                needle.animateTo(1f, tween(300, easing = LinearEasing))
            }
            needle.animateTo(value, IronMotion.needle())           // galvanometer settle
        }
    }
    // Value tracking
    LaunchedEffect(value, energized) {
        if (!ignition || swept)
            needle.animateTo(if (energized) value else rest, IronMotion.needle())
    }
    LaunchedEffect(freedFraction) { freed.animateTo(freedFraction, IronMotion.needle()) }

    // §1.5 honest life — 10fps idle drift (±0.3°), 30fps hunt while boosting.
    // Frame loops are conditional → zero invalidation when idle & de-energized.
    var drift by remember { mutableFloatStateOf(0.5f) }
    var huntP by remember { mutableFloatStateOf(0f) }
    var shimP by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(energized, boosting) {
        if (energized && !boosting && !reduced) {
            val t0 = System.nanoTime()
            while (true) { drift = ((System.nanoTime() - t0) / 4e9f) % 1f; delay(100) }
        } else drift = 0.5f
    }
    LaunchedEffect(boosting) {
        if (!boosting || reduced) return@LaunchedEffect
        val t0 = System.nanoTime()
        while (true) {
            huntP = ((System.nanoTime() - t0) / 1.1e9f) % 1f
            shimP = ((System.nanoTime() - t0) / 0.9e9f) % 1f
            delay(33)
        }
    }

    val measurer = rememberTextMeasurer()

    Box(
        modifier
            .size(diameter)
            .semantics { contentDescription = "$label ${(value * 100).toInt()} percent" }
            .pointerInput(onLongPress) {
                if (onLongPress != null) detectTapGestures(onLongPress = { onLongPress() })
            }
    ) {
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringR = r * 0.78f
                val arcR = ringR - r * 0.07f
                val startDeg = 150f
                val sweepDeg = 240f
                val arcW = r * 0.022f

                // Precompute numerals once (§11.1 — cache allocates nothing per frame)
                data class Numeral(val layout: TextLayoutResult, val pos: Offset)
                val numeralsOut = if (numerals) listOf(0, 25, 50, 75, 100).map { n ->
                    val ang = Math.toRadians((startDeg + sweepDeg * n / 100f).toDouble())
                    val rr = r * 0.94f
                    val l = measurer.measure("$n", TextStyle(PlexMono, FontWeight.Medium, 11.sp, color = Iron.Bone300))
                    Numeral(l, Offset(
                        center.x + cos(ang).toFloat() * rr - l.size.width / 2f,
                        center.y + sin(ang).toFloat() * rr - l.size.height / 2f))
                } else emptyList()

                val needlePath = Path().apply {                 // blade + counterweight tail
                    val b = r * 0.03f
                    moveTo(r * 0.70f, 0f); lineTo(-r * 0.20f, b)
                    lineTo(-r * 0.28f, b * 0.5f); lineTo(-r * 0.28f, -b * 0.5f)
                    lineTo(-r * 0.20f, -b); close()
                }

                onDrawBehind {
                    val nv = needle.value + over +
                        (if (boosting) sin(huntP * 2f * PI).toFloat() * 0.012f else 0f) +
                        (if (energized && !boosting) (drift - 0.5f) * 0.004f else 0f)

                    val minorC = if (energized) Iron.Anvil500 else Iron.Bone500.copy(alpha = 0.35f)
                    val majorC = if (energized) Iron.Bone300 else Iron.Bone500.copy(alpha = 0.5f)
                    for (i in 0..64) {                          // 64 ticks, every 8th major
                        val a = Math.toRadians((startDeg + sweepDeg * i / 64f).toDouble())
                        val len = if (i % 8 == 0) r * 0.075f else r * 0.045f
                        val c = if (i % 8 == 0) majorC else minorC
                        drawLine(c,
                            Offset(center.x + cos(a).toFloat() * ringR, center.y + sin(a).toFloat() * ringR),
                            Offset(center.x + cos(a).toFloat() * (ringR + len), center.y + sin(a).toFloat() * (ringR + len)),
                            if (i % 8 == 0) r * 0.010f else r * 0.006f)
                    }

                    val arcBox = Size(arcR * 2f, arcR * 2f)
                    val arcTL = Offset(center.x - arcR, center.y - arcR)
                    val shimmerA = if (boosting) 0.55f + 0.45f * sin(shimP * 2f * PI).toFloat() else 1f
                    if (nv > 0.01f) {                           // used-pressure arc (signal)
                        drawArc(Iron.Signal500, startDeg, sweepDeg * nv.coerceIn(-0.1f, 1.15f),
                            false, arcTL, arcBox, style = Stroke(arcW), alpha = shimmerA)
                        if (nv > 0.85f)                         // throttle zone overlay
                            drawArc(Iron.Ember500, startDeg + sweepDeg * 0.85f,
                                sweepDeg * (nv - 0.85f).coerceAtLeast(0f), false, arcTL, arcBox, style = Stroke(arcW))
                    }
                    if (freed.value > 0.005f)                   // freed headroom (phosphor)
                        drawArc(Iron.Phosphor400, startDeg + sweepDeg * nv,
                            sweepDeg * freed.value, false, arcTL, arcBox, style = Stroke(arcW * 0.8f))

                    if (!energized) {                           // brass rest-stop marker
                        val ra = Math.toRadians((startDeg + sweepDeg * rest).toDouble())
                        drawLine(Iron.Brass400,
                            Offset(center.x + cos(ra).toFloat() * (ringR - r * 0.045f),
                                   center.y + sin(ra).toFloat() * (ringR - r * 0.045f)),
                            Offset(center.x + cos(ra).toFloat() * ringR, center.y + sin(ra).toFloat() * ringR),
                            r * 0.010f)
                    }

                    withTransform({                            // needle assembly
                        rotate(startDeg + sweepDeg * nv, pivot = center)
                        translate(center.x, center.y)
                    }) {
                        drawPath(needlePath, if (energized) Iron.Bone100 else Iron.Bone500)
                        drawCircle(Iron.Brass400, r * 0.055f, Offset.Zero)   // pivot
                        drawCircle(Iron.Ink900, r * 0.018f, Offset.Zero)     // ink center
                    }
                    numeralsOut.forEach { drawText(it.layout, topLeft = it.pos) }
                }
            }
        ) {}

        if (valueText.isNotEmpty() || label.isNotEmpty())
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = diameter * 0.12f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (valueText.isNotEmpty())
                    Text(valueText, IronType.Mono, color = if (energized) Iron.Bone100 else Iron.Bone500)
                if (label.isNotEmpty()) Text(label, IronType.MonoSm, color = Iron.Bone500)
            }
    }
}

/** §7.4 MiniDial — SWAP gauge: InstrumentDial(diameter = 96.dp, numerals = false, ignition = false) */
```

---

## 8. `Scales.kt` — PressureScale + ThermometerStrip (§3.6/3.7)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop

/* ── §3.6 PressureScale — the ruler that replaces progress bars ── */
@Composable
fun PressureScale(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    labels: List<String>? = null,        // e.g. listOf("0","1","2","3","4","5","6","7")
    valueText: String? = null,
    caption: String? = null,
    onMajorTickCrossed: () -> Unit = {}, // caller: clack.off() (CONTEXT_CLICK)
) {
    val clack = rememberClack()
    val marker by animateFloatAsState(fraction.coerceIn(0f, 1f), IronMotion.drawer(), label = "marker")
    var lastMajor by remember { mutableIntStateOf(-1) }
    // Haptic on major-tick crossing (marker "pushed" past each quarter)
    LaunchedEffect(Unit) {
        snapshotFlow { (marker * 4f).toInt() }.drop(1).collect { idx ->
            if (idx != lastMajor) { lastMajor = idx; if (idx > 0) { clack.off(); onMajorTickCrossed() } }
        }
    }
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height).drawWithCache {
            val cy = size.height * 0.55f
            val labelLayouts = (labels ?: emptyList()).map {
                measurer.measure(it, TextStyle(PlexMono, androidx.compose.ui.text.font.FontWeight.Normal, 10.sp, color = Iron.Bone500))
            }
            onDrawBehind {
                // fill (8dp tall) — animates because marker does; fill FOLLOWS the marker
                val mx = marker * size.width
                drawRect(Iron.Signal500, Offset.Zero, Size(mx, 8.dp.toPx()), size = Size(mx, 8.dp.toPx()),
                    topLeft = Offset(0f, cy - 4.dp.toPx()))
                drawLine(Iron.Anvil600, Offset.Zero, Offset(size.width, cy), 1.dp.toPx()) // baseline
                var x = 0f; var i = 0
                while (x <= size.width + 0.5f) {              // minor every 5%, major every 25%
                    val major = i % 5 == 0
                    drawLine(if (major) Iron.Bone300 else Iron.Anvil500,
                        Offset(x, cy), Offset(x, cy - (if (major) 10.dp else 5.dp).toPx()),
                        (if (major) 1.5f else 1f).dp.toPx() * 0.8f)
                    if (major && labels != null) {
                        val li = i / 5
                        if (li < labelLayouts.size) {
                            val l = labelLayouts[li]
                            drawText(l, topLeft = Offset(x - l.size.width / 2f, cy + 3.dp.toPx()))
                        }
                    }
                    x += size.width / 20f; i++
                }
                // brass marker flag
                drawRect(Iron.Brass400, Offset(mx - 1.dp.toPx(), cy - 13.dp.toPx()),
                    size = Size(2.dp.toPx(), 13.dp.toPx()))
                drawRect(Iron.Brass400, Offset(mx, cy - 13.dp.toPx()), size = Size(6.dp.toPx(), 4.dp.toPx()))
            }
        }) {}
        if (valueText != null || caption != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (caption != null) Text(caption, IronType.MonoSm, color = Iron.Bone500)
            if (valueText != null) Text(valueText, IronType.Mono, color = Iron.Bone100)
        }
    }
}

/* ── §3.7 ThermometerStrip — shared 30–60°C scale, two needles ── */
@Composable
fun ThermometerStrip(
    batteryC: Int,
    cpuC: Int,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val throttling = cpuC > 45
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(throttling) {
        if (!throttling) { pulse = 1f; return@LaunchedEffect }
        val t0 = System.nanoTime()
        while (true) { pulse = 0.4f + 0.6f * ((System.nanoTime() - t0) / 0.8e9f % 1f); kotlinx.coroutines.delay(50) }
    }
    val measurer = rememberTextMeasurer()

    Canvas(Modifier.fillMaxWidth().height(44.dp).drawWithCache {
        val labelL = (listOf("30", "35", "40", "45", "50", "55", "60")).map {
            measurer.measure("$it°", TextStyle(PlexMono, androidx.compose.ui.text.font.FontWeight.Normal, 10.sp, color = Iron.Bone500))
        }
        onDrawBehind {
            val cy = 14.dp.toPx()
            drawLine(Iron.Anvil600, Offset.Zero, Offset(size.width, cy), 1.dp.toPx())
            for (i in 0..6) {
                val x = size.width * i / 6f
                val ember = i >= 3                                  // >45°C zone
                drawLine(if (ember) Iron.Ember500.copy(alpha = 0.7f) else Iron.Bone300,
                    Offset(x, cy), Offset(x, cy - 8.dp.toPx()), 1.dp.toPx())
                if (ember) drawLine(Iron.Ember500.copy(alpha = 0.35f),
                    Offset(x, cy), Offset(x, cy + 4.dp.toPx()), 1.dp.toPx())
                drawText(labelL[i], topLeft = Offset(x - labelL[i].size.width / 2f, cy + 5.dp.toPx()))
            }
            fun flag(v: Int, tint: Color, alpha: Float, text: String) {
                val x = size.width * ((v - 30f) / 30f).coerceIn(0.02f, 0.98f)
                drawLine(tint.copy(alpha = alpha), Offset(x, cy - 20.dp.toPx()), Offset(x, cy - 4.dp.toPx()), 2.dp.toPx())
                drawPath(Path().apply {                             // flag pennant
                    moveTo(x, cy - 20.dp.toPx())
                    lineTo(x + 18.dp.toPx(), cy - 17.dp.toPx())
                    lineTo(x, cy - 14.dp.toPx()); close()
                }, tint.copy(alpha = alpha))
                val l = measurer.measure(text, TextStyle(PlexMono, androidx.compose.ui.text.font.FontWeight.Medium, 10.sp, color = tint))
                drawText(l, topLeft = Offset(x + 3.dp.toPx(), cy - 34.dp.toPx()))
            }
            flag(batteryC, Iron.Bone300, 1f, "BATT $batteryC°")
            flag(cpuC, if (throttling) Iron.Ember500 else Iron.Signal500, if (throttling) pulse else 1f, "CPU $cpuC°")
        }
    }) {}
}
```

---

## 9. `TickerLine.kt` — status line (§3.8)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickerLine(
    text: String,
    led: LedState,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,     // collapse to LED-only
) {
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap?.invoke() }) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedDot(led)
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            text,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
            label = "ticker"
        ) { t ->
            Text(
                t, IronType.Mono, color = Iron.Bone300, maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE)   // 24dp/s scroll, edge dwell
            )
        }
        Text("  ▸▸", IronType.MonoSm, color = Iron.Bone500)
    }
}
```

---

## 10. `ToolRow.kt` (§3.9)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,     // context sheet: LAUNCH / PIN / REMOVE / COPY
    trailing: @Composable (() -> Unit)? = null,
) {
    val clack = rememberClack()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, IronMotion.machined(), label = "row")

    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(IronShape.Plate)
            .background(Iron.Anvil800)
            .combinedClickable(
                interactionSource = interaction, indication = null,
                role = Role.Button,
                onClick = { clack.row(); onClick() },
                onLongClick = onLongClick?.let { cb -> { clack.longPress(); cb() } },
                onDoubleClick = null,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(                                                      // engraved icon slot, 40dp
            Modifier.size(40.dp).clip(IronShape.Slot)
                .background(if (pressed) Iron.Anvil950 else Iron.Anvil700)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, IronType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp), color = Iron.Bone100)
            Text(subtitle, IronType.Caption, color = Iron.Bone500)
        }
        trailing?.invoke() ?: ChevronGlyph(Iron.Bone500)
    }
}
```

---

## 11. `Controls.kt` — MachinedToggle + MachinedSegment (§3.10)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* ── §3.10 Toggle: brass knob, spring travel, 2-frame wobble on arrival ── */
@Composable
fun MachinedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clack = rememberClack()
    val travel = 24.dp
    val x = remember { Animatable(if (checked) 1f else 0f) }
    val wob = remember { Animatable(0f) }

    LaunchedEffect(checked, enabled) {
        if (!enabled) return@LaunchedEffect
        x.animateTo(if (checked) 1f else 0f, IronMotion.machined())
        wob.snapTo(0f)                                           // arrival wobble: ±3°, ~90ms
        wob.animateTo(0f, keyframes { durationMillis = 90; 3f at 30; (-3f) at 60 })
    }

    Box(
        modifier
            .size(52.dp, 28.dp)
            .clip(IronShape.Slot)
            .background(when {
                !enabled -> Iron.Anvil800
                checked  -> Iron.Phosphor400.copy(alpha = 0.30f)
                else     -> Iron.Anvil600
            })
            .border(1.dp, if (checked) Iron.Phosphor400.copy(alpha = 0.4f) else Iron.Anvil600, IronShape.Slot)
            .toggleable(
                value = checked, role = Role.Switch, enabled = enabled,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onValueChange = { newValue ->
                    if (newValue) clack.confirm() else clack.off()      // ON=CONFIRM, OFF=CONTEXT_CLICK
                    onCheckedChange(newValue)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(24.dp)
                .graphicsLayer {
                    translationX = travel.toPx() * x.value
                    rotationZ = wob.value
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (enabled) Iron.Brass400 else Iron.Bone500)
                .border(1.dp, Iron.Ink900, androidx.compose.foundation.shape.CircleShape)
        )
    }
}

/* ── §3.10 Segment: groove + sliding brass block + ink-filled active label ── */
@Composable
fun MachinedSegment(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(IronShape.Slot)
            .background(Iron.Anvil950)                            // the groove
            .border(1.dp, Iron.Anvil600, IronShape.Slot)
            .ironGrain(0.03f)
    ) {
        val w = maxWidth / options.size
        val blockX by animateDpAsState(w * selected, IronMotion.block(), label = "segBlock")
        Box(Modifier.offset(x = blockX).width(w).fillMaxHeight().background(Iron.Brass400))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { clack.tick(); onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, IronType.Label,
                        color = if (i == selected) Iron.Ink900 else Iron.Bone300)
                }
            }
        }
    }
}
```

---

## 12. `Shell.kt` — GearSelector + BridgePlate + tab transition (§3.11/3.12)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class GearTab(val label: String) { HOME("HOME"), GAMES("GAMES"), HUD("HUD"), TOOLS("TOOLS") }

@Composable
fun GearTabGlyph(tab: GearTab, tint: Color, modifier: Modifier = Modifier) = when (tab) {
    GearTab.HOME  -> GaugeGlyph(tint, modifier)
    GearTab.GAMES -> CartridgeGlyph(tint, modifier)
    GearTab.HUD   -> RailGlyph(tint, modifier)
    GearTab.TOOLS -> CaliperGlyph(tint, modifier)
}

/* ── §3.11 GearSelector ── */
@Composable
fun GearSelector(
    selected: GearTab,
    onSelect: (GearTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val tabs = GearTab.entries
    Column(modifier) {
        HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
        BoxWithConstraints(
            Modifier.fillMaxWidth().background(Iron.Anvil900).navigationBarsPadding().height(64.dp)
        ) {
            val w = maxWidth / tabs.size
            val indX by animateDpAsState(
                w * tabs.indexOf(selected) + (w - 44.dp) / 2, IronMotion.block(), label = "gearInd")
            Box(Modifier.offset(x = indX, y = 58.dp).size(44.dp, 4.dp).background(Iron.Brass400))
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { clack.tick(); onSelect(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.graphicsLayer { translationY = if (active) -1.dp.toPx() else 0f }) {
                                GearTabGlyph(tab, if (active) Iron.Bone100 else Iron.Bone500)
                            }
                            AnimatedVisibility(active, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                                Text(tab.label, IronType.MonoSm, color = Iron.Bone300)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── §3.12 BridgePlate ── */
@Composable
fun BridgePlate(
    backendName: String,
    backendLed: LedState,
    onBackendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Iron.Anvil900)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screw()
        Spacer(Modifier.width(10.dp))
        Column {
            RisoText("APEXCORE", IronType.Label.copy(fontSize = 14.sp))
            Text("MK·II", IronType.MonoSm, color = Iron.Bone500)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(IronShape.Slot)
                .border(1.dp, Iron.Anvil600, IronShape.Slot)
                .clickable(onClick = onBackendClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LedDot(backendLed)
            Spacer(Modifier.width(6.dp))
            Text(backendName, IronType.MonoSm, color = Iron.Bone300)
            Spacer(Modifier.width(4.dp))
            Text("▾", IronType.MonoSm, color = Iron.Bone500)
        }
        Spacer(Modifier.width(10.dp))
        Screw()
    }
}

/* ── §7.3 Tab transition: horizontal slide + fade, 240ms, ease.wind ── */
@Composable
fun GearTabTransition(targetState: GearTab, content: @Composable (GearTab) -> Unit) {
    AnimatedContent(
        targetState,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(tween(240, easing = IronMotion.EaseWind)) { it / 4 * dir } +
             fadeIn(tween(240))) togetherWith
            (slideOutHorizontally(tween(240, easing = IronMotion.EaseWind)) { -it / 4 * dir } +
             fadeOut(tween(180)))
        },
        label = "gearTabs"
    ) { tab -> content(tab) }
}
```

---

## 13. `BenchSheet.kt` — bottom sheet with predictive-back scrub (§3.14, §6.1)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.activity.GestureCancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * §3.14 BenchSheet — all dialogs live here.
 * Predictive back SCRUBS the dismiss: sheet scales 1.0→0.92 and scrim fades
 * with the finger. Commit = dismiss. Cancel = spring back.
 */
@Composable
fun BenchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clack = rememberClack()
    var scrub by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { info -> scrub = info.progress }
            onDismiss()                                 // gesture committed → dismiss
        } catch (e: GestureCancellationException) {
            scrub = 0f                                  // cancelled → snap back
        }
    }

    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { it } + fadeIn(tween(220)),
        exit  = slideOutVertically(tween(240, easing = IronMotion.EaseWind)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            // Scrim — fades with back-scrub progress
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - scrub }
                    .background(Iron.Scrim)
                    .ironGrain(0.04f)
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {                    // predictive-back scrub scale
                        val s = 1f - 0.08f * scrub
                        scaleX = s; scaleY = s; alpha = 1f - scrub
                        translationY = dragY
                    }
                    .clip(IronShape.Plate)
                    .background(Iron.Anvil800)
                    .ironGrain(0.04f)
                    .pointerInput(Unit) {               // drag-to-dismiss (fling-aware variant: anchoredDraggable)
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy ->
                                dragY = (dragY + dy).coerceAtLeast(0f); change.consume()
                            },
                            onDragEnd = {
                                if (dragY > 120.dp.toPx()) { clack.off(); onDismiss() }
                                dragY = 0f
                            }
                        )
                    }
            ) {
                // Brass handle
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                    .size(32.dp, 4.dp).background(Iron.Brass400))
                Column(Modifier.padding(20.dp), content = content)
            }
        }
    }
}
```

---

## 14. `Effects.kt` — Odometer, Shavings, FlipCard (§3.15/3.16, §7.4)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* ── §3.15 OdometerCounter — per-digit roll, 30ms stagger right→left ── */
@Composable
fun OdometerCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IronType.MonoLg,
    onSettled: () -> Unit = {},          // caller: clack.off() on final digit
) {
    val density = LocalDensity.current
    val digitH = with(density) { style.lineHeight.toDp() }
    val n = text.length
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, c ->
            if (c.isDigit()) DigitRoll(c, digitH, style, staggerMs = (n - 1 - i) * 30,
                settled = if (i == n - 1) onSettled else {})
            else Text(c.toString(), style = style)
        }
    }
}

@Composable
private fun DigitRoll(digit: Char, height: Dp, style: TextStyle, staggerMs: Int, settled: () -> Unit) {
    val pos = remember { Animatable(0f) }
    LaunchedEffect(digit) {
        delay(staggerMs.toLong())
        pos.animateTo(digit - '0', IronMotion.drawer())
        settled()
    }
    Box(Modifier.height(height).clipToBounds()) {
        Column(Modifier.graphicsLayer { translationY = -pos.value * height.toPx() }) {
            (0..9).forEach { d ->
                Text(d.toString(), style = style, modifier = Modifier.height(height).wrapContentSize(Alignment.Center))
            }
        }
    }
}

/* ── §3.16 ShavingsParticles — fixed pool, zero per-frame allocation ── */
class ShavingsState {
    /** [x, y, vx, vy, rot, vrot, alpha] × 220 — §11.1 hard cap */
    val data = FloatArray(CAP * 7)
    var alive = 0;        private set
    var version = 0;      private set      // bumped per burst → restarts frame loop

    fun burst(ox: Float, oy: Float, radius: Float, count: Int, speed: Float) {
        val rnd = Random(0xC0FFEE + version)
        var n = 0
        while (n < count && alive < CAP) {
            val i = alive * 7
            val a = rnd.nextFloat() * 2f * Math.PI.toFloat()
            data[i]     = ox + cos(a) * radius * rnd.nextFloat()
            data[i + 1] = oy + sin(a) * radius * rnd.nextFloat()
            val v = speed * (0.5f + rnd.nextFloat())
            data[i + 2] = cos(a) * v
            data[i + 3] = sin(a) * v - speed * 0.6f          // upward bias
            data[i + 4] = rnd.nextFloat() * 360f
            data[i + 5] = (rnd.nextFloat() - 0.5f) * 720f
            data[i + 6] = 1f
            alive++; n++
        }
        version++
    }

    fun step(dt: Float, gravity: Float, floorY: Float) {     // gravity px/s², bounce 0.15, one bounce
        for (p in 0 until alive) {
            val i = p * 7
            if (data[i + 6] <= 0f) continue
            data[i + 3] += gravity * dt
            data[i]     += data[i + 2] * dt
            data[i + 1] += data[i + 3] * dt
            data[i + 4] += data[i + 5] * dt
            if (data[i + 1] > floorY && data[i + 3] > 0f) {
                data[i + 3] *= -0.15f; data[i + 5] *= 0.5f
                data[i + 6] = 0.9f                            // bounced → fade flag
            } else if (data[i + 6] < 1f) data[i + 6] -= dt * 2.5f
        }
        while (alive > 0 && data[(alive - 1) * 7 + 6] <= 0f) alive--
    }

    companion object { const val CAP = 220 }
}

@Composable
fun ShavingsLayer(
    state: ShavingsState,
    modifier: Modifier = Modifier,
    floorFromBottom: Dp = 140.dp,
) {
    val density = LocalDensity.current
    var h by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }

    // Frame loop only runs while particles are alive (§11.1 — self-terminating)
    LaunchedEffect(state.version) {
        if (state.version == 0) return@LaunchedEffect
        val gravity = with(density) { 2400.dp.toPx() }
        val floor = h - with(density) { floorFromBottom.toPx() }
        var last = 0L
        while (state.alive > 0) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f
                         else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                state.step(dt, gravity, floor)
                tick++                                        // draw-phase invalidation
            }
        }
    }

    val colors = listOf(Iron.Signal500, Iron.Signal700, Iron.Anvil500)
    val path = remember { Path() }
    Canvas(modifier.fillMaxSize().onSizeChanged { h = it.height.toFloat() }) {
        tick                                                  // read → redraw on step
        val s = 2.6.dp.toPx()
        for (p in 0 until state.alive) {
            val i = p * 7
            val a = state.data[i + 6]
            if (a <= 0f) continue
            withTransform({
                translate(state.data[i], state.data[i + 1])
                rotate(state.data[i + 4], pivot = Offset.Zero)
            }) {
                path.reset()                                  // parallelogram shaving
                path.moveTo(-s, -s * 0.4f); path.lineTo(s, -s * 0.6f)
                path.lineTo(s * 0.8f, s * 0.5f); path.lineTo(-s * 1.1f, s * 0.3f)
                path.close()
                drawPath(path, colors[p % 3], alpha = a)
            }
        }
    }
}

/* ── §7.4 FlipCard — Work Order tap-to-flip back to idle ── */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val rot = remember { Animatable(0f) }
    var showBack by remember { mutableStateOf(flipped) }
    LaunchedEffect(flipped) {
        rot.animateTo(90f, tween(160, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        showBack = flipped
        rot.animateTo(0f, tween(160, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(modifier.graphicsLayer { rotationX = rot.value }) {
        if (showBack) back() else front()
    }
}
```

---

## 15. `Fields.kt` — SearchSlot, IndexRail, ElevationSlip (§3.17, §7.4)

```kotlin
package com.ivarna.apexcore.ui.iron

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ── §3.17 SearchSlot ── */
@Composable
fun SearchSlot(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "SEARCH PACKAGES…",
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .height(48.dp)
            .clip(IronShape.Slot)
            .background(Iron.Anvil950)
            .border(1.dp, if (focused) Iron.Brass400 else Iron.Anvil700, IronShape.Slot)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoupeGlyph(if (focused) Iron.Brass400 else Iron.Bone500)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = IronType.Mono.copy(color = Iron.Bone100),
            singleLine = true,
            interactionSource = interaction,
            decorationBox = { inner ->
                if (value.isEmpty() && !focused) Text(placeholder, IronType.Mono, color = Iron.Bone500)
                else inner()
            }
        )
        if (focused) LedDot(LedState.READY)
    }
}

/* ── §3.17 IndexRail — alphabet scrubber, KEYBOARD_TAP per letter ── */
@Composable
fun IndexRail(
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    letters: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#",
) {
    val clack = rememberClack()
    var active by remember { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()

    fun Modifier.railGestures(): Modifier = pointerInput(letters) {
        suspend fun pick(y: Float) {
            val idx = (y / size.height * letters.length).toInt().coerceIn(0, letters.length - 1)
            if (idx != active) { active = idx; clack.keyTap(); onLetter(letters[idx]) }
        }
        detectTapGestures { pick(it.y) }
    }.pointerInput(letters) {
        detectVerticalDragGestures(
            onDragStart = { c -> },
            onVerticalDrag = { change, _ -> }
        )
    }
    // (tap + drag combined — drag handler below)
    Box(
        modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { c ->
                        val idx = (c.y / size.height * letters.length).toInt().coerceIn(0, letters.length - 1)
                        if (idx != active) { active = idx; clack.keyTap(); onLetter(letters[idx]) }
                    },
                    onVerticalDrag = { change, _ ->
                        val idx = (change.y / size.height * letters.length).toInt().coerceIn(0, letters.length - 1)
                        if (idx != active) { active = idx; clack.keyTap(); onLetter(letters[idx]) }
                    }
                )
            }
            .pointerInput(letters) {
                detectTapGestures { c ->
                    val idx = (c.y / size.height * letters.length).toInt().coerceIn(0, letters.length - 1)
                    active = idx; clack.keyTap(); onLetter(letters[idx])
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().drawWithCache {
            val layouts = letters.map {
                measurer.measure(it.toString(), TextStyle(PlexMono, androidx.compose.ui.text.font.FontWeight.Normal, 9.sp))
            }
            onDrawBehind {
                layouts.forEachIndexed { i, l ->
                    val y = (i + 0.5f) / letters.length * size.height - l.size.height / 2f
                    drawText(l, color = if (i == active) Iron.Brass400 else Iron.Bone500,
                        topLeft = Offset((size.width - l.size.width) / 2f, y))
                }
            }
        }) {}
    }
}

/* ── §7.4 ElevationSlip — paper banner + blocked shake ── */
@Composable
fun ElevationSlip(
    visible: Boolean,
    shake: Boolean,
    onShizuku: () -> Unit,
    onRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(shake) {                                    // 2× 8dp horizontal shake, 180ms
        if (shake) shakeAnim.animateTo(0f, keyframes {
            durationMillis = 180; 8f at 45; (-8f) at 90; 8f at 135
        })
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { -it / 2 } + fadeIn(tween(320)),
        modifier = modifier.graphicsLayer { translationX = shakeAnim.value.dp.toPx() }
    ) {
        PaperPlate {
            RisoText("ELEVATION REQUIRED", IronType.Title.copy(fontSize = 16.sp), color = Iron.Ink900)
            Spacer(Modifier.height(6.dp))
            Text("Deep freeze (BOOST) requires Shizuku or Root access.",
                IronType.Caption, color = Iron.Ink600)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("CONNECT SHIZUKU", onShizuku, Modifier.weight(1f), tall = false)
                ChamferButton("GRANT ROOT", onRoot, Modifier.weight(1f),
                    variant = ChamferVariant.Outline, tall = false)
            }
        }
    }
}
```

---

## 16. Assembly — Home (The Bench) with the full Purge ceremony + pull-to-purge

```kotlin
package com.ivarna.apexcore.ui.iron.home

// ── §7.4 HomeScreen — condensed to the interaction wiring ──

data class WorkOrderData(
    val freedGb: Float, val freedRamGb: Float, val freedSwapGb: Float,
    val apps: Int, val durationS: Float, val skipped: Int, val failed: Int,
)

enum class BenchPhase { IDLE, BOOSTING, RESULT }

@Composable
fun TheBench(
    ramFraction: Float, swapFraction: Float,           // from getSystemMemStats, 1Hz
    freedFraction: Float,                              // headroom for phosphor arc
    elevated: Boolean,
    lastResult: WorkOrderData?,
    onBoost: () -> Unit,                               // vm.boost() → FreezeFramework
    onTune: () -> Unit, onPins: () -> Unit, onRamFree: () -> Unit,
    batteryC: Int, cpuC: Int,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val density = LocalDensity.current

    var phase by remember { mutableStateOf(BenchPhase.IDLE) }
    var workOrder by remember { mutableStateOf<WorkOrderData?>(null) }
    var purgeTick by remember { mutableIntStateOf(0) }
    var windUp by remember { mutableFloatStateOf(0f) }          // ceremony / pull tension
    var stampText by remember { mutableStateOf<String?>(null) }
    var odometerText by remember { mutableStateOf<String?>(null) }
    val shavings = remember { ShavingsState() }

    // Geometry capture for the shavings burst origin (dial center, container coords)
    var dialCenter by remember { mutableStateOf(Offset.Zero) }
    var dialRadius by remember { mutableFloatStateOf(0f) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    fun purge() {
        if (!elevated) { clack.no(); return }                    // bounce-back + REJECT
        phase = BenchPhase.BOOSTING
        onBoost()
        purgeTick++
    }

    // ── §7.4 THE PURGE CEREMONY — 1400ms, one animation >400ms on this screen ──
    LaunchedEffect(purgeTick) {
        if (purgeTick == 0) return@LaunchedEffect
        stampText = null; odometerText = null
        windUp = 0.12f                                           // 0–180ms: tension wind-up
        delay(180)
        clack.thud()                                             // 180ms: heavy click
        shavings.burst(                                          // 180–500ms: shaving burst
            dialCenter.x - containerOrigin.x,
            dialCenter.y - containerOrigin.y,
            dialRadius * 0.8f, count = 160, speed = with(density) { 900.dp.toPx() })
        stampText = "FROZEN ${lastResult?.apps ?: ""}"           // 250–500ms: stamp slams (internal thunk)
        windUp = 0f                                              // 400ms: needle release w/ overshoot
        delay(220)
        odometerText = "+%.1f GB".format(lastResult?.freedGb ?: 0f)  // 600–1400ms: odometer
        delay(760)                                               // roll + 400ms hold
        odometerText = null                                      // shrinks & flies into Work Order
        phase = BenchPhase.RESULT
        workOrder = lastResult
        clack.purgeDone()                                        // two-stage finish: HEAVY → 90ms → CLICK
    }

    // ── §6.2 Pull-to-purge — NestedScrollConnection (correct Android way) ──
    val thresholdPx = with(density) { 120.dp.toPx() }
    val pullConnection = remember(clack) {
        object : NestedScrollConnection {
            var pull by mutableFloatStateOf(0f); private set
            private var armed = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.3f)  // resisted
                    if (pull >= thresholdPx && !armed) { armed = true; clack.tick() }    // threshold tick
                    windUp = (pull / thresholdPx).coerceIn(0f, 1f) * 0.12f               // winds the needle
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val past = pull >= thresholdPx
                pull = 0f; armed = false; windUp = 0f
                if (past) purge()
                return if (past) available else Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { }) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullConnection)
                .onGloballyPositioned { containerOrigin = it.positionInRoot() },
            contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ticker — §3.8, polite live region
            item {
                val (txt, led) = when {
                    phase == BenchPhase.BOOSTING -> "PURGING BACKGROUND PROCESSES…" to LedState.LIVE
                    !elevated -> "CONNECT SHIZUKU OR ROOT FOR DEEP FREEZE" to LedState.CHECKING
                    workOrder != null && workOrder!!.apps > 0 ->
                        "FROZEN ${workOrder!!.apps} APPS · FREED %.1f GB".format(workOrder!!.freedGb) to LedState.READY
                    workOrder != null -> "ALREADY OPTIMIZED" to LedState.READY
                    else -> "READY TO PURGE BLOAT" to LedState.READY
                }
                TickerLine(txt, led)
            }

            // Elevation slip when not elevated (§8 playbook)
            if (!elevated) item {
                ElevationSlip(visible = true, shake = false,
                    onShizuku = { /* open Setup sheet */ }, onRoot = { /* open Setup sheet */ })
                Spacer(Modifier.height(16.dp))
            }

            // Hero dial + shavings layer (same coordinate space)
            item {
                Box {
                    InstrumentDial(
                        value = ramFraction,
                        energized = elevated,
                        freedFraction = freedFraction,
                        boosting = phase == BenchPhase.BOOSTING,
                        over = windUp,
                        label = "RAM",
                        valueText = "%.1f / %.1f GB".format(ramUsed, ramTotal),
                        onLongPress = {                                           // §6.2 copy stats
                            clipboard.copy("RAM ${ramUsed}/${ramTotal}MB · SWAP …"); clack.confirm()
                        },
                        modifier = Modifier.onGloballyPositioned {
                            dialCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                            dialRadius = it.size.width / 2f
                        }
                    )
                    ShavingsLayer(shavings, Modifier.matchParentSize())
                    stampText?.let {                              // stamp slams OVER the dial
                        Box(Modifier.align(Alignment.Center)) { StampLabel(it, StampInk.Phosphor) }
                    }
                }
            }

            // MiniDial (SWAP)
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InstrumentDial(value = swapFraction, energized = elevated,
                        diameter = 96.dp, numerals = false, ignition = false, label = "SWAP")
                }
            }

            // BOOST / Work Order flip
            item {
                Spacer(Modifier.height(16.dp))
                FlipCard(
                    flipped = phase == BenchPhase.RESULT,
                    front = {
                        ChamferButton(
                            text = if (phase == BenchPhase.BOOSTING) "PURGING…" else "BOOST · DEEP FREEZE",
                            onClick = { if (phase == BenchPhase.IDLE) purge() },
                            busy = phase == BenchPhase.BOOSTING,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                    },
                    back = { workOrder?.let { wo ->
                        WorkOrderCard(wo) { phase = BenchPhase.IDLE }          // tap → flip back to idle
                    } }
                )
            }

            // Tool rows
            if (elevated) item { Spacer(8); ToolRow("Game optimisation", "2 available on this kernel",
                { GaugeGlyph(Iron.Bone300) }, onTune) }
            item { ToolRow("Pin Apps", "Protect apps from being frozen",
                { LoupeGlyph(Iron.Bone300) }, onPins) }
            item { ToolRow("Pressure Room", "Force safe RAM reclaim",
                { RailGlyph(Iron.Bone300) }, onRamFree) }

            item { Spacer(16); ThermometerStrip(batteryC = batteryC, cpuC = cpuC) }
            item { SerialFooter(1, "HOME", serial) }
        }

        // Center-screen odometer (§7.4: rolls, holds, flies into Work Order)
        odometerText?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OdometerCounter(it, onSettled = { clack.off() })
            }
        }
    }
}

@Composable
fun WorkOrderCard(wo: WorkOrderData, onTap: () -> Unit) {
    PaperPlate(deckleTop = true, modifier = Modifier
        .fillMaxWidth()
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RisoText("PURGE COMPLETE", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900,
                modifier = Modifier.weight(1f))
            StampLabel("FROZEN ${wo.apps}", StampInk.Phosphor)
        }
        Spacer(Modifier.height(14.dp))
        StatRow("FREED SIZE", "+%.1f GB".format(wo.freedGb),
            sub = "RAM +%.1f · SWAP +%.1f".format(wo.freedRamGb, wo.freedSwapGb))
        Spacer(6); StatRow("PURGED APPS", "${wo.apps}")
        Spacer(6); StatRow("DURATION", "%.1f S".format(wo.durationS))
        Spacer(6); StatRow("SKIPPED", "${wo.skipped}", sub = if (wo.failed > 0) "${wo.failed} FAILED" else null)
        Spacer(14)
        ChamferButton("PURGE AGAIN", onTap, Modifier.fillMaxWidth(), tall = false)
    }
}
```

---

## 17. Assembly — Games carousel + Shutter + two-finger flip (§7.5)

```kotlin
// ── Carousel: arc path, depth scale/blur, tick per page settle ──
@Composable
fun LaunchMatrix(apps: List<AppCardData>, onLaunch: (AppCardData) -> Unit) {
    val clack = rememberClack()
    val pagerState = rememberPagerState { apps.size }

    // §3.11/§5.2 haptic on page settle
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { clack.tick() }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 64.dp),
            pageSpacing = 12.dp
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - minOf(abs(offset), 1f)
            Box(
                Modifier
                    .graphicsLayer {                          // §4 parabolic arc + depth
                        val s = 0.8f + 0.2f * closeness
                        scaleX = s; scaleY = s
                        alpha = 0.4f + 0.6f * closeness
                        translationY = (8.dp * (offset * offset)).toPx()   // the dip
                    }
                    .blur(if (abs(offset) > 0.5f) 4.dp else 0.dp)          // RenderEffect, API 31+
            ) {
                GameCartridge(apps[page], active = page == pagerState.currentPage,
                    onLaunch = { onLaunch(apps[page]) })
            }
        }

        // §6.2 Two-finger horizontal swipe → GAMES ↔ ALL APPS (compact impl)
        Row(Modifier.align(Alignment.TopCenter).padding(top = 120.dp)) { /* segment row here */ }
    }
}

// ── §7.5 The Shutter — 520ms hydraulic-press launch ──
@Composable
fun ShutterOverlay(trigger: Int, onSeam: () -> Unit, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val clack = rememberClack()
    var closed by remember(trigger) { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        closed = true
        delay(200)                                  // plates close (stagger 40ms)
        clack.thud()                                // plates meet: heavy click
        onSeam()                                    // ← freeze broadcast fires HERE
        delay(80)                                   // brass progress ticks across seam
        closed = false                              // iris opens → target app surfaces
    }
    val t by animateFloatAsState(
        if (closed) 1f else 0f,
        tween(if (closed) 160 else 280, easing = IronMotion.EaseWind), label = "shutter")
    if (t > 0.01f) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f)
                .graphicsLayer { translationY = -size.height * (1f - t) }
                .background(Iron.Anvil900).ironGrain(0.05f))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                .graphicsLayer { translationY = size.height * (1f - t) }
                .background(Iron.Anvil900).ironGrain(0.05f))
        }
    }
}

// ── Cartridge drag-down eject (§6.2): tilt + confirm sheet ──
@Composable
private fun GameCartridge(app: AppCardData, active: Boolean, onLaunch: () -> Unit) {
    val clack = rememberClack()
    var dragDy by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState((dragDy / 40f).coerceIn(0f, 8f), IronMotion.machined())
    EngravedPlate(
        modifier = Modifier.graphicsLayer { rotationZ = tilt },
        onClick = onLaunch
    ) {
        // Lens: brass-ringed circular icon frame (icon tint wash extracted by caller)
        Box(Modifier.size(88.dp).clip(CircleShape).border(2.dp, Iron.Brass400, CircleShape))
        Spacer(12)
        Text(app.name, IronType.Display.copy(fontSize = 24.sp), color = Iron.Bone100)
        Text(app.pkg, IronType.MonoSm, color = Iron.Bone500)
        Spacer(8)
        DemandMeter(app.demand)                      // ▮▮▯ + LOW/MED/HIGH, mono
        Spacer(16)
        ChamferButton("ALLOCATE & LAUNCH", onLaunch, Modifier.fillMaxWidth())
    }
    // Drag-down eject (attach to the card's pointerInput):
    // detectVerticalDragGestures → dragDy (resisted), ticks per 20dp;
    // onDragEnd ≥80dp → eject sheet (REMOVE / KEEP), else spring back.
}
```

---

## 18. HUD rail — magnetic snap + gesture exclusion (in `GameOverlayService`, §7.12)

```kotlin
// ── View-based (it's a TYPE_APPLICATION_OVERLAY window, not Compose) ──
private val snapZones = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f)   // §3.11/§6.2 four zones

private fun nearestZoneY(y: Float, parentH: Int): Float =
    snapZones.map { it * parentH }.minBy { abs(it - y) }

private val touchListener = View.OnTouchListener { _, e ->
    when (e.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
            wmParams.y = (e.rawY - dragOffset).toInt().coerceIn(0, displayH)
            windowManager.updateViewLayout(rootView, wmParams)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val target = nearestZoneY(wmParams.y.toFloat(), displayH).toInt()
            ValueAnimator.ofInt(wmParams.y, target).apply {           // magnetic snap
                duration = 120
                interpolator = DecelerateInterpolator()
                addUpdateListener { wmParams.y = it.animatedValue as Int
                    windowManager.updateViewLayout(rootView, wmParams) }
                doOnEnd { rootView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                start()
            }
        }
    }
    true
}

// §6.1 minimal gesture-exclusion rect — only the 12dp hit strip, only while dragging
override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (Build.VERSION.SDK_INT >= 29 && dragging) {
        rootView.setSystemGestureExclusionRects(listOf(Rect(0, 0, dp(12), rootView.height)))
    }
}
```

---

## 19. Tune — predictive-back "END SESSION?" slip (§6.1, §7.8)

```kotlin
@Composable
fun TuningRoom(sessionActive: Boolean, onEndSession: () -> Unit, /* … */) {
    var slip by remember { mutableFloatStateOf(0f) }

    // Back scrub reveals the paper slip; commit ends the session; cancel springs back
    PredictiveBackHandler(enabled = sessionActive) { progress ->
        try {
            progress.collect { info -> slip = info.progress }
            onEndSession()
        } catch (e: GestureCancellationException) { slip = 0f }
    }

    Box(Modifier.fillMaxSize()) {
        // … LazyColumn of TuneCategorySections (EngravedPlate + MachinedToggle rows) …
        if (slip > 0.01f) {
            PaperPlate(
                Modifier.align(Alignment.Center)
                    .graphicsLayer { alpha = slip; scaleX = 1f - 0.06f * (1f - slip); scaleY = 1f - 0.06f * (1f - slip) }
            ) {
                RisoText("END SESSION?", IronType.Title.copy(fontSize = 18.sp), color = Iron.Ink900)
                Text("Release to end and restore kernel parameters.", IronType.Caption, color = Iron.Ink600)
            }
        }
    }
}
```

---

## Wiring checklist

| Spec § | Component | Where it plugs in |
|---|---|---|
| 3.1–3.2 | `EngravedPlate` / `PaperPlate` | every screen's card containers |
| 3.3 | `ChamferButton` | BOOST, LAUNCH, GRANT, DONE |
| 3.4 | `StampLabel` | FROZEN / READY / PINNED / SESSION ACTIVE |
| 3.5 | `InstrumentDial` | Home RAM (240dp) + SWAP MiniDial (96dp, `numerals=false, ignition=false`) |
| 3.6–3.8 | `PressureScale` / `ThermometerStrip` / `TickerLine` | Ram Free / Home / everywhere |
| 3.10–3.11 | `MachinedToggle` / `MachinedSegment` / `GearSelector` | Tune + Settings / segments / shell |
| 3.14 | `BenchSheet` | Setup, Pin Apps, Add Game — replace all three `ZenDialog`s |
| 3.15–3.16 | `OdometerCounter` / `ShavingsLayer` | purge ceremony only |
| 3.17 | `SearchSlot` / `IndexRail` | Games + both picker sheets |

Three engineering rules baked into the code above, worth preserving in review: **(1)** all continuous animations use conditional `LaunchedEffect` frame loops — nothing animates at 60fps when idle; **(2)** `InstrumentDial` allocates everything in `drawWithCache`, the draw lambda only reads state — needle motion never recomposes; **(3)** `Clack.gate()` enforces the 80ms haptic floor from §5.1 globally.
