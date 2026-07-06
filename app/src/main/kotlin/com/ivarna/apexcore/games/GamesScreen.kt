package com.ivarna.apexcore.games

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GamesScreen(
    gameManager: GameManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var games by remember { mutableStateOf(gameManager.load()) }
    var manualPackage by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    
    val pm = context.packageManager

    fun resolveLabel(pkg: String): String? = try {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai)?.toString() ?: pkg
    } catch (_: PackageManager.NameNotFoundException) { null }

    fun refreshList() {
        games = gameManager.load()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GAMES",
                    color = TextTitle,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Tap to optimize and launch",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            
            // Scan Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isScanning) AccentWarning.copy(alpha = 0.2f) else AccentPrimary.copy(alpha = 0.15f))
                    .clickable(enabled = !isScanning) {
                        isScanning = true
                        coroutineScope.launch {
                            val detected = gameManager.detect(context)
                            if (detected.isEmpty()) {
                                Toast.makeText(context, "No new games found", Toast.LENGTH_SHORT).show()
                            } else {
                                gameManager.acceptDetected(context)
                                Toast.makeText(context, "Added ${detected.size} game(s)", Toast.LENGTH_SHORT).show()
                            }
                            refreshList()
                            isScanning = false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isScanning) "SCANNING…" else "SCAN",
                    color = if (isScanning) AccentWarning else AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Game List
        Box(modifier = Modifier.weight(1f)) {
            if (games.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EMPTY",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your gaming library is empty.\nScan for games or add a package manually.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(games, key = { it.pkg }) { game ->
                        GameCard(
                            game = game,
                            onClick = {
                                Toast.makeText(context, "Optimizing & launching ${game.name}…", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val result = GameLauncher.launch(context, game.pkg)
                                    if (!result.success) {
                                        Toast.makeText(context, "Failed: ${result.error}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLongClick = {
                                gameManager.remove(game.pkg)
                                refreshList()
                                Toast.makeText(context, "Removed ${game.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Add Package Input at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = manualPackage,
                onValueChange = { manualPackage = it },
                textStyle = TextStyle(color = TextTitle, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                cursorBrush = SolidColor(AccentPrimary),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgDark)
                    .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { innerTextField ->
                    if (manualPackage.isEmpty()) {
                        Text("com.example.game", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary)))
                    .clickable {
                        val pkg = manualPackage.trim()
                        if (pkg.isNotEmpty()) {
                            val name = resolveLabel(pkg)
                            if (name != null) {
                                gameManager.add(pkg, name, autoDetected = false)
                                manualPackage = ""
                                refreshList()
                            } else {
                                Toast.makeText(context, "Package $pkg not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text("ADD", color = TextTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GameCard(game: GameInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tag = if (game.isAutoDetected) "AUTO" else "MANUAL"
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(SurfaceCard.copy(alpha=0.6f), SurfaceCard)))
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = game.name,
                    color = TextTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BorderGlass)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        color = TextMuted,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = game.pkg,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(AccentPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(">", color = AccentPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}
