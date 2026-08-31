package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Iron ruler slider (§3.6 Scale + Optics OPACITY ruler).
 *
 * Fully custom Compose Canvas ruler — no Material slider component.
 * Groove + major/minor ticks + brass flag carriage, matching [OpacityRuler] /
 * [PressureScale].
 *
 * Commit-only callers: drive [onValueChange] into local state and persist in
 * [onValueChangeFinished] only (T13 contract).
 */
@Composable
fun IronSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    val skin = ironSkin()
    val clack = rememberClack()
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val onFinishedState = rememberUpdatedState(onValueChangeFinished)
    val rangeState = rememberUpdatedState(valueRange)
    val stepsState = rememberUpdatedState(steps)
    val enabledState = rememberUpdatedState(enabled)

    val rail = if (skin.isPaper) skin.hairline else Iron.Anvil600
    val majorTick = when {
        !enabled && skin.isPaper -> Iron.Ink600.copy(alpha = 0.45f)
        !enabled -> Iron.Anvil500
        skin.isPaper -> Iron.Ink600
        else -> Iron.Bone300
    }
    val minorTick = when {
        !enabled && skin.isPaper -> Iron.Ink600.copy(alpha = 0.22f)
        !enabled -> Iron.Anvil700
        skin.isPaper -> Iron.Ink600.copy(alpha = 0.35f)
        else -> Iron.Anvil500
    }
    val fill = if (enabled) Iron.Signal500 else Iron.Bone500.copy(alpha = 0.55f)
    val flag = if (enabled) Iron.Brass400 else if (skin.isPaper) Iron.Bone500 else Iron.Anvil500

    var lastMajor by remember { mutableIntStateOf(-1) }

    fun fractionOf(v: Float): Float {
        val start = valueRange.start
        val end = valueRange.endInclusive
        val span = (end - start).takeIf { it != 0f } ?: 1f
        return ((v - start) / span).coerceIn(0f, 1f)
    }

    fun snap(raw: Float): Float {
        val start = rangeState.value.start
        val end = rangeState.value.endInclusive
        val coerced = raw.coerceIn(start, end)
        val s = stepsState.value
        if (s <= 0) return coerced
        val span = end - start
        if (span == 0f) return start
        val stepSize = span / (s + 1)
        val n = ((coerced - start) / stepSize).roundToInt().coerceIn(0, s + 1)
        return (start + n * stepSize).coerceIn(start, end)
    }

    fun valueAt(x: Float, width: Float): Float {
        val start = rangeState.value.start
        val end = rangeState.value.endInclusive
        val t = (x / width).coerceIn(0f, 1f)
        return snap(start + t * (end - start))
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .height(48.dp)
                .testTag("iron_slider")
                .progressSemantics(
                    value = value,
                    valueRange = valueRange,
                    steps = steps,
                )
                .semantics {
                    if (!enabled) disabled()
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (!enabledState.value) return@detectTapGestures
                        val next = valueAt(pos.x, size.width.toFloat())
                        clack.tick()
                        onValueChangeState.value(next)
                        onFinishedState.value()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (enabledState.value) onFinishedState.value()
                        },
                        onDragCancel = {
                            if (enabledState.value) onFinishedState.value()
                        },
                    ) { change, _ ->
                        if (!enabledState.value) return@detectDragGestures
                        val next = valueAt(change.position.x, size.width.toFloat())
                        val major = (fractionOf(next) * 4f).toInt()
                        if (major != lastMajor) {
                            lastMajor = major
                            if (major > 0) clack.off()
                        }
                        onValueChangeState.value(next)
                        change.consume()
                    }
                }
        ) {
            val cy = size.height * 0.58f
            val mx = fractionOf(value) * size.width

            // Active fill follows the brass marker (§3.6 — fill is pushed, not a bar alone).
            drawRect(
                fill,
                topLeft = Offset(0f, cy - 4.dp.toPx()),
                size = Size(mx.coerceAtLeast(0f), 8.dp.toPx()),
            )

            // Groove rail
            drawLine(rail, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())

            // End caps: ├ … ┤
            drawLine(majorTick, Offset(0f, cy - 8.dp.toPx()), Offset(0f, cy + 4.dp.toPx()), 1.5.dp.toPx())
            drawLine(
                majorTick,
                Offset(size.width, cy - 8.dp.toPx()),
                Offset(size.width, cy + 4.dp.toPx()),
                1.5.dp.toPx(),
            )

            // Major / minor ruler ticks
            var x = 0f
            var i = 0
            while (x <= size.width + 0.5f) {
                val major = i % 5 == 0
                drawLine(
                    if (major) majorTick else minorTick,
                    Offset(x, cy),
                    Offset(x, cy - (if (major) 10.dp else 5.dp).toPx()),
                    1.dp.toPx(),
                )
                x += size.width / 20f
                i++
            }

            // Brass flag carriage (same silhouette as PressureScale / OpacityRuler)
            drawRect(
                flag,
                topLeft = Offset(mx - 1.dp.toPx(), cy - 14.dp.toPx()),
                size = Size(2.dp.toPx(), 14.dp.toPx()),
            )
            drawPath(
                Path().apply {
                    val top = cy - 14.dp.toPx()
                    moveTo(mx, top)
                    lineTo(mx + 7.dp.toPx(), top + 3.dp.toPx())
                    lineTo(mx, top + 6.dp.toPx())
                    close()
                },
                flag,
            )
            // Knob accent on the rail (Optics ├────●──────────┤)
            drawCircle(
                flag,
                radius = 5.dp.toPx(),
                center = Offset(mx, cy),
            )
            drawCircle(
                if (skin.isPaper) Iron.Ink900.copy(alpha = if (enabled) 0.85f else 0.4f)
                else Iron.Anvil950.copy(alpha = if (enabled) 0.9f else 0.5f),
                radius = 5.dp.toPx(),
                center = Offset(mx, cy),
                style = Stroke(width = 1.25.dp.toPx()),
            )
        }
    }
}

/** Compact mono readout slot for the live slider value (Tuning Room). */
@Composable
fun IronSliderReadout(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = true,
) {
    val skin = ironSkin()
    Box(
        modifier
            .background(skin.inputSurface(), IronShape.Slot)
            .border(1.dp, skin.inputBorder(), IronShape.Slot)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            style = IronType.MonoSm,
            color = if (emphasized) skin.text else skin.textDim,
        )
    }
}
