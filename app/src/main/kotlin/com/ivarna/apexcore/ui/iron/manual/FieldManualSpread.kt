package com.ivarna.apexcore.ui.iron.manual

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ManualSpread(
    isReplay: Boolean,
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
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val page = pagerState.currentPage

    LaunchedEffect(Unit) {
        while (true) {
            onProbe()
            delay(1200)
        }
    }

    IronScreen("MANUAL") {
        val skin = ironSkin()
        Box(Modifier.fillMaxSize().background(skin.canvas).ironGrain(0.05f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackArrow(skin.text, onClick = {
                        when {
                            page > 0 -> scope.launch { pagerState.animateScrollToPage(page - 1) }
                            isReplay -> onClose()
                            else -> onFinish(null)
                        }
                        Unit
                    })
                    Spacer(Modifier.weight(1f))
                    SpreadRuler(page)
                    Spacer(Modifier.weight(1f))
                    if (!isReplay) {
                        Text(
                            "SKIP",
                            style = IronType.MonoSm,
                            color = skin.textDim,
                            modifier = Modifier.clickableNoIndication { clack.row(); onFinish(null) },
                        )
                    } else {
                        Spacer(Modifier.width(40.dp))
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { spread ->
                    // Clip per spread so parallax cannot bleed previous content onto the next page.
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        when (spread) {
                            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CoverPage(pagerState, figureSize = 260.dp)
                            }
                            1 -> OpenSpread(
                                left = {
                                    FigurePageContent(
                                        figure = 1,
                                        pagerState = pagerState,
                                        pageIndex = spread,
                                        figureFactor = 0.35f,
                                        textFactor = 0.15f,
                                    )
                                },
                                right = {
                                    FigurePageContent(
                                        figure = 2,
                                        pagerState = pagerState,
                                        pageIndex = spread,
                                        figureFactor = 0.35f,
                                        textFactor = 0.15f,
                                    )
                                },
                            )
                            else -> OpenSpread(
                                left = {
                                    FigurePageContent(
                                        figure = 3,
                                        pagerState = pagerState,
                                        pageIndex = spread,
                                        figureFactor = 0.35f,
                                        textFactor = 0.15f,
                                    )
                                },
                                right = {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(24.dp)
                                    ) {
                                        Text(
                                            "04 · SYSTEM ACCESS",
                                            style = IronType.MonoSm,
                                            color = accentColor(),
                                            letterSpacing = 1.5.sp,
                                        )
                                        Text(
                                            "Elevate Your Control",
                                            style = IronType.Title.copy(fontSize = 22.sp),
                                            color = skin.text,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        KeyCard(
                                            BackendChoice.SHIZUKU, shizuku,
                                            selectedBackend == BackendChoice.SHIZUKU,
                                            onPaper = skin.isPaper,
                                            badge = "RECOMMENDED",
                                            onUse = { onSelect(BackendChoice.SHIZUKU) },
                                            onConfigure = onConfigureShizuku,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        KeyCard(
                                            BackendChoice.ROOT, root,
                                            selectedBackend == BackendChoice.ROOT,
                                            onPaper = skin.isPaper,
                                            badge = null,
                                            onUse = { onSelect(BackendChoice.ROOT) },
                                            onConfigure = onGrantRoot,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DoodleStar()
                                            Spacer(Modifier.width(8.dp))
                                            Text("~ pick your key", style = IronType.Hand, color = skin.textDim)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                val cta = when (page) {
                    0 -> "GET STARTED"
                    2 -> "ENTER THE WORKSHOP"
                    else -> "CONTINUE"
                }
                ChamferButton(
                    cta,
                    {
                        clack.confirm()
                        if (page < 2) scope.launch { pagerState.animateScrollToPage(page + 1) }
                        else onFinish(selectedBackend)
                    },
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun OpenSpread(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 10.dp)) { left() }
        Box(Modifier.width(30.dp).fillMaxHeight()) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(Iron.Ink900.copy(alpha = 0.06f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(Iron.Ink900.copy(alpha = 0.06f))
            )
            BindingLane(Modifier.align(Alignment.Center).width(22.dp).fillMaxHeight())
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp)) { right() }
    }
}

@Composable
private fun SpreadRuler(page: Int) {
    val active = when (page) {
        0 -> setOf(0)
        1 -> setOf(1, 2)
        else -> setOf(3, 4)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(4.dp, 16.dp)
                    .clip(IronShape.Slot)
                    .background(
                        if (i in active) ironSkin().text else ironSkin().text.copy(alpha = 0.2f)
                    )
            )
        }
    }
}
