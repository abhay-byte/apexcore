package com.ivarna.apexcore.ui.iron.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.iron.*
import com.ivarna.apexcore.ui.iron.manual.KeyCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ── Setup sheet: the Key Selector (replaces SetupDialog; same contract) ── */
@Composable
fun SystemAccessSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    shizuku: KeyStatus,
    root: KeyStatus,
    selected: BackendChoice?,
    onProbe: () -> Unit,
    onSelect: (BackendChoice) -> Unit,
    onConfigureShizuku: () -> Unit,
    onGrantRoot: () -> Unit,
) {
    LaunchedEffect(visible) {
        while (visible) {
            onProbe()
            delay(1200)
        }
    }
    var localSel by remember { mutableStateOf<BackendChoice?>(null) }
    val sel = localSel ?: selected

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        StampLabel("SYSTEM ACCESS", StampInk.Signal, slam = true)
        Spacer(Modifier.height(6.dp))
        Text(
            "Deep freeze (BOOST) requires Shizuku or Root access.",
            style = IronType.Caption, color = Iron.Bone500
        )
        Spacer(Modifier.height(16.dp))
        KeyCard(
            BackendChoice.SHIZUKU, shizuku, sel == BackendChoice.SHIZUKU, onPaper = false,
            badge = "RECOMMENDED",
            onUse = { onSelect(BackendChoice.SHIZUKU); localSel = BackendChoice.SHIZUKU },
            onConfigure = onConfigureShizuku
        )
        Spacer(Modifier.height(12.dp))
        KeyCard(
            BackendChoice.ROOT, root, sel == BackendChoice.ROOT, onPaper = false, badge = null,
            onUse = { onSelect(BackendChoice.ROOT); localSel = BackendChoice.ROOT },
            onConfigure = onGrantRoot
        )
        LaunchedEffect(localSel) {
            if (localSel != null) {
                delay(450)
                onDismiss()
            }
        }
    }
}

/* ── Pin Apps sheet: searchable list + brass pin toggles + IndexRail ── */
@Composable
fun PinAppsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    apps: List<PickerApp>,
    pinned: Set<String>,
    onTogglePin: (String) -> Unit,
) {
    val clack = rememberClack()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Derived filtering: avoid recompute during sheet slide animation jank
    val visibleApps by remember(query, apps) {
        derivedStateOf {
            if (query.isBlank()) apps else apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
        }
    }
    val letterIndex = remember(apps) {
        val map = mutableMapOf<Char, Int>()
        var last: Char? = null
        apps.forEachIndexed { i, a ->
            val c = a.name.firstOrNull()?.uppercaseChar() ?: '#'
            if (c != last) {
                map[c] = i
                last = c
            }
        }
        map
    }

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        RisoText("PIN APPS", IronType.Title.copy(fontSize = 18.sp))
        Text(
            "PINNED APPS ARE NEVER FROZEN · ${pinned.size} PINNED",
            style = IronType.MonoSm, color = Iron.Bone500
        )
        Spacer(Modifier.height(12.dp))
        SearchSlot(query, { query = it })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(360.dp)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(
                    count = visibleApps.size,
                    key = { i -> visibleApps[i].pkg },
                    contentType = { "pinRow" }
                ) { i ->
                    val app = visibleApps[i]
                    PickerRow(app, pinned.contains(app.pkg)) { onTogglePin(app.pkg) }
                }
            }
            IndexRail(onLetter = { c ->
                letterIndex[c]?.let { scope.launch { listState.animateScrollToItem(it) } }
            })
        }
        Spacer(Modifier.height(12.dp))
        ChamferButton(
            if (pinned.isEmpty()) "DONE" else "DONE · ${pinned.size}",
            {
                clack.confirm()
                onDismiss()
            },
            Modifier.fillMaxWidth(),
            tall = false
        )
    }
}

/* ── Add Game sheet: multi-select + ADDED stamp ── */
@Composable
fun AddGameSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    apps: List<PickerApp>,
    alreadyAdded: Set<String>,
    onAdd: (List<PickerApp>) -> Unit,
) {
    val clack = rememberClack()
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var stamped by remember { mutableStateOf(false) }
    val visibleApps by remember(query, apps) {
        derivedStateOf {
            if (query.isBlank()) apps else apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
        }
    }

    BenchSheet(visible = visible, onDismiss = onDismiss) {
        RisoText("ADD GAMES", IronType.Title.copy(fontSize = 18.sp))
        Text("BUILD YOUR RACK · ${apps.size} INSTALLED", style = IronType.MonoSm, color = Iron.Bone500)
        Spacer(Modifier.height(12.dp))
        SearchSlot(query, { query = it })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.height(360.dp)) {
            items(
                count = visibleApps.size,
                key = { i -> visibleApps[i].pkg },
                contentType = { "addRow" }
            ) { i ->
                val app = visibleApps[i]
                val isPicked = picked.contains(app.pkg) || alreadyAdded.contains(app.pkg)
                PickerRow(app, isPicked) {
                    if (alreadyAdded.contains(app.pkg)) return@PickerRow
                    if (picked.contains(app.pkg)) picked.remove(app.pkg) else picked.add(app.pkg)
                    clack.off()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box {
            ChamferButton(
                if (picked.isEmpty()) "CANCEL" else "ADD ${picked.size}",
                {
                    if (picked.isEmpty()) {
                        onDismiss()
                        return@ChamferButton
                    }
                    onAdd(apps.filter { picked.contains(it.pkg) })
                    clack.confirm()
                    stamped = true
                },
                Modifier.fillMaxWidth(),
                tall = false
            )
            if (stamped) Box(Modifier.align(Alignment.Center)) {
                StampLabel("ADDED ${picked.size}", StampInk.Phosphor, slam = true)
                LaunchedEffect(Unit) {
                    delay(500)
                    stamped = false
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PickerRow(app: PickerApp, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.5.dp, Iron.Anvil500, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(28.dp)) { app.icon() }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = IronType.Body.copy(color = Iron.Bone100))
            Text(app.pkg, style = IronType.MonoSm, color = Iron.Bone500)
        }
        MachinedToggleCompact(on, onToggle = { onToggle() })
    }
}

@Composable
fun MachinedToggleCompact(checked: Boolean, onToggle: () -> Unit) {
    val clack = rememberClack()
    val x = remember { Animatable(if (checked) 1f else 0f) }
    val wob = remember { Animatable(0f) }
    LaunchedEffect(checked) {
        x.animateTo(if (checked) 1f else 0f, IronMotion.machined())
        wob.snapTo(0f)
        wob.animateTo(0f, keyframes {
            durationMillis = 90
            4f at 30
            -4f at 60
        })
    }
    Box(
        Modifier
            .size(40.dp, 22.dp)
            .clip(IronShape.Slot)
            .background(if (checked) Iron.Brass400.copy(alpha = 0.35f) else Iron.Anvil600)
            .border(1.dp, if (checked) Iron.Brass400 else Iron.Anvil600, IronShape.Slot)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (checked) clack.off() else clack.confirm()
                onToggle()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(18.dp)
                .graphicsLayer {
                    translationX = 18.dp.toPx() * x.value
                    rotationZ = wob.value
                }
                .clip(CircleShape)
                .background(if (checked) Iron.Brass400 else Iron.Bone500)
        )
    }
}
