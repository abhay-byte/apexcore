package com.ivarna.apexcore.ui.iron.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.iron.shell.IronSeamColumn
import com.ivarna.apexcore.ui.iron.window.IronFormFactor
import com.ivarna.apexcore.ui.iron.window.LocalIronWindow
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
    coordinator: GameLaunchCoordinator,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onAutoScan: () -> Unit = {},
    autoScanning: Boolean = false,
    onRemove: (AppCardData) -> Unit,
    addSheet: @Composable () -> Unit = {},
    pinSheet: @Composable () -> Unit = {},
) {
    val win = LocalIronWindow.current
    IronScreen("GAMES") {
        if (win.form == IronFormFactor.TABLET) {
            RackMasterDetail(
                games = games,
                allApps = allApps,
                allLoading = allLoading,
                coordinator = coordinator,
                onAdd = onAdd,
                onPin = onPin,
                onAutoScan = onAutoScan,
                autoScanning = autoScanning,
                onRemove = onRemove,
                addSheet = addSheet,
                pinSheet = pinSheet,
            )
        } else {
            RackCarousel(
                games = games,
                allApps = allApps,
                allLoading = allLoading,
                coordinator = coordinator,
                onAdd = onAdd,
                onPin = onPin,
                onAutoScan = onAutoScan,
                autoScanning = autoScanning,
                onRemove = onRemove,
                addSheet = addSheet,
                pinSheet = pinSheet,
                carouselPadding = if (win.form == IronFormFactor.LANDSCAPE) 44.dp else 56.dp,
            )
        }
    }
}

@Composable
private fun RackCarousel(
    games: List<AppCardData>,
    allApps: List<AppCardData>,
    allLoading: Boolean,
    coordinator: GameLaunchCoordinator,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onAutoScan: () -> Unit,
    autoScanning: Boolean,
    onRemove: (AppCardData) -> Unit,
    addSheet: @Composable () -> Unit,
    pinSheet: @Composable () -> Unit,
    carouselPadding: Dp,
) {
    val serial = rememberSerial()
    val skin = ironSkin()
    var query by remember { mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var eject by remember { mutableStateOf<AppCardData?>(null) }

    val source = if (segment == 0) games else allApps
    val visible = remember(query, source) {
        source.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            SearchSlot(query, { query = it })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (games.isNotEmpty()) {
                    ChamferButton("+ ADD", onAdd, tall = false, variant = ChamferVariant.Outline, modifier = Modifier.weight(1f))
                }
                ChamferButton(
                    if (autoScanning) "SCANNING…" else "AUTO SCAN",
                    onAutoScan,
                    tall = false,
                    variant = if (games.isEmpty()) ChamferVariant.Primary else ChamferVariant.Outline,
                    busy = autoScanning,
                    enabled = !autoScanning,
                    modifier = Modifier.weight(1f)
                )
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
                    onAutoScan = onAutoScan,
                    autoScanning = autoScanning,
                    modifier = Modifier.weight(1f)
                )
                else -> Rack(
                    apps = visible,
                    segment = segment,
                    onSegment = { segment = it },
                    onLaunch = { card -> coordinator.launch(card) },
                    launchBusy = coordinator.state.phase != LaunchPhase.IDLE,
                    onEject = { eject = it },
                    carouselPadding = carouselPadding,
                    modifier = Modifier.weight(1f)
                )
            }
            SerialFooter(3, "GAMES", serial)
        }

        EjectSheet(eject, onDismiss = { eject = null }, onRemove = onRemove)
        addSheet()
        pinSheet()
    }
}

@Composable
private fun RackMasterDetail(
    games: List<AppCardData>,
    allApps: List<AppCardData>,
    allLoading: Boolean,
    coordinator: GameLaunchCoordinator,
    onAdd: () -> Unit,
    onPin: () -> Unit,
    onAutoScan: () -> Unit,
    autoScanning: Boolean,
    onRemove: (AppCardData) -> Unit,
    addSheet: @Composable () -> Unit,
    pinSheet: @Composable () -> Unit,
) {
    val clack = rememberClack()
    val serial = rememberSerial()
    val skin = ironSkin()
    var query by remember { mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<AppCardData?>(games.firstOrNull()) }
    var eject by remember { mutableStateOf<AppCardData?>(null) }

    val source = if (segment == 0) games else allApps
    val visible = remember(query, source) {
        source.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    }

    LaunchedEffect(games, selected) {
        if (selected == null && games.isNotEmpty()) selected = games.first()
        if (selected != null && games.none { it.pkg == selected!!.pkg } && segment == 0) {
            selected = games.firstOrNull()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .widthIn(min = 300.dp, max = 380.dp)
                    .fillMaxHeight()
                    .padding(start = 16.dp, top = 8.dp)
                    .imePadding(),
            ) {
                SearchSlot(query, { query = it })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (games.isNotEmpty()) {
                        ChamferButton("+ ADD", onAdd, tall = false, variant = ChamferVariant.Outline)
                    }
                    ChamferButton(
                        if (autoScanning) "SCAN…" else "AUTO",
                        onAutoScan,
                        tall = false,
                        variant = if (games.isEmpty()) ChamferVariant.Primary else ChamferVariant.Outline,
                        busy = autoScanning,
                        enabled = !autoScanning,
                    )
                    ChamferButton("PIN", onPin, tall = false, variant = ChamferVariant.Outline)
                }
                Spacer(Modifier.height(10.dp))
                MachinedSegment(listOf("GAMES", "ALL APPS"), segment, onSelect = { segment = it })
                Spacer(Modifier.height(10.dp))
                when {
                    segment == 1 && allLoading -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingNeedle()
                            Spacer(Modifier.height(8.dp))
                            Text("SCANNING…", style = IronType.MonoSm, color = skin.textDim)
                        }
                    }
                    visible.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isEmpty()) "NO ITEMS FOUND" else "∅ NO MATCH",
                            style = IronType.Mono,
                            color = skin.textDim,
                        )
                    }
                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(visible.size, key = { visible[it].pkg }) { i ->
                            val app = visible[i]
                            CartridgeRow(app, selected = app.pkg == selected?.pkg) {
                                clack.tick()
                                selected = app
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))
            IronSeamColumn(brass = false)

            Column(
                Modifier.weight(1f).fillMaxHeight().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                selected?.let { app ->
                    CartridgeDetail(
                        app = app,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onLaunch = { coordinator.launch(app) },
                        launchBusy = coordinator.state.phase != LaunchPhase.IDLE,
                        onEject = { eject = app },
                    )
                } ?: EngravedPlate(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("SELECT A CARTRIDGE", style = IronType.Mono, color = skin.textDim)
                    }
                }
                SerialFooter(3, "GAMES", serial)
            }
        }

        EjectSheet(eject, onDismiss = { eject = null }, onRemove = onRemove)
        addSheet()
        pinSheet()
    }
}

@Composable
private fun CartridgeRow(app: AppCardData, selected: Boolean, onClick: () -> Unit) {
    val skin = ironSkin()
    val rowBg = when {
        selected && skin.isPaper -> Iron.Bone100
        selected -> Iron.Anvil800
        skin.isPaper -> Iron.Bone50
        else -> Iron.Anvil700
    }
    val idleCell = if (skin.isPaper) skin.hairline else Iron.Anvil600
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(IronShape.Plate)
            .background(rowBg)
            .then(if (skin.isPaper) Modifier.border(1.dp, skin.hairline, IronShape.Plate) else Modifier)
            .ironGrain(0.04f)
            .clickableNoIndication(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(if (selected) 32.dp else 0.dp)
                .background(Iron.Brass400)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(1.5.dp, Iron.Brass400, CircleShape)
                .background(app.tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp)) { app.icon() }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = IronType.Title.copy(fontSize = 15.sp), color = skin.text)
            Text(app.pkg, style = IronType.MonoSm, color = skin.textDim)
        }
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(10.dp, 7.dp)
                    .clip(IronShape.Slot)
                    .background(if (i < app.demand.cells) Iron.Signal500 else idleCell)
            )
        }
    }
}

@Composable
private fun CartridgeDetail(
    app: AppCardData,
    modifier: Modifier,
    onLaunch: () -> Unit,
    launchBusy: Boolean = false,
    onEject: () -> Unit,
) {
    val clack = rememberClack()
    val skin = ironSkin()
    var dy by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState((dy / 12f).coerceIn(0f, 8f), IronMotion.machined(), label = "detailTilt")

    EngravedPlate(
        modifier = modifier
            .graphicsLayer { rotationZ = tilt }
            .fillMaxWidth(),
        structural = true,
        caption = "PLATE 03 · ${app.pkg.uppercase().take(12)}",
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Eject drag lives on the header only — never over ALLOCATE & LAUNCH.
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(app.pkg) {
                        var lastTick = 0
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, drag ->
                                dy = (dy + drag * 0.5f).coerceIn(0f, 160.dp.toPx())
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
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(app.tint.copy(alpha = 0.12f))
                        .border(2.dp, Iron.Brass400, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(68.dp)) { app.icon() }
                }
                Spacer(Modifier.height(16.dp))
                Text(app.name, style = IronType.Display.copy(fontSize = 26.sp), color = skin.text)
                Spacer(Modifier.height(4.dp))
                Text(app.pkg, style = IronType.MonoSm, color = skin.textDim)
                Spacer(Modifier.height(16.dp))
                DemandMeter(app.demand)
                Spacer(Modifier.height(12.dp))
                Text("drag card down to eject", style = IronType.MonoSm, color = skin.textDim)
            }
            Spacer(Modifier.height(24.dp))
            ChamferButton(
                "ALLOCATE & LAUNCH",
                onLaunch,
                Modifier.fillMaxWidth(),
                busy = launchBusy,
                enabled = !launchBusy,
            )
        }
    }
}

@Composable
private fun EjectSheet(
    eject: AppCardData?,
    onDismiss: () -> Unit,
    onRemove: (AppCardData) -> Unit,
) {
    val skin = ironSkin()
    BenchSheet(visible = eject != null, onDismiss = onDismiss) {
        Text("EJECT CARTRIDGE?", style = IronType.Title.copy(fontSize = 18.sp), color = skin.text)
        Spacer(Modifier.height(4.dp))
        eject?.let {
            Text("${it.name} · ${it.pkg}", style = IronType.MonoSm, color = skin.textDim)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChamferButton("KEEP", onDismiss, Modifier.weight(1f), variant = ChamferVariant.Outline, tall = false)
            ChamferButton(
                "REMOVE",
                {
                    eject?.let(onRemove)
                    onDismiss()
                },
                Modifier.weight(1f),
                tall = false,
            )
        }
    }
}

@Composable
private fun Rack(
    apps: List<AppCardData>,
    segment: Int,
    onSegment: (Int) -> Unit,
    onLaunch: (AppCardData) -> Unit,
    launchBusy: Boolean = false,
    onEject: (AppCardData) -> Unit,
    carouselPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val clack = rememberClack()
    val scope = rememberCoroutineScope()
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
            .pointerInput(segment) {
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
            contentPadding = PaddingValues(horizontal = carouselPadding),
            pageSpacing = 12.dp,
            key = { index -> apps.getOrNull(index)?.pkg ?: index },
            beyondViewportPageCount = 1,
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - minOf(abs(offset), 1f)
            val active = page == pagerState.currentPage
            Box(
                Modifier.graphicsLayer {
                    val s = 0.8f + 0.2f * closeness
                    scaleX = s
                    scaleY = s
                    alpha = 0.4f + 0.6f * closeness
                    translationY = (8.dp * offset * offset).toPx()
                }
            ) {
                Cartridge(
                    apps[page],
                    active = active,
                    onTap = { if (!active) scope.launch { pagerState.animateScrollToPage(page) } },
                    onLaunch = { onLaunch(apps[page]) },
                    launchBusy = launchBusy,
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
    launchBusy: Boolean = false,
    onEject: () -> Unit,
) {
    val clack = rememberClack()
    var dy by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState((dy / 12f).coerceIn(0f, 8f), IronMotion.machined(), label = "tilt")
    val skin = ironSkin()
    val paper = LocalPaperSurfaces.current

    val ejectDrag = if (active) {
        Modifier.pointerInput(card.pkg) {
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
    } else {
        Modifier
    }

    val cardContent: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header is the eject-drag zone; button stays free of pointer steal.
            Column(
                Modifier.fillMaxWidth().then(ejectDrag),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                if (active) {
                    Spacer(Modifier.height(10.dp))
                    Text("drag card down to eject", style = IronType.MonoSm, color = skin.textDim)
                }
            }
            Spacer(Modifier.height(16.dp))
            ChamferButton(
                "ALLOCATE & LAUNCH",
                onLaunch,
                Modifier.fillMaxWidth(),
                busy = launchBusy,
                enabled = !launchBusy,
            )
        }
    }

    val plateModifier = Modifier
        .graphicsLayer { rotationZ = tilt }
        .pointerInput(active) { detectTapGestures { onTap() } }
    if (paper) {
        PaperPlate(modifier = plateModifier) { cardContent() }
    } else {
        EngravedPlate(
            modifier = plateModifier,
            onClick = if (active) null else onTap,
        ) { cardContent() }
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
private fun EmptyPlate(
    noGames: Boolean,
    noMatch: Boolean,
    onAdd: () -> Unit,
    onAutoScan: (() -> Unit)? = null,
    autoScanning: Boolean = false,
    modifier: Modifier = Modifier
) {
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
                    if (noGames && onAutoScan != null) {
                        Spacer(Modifier.height(10.dp))
                        ChamferButton(
                            if (autoScanning) "SCANNING…" else "AUTO SCAN ALL GAMES",
                            onAutoScan,
                            busy = autoScanning,
                            enabled = !autoScanning,
                            variant = ChamferVariant.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Auto-detects games via CATEGORY_GAME", style = IronType.MonoSm, color = dimC)
                    }
                }
            }
        }
    }
}
