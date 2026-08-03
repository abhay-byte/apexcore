package com.ivarna.apexcore.games

import android.content.Context
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ivarna.apexcore.ui.components.ZenTextField
import com.ivarna.apexcore.ui.components.zenBloom
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenColors
import com.ivarna.apexcore.ui.theme.ZenDimens
import com.ivarna.apexcore.ui.theme.ZenIcons
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
    var showAddPicker by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }
    
    // App loading states
    var customGames by remember { mutableStateOf(gameManager.load()) }
    var allAppsList by remember { mutableStateOf<List<GameInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    // Launch animation states
    var isLaunching by remember { mutableStateOf(false) }
    var launchingPkg by remember { mutableStateOf("") }

    // Load all apps in background
    LaunchedEffect(showAllApps) {
        if (showAllApps) {
            isLoadingApps = true
            allAppsList = gameManager.listInstallableApps(context)
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

    // Coerce pagerState when list shrinks
    LaunchedEffect(currentList.size) {
        if (pagerState.currentPage >= currentList.size && currentList.isNotEmpty()) {
            try {
                pagerState.scrollToPage(currentList.size - 1)
            } catch (_: Throwable) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Soft search + action row
            Column(modifier = Modifier.fillMaxWidth()) {
                val scheme = MaterialTheme.colorScheme
                val glassShape = RoundedCornerShape(ZenDimens.roundedLg)
                val glassFill = scheme.surfaceContainerLowest.copy(alpha = 0.92f)
                val glassBorder = scheme.outlineVariant.copy(alpha = 0.6f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZenTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search packages…",
                        modifier = Modifier.weight(1f)
                    )

                    // Show add button next to search bar if library is not empty
                    if (customGames.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .zenGlassBackground(
                                    shape = glassShape,
                                    fill = glassFill,
                                    borderColor = glassBorder
                                )
                                .clickable { showAddPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add games",
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Pin apps (never freeze) button
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .zenGlassBackground(
                                shape = glassShape,
                                fill = glassFill,
                                borderColor = glassBorder
                            )
                            .clickable { showPinPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = ZenIcons.PushPin,
                                contentDescription = "Pin apps",
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PIN",
                                color = scheme.primary,
                                fontSize = 7.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Toggle Bar (GAMES vs ALL APPS) — soft glass segment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zenGlassBackground(
                            shape = RoundedCornerShape(50),
                            fill = glassFill,
                            borderColor = glassBorder
                        )
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (!showAllApps) scheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (!showAllApps) 1.dp else 0.dp,
                                color = if (!showAllApps) scheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { showAllApps = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GAMES",
                            color = if (!showAllApps) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (showAllApps) scheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (showAllApps) 1.dp else 0.dp,
                                color = if (showAllApps) scheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { showAllApps = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ALL APPS",
                            color = if (showAllApps) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontFamily = PlusJakartaSans,
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else if (currentList.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "NO ITEMS FOUND",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        if (!showAllApps) {
                            val emptyCtaShape = RoundedCornerShape(ZenDimens.roundedLg)
                            Spacer(modifier = Modifier.height(24.dp))
                            // CTA: ADD GAMES
                            Box(
                                modifier = Modifier
                                    .clip(emptyCtaShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showAddPicker = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "ADD GAMES",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = PlusJakartaSans
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // CTA: SCAN FOR GAMES
                            Box(
                                modifier = Modifier
                                    .zenGlassBackground(
                                        shape = emptyCtaShape,
                                        fill = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            val oldSize = customGames.size
                                            gameManager.acceptDetected(context)
                                            customGames = gameManager.load()
                                            val added = customGames.size - oldSize
                                            if (added > 0) {
                                                Toast.makeText(context, "Added $added games", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No new games found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "SCAN FOR GAMES",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = PlusJakartaSans
                                )
                            }
                        }
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
                        val cardShape = RoundedCornerShape(32.dp)
                        val cardActive = absOffset < 0.2f
                        val scheme = MaterialTheme.colorScheme

                        Box(
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
                                .zenGlassBackground(
                                    shape = cardShape,
                                    fill = scheme.surfaceContainerLowest.copy(alpha = 0.94f),
                                    borderColor = if (cardActive) {
                                        scheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        scheme.outlineVariant.copy(alpha = 0.6f)
                                    },
                                    borderWidth = if (cardActive) 1.5.dp else 1.dp
                                )
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(customThemeColor, Color.Transparent),
                                        radius = 350f
                                    )
                                )
                                .combinedClickable(
                                    onClick = {
                                        launchingPkg = game.pkg
                                        isLaunching = true
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        if (showAllApps) {
                                            if (customGames.any { it.pkg == game.pkg }) {
                                                Toast.makeText(context, "Already in library", Toast.LENGTH_SHORT).show()
                                            } else {
                                                gameManager.add(game.pkg, game.name, false)
                                                customGames = gameManager.load()
                                                Toast.makeText(context, "Added to library", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            gameManager.remove(game.pkg)
                                            customGames = gameManager.load()
                                            Toast.makeText(context, "Removed from library", Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
                                        .zenGlassBackground(
                                            shape = RoundedCornerShape(22.dp),
                                            fill = scheme.surfaceContainerLowest.copy(alpha = 0.92f),
                                            borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                                        )
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
                                    color = scheme.onSurface,
                                    fontSize = 18.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Package Name
                                Text(
                                    text = game.pkg,
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    fontSize = 11.sp,
                                    fontFamily = PlusJakartaSans,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Resource Demand Meter
                                val demand = remember(game.pkg) { getResourceDemand(game.pkg) }
                                val demandColor = when (demand) {
                                    "HIGH" -> scheme.secondary
                                    "MEDIUM" -> scheme.primary
                                    else -> scheme.primary
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "RESOURCE DEMAND",
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        fontSize = 8.sp,
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(ZenDimens.roundedSm))
                                            .background(demandColor.copy(alpha = 0.15f))
                                            .border(1.dp, demandColor.copy(alpha = 0.4f), RoundedCornerShape(ZenDimens.roundedSm))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = demand,
                                            color = demandColor,
                                            fontSize = 9.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // bottom Launch Trigger — pebble-style CTA
            val activeApp = currentList.getOrNull(pagerState.currentPage)
            val pebbleShape = RoundedCornerShape(32.dp)
            val scheme = MaterialTheme.colorScheme
            val buttonBgModifier = if (activeApp == null) {
                Modifier.background(scheme.outlineVariant)
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(scheme.primary, scheme.primaryContainer)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .then(
                        if (activeApp != null) Modifier.zenBloom(pebbleShape) else Modifier
                    )
                    .clip(pebbleShape)
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
                    color = if (activeApp == null) {
                        scheme.onSurfaceVariant.copy(alpha = 0.72f)
                    } else {
                        scheme.onPrimary
                    },
                    fontSize = 13.sp,
                    fontFamily = PlusJakartaSans,
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

        if (showAddPicker) {
            AddGamePickerDialog(
                gameManager = gameManager,
                onAdded = {
                    customGames = gameManager.load()
                },
                onDismiss = {
                    showAddPicker = false
                }
            )
        }

        if (showPinPicker) {
            WhitelistPickerDialog(
                gameManager = gameManager,
                onDismiss = {
                    showPinPicker = false
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
    
    val primaryColor = MaterialTheme.colorScheme.primary
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
                    color = primaryColor.copy(alpha = 0.18f * (1f - sweepProgress.value)),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 0..lineCountY) {
                val y = i * gridSpacing
                drawLine(
                    color = primaryColor.copy(alpha = 0.18f * (1f - sweepProgress.value)),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            // Horizontal scan line
            drawLine(
                color = primaryColor.copy(alpha = 0.85f * (1f - sweepProgress.value)),
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
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

private val iconCache = LruCache<String, ImageBitmap>(120)

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageState = produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        val cached = iconCache.get(packageName)
        if (cached != null) {
            value = cached
            return@produceState
        }
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = when (drawable) {
                    is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                    else -> {
                        val bmp = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                }
                val imageBitmap = bitmap.asImageBitmap()
                iconCache.put(packageName, imageBitmap)
                value = imageBitmap
            } catch (_: Throwable) {
                try {
                    val fallbackDrawable = context.getDrawable(android.R.drawable.sym_def_app_icon)
                    if (fallbackDrawable != null) {
                        val bmp = android.graphics.Bitmap.createBitmap(
                            fallbackDrawable.intrinsicWidth.coerceAtLeast(1),
                            fallbackDrawable.intrinsicHeight.coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        fallbackDrawable.setBounds(0, 0, canvas.width, canvas.height)
                        fallbackDrawable.draw(canvas)
                        val imageBitmap = bmp.asImageBitmap()
                        iconCache.put(packageName, imageBitmap)
                        value = imageBitmap
                    }
                } catch (_: Throwable) {
                    value = null
                }
            }
        }
    }

    val img = imageState.value
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier)
    }
}

// Cache for icon theme colors to avoid heavy extraction during scroll
private val iconThemeColorCache = mutableMapOf<String, Color>()

fun getIconThemeColor(context: Context, pkg: String): Color {
    iconThemeColorCache[pkg]?.let { return it }
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
        
        val color = Color(r, g, b).copy(alpha = 0.22f)
        iconThemeColorCache[pkg] = color
        return color
    } catch (_: Throwable) {
        val color = ZenColors.primary.copy(alpha = 0.12f)
        iconThemeColorCache[pkg] = color
        return color
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

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)
