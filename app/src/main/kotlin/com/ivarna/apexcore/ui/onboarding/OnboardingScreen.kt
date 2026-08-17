package com.ivarna.apexcore.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.OptionCard
import com.ivarna.apexcore.R
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.freeze.RootFreezeBackend
import com.ivarna.apexcore.freeze.ShizukuFreezeBackend
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val kicker: String,
    val title: String,
    val description: String,
    val imageRes: Int
)

private val FEATURE_PAGES = listOf(
    OnboardingPageData(
        kicker = "01 · PURGE ENGINE",
        title = "Focus Resources for Gaming",
        description = "Deep-freeze background bloat and prioritize RAM so your hardware stays responsive and lag-free.",
        imageRes = R.drawable.ic_onboard_purge
    ),
    OnboardingPageData(
        kicker = "02 · PERFORMANCE HUD",
        title = "Live On-Screen Telemetry",
        description = "Track in-game FPS, RAM pressure, and CPU load in real time with an unobtrusive floating overlay.",
        imageRes = R.drawable.ic_onboard_hud
    ),
    OnboardingPageData(
        kicker = "03 · MEMORY TOOLKIT",
        title = "App Pins & Safe Reclaim",
        description = "Pin your essential apps to keep them awake, and safely reclaim inactive system memory with a 90% cap.",
        imageRes = R.drawable.ic_onboard_library
    ),
    OnboardingPageData(
        kicker = "04 · SYSTEM ACCESS",
        title = "Elevate Your Control",
        description = "Deep freeze requires Shizuku (recommended) or Root access. Configure your backend now, or proceed in standard mode.",
        imageRes = R.drawable.ic_onboard_access
    )
)

private const val TOTAL_PAGES = 5 // 0: Welcome, 1..3: Features, 4: Elevation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    isReplay: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })

    // Shizuku & Root detection states for the final elevation page
    var shizukuReady by remember { mutableStateOf<Boolean?>(null) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }
    var selectedBackend by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == TOTAL_PAGES - 1) {
            val shizuku = ShizukuFreezeBackend()
            val root = RootFreezeBackend()
            while (true) {
                shizukuReady = try { shizuku.isReady() } catch (_: Throwable) { false }
                rootReady = try { root.invalidate(); root.isReady() } catch (_: Throwable) { false }
                delay(1200)
            }
        }
    }

    fun completeOnboarding() {
        OnboardingPreferences.setOnboardingCompleted(context, true)
        onFinish()
    }

    fun applyBackendChoice(prefKey: String, displayName: String) {
        context.getSharedPreferences("apexcore", Context.MODE_PRIVATE)
            .edit().putString("preferred_backend", prefKey).apply()
        FreezeFramework.setPreferredBackend(displayName)
        FpsStack.get(context).syncPreferredBackend(prefKey)
        selectedBackend = prefKey
        coroutineScope.launch {
            FreezeFramework.resolver()?.invalidate()
            FreezeFramework.detect()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // Top Atmosphere radial glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scheme.primaryContainer.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top Bar: Navigation indicator / Skip / Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReplay) {
                    IconButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerLow.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerLow.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                // Page indicator pills (top)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(TOTAL_PAGES) { index ->
                        val isSelected = pagerState.currentPage == index
                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "pillWidth"
                        )
                        val pillColor = if (isSelected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(pillWidth)
                                .clip(RoundedCornerShape(3.dp))
                                .background(pillColor)
                        )
                    }
                }

                // Skip button
                if (!isReplay && pagerState.currentPage < TOTAL_PAGES - 1) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(TOTAL_PAGES - 1)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Skip",
                            color = scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }
            }

            // Main Pager Area
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        // Page 0: Welcome with Big Center Logo & Tagline
                        WelcomePage()
                    }
                    in 1..3 -> {
                        // Pages 1-3: Features (Purge, HUD, Library)
                        StandardShowcasePage(page = FEATURE_PAGES[pageIndex - 1])
                    }
                    4 -> {
                        // Page 4: Interactive Elevation Setup
                        ElevationSetupPage(
                            page = FEATURE_PAGES[3],
                            shizukuReady = shizukuReady,
                            rootReady = rootReady,
                            selectedBackend = selectedBackend,
                            onSelectShizuku = { applyBackendChoice("shizuku", "Shizuku") },
                            onSelectRoot = { applyBackendChoice("root", "Root") },
                            onConfigureShizuku = { openShizukuApp(context) },
                            onGrantRoot = {
                                coroutineScope.launch {
                                    RootFreezeBackend().invalidate()
                                    FreezeFramework.resolver()?.invalidate()
                                    val isRoot = try { RootFreezeBackend().isReady() } catch (_: Throwable) { false }
                                    if (isRoot) {
                                        applyBackendChoice("root", "Root")
                                    } else {
                                        Toast.makeText(context, "Root permission not found yet", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Bottom CTA Bar
            val ctaInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isCtaPressed by ctaInteractionSource.collectIsPressedAsState()
            val ctaScale by animateFloatAsState(
                targetValue = if (isCtaPressed) 0.97f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 400f),
                label = "ctaScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val isLastPage = pagerState.currentPage == TOTAL_PAGES - 1
                val btnShape = RoundedCornerShape(30.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .scale(ctaScale)
                        .clip(btnShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    scheme.primary,
                                    scheme.primary.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(1.5.dp, scheme.onPrimary.copy(alpha = 0.28f), btnShape)
                        .clickable(
                            interactionSource = ctaInteractionSource,
                            indication = ripple(color = scheme.onPrimary)
                        ) {
                            if (isLastPage) {
                                completeOnboarding()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastPage) "Enter ApexCore" else if (pagerState.currentPage == 0) "Get Started" else "Continue",
                            color = scheme.onPrimary,
                            fontSize = 16.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = if (isLastPage) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ambient Glowing Container with Big Center Logo
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Radial Glow
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.35f),
                                scheme.primaryContainer.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Centered Glass Plate with Logo
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(scheme.surfaceContainerLow.copy(alpha = 0.95f))
                    .border(
                        2.dp,
                        Brush.verticalGradient(
                            listOf(
                                scheme.primary.copy(alpha = 0.7f),
                                scheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ),
                        RoundedCornerShape(44.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "ApexCore Logo",
                    modifier = Modifier.size(110.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = "ApexCore",
            color = scheme.onSurface,
            fontSize = 36.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Tagline
        Text(
            text = "ZEN PERFORMANCE ENGINE",
            color = scheme.primary,
            fontSize = 12.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "Harmonious device optimization. Deep-freeze background bloat, track real-time telemetry HUD, and unlock smooth, lag-free mobile gaming.",
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun StandardShowcasePage(page: OnboardingPageData) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Showcase Artwork (High DPI)
        Box(
            modifier = Modifier
                .size(310.dp)
                .clip(RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = page.imageRes),
                contentDescription = page.title,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Kicker Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = page.kicker,
                color = scheme.primary,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title
        Text(
            text = page.title,
            color = scheme.onSurface,
            fontSize = 24.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = page.description,
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun ElevationSetupPage(
    page: OnboardingPageData,
    shizukuReady: Boolean?,
    rootReady: Boolean?,
    selectedBackend: String?,
    onSelectShizuku: () -> Unit,
    onSelectRoot: () -> Unit,
    onConfigureShizuku: () -> Unit,
    onGrantRoot: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Compact Access Art
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = page.imageRes),
                contentDescription = page.title,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Kicker Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = page.kicker,
                color = scheme.primary,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = page.title,
            color = scheme.onSurface,
            fontSize = 24.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select an elevated backend for deep freeze optimization.",
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontFamily = PlusJakartaSans,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Option 1: Shizuku
        OptionCard(
            title = "Shizuku Service",
            sub = when (shizukuReady) {
                true -> if (selectedBackend == "shizuku") "Selected & active." else "Connected and ready for deep freeze."
                false -> "Wireless debugging API — does not require root."
                null -> "Checking Shizuku status…"
            },
            cta = when (shizukuReady) {
                true -> if (selectedBackend == "shizuku") "SELECTED" else "USE SHIZUKU"
                false -> "CONFIGURE SHIZUKU"
                null -> "CHECKING…"
            },
            ready = shizukuReady,
            badge = when (shizukuReady) {
                true -> "READY"
                false -> "RECOMMENDED"
                null -> "…"
            },
            isRecommended = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (shizukuReady == true) {
                onSelectShizuku()
            } else {
                onConfigureShizuku()
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Option 2: Root
        OptionCard(
            title = "Root Access",
            sub = when (rootReady) {
                true -> if (selectedBackend == "root") "Selected & active." else "su granted — ready for deep freeze."
                false -> "Direct su execution for rooted devices."
                null -> "Checking root status…"
            },
            cta = when (rootReady) {
                true -> if (selectedBackend == "root") "SELECTED" else "USE ROOT"
                false -> "GRANT ROOT"
                null -> "CHECKING…"
            },
            ready = rootReady,
            badge = when (rootReady) {
                true -> "READY"
                false -> null
                null -> "…"
            },
            isRecommended = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (rootReady == true) {
                onSelectRoot()
            } else {
                onGrantRoot()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can always change backend or connect later in Settings.",
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = PlusJakartaSans,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun openShizukuApp(context: Context) {
    val pm = context.packageManager
    val candidates = listOf("moe.shizuku.manager", "moe.shizuku.api")
    for (pkg in candidates) {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {}
        }
    }
    val play = Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api&hl=en&pli=1")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(play)
    } catch (_: Throwable) {}
}
