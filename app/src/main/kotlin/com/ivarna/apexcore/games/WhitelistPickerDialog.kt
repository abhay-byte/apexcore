package com.ivarna.apexcore.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.freeze.WhitelistStore
import com.ivarna.apexcore.ui.theme.*

@Composable
fun WhitelistPickerDialog(
    gameManager: GameManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<GameInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(WhitelistStore.allPinned(context)) }

    LaunchedEffect(Unit) {
        isLoading = true
        allApps = gameManager.listInstallableApps(context)
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery) {
        allApps.filter { app ->
            app.name.contains(searchQuery, ignoreCase = true) ||
                app.pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceGlass)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderGlass, RoundedCornerShape(28.dp))
                    .clickable(enabled = false) {} // block click propagation
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "PIN APPS",
                    color = TextTitle,
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pinned apps are never frozen · ${pinned.size} pinned",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = SpaceGrotesk
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Search Input Field
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = TextTitle, fontSize = 13.sp, fontFamily = JetBrainsMono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    cursorBrush = SolidColor(AccentPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("SEARCH APPS...", color = TextMuted, fontSize = 13.sp, fontFamily = JetBrainsMono)
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // List Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = AccentPrimary)
                    } else if (filteredApps.isEmpty()) {
                        Text(
                            text = "NO APPS FOUND",
                            color = TextMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.pkg }) { app ->
                                val isPinned = pinned.contains(app.pkg)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isPinned) AccentPrimary.copy(alpha = 0.08f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (isPinned) AccentPrimary.copy(alpha = 0.3f) else BorderGlass,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            WhitelistStore.setPinned(context, app.pkg, !isPinned)
                                            pinned = WhitelistStore.allPinned(context)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(BgDark)
                                            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                            .padding(6.dp)
                                    ) {
                                        AppIcon(
                                            packageName = app.pkg,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.name,
                                            color = TextTitle,
                                            fontSize = 14.sp,
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = app.pkg,
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            fontFamily = JetBrainsMono,
                                            maxLines = 1
                                        )
                                    }

                                    Checkbox(
                                        checked = isPinned,
                                        onCheckedChange = {
                                            WhitelistStore.setPinned(context, app.pkg, it)
                                            pinned = WhitelistStore.allPinned(context)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = AccentPrimary,
                                            uncheckedColor = TextMuted,
                                            checkmarkColor = BgDark
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentPrimary)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DONE",
                        color = BgDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
