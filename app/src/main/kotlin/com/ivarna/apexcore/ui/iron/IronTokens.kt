package com.ivarna.apexcore.ui.iron

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.R
import kotlin.math.abs

/* ── §2.1 Color ─────────────────────────────────────────── */
object Iron {
    val Anvil950 = Color(0xFF0B0C0D)
    val Anvil900 = Color(0xFF101113)
    val Anvil800 = Color(0xFF17191C)
    val Anvil700 = Color(0xFF1F2226)
    val Anvil650 = Color(0xFF262B32)  // steel-blue plate top (Graphite machined identity)
    val Anvil600 = Color(0xFF2B2F34)
    val Anvil500 = Color(0xFF3A3F45)
    val Bone50 = Color(0xFFF5F0E4)
    val Bone100 = Color(0xFFEAE3D2)
    val Bone300 = Color(0xFFCFC6AE)
    val Bone500 = Color(0xFFA29880)
    val Ink900 = Color(0xFF201C16)
    val Ink600 = Color(0xFF4A4436)
    val Signal500 = Color(0xFFFF5A1F)
    val Signal300 = Color(0xFFFF8A50)
    val Signal700 = Color(0xFFB23A0F)
    val Phosphor400 = Color(0xFF7FE060)
    val Phosphor600 = Color(0xFF3E9B2E)
    /** Darker phosphor for Vellum success/status text (~4.5:1 on Bone plates). */
    val Phosphor800 = Color(0xFF24601C)
    val Ember500 = Color(0xFFF5402C)
    val Brass400 = Color(0xFFD9A75A)
    val Scrim = Color(0xFF000000).copy(alpha = 0.64f)
}

/* ── §2.2 Type ──────────────────────────────────────────── */
val Archivo = FontFamily(
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
    val Display = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.34.sp)
    val Title   = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold,  fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.22.sp)
    val Label   = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold,  fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.04.sp)
    val Body    = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium,fontSize = 15.sp, lineHeight = 22.sp)
    val Caption = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium,fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.24.sp)
    val MonoLg  = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.8.sp)
    val Mono    = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium,  fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp)
    val MonoSm  = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal,  fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.66.sp)
    val Hand    = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp)
}

/* ── §2.4 Shape ─────────────────────────────────────────── */
object IronShape {
    val Plate = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val Slot  = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
}

/** Signature silhouette: 4dp corners, 10dp 45° cut top-right (§2.4). */
class ChamferShape(private val corner: Dp = 4.dp, private val cut: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(with(density) { chamferPath(size, corner.toPx(), cut.toPx()) })
}

fun chamferPath(size: Size, r: Float, c: Float): Path = Path().apply {
    moveTo(r, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height - r)
    quadraticTo(size.width, size.height, size.width - r, size.height)
    lineTo(r, size.height)
    quadraticTo(0f, size.height, 0f, size.height - r)
    lineTo(0f, r)
    quadraticTo(0f, 0f, r, 0f)
    close()
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

/* ── §2.7 / §3.13 Serial numbers (per-install S/N) ──────── */
object SerialNumber {
    fun hashOf(id: String): String {
        val h = abs(id.hashCode())
        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        return "%c%c-%04d".format(letters[h % letters.length], letters[(h / 26) % letters.length], h % 10000)
    }

    fun generate(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "apex"
        return hashOf(id)
    }
}

@Composable
fun rememberSerial(): String {
    val ctx = LocalContext.current
    return remember { SerialNumber.generate(ctx) }
}

/* ── §4.4 Reduced motion ────────────────────────────────── */
val LocalReducedMotion = staticCompositionLocalOf { false }
