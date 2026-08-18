package com.ivarna.apexcore.ui.tune

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.tune.*
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@Composable
fun TuneScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val tuneManager = remember { TuneManager.get(context) }
    val capabilities by tuneManager.capabilities.collectAsState()
    val isProbing by tuneManager.probe.isProbing.collectAsState()
    val sessionActive by tuneManager.sessionActive.collectAsState()

    // Intent cache state for fast UI feedback
    var intents by remember {
        mutableStateOf(TuneSpecs.all.associate { it.id to tuneManager.intent(it.id) })
    }

    val scheme = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }

    // Categories with at least one available spec (Empty categories hidden)
    val visibleCategories = remember(capabilities) {
        TuneCategory.values().filter { cat ->
            val specs = TuneSpecs.byCategory[cat].orEmpty()
            specs.any { spec -> capabilities[spec.id]?.available == true }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 80.dp,
                bottom = ZenDimens.bottomNavClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(ZenDimens.elementGap)
        ) {
            // Header Info
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "Real Kernel & Session Tuning",
                        color = scheme.onSurface,
                        fontSize = 18.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Capability-gated parameters safely applied during game sessions and restored on exit.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (sessionActive) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.primaryContainer,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(
                                text = "SESSION ACTIVE",
                                color = scheme.onPrimaryContainer,
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (visibleCategories.isEmpty() && !isProbing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No supported kernel nodes found on this device.",
                            color = scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(visibleCategories, key = { it.name }) { category ->
                val specs = TuneSpecs.byCategory[category].orEmpty()
                TuneCategorySection(
                    category = category,
                    specs = specs,
                    capabilities = capabilities,
                    intents = intents,
                    enabled = !isProbing,
                    onIntentChange = { id, newIntent ->
                        val success = tuneManager.setIntent(id, newIntent)
                        if (success) {
                            intents = intents.toMutableMap().apply { put(id, newIntent) }
                        }
                    }
                )
            }

            // Honest footer disclosure
            item {
                Text(
                    text = "Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.",
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = PlusJakartaSans,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // Frosted Top App Bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(64.dp)
                .hazeChild(hazeState)
                .background(scheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Game optimisation",
                        color = scheme.onSurface,
                        fontSize = 17.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { tuneManager.refreshCapabilities() },
                    enabled = !isProbing
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-probe kernel",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
