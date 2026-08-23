package com.ivarna.apexcore.ui.iron.manual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val pageData = listOf(
    Triple("", "", ""),
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
    onboardingCompletedProbe: () -> Boolean,
    shizuku: KeyStatus,
    root: KeyStatus,
    selectedBackend: BackendChoice?,
    onProbe: () -> Unit,
    onSelect: (BackendChoice) -> Unit,
    onConfigureShizuku: () -> Unit,
    onGrantRoot: () -> Unit,
    onFinish: (BackendChoice?) -> Unit,
    onClose: () -> Unit,
) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val page = pagerState.currentPage

    LaunchedEffect(page) {
        if (page != 4) return@LaunchedEffect
        while (true) {
            onProbe()
            delay(1200)
        }
    }

    IronScreen("MANUAL") {
    val skin = ironSkin()
    Box(Modifier.fillMaxSize().background(skin.canvas).ironGrain(0.05f)) {
        Row(Modifier.fillMaxSize()) {
            BindingLane()
            Column(
                Modifier.weight(1f).fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReplay) BackArrow(skin.text, onClose)
                    else BackArrow(skin.text, {
                        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) } else onClose()
                    })
                    Spacer(Modifier.weight(1f))
                    RulerPager(5, page)
                    Spacer(Modifier.weight(1f))
                    if (!isReplay) Text("SKIP", style = IronType.MonoSm, color = skin.textDim,
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
}

@Composable
private fun BindingLane() {
    val dash = inkColor()
    val stitch = ironSkin().text
    Canvas(Modifier.width(22.dp).fillMaxHeight()) {
        val cx = 12.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(dash, Offset(cx, y), Offset(cx, y + 5.dp.toPx()), 1.5.dp.toPx())
            y += 9.dp.toPx()
        }
        var sy = 14.dp.toPx()
        while (sy < size.height) {
            drawLine(stitch, Offset(cx - 5.dp.toPx(), sy), Offset(cx + 5.dp.toPx(), sy), 2.dp.toPx())
            sy += 26.dp.toPx()
        }
    }
}

@Composable
private fun RulerPager(count: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(4.dp, 16.dp).clip(IronShape.Slot)
                    .background(if (i == active) ironSkin().text else ironSkin().text.copy(alpha = 0.2f))
            )
        }
    }
}

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
    Column(
        Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FigFrame(
            "APEXCORE · MK·II", Modifier.size(200.dp)
            .manualParallax(pagerState, 0, 0.4f, LocalReducedMotion.current)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FigArtwork(0) }
        }
        Spacer(Modifier.height(24.dp))
        RisoText("APEXCORE", IronType.Display.copy(fontSize = 30.sp), color = ironSkin().text)
        Text(
            "FIELD-GRADE PERFORMANCE INSTRUMENTS", style = IronType.MonoSm, color = ironSkin().textDim,
            modifier = Modifier.padding(top = 6.dp), letterSpacing = 2.sp
        )
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoodleStar()
            Spacer(Modifier.width(10.dp))
            Text("hello, operator.", style = IronType.Hand, color = ironSkin().textDim)
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
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FigFrame("FIG. 0$page", Modifier.size(196.dp).manualParallax(pagerState, page, 0.4f, reduced)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FigArtwork(page) }
            }
            Spacer(Modifier.height(20.dp))
            Text(kicker, style = IronType.MonoSm, color = accentColor(), letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, style = IronType.Title.copy(fontSize = 24.sp), color = ironSkin().text)
            Spacer(Modifier.height(10.dp))
            Text(body, style = IronType.Body, color = ironSkin().textDim, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 12.dp)
                .manualParallax(pagerState, page, 0.7f, reduced),
            horizontalAlignment = Alignment.End
        ) {
            DoodleArrow(accentColor(), Modifier.graphicsLayer { rotationZ = 200f })
            Text(
                marginNotes[page], style = IronType.Hand, color = ironSkin().textDim,
                modifier = Modifier.graphicsLayer { rotationZ = -4f }
            )
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
        Text("04 · SYSTEM ACCESS", style = IronType.MonoSm, color = accentColor(), letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Text("Elevate Your Control", style = IronType.Title.copy(fontSize = 24.sp), color = ironSkin().text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Deep freeze needs a key. Pick Shizuku or Root — change it any time in Settings.",
            style = IronType.Body, color = ironSkin().textDim
        )
        Spacer(Modifier.height(16.dp))
        KeyCard(
            BackendChoice.SHIZUKU, shizuku, selected == BackendChoice.SHIZUKU,
            badge = "RECOMMENDED",
            onUse = { onSelect(BackendChoice.SHIZUKU) }, onConfigure = onConfigureShizuku
        )
        Spacer(Modifier.height(12.dp))
        KeyCard(
            BackendChoice.ROOT, root, selected == BackendChoice.ROOT,
            badge = null,
            onUse = { onSelect(BackendChoice.ROOT) }, onConfigure = onGrantRoot
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.align(Alignment.CenterHorizontally)
                .manualParallax(pagerState, 4, 0.7f, LocalReducedMotion.current),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DoodleStar()
            Spacer(Modifier.width(8.dp))
            Text(marginNotes[4], style = IronType.Hand, color = ironSkin().textDim)
        }
    }
}

@Composable
fun KeyCard(
    choice: BackendChoice,
    status: KeyStatus,
    selected: Boolean,
    onUse: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onPaper: Boolean = ironSkin().isPaper,
) {
    val clack = rememberClack()
    val borderC = if (onPaper) Iron.Ink600 else Iron.Anvil500
    val textC = if (onPaper) Iron.Ink900 else Iron.Bone100
    val dimC = if (onPaper) Iron.Ink600 else Iron.Bone500
    Box(modifier.fillMaxWidth().border(1.5.dp, borderC, IronShape.Plate)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (choice == BackendChoice.SHIZUKU) SkeletonKeyGlyph(textC) else AllenKeyGlyph(textC)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (choice == BackendChoice.SHIZUKU) "SHIZUKU SERVICE" else "ROOT ACCESS",
                    style = IronType.Title.copy(fontSize = 15.sp), color = textC, modifier = Modifier.weight(1f)
                )
                if (status.ready && !selected && badge != null)
                    StampLabel(badge, StampInk.Brass, slam = false)
                else if (status.ready && !selected)
                    StampLabel("READY", StampInk.Phosphor, slam = false)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(when { status.ready -> LedState.READY; status.checking -> LedState.CHECKING; else -> LedState.BLOCKED })
                Spacer(Modifier.width(6.dp))
                Text(status.statusLine, style = IronType.MonoSm, color = dimC)
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
        if (selected) Box(Modifier.align(Alignment.Center)) {
            StampLabel("READY", StampInk.Phosphor, slam = true)
        }
    }
}
