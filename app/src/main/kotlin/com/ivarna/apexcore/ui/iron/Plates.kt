package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ── §3.1 EngravedPlate — adaptive: Graphite=anvil, Vellum=paper plate ── */
@Composable
fun EngravedPlate(
    modifier: Modifier = Modifier,
    structural: Boolean = false,
    caption: String? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val skin = ironSkin()
    val bg = when {
        pressed && onClick != null -> skin.platePressed
        else -> skin.plate
    }
    val stroke = skin.hairline
    Box(
        modifier
            .graphicsLayer {
                val s = if (pressed && onClick != null) 0.98f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(IronShape.Plate)
            .background(bg)
            .drawWithCache {
                val i = 3.dp.toPx()
                val inner = Path().apply {
                    addRoundRect(
                        RoundRect(
                            i, i,
                            this@drawWithCache.size.width - i,
                            this@drawWithCache.size.height - i,
                            CornerRadius(3.dp.toPx())
                        )
                    )
                }
                onDrawWithContent {
                    drawContent()
                    // machined top edge: brass highlight catching light (Graphite identity; subtle on paper too).
                    val w = this@drawWithCache.size.width
                    drawLine(
                        Iron.Brass400.copy(alpha = if (skin.isPaper) 0.28f else 0.42f),
                        Offset(3.dp.toPx(), 1.dp.toPx()),
                        Offset(w - 3.dp.toPx(), 1.dp.toPx()),
                        1.dp.toPx()
                    )
                    drawPath(inner, stroke, style = Stroke(0.75.dp.toPx()))
                }
            }
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
    ) {
        Column(Modifier.padding(padding)) {
            content()
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Iron.Anvil600, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))
                Text(caption, style = IronType.MonoSm, color = Iron.Bone500)
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
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bite = with(density) { 2.dp.toPx() }
        val n = 7
        val step = size.width / n
        return Outline.Generic(Path().apply {
            moveTo(0f, bite)
            for (i in 0 until n) {
                val x = i * step
                lineTo(x + step * 0.35f, bite)
                lineTo(x + step * 0.5f, 0f)
                lineTo(x + step * 0.65f, bite)
                lineTo(x + step, bite)
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
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
            .drawWithCache {
                val p = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, this@drawWithCache.size.width, this@drawWithCache.size.height, CornerRadius(4.dp.toPx())))
                }
                val dp1Px = 1.dp.toPx()
                onDrawBehind {
                    withTransform({ translate(0f, dp1Px) }) {
                        drawPath(p, Iron.Ink900.copy(alpha = 0.35f))
                    }
                }
            }
            .shadow(
                8.dp, shape, clip = false,
                ambientColor = Iron.Ink900.copy(alpha = 0.35f),
                spotColor = Iron.Ink900.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(Iron.Bone100)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/* ── Work Order stat row (§7.4) ── */
@Composable
fun StatRow(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = IronType.MonoSm, color = Iron.Ink600)
            Text(value, style = IronType.Mono, color = Iron.Ink900)
        }
        if (sub != null) Text(
            sub,
            style = IronType.MonoSm.copy(fontSize = 10.sp),
            color = Iron.Ink600,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/* ── §3.13 SerialFooter ── */
@Composable
fun SerialFooter(
    plateNo: Int,
    screen: String,
    serial: String,
    rev: String = "C",
    modifier: Modifier = Modifier,
    onDebugTap: (() -> Unit)? = null
) {
    val skin = ironSkin()
    var taps by remember { mutableIntStateOf(0) }
    var firstAt by remember { mutableLongStateOf(0L) }
    Text(
        "PLATE %02d · %s · S/N %s · REV %s".format(plateNo, screen, serial, rev),
        style = IronType.MonoSm,
        color = skin.textDim,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clickableNoIndication {
                if (onDebugTap == null) return@clickableNoIndication
                val now = android.os.SystemClock.uptimeMillis()
                if (now - firstAt > 3000) {
                    taps = 0
                    firstAt = now
                }
                if (++taps >= 5) {
                    taps = 0
                    onDebugTap()
                }
            },
        textAlign = TextAlign.Center
    )
}
