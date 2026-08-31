package com.ivarna.apexcore.ui.iron.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import com.ivarna.apexcore.fps.model.FpsMethod
import com.ivarna.apexcore.fps.model.abbrev
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.iron.shell.IronSeamColumn
import com.ivarna.apexcore.ui.iron.window.IronFormFactor
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class RailEdge { LEFT, RIGHT }
enum class RailSize(val panelW: Dp, val fpsSp: TextUnit) { S(56.dp, 18.sp), M(78.dp, 26.sp), L(100.dp, 34.sp) }

data class OpticsUiState(
    val permissionGranted: Boolean,
    val previewRunning: Boolean,
    val size: RailSize,
    val opacity: Float,
    val edge: RailEdge,
)

@Composable
fun OpticsBench(
    state: OpticsUiState,
    onGrant: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onSize: (RailSize) -> Unit,
    onOpacity: (Float) -> Unit,
    onEdge: (RailEdge) -> Unit,
    active: Boolean = true,
) {
    val serial = rememberSerial()
    val skin = ironSkin()
    val ctx = LocalContext.current
    fun previewRefreshHz(): Float = try {
        if (Build.VERSION.SDK_INT >= 30) ctx.display?.refreshRate ?: 60f
        else @Suppress("DEPRECATION") (ctx.getSystemService(WindowManager::class.java)?.defaultDisplay?.refreshRate ?: 60f)
    } catch (_: Throwable) { 60f }

    var fps by remember { mutableIntStateOf(60) }
    var fpsMethod by remember { mutableStateOf(FpsMethod.SURFACEFLINGER) }
    val ram = remember { mutableStateListOf(*(FloatArray(40) { 0.5f }).toTypedArray()) }
    val cpu = remember { mutableStateListOf(*(FloatArray(8) { 0.3f }).toTypedArray()) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val ceiling = previewRefreshHz().toInt().coerceAtLeast(60)
            // Never exceed display refresh — preview simulates capped live data (max variance 20 below ceiling)
            val simulated = (ceiling - (0..18).random()).coerceIn(10, ceiling)
            fps = simulated
            // Cycle demo method so compact label is visible in preview
            fpsMethod = listOf(FpsMethod.SURFACEFLINGER, FpsMethod.DMA_FENCE, FpsMethod.GFXINFO).random()
            ram.removeAt(0)
            ram.add(0.3f + (0..40).random() / 100f)
            repeat(8) { cpu[it] = 0.15f + (0..70).random() / 100f }
            delay(500)
        }
    }

    IronScreen("HUD") {
        if (LocalIronWindow.current.form == IronFormFactor.PHONE) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Text("OPTICS", style = IronType.Display.copy(fontSize = 26.sp), color = skin.text)
                Text("Configure the in-game telemetry rail", style = IronType.Caption, color = skin.textDim)
                Spacer(Modifier.height(12.dp))
                PermissionCard(state.permissionGranted, onGrant)
                Spacer(Modifier.height(14.dp))
                PreviewCard(state, fps, ram.toList(), cpu.toList(), fpsMethod, onTogglePreview)
                Spacer(Modifier.height(14.dp))
                FitCard(state, onSize, onOpacity, onEdge)
                SerialFooter(5, "OPTICS", serial)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Column(
                    Modifier
                        .weight(0.52f)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text("OPTICS", style = IronType.Display.copy(fontSize = 26.sp), color = skin.text)
                    Text("Configure the in-game telemetry rail", style = IronType.Caption, color = skin.textDim)
                    Spacer(Modifier.height(12.dp))
                    PermissionCard(state.permissionGranted, onGrant)
                    Spacer(Modifier.height(14.dp))
                    PreviewCard(state, fps, ram.toList(), cpu.toList(), fpsMethod, onTogglePreview)
                }
                Spacer(Modifier.width(16.dp))
                IronSeamColumn(brass = false)
                Column(
                    Modifier
                        .weight(0.48f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    FitCard(state, onSize, onOpacity, onEdge)
                    SerialFooter(5, "OPTICS", serial)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onGrant: () -> Unit) {
    val skin = ironSkin()
    IronSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PERMISSION", style = IronType.Label, color = skin.text)
                Text(
                    if (granted)
                        "ApexCore may draw over other apps."
                    else "Draw-over-apps permission required for the HUD.",
                    style = IronType.Caption, color = skin.textDim
                )
            }
            if (granted) StampLabel("GRANTED", StampInk.Phosphor, slam = false)
            else StampLabel("HUD NOT ARMED", StampInk.Ember, slam = false)
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            ChamferButton("GRANT PERMISSION", onGrant, Modifier.fillMaxWidth(), tall = false)
        }
    }
}

@Composable
private fun PreviewCard(
    state: OpticsUiState,
    fps: Int,
    ram: List<Float>,
    cpu: List<Float>,
    fpsMethod: FpsMethod,
    onTogglePreview: (Boolean) -> Unit,
) {
    val skin = ironSkin()
    IronSurface {
        Text("PREVIEW", style = IronType.Label, color = skin.text)
        Text(
            "Drag the rail. Feel the magnet snap. Double-tap to expand.",
            style = IronType.Caption, color = skin.textDim
        )
        Spacer(Modifier.height(12.dp))
        PhantomRailPreview(fps, ram, cpu, state, fpsMethod)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PREVIEW SERVICE", style = IronType.Label, color = skin.text, modifier = Modifier.weight(1f))
            MachinedToggle(
                checked = state.previewRunning,
                onCheckedChange = onTogglePreview,
                enabled = state.permissionGranted
            )
        }
        if (!state.permissionGranted) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Grant draw-over permission to start the preview service",
                style = IronType.MonoSm, color = Iron.Signal500
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            ChamferButton(
                "START OVERLAY",
                { if (state.permissionGranted) onTogglePreview(true) },
                Modifier.weight(1f),
                tall = false,
                enabled = state.permissionGranted && !state.previewRunning,
                variant = if (state.previewRunning) ChamferVariant.Outline else ChamferVariant.Primary,
            )
            Spacer(Modifier.width(10.dp))
            ChamferButton(
                "STOP OVERLAY",
                { onTogglePreview(false) },
                Modifier.weight(1f),
                tall = false,
                enabled = state.previewRunning,
                variant = if (state.previewRunning) ChamferVariant.Primary else ChamferVariant.Outline,
            )
        }
    }
}

@Composable
private fun FitCard(
    state: OpticsUiState,
    onSize: (RailSize) -> Unit,
    onOpacity: (Float) -> Unit,
    onEdge: (RailEdge) -> Unit,
) {
    val skin = ironSkin()
    IronSurface {
        Text("FIT", style = IronType.Label, color = skin.text)
        Spacer(Modifier.height(10.dp))
        Text("SIZE", style = IronType.MonoSm, color = skin.textDim)
        Spacer(Modifier.height(6.dp))
        MachinedSegment(listOf("S", "M", "L"), state.size.ordinal, onSelect = { onSize(RailSize.entries[it]) })
        Spacer(Modifier.height(14.dp))
        Text("OPACITY", style = IronType.MonoSm, color = skin.textDim)
        Spacer(Modifier.height(6.dp))
        OpacityRuler(state.opacity, onOpacity)
        Spacer(Modifier.height(14.dp))
        Text("EDGE", style = IronType.MonoSm, color = skin.textDim)
        Spacer(Modifier.height(6.dp))
        MachinedSegment(listOf("LEFT", "RIGHT"), state.edge.ordinal, onSelect = { onEdge(RailEdge.entries[it]) })
    }
}

fun Modifier.dashedWindow(color: Color = Iron.Anvil500): Modifier = drawBehind {
    drawRoundRect(
        color, cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        )
    )
}

@Composable
fun PhantomRailPreview(
    fps: Int,
    ram: List<Float>,
    cpu: List<Float>,
    state: OpticsUiState,
    fpsMethod: FpsMethod = FpsMethod.SURFACEFLINGER,
) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val y = remember { Animatable(40f) }
    val flashDefrost = remember { mutableStateOf(false) }
    val expandT by animateFloatAsState(
        if (expanded) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "railExpand",
    )

    LaunchedEffect(interaction) {
        delay(5000)
        expanded = false
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp).dashedWindow()) {
        val areaH = constraints.maxHeight.toFloat()
        val snapZones = floatArrayOf(0.05f, 0.37f, 0.63f, 0.9f)
        val panelHPx = with(density) { 170.dp.toPx() }

        fun snap() {
            val target = snapZones.map { it * (areaH - panelHPx) }.minByOrNull { kotlin.math.abs(it - y.value) } ?: 0f
            scope.launch {
                y.animateTo(target, IronMotion.drawer())
                clack.tick()
            }
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
                        onDragEnd = { snap() },
                    ) { change, dragAmount ->
                        scope.launch {
                            y.snapTo((y.value + dragAmount.y).coerceIn(0f, (areaH - panelHPx).coerceAtLeast(0f)))
                        }
                        change.consume()
                    }
                }
        ) {
            if (expandT > 0.02f) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = expandT
                        val s = 0.92f + 0.08f * expandT
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(
                            if (state.edge == RailEdge.LEFT) 0f else 1f, 0.5f
                        )
                    }
                ) {
                    RailPanel(fps, ram, cpu, state.size, state.opacity, flashDefrost.value, fpsMethod) {
                        interaction++
                        flashDefrost.value = true
                        clack.confirm()
                    }
                }
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 7.dp)
                        .width(2.dp)
                        .height(120.dp)
                        .background(Iron.Brass400)
                )
            }
        }
        LaunchedEffect(flashDefrost.value) {
            if (flashDefrost.value) {
                delay(600)
                flashDefrost.value = false
            }
        }
    }
}

@Composable
private fun RailPanel(
    fps: Int, ram: List<Float>, cpu: List<Float>,
    size: RailSize, opacity: Float, defrostFlash: Boolean,
    fpsMethod: FpsMethod = FpsMethod.SURFACEFLINGER,
    onDefrost: () -> Unit,
) {
    Column(
        Modifier
            .width(size.panelW)
            .height(170.dp)
            .clip(IronShape.Slot)
            .background(Iron.Anvil950.copy(alpha = opacity))
            .border(1.dp, Iron.Anvil600, IronShape.Slot)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$fps", style = IronType.MonoLg.copy(fontSize = size.fpsSp), color = ironSkin().phosphor())
        Text("FPS", style = IronType.MonoSm, color = Iron.Bone500)
        // Compact method/fallback abbreviation — small mono, centered
        Text(
            fpsMethod.abbrev(),
            style = IronType.MonoSm.copy(fontSize = 7.sp),
            color = Iron.Bone500.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.width(size.panelW - 16.dp).height(22.dp)) {
            val path = Path()
            ram.forEachIndexed { i, v ->
                val x = i / (ram.size - 1f) * this.size.width
                val yy = this.size.height - v * this.size.height
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, Iron.Bone300, style = Stroke(1.2.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(size.panelW - 16.dp).height(26.dp)) {
            val bw = this.size.width / 8f
            cpu.forEachIndexed { i, v ->
                val h = v * this.size.height
                drawRect(Iron.Brass400, Offset(i * bw + 1f, this.size.height - h),
                    Size(bw - 2f, h))
            }
        }
        Spacer(Modifier.weight(1f))
        DefrostNode(defrostFlash, onDefrost)
    }
}

@Composable
private fun DefrostNode(flash: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (flash) Iron.Phosphor400 else Iron.Brass400, CircleShape)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val c = center
            val r = size.minDimension / 2f
            repeat(6) { i ->
                val a = i / 6f * 2f * Math.PI.toFloat()
                drawLine(
                    Iron.Bone300,
                    Offset(c.x - cos(a) * r, c.y - sin(a) * r),
                    Offset(c.x + cos(a) * r, c.y + sin(a) * r),
                    1.5.dp.toPx()
                )
            }
        }
        if (flash) {
            Text(
                "DEFROSTED", style = IronType.MonoSm.copy(fontSize = 8.sp),
                color = Iron.Phosphor400,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { rotationZ = -3f }
            )
        }
    }
}

@Composable
fun OpacityRuler(value: Float, onChange: (Float) -> Unit) {
    val clack = rememberClack()
    val skin = ironSkin()
    val rail = if (skin.isPaper) skin.hairline else Iron.Anvil600
    val majorTick = if (skin.isPaper) Iron.Ink600 else Iron.Bone300
    val minorTick = if (skin.isPaper) Iron.Ink600.copy(alpha = 0.35f) else Iron.Anvil500
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        clack.tick()
                        onChange((0.4f + 0.6f * (pos.x / size.width)).coerceIn(0.4f, 1f))
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
            drawLine(rail, Offset.Zero, Offset(size.width, cy), 1.dp.toPx())
            var x = 0f
            var i = 0
            while (x <= size.width + 0.5f) {
                val major = i % 5 == 0
                drawLine(
                    if (major) majorTick else minorTick,
                    Offset(x, cy),
                    Offset(x, cy - (if (major) 10.dp else 5.dp).toPx()),
                    1.dp.toPx()
                )
                x += size.width / 20f
                i++
            }
            val mx = ((value - 0.4f) / 0.6f) * size.width
            drawRect(
                Iron.Brass400, Offset(mx - 1.dp.toPx(), cy - 14.dp.toPx()),
                Size(2.dp.toPx(), 14.dp.toPx())
            )
        }
        Text(
            "${(value * 100).toInt()}%",
            style = IronType.MonoSm,
            color = skin.textDim,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
