package com.ivarna.apexcore.ui.iron.games

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class Demand(val cells: Int) { LOW(1), MEDIUM(2), HIGH(3) }

data class AppCardData(
    val name: String,
    val pkg: String,
    val demand: Demand,
    val tint: Color,
    val icon: @Composable () -> Unit,
)

@Composable
fun LaunchMatrixScreen(
    games: List<AppCardData>,
    allApps: List<AppCardData>,
    allLoading: Boolean,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onLaunch: (AppCardData) -> Unit,
    onRemove: (AppCardData) -> Unit,
    addSheet: @Composable () -> Unit = {},
    pinSheet: @Composable () -> Unit = {},
) {
    val serial = rememberSerial()
    val skin = ironSkin()
    var query by remember { mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var eject by remember { mutableStateOf<AppCardData?>(null) }
    var pending by remember { mutableStateOf<AppCardData?>(null) }
    var launchTick by remember { mutableIntStateOf(0) }

    val source = if (segment == 0) games else allApps
    val visible = remember(query, source) {
        source.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }

    IronScreen("GAMES") {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            SearchSlot(query, { query = it })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (games.isNotEmpty()) ChamferButton("+ ADD", onAdd, tall = false, variant = ChamferVariant.Outline, modifier = Modifier.weight(1f))
                ChamferButton("PIN", onPin, tall = false, variant = ChamferVariant.Outline, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            MachinedSegment(listOf("GAMES", "ALL APPS"), segment, onSelect = { segment = it })
            Spacer(Modifier.height(16.dp))

            when {
                segment == 1 && allLoading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingNeedle()
                        Spacer(Modifier.height(8.dp))
                        Text("SCANNING PACKAGES…", style = IronType.MonoSm, color = skin.textDim)
                    }
                }
                visible.isEmpty() -> EmptyPlate(
                    noGames = segment == 0 && games.isEmpty() && query.isEmpty(),
                    noMatch = query.isNotEmpty(),
                    onAdd = onAdd,
                    modifier = Modifier.weight(1f)
                )
                else -> Rack(
                    apps = visible,
                    segment = segment,
                    onSegment = { segment = it },
                    onLaunch = { card -> pending = card; launchTick++ },
                    onEject = { eject = it },
                    modifier = Modifier.weight(1f)
                )
            }
            SerialFooter(3, "GAMES", serial)
        }

        ShutterOverlay(trigger = launchTick, onSeam = { pending?.let(onLaunch) })

        BenchSheet(visible = eject != null, onDismiss = { eject = null }) {
            Text("EJECT CARTRIDGE?", style = IronType.Title.copy(fontSize = 18.sp), color = Iron.Bone100)
            Spacer(Modifier.height(4.dp))
            eject?.let {
                Text("${it.name} · ${it.pkg}", style = IronType.MonoSm, color = Iron.Bone500)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChamferButton("KEEP", { eject = null }, Modifier.weight(1f),
                    variant = ChamferVariant.Outline, tall = false)
                ChamferButton("REMOVE", {
                    eject?.let(onRemove)
                    eject = null
                }, Modifier.weight(1f), tall = false)
            }
        }
        addSheet()
        pinSheet()
    }
    }

    LaunchedEffect(launchTick) {
        if (launchTick > 0) {
            delay(1400)
            pending = null
        }
    }
}

@Composable
private fun Rack(
    apps: List<AppCardData>,
    segment: Int,
    onSegment: (Int) -> Unit,
    onLaunch: (AppCardData) -> Unit,
    onEject: (AppCardData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
    // §1.4 — RenderEffect blur is API 31+; skip entirely under reduced motion
    val blurAllowed = Build.VERSION.SDK_INT >= 31 && !LocalReducedMotion.current
    // §1.5 — pager page survives tab unmount
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
    ) { apps.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            clack.tick()
            savedPage = it
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(apps) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var flipped = false
                    var two = false
                    var x0 = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (!two) {
                                two = true
                                x0 = pressed.map { it.position.x }.average().toFloat()
                            }
                            val x = pressed.map { it.position.x }.average().toFloat()
                            if (!flipped && abs(x - x0) > 80.dp.toPx()) {
                                flipped = true
                                onSegment(1 - segment)
                                clack.keyTap()
                            }
                        } else two = false
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - minOf(abs(offset), 1f)
            val active = page == pagerState.currentPage
            Box(
                Modifier
                    .graphicsLayer {
                        val s = 0.8f + 0.2f * closeness
                        scaleX = s
                        scaleY = s
                        alpha = 0.4f + 0.6f * closeness
                        translationY = (8.dp * offset * offset).toPx()
                    }
                    .then(
                        if (blurAllowed && abs(offset) > 0.5f) Modifier.blur(4.dp) else Modifier
                    )
            ) {
                Cartridge(
                    apps[page],
                    active = active,
                    onTap = { if (!active) scope.launch { pagerState.animateScrollToPage(page) } },
                    onLaunch = { onLaunch(apps[page]) },
                    onEject = { onEject(apps[page]) },
                )
            }
        }
    }
}

@Composable
private fun Cartridge(
    card: AppCardData,
    active: Boolean,
    onTap: () -> Unit,
    onLaunch: () -> Unit,
    onEject: () -> Unit,
) {
    val clack = rememberClack()
    var dy by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState((dy / 12f).coerceIn(0f, 8f), IronMotion.machined(), label = "tilt")
    val skin = ironSkin()
    val paper = LocalPaperSurfaces.current

    val cardContent: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(card.tint.copy(alpha = 0.12f))
                        .border(2.dp, Iron.Brass400, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(56.dp)) { card.icon() }
                }
                Spacer(Modifier.height(14.dp))
                Text(card.name, style = IronType.Display.copy(fontSize = 22.sp), color = skin.text)
                Spacer(Modifier.height(2.dp))
                Text(card.pkg, style = IronType.MonoSm, color = skin.textDim)
                Spacer(Modifier.height(12.dp))
                DemandMeter(card.demand)
                Spacer(Modifier.height(16.dp))
                ChamferButton("ALLOCATE & LAUNCH", onLaunch, Modifier.fillMaxWidth())
                if (active) {
                    Spacer(Modifier.height(10.dp))
                    Text("drag card down to eject", style = IronType.MonoSm, color = skin.textDim)
                }
        }
    }

    Box {
        val plateModifier = Modifier
            .graphicsLayer { rotationZ = tilt }
            .pointerInput(active) { detectTapGestures { onTap() } }
        if (paper) {
            PaperPlate(modifier = plateModifier) { cardContent() }
        } else {
            EngravedPlate(
                modifier = plateModifier,
                onClick = if (active) onLaunch else onTap,
            ) { cardContent() }
        }

        if (active) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    var lastTick = 0
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            dy = (dy + dragAmount * 0.5f).coerceIn(0f, 160.dp.toPx())
                            val t = (dy / 20.dp.toPx()).toInt()
                            if (t != lastTick) {
                                lastTick = t
                                clack.tick()
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (dy >= 80.dp.toPx()) {
                                clack.off()
                                onEject()
                            }
                            dy = 0f
                        },
                        onDragCancel = { dy = 0f },
                    )
                }
            )
        }
    }
}

@Composable
private fun DemandMeter(demand: Demand) {
    val dimC = ironSkin().textDim
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("RESOURCE DEMAND", style = IronType.MonoSm, color = dimC, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        repeat(3) { i ->
            Box(
                Modifier.size(width = 12.dp, height = 8.dp).clip(IronShape.Slot)
                    .background(if (i < demand.cells) Iron.Signal500 else ironSkin().hairline)
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(demand.name, style = IronType.MonoSm, color = dimC, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun EmptyPlate(noGames: Boolean, noMatch: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val dimC = ironSkin().textDim
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IronSurface(Modifier.fillMaxWidth(0.85f)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (noMatch) {
                    Text("∅ NO MATCH · CHECK SPELLING", style = IronType.Mono, color = dimC)
                } else {
                    Text("NO ITEMS FOUND", style = IronType.Mono, color = dimC)
                    Spacer(Modifier.height(14.dp))
                    DoodleArrow(Iron.Signal700, Modifier.graphicsLayer { rotationZ = 90f })
                    Spacer(Modifier.height(14.dp))
                    ChamferButton(if (noGames) "ADD GAMES" else "SCAN FOR GAMES", onAdd)
                }
            }
        }
    }
}
