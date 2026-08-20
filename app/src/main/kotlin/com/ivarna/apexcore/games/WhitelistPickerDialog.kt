package com.ivarna.apexcore.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.freeze.WhitelistStore
import com.ivarna.apexcore.ui.components.ZenDialog
import com.ivarna.apexcore.ui.components.ZenTextField
import com.ivarna.apexcore.ui.components.zenDialogSheet
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenType

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

    val scheme = MaterialTheme.colorScheme
    val dialogShape = RoundedCornerShape(28.dp)
    val rowShape = RoundedCornerShape(ZenDimens.roundedLg)

    ZenDialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    // Window is already sized 0.92×/max 560dp by ZenDialog; no horizontal
                    // padding so the glass card fills that width exactly, inner 24dp keeps inset.
                    .padding(vertical = 24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .zenGlassBackground(
                        shape = dialogShape,
                        fill = scheme.surfaceContainerLowest.copy(alpha = 0.96f),
                        borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    .zenDialogSheet()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "PIN APPS",
                    color = scheme.onSurface,
                    style = ZenType.body,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pinned apps are never frozen · ${pinned.size} pinned",
                    color = scheme.onSurfaceVariant,
                    style = ZenType.label,
                    fontFamily = PlusJakartaSans
                )
                Spacer(modifier = Modifier.height(16.dp))

                ZenTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search apps…",
                    modifier = Modifier.fillMaxWidth()
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
                        CircularProgressIndicator(color = scheme.primary)
                    } else if (filteredApps.isEmpty()) {
                        Text(
                            text = "NO APPS FOUND",
                            color = scheme.onSurfaceVariant,
                            fontFamily = PlusJakartaSans,
                            style = MaterialTheme.typography.labelSmall,
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
                                        .zenGlassBackground(
                                            shape = rowShape,
                                            fill = if (isPinned) {
                                                scheme.primary.copy(alpha = 0.08f)
                                            } else {
                                                scheme.surfaceContainerLow.copy(alpha = 0.85f)
                                            },
                                            borderColor = if (isPinned) {
                                                scheme.primary.copy(alpha = 0.3f)
                                            } else {
                                                scheme.outlineVariant.copy(alpha = 0.6f)
                                            }
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
                                            .zenGlassBackground(
                                                shape = RoundedCornerShape(ZenDimens.roundedMd),
                                                fill = scheme.surfaceContainerLowest.copy(alpha = 0.92f),
                                                borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                                            )
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
                                            color = scheme.onSurface,
                                            style = ZenType.body,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = app.pkg,
                                            color = scheme.onSurfaceVariant,
                                            style = ZenType.overline,
                                            fontFamily = PlusJakartaSans,
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
                                            checkedColor = scheme.primary,
                                            uncheckedColor = scheme.onSurfaceVariant,
                                            checkmarkColor = scheme.onPrimary
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
                        .clip(RoundedCornerShape(ZenDimens.roundedLg))
                        .background(scheme.primary)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DONE",
                        color = scheme.onPrimary,
                        style = ZenType.label,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
    }
}