package com.ivarna.apexcore.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.ivarna.apexcore.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    gameManager: GameManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    
    // Toggle tab state
    var showAllApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // App loading states
    var customGames by remember { mutableStateOf(gameManager.load()) }
    var allAppsList by remember { mutableStateOf<List<GameInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    // Launch animation states
    var isLaunching by remember { mutableStateOf(false) }
    var launchingPkg by remember { mutableStateOf("") }

    // Load all apps in background
    LaunchedEffect(showAllApps) {
        if (showAllApps && allAppsList.isEmpty()) {
            isLoadingApps = true
            allAppsList = withContext(Dispatchers.IO) {
                getAllInstalledApps(context)
            }
            isLoadingApps = false
        }
    }

    // Dynamic filtered list based on toggle & search
    val currentList = remember(showAllApps, customGames, allAppsList, searchQuery) {
        val baseList = if (showAllApps) allAppsList else customGames
        if (searchQuery.trim().isEmpty()) {
            baseList
        } else {
            baseList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.pkg.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Horizontal Pager State
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { currentList.size }
    )

    // Trigger haptic CLOCK_TICK on page changes
    var lastSignaledPage by remember { mutableStateOf(-1) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastSignaledPage && currentList.isNotEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastSignaledPage = pagerState.currentPage
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Minimalist Search and Toggle Row
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        .background(SurfaceCard)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("SEARCH PACKAGES...", color = TextMuted, fontSize = 13.sp, fontFamily = JetBrainsMono)
                        }
                        innerTextField()
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Mode Toggle Bar (GAMES vs ALL APPS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceCard)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (!showAllApps) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (!showAllApps) 1.dp else 0.dp,
                                color = if (!showAllApps) AccentPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { showAllApps = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GAMES",
                            color = if (!showAllApps) AccentPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (showAllApps) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (showAllApps) 1.dp else 0.dp,
                                color = if (showAllApps) AccentPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { showAllApps = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ALL APPS",
                            color = if (showAllApps) AccentPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Carousel / Treadmill Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingApps) {
                    CircularProgressIndicator(color = AccentPrimary)
                } else if (currentList.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "NO ITEMS FOUND",
                            color = TextMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    val density = LocalDensity.current
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 70.dp)
                    ) { page ->
                        val game = currentList.getOrNull(page) ?: return@HorizontalPager
                        
                        // Calculate page transformations
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val absOffset = pageOffset.absoluteValue
                        
                        val scale = lerp(0.8f, 1.0f, 1f - absOffset.coerceIn(0f, 1f))
                        val alpha = lerp(0.4f, 1.0f, 1f - absOffset.coerceIn(0f, 1f))
                        val zIndex = if (absOffset < 0.5f) 10f else 1f
                        
                        val customThemeColor = remember(game.pkg) {
                            getIconThemeColor(context, game.pkg)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(horizontal = 8.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                    // 3D Parabolic dip
                                    translationY = absOffset * 35.dp.toPx()
                                    cameraDistance = 8 * density.density
                                    rotationY = pageOffset * -18f
                                }
                                .zIndex(zIndex)
                                .clip(RoundedCornerShape(32.dp))
                                .background(SurfaceCard)
                                .border(
                                    width = if (absOffset < 0.2f) 1.5.dp else 1.dp,
                                    brush = if (absOffset < 0.2f) {
                                        Brush.verticalGradient(listOf(AccentPrimary, AccentSecondary))
                                    } else {
                                        SolidColor(BorderGlass)
                                    },
                                    shape = RoundedCornerShape(32.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(customThemeColor, Color.Transparent),
                                            radius = 350f
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    // App Icon Wrapper
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(SurfaceCard)
                                            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
                                            .padding(10.dp)
                                    ) {
                                        AppIcon(
                                            packageName = game.pkg,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // App Name
                                    Text(
                                        text = game.name,
                                        color = TextTitle,
                                        fontSize = 18.sp,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Package Name
                                    Text(
                                        text = game.pkg,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = JetBrainsMono,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Resource Demand Meter
                                    val demand = remember(game.pkg) { getResourceDemand(game.pkg) }
                                    val demandColor = when (demand) {
                                        "HIGH" -> AccentWarning
                                        "MEDIUM" -> AccentPrimary
                                        else -> AccentSuccess
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "RESOURCE DEMAND",
                                            color = TextMuted,
                                            fontSize = 8.sp,
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(demandColor.copy(alpha = 0.15f))
                                                .border(1.dp, demandColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = demand,
                                                color = demandColor,
                                                fontSize = 9.sp,
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // bottom Launch Trigger Button
            val activeApp = currentList.getOrNull(pagerState.currentPage)
            val buttonBgModifier = if (activeApp == null) {
                Modifier.background(BorderGlass)
            } else {
                Modifier.background(Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary)))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(buttonBgModifier)
                    .clickable(enabled = activeApp != null) {
                        activeApp?.let { app ->
                            launchingPkg = app.pkg
                            isLaunching = true
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ALLOCATE & LAUNCH",
                    color = if (activeApp == null) TextMuted else TextTitle,
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Topographic sweep and launch overlay
        if (isLaunching) {
            TopographicGridSweep(
                packageName = launchingPkg,
                trigger = isLaunching,
                onComplete = {
                    isLaunching = false
                    Toast.makeText(context, "Purging memory & launching app…", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        val result = GameLauncher.launch(context, launchingPkg)
                        if (!result.success) {
                            Toast.makeText(context, "Launch failed: ${result.error}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun TopographicGridSweep(
    packageName: String,
    trigger: Boolean,
    onComplete: () -> Unit
) {
    if (!trigger) return
    val sweepProgress = remember { Animatable(0f) }
    
    val iconScale = remember { Animatable(1f) }
    val iconAlpha = remember { Animatable(1f) }
    
    LaunchedEffect(trigger) {
        launch {
            sweepProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
            onComplete()
        }
        launch {
            iconScale.animateTo(2.8f, tween(800, easing = EaseInCubic))
        }
        launch {
            iconAlpha.animateTo(0f, tween(800, easing = EaseInCubic))
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gridSpacing = 40.dp.toPx()
            
            // Background dim
            drawRect(color = Color.Black.copy(alpha = 0.82f * sweepProgress.value))
            
            // Draw grid lines
            val lineCountX = (width / gridSpacing).toInt()
            val lineCountY = (height / gridSpacing).toInt()
            
            // Sweep scanning line
            val scanY = height * sweepProgress.value
            
            for (i in 0..lineCountX) {
                val x = i * gridSpacing
                drawLine(
                    color = AccentPrimary.copy(alpha = 0.18f * (1f - sweepProgress.value)),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 0..lineCountY) {
                val y = i * gridSpacing
                drawLine(
                    color = AccentPrimary.copy(alpha = 0.18f * (1f - sweepProgress.value)),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // Horizontal scan line glowing Cryo-Blue
            drawLine(
                color = AccentPrimary.copy(alpha = 0.85f * (1f - sweepProgress.value)),
                start = Offset(0f, scanY),
                end = Offset(width, scanY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Rising scaling app icon in center of overlay
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(iconScale.value)
                    .alpha(iconAlpha.value)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceCard)
                    .border(1.5.dp, AccentPrimary, RoundedCornerShape(24.dp))
                    .padding(14.dp)
            ) {
                AppIcon(
                    packageName = packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    val drawable = remember(packageName) {
        try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            context.getDrawable(android.R.drawable.sym_def_app_icon)
        }
    }
    
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                setImageDrawable(drawable)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier
    )
}

fun getIconThemeColor(context: Context, pkg: String): Color {
    try {
        val pm = context.packageManager
        val icon = pm.getApplicationIcon(pkg)
        val bitmap = when (icon) {
            is android.graphics.drawable.BitmapDrawable -> icon.bitmap
            else -> {
                val bmp = android.graphics.Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                bmp
            }
        }
        val p1 = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val p2 = bitmap.getPixel(bitmap.width / 3, bitmap.height / 3)
        val p3 = bitmap.getPixel(2 * bitmap.width / 3, 2 * bitmap.height / 3)
        
        val r = (android.graphics.Color.red(p1) + android.graphics.Color.red(p2) + android.graphics.Color.red(p3)) / 3
        val g = (android.graphics.Color.green(p1) + android.graphics.Color.green(p2) + android.graphics.Color.green(p3)) / 3
        val b = (android.graphics.Color.blue(p1) + android.graphics.Color.blue(p2) + android.graphics.Color.blue(p3)) / 3
        
        return Color(r, g, b).copy(alpha = 0.22f)
    } catch (_: Throwable) {
        return AccentPrimary.copy(alpha = 0.12f)
    }
}

fun getResourceDemand(pkg: String): String {
    val lower = pkg.lowercase()
    return when {
        lower.contains("genshin") || lower.contains("pubg") || lower.contains("cod") || 
        lower.contains("fortnite") || lower.contains("benchmark") || lower.contains("heavy") || 
        lower.contains("engine") || lower.contains("unity") || lower.contains("unreal") ||
        lower.contains("bench") || lower.contains("antutu") || lower.contains("geekbench") -> "HIGH"
        lower.contains("game") || lower.contains("play") || lower.contains("social") || 
        lower.contains("browser") || lower.contains("video") || lower.contains("render") ||
        lower.contains("editor") || lower.contains("youtube") || lower.contains("netflix") -> "MEDIUM"
        else -> "LOW"
    }
}

fun getAllInstalledApps(context: Context): List<GameInfo> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps.filter { app ->
        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        app.packageName != context.packageName && (!isSystem || isUpdatedSystem)
    }.map { app ->
        val label = pm.getApplicationLabel(app)?.toString() ?: app.packageName
        GameInfo(app.packageName, label, isAutoDetected = false)
    }.sortedBy { it.name }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)
