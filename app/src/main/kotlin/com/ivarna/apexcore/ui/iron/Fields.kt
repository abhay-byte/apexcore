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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    val skin = ironSkin()
    val slotBg = if (skin.isPaper) Iron.Bone100 else Iron.Anvil950
    val slotBorder = when {
        focused -> Iron.Brass400
        skin.isPaper -> skin.hairline
        else -> Iron.Anvil700
    }
    val textColor = skin.text
    val hintColor = skin.textDim
    val loupeIdle = if (skin.isPaper) Iron.Ink600 else Iron.Bone500
    Row(
        modifier
            .height(48.dp)
            .clip(IronShape.Slot)
            .background(slotBg)
            .border(1.dp, slotBorder, IronShape.Slot)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoupeGlyph(if (focused) Iron.Brass400 else loupeIdle)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = IronType.Mono.copy(color = textColor),
            singleLine = true,
            interactionSource = interaction,
            decorationBox = { inner ->
                if (value.isEmpty() && !focused) Text(placeholder, style = IronType.Mono, color = hintColor)
                else inner()
            }
        )
        if (focused) LedDot(LedState.READY)
    }
}

/* ── §3.17 IndexRail — single gesture handler (errata #9) ── */
@Composable
fun IndexRail(
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    letters: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#",
) {
    val clack = rememberClack()
    var active by remember { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val idleColor = ironSkin().textDim

    fun pick(y: Float, height: Float) {
        val idx = (y / height * letters.length).toInt().coerceIn(0, letters.length - 1)
        if (idx != active) {
            active = idx
            clack.keyTap()
            onLetter(letters[idx])
        }
    }

    Box(
        modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { c -> pick(c.y, size.height.toFloat()) },
                    onVerticalDrag = { change, _ ->
                        pick(change.position.y, size.height.toFloat())
                        change.consume()
                    }
                )
            }
            .pointerInput(letters) {
                detectTapGestures { c ->
                    pick(c.y, size.height.toFloat())
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val layouts = letters.map {
                measurer.measure(
                    it.toString(),
                    TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 9.sp)
                )
            }
            layouts.forEachIndexed { i, l ->
                val y = (i + 0.5f) / letters.length * size.height - l.size.height / 2f
                drawText(
                    l,
                    color = if (i == active) Iron.Brass400 else idleColor,
                    topLeft = Offset((size.width - l.size.width) / 2f, y)
                )
            }
        }
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
    LaunchedEffect(shake) {
        if (shake) {
            shakeAnim.animateTo(0f, keyframes {
                durationMillis = 180
                8f at 45
                (-8f) at 90
                8f at 135
            })
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(320, easing = IronMotion.EaseWind)) { -it / 2 } + fadeIn(tween(320)),
        modifier = modifier.graphicsLayer { translationX = shakeAnim.value.dp.toPx() }
    ) {
        PaperPlate {
            RisoText("ELEVATION REQUIRED", IronType.Title.copy(fontSize = 16.sp), color = Iron.Ink900)
            Spacer(Modifier.height(6.dp))
            Text(
                "Deep freeze (BOOST) requires Shizuku or Root access.",
                style = IronType.Caption,
                color = Iron.Ink600
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("CONNECT SHIZUKU", onShizuku, Modifier.weight(1f), tall = false)
                ChamferButton("GRANT ROOT", onRoot, Modifier.weight(1f), variant = ChamferVariant.Outline, tall = false)
            }
        }
    }
}
