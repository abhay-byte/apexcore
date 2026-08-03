package com.ivarna.apexcore.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.ivarna.apexcore.ui.components.ZenDialog
import com.ivarna.apexcore.ui.components.ZenTextField
import com.ivarna.apexcore.ui.components.zenDialogSheet
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens

@Composable
fun AddGamePickerDialog(
    gameManager: GameManager,
    onAdded: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<GameInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateMapOf<String, Boolean>() }

    val alreadyAdded = remember {
        gameManager.load().map { it.pkg }.toSet()
    }

    LaunchedEffect(Unit) {
        isLoading = true
        allApps = gameManager.listInstallableApps(context)
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery, alreadyAdded) {
        allApps.filter { app ->
            app.pkg !in alreadyAdded && (
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.pkg.contains(searchQuery, ignoreCase = true)
            )
        }
    }

    val scheme = MaterialTheme.colorScheme
    val dialogShape = RoundedCornerShape(28.dp)
    val rowShape = RoundedCornerShape(ZenDimens.roundedLg)

    ZenDialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
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
                    text = "ADD TO LIBRARY",
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select apps to register in library",
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp,
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
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.pkg }) { app ->
                                val isSelected = selectedPackages[app.pkg] == true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zenGlassBackground(
                                            shape = rowShape,
                                            fill = if (isSelected) {
                                                scheme.primary.copy(alpha = 0.08f)
                                            } else {
                                                scheme.surfaceContainerLow.copy(alpha = 0.85f)
                                            },
                                            borderColor = if (isSelected) {
                                                scheme.primary.copy(alpha = 0.3f)
                                            } else {
                                                scheme.outlineVariant.copy(alpha = 0.6f)
                                            }
                                        )
                                        .clickable {
                                            selectedPackages[app.pkg] = !isSelected
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // App Icon
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

                                    // Name + Package
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.name,
                                            color = scheme.onSurface,
                                            fontSize = 14.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = app.pkg,
                                            color = scheme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                            fontFamily = PlusJakartaSans,
                                            maxLines = 1
                                        )
                                    }

                                    // Checkbox
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { selectedPackages[app.pkg] = it },
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

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val footerShape = RoundedCornerShape(ZenDimens.roundedLg)
                    // Cancel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .zenGlassBackground(
                                shape = footerShape,
                                fill = scheme.surfaceContainerLow.copy(alpha = 0.9f),
                                borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                            )
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = scheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = PlusJakartaSans
                        )
                    }

                    val selectedCount = selectedPackages.filterValues { it }.size
                    val isAddEnabled = selectedCount > 0

                    // Add N
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(footerShape)
                            .background(if (isAddEnabled) scheme.primary else scheme.outlineVariant)
                            .clickable(enabled = isAddEnabled) {
                                val toAdd = filteredApps.filter { selectedPackages[it.pkg] == true }
                                gameManager.addAll(toAdd)
                                onAdded()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ADD${if (selectedCount > 0) " $selectedCount" else ""}",
                            color = if (isAddEnabled) scheme.onPrimary else scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
    }
}
