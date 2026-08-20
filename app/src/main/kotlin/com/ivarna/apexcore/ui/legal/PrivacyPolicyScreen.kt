package com.ivarna.apexcore.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.ui.components.zenFrostChild
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Renders the privacy policy from a bundled offline asset.
 * No WebView, no INTERNET permission — policy doc is bundled at build time.
 * ponytail: offline asset duplicated from docs/privacy-policy.md; upgrade to live
 * WebView + https://raw.githubusercontent.com/abhay-byte/apexcore/main/docs/privacy-policy.md
 * when live updates needed (add INTERNET + WebView then).
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lines = remember {
        try {
            context.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }.lines()
        } catch (_: Throwable) {
            listOf("# Privacy Policy", "", "Privacy policy could not be loaded.")
        }
    }
    val scheme = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(horizontal = ZenDimens.containerPadding)
        ) {
            item { Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(modifier = Modifier.height(ZenDimens.topBarClearance)) }
            item { Spacer(modifier = Modifier.height(ZenDimens.elementGap)) }

            items(lines) { raw ->
                val trimmed = raw.trimEnd()
                when {
                    trimmed.startsWith("## ") -> Text(
                        text = trimmed.removePrefix("## ").trim(),
                        color = scheme.onSurface,
                        fontSize = 17.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )

                    trimmed.startsWith("### ") -> Text(
                        text = trimmed.removePrefix("### ").trim(),
                        color = scheme.onSurface,
                        fontSize = 15.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )

                    trimmed.startsWith("* ") || trimmed.startsWith("- ") -> Text(
                        text = "•  " + trimmed.removePrefix("* ").removePrefix("- ").trim(),
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    trimmed.startsWith("# ") -> Text(
                        text = trimmed.removePrefix("# ").trim(),
                        color = scheme.onSurface,
                        fontSize = 20.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    trimmed.startsWith("**") -> {
                        val bold = trimmed.removePrefix("**").let {
                            if (it.endsWith("**")) it.removeSuffix("**") else it
                        }
                        Text(
                            text = bold,
                            color = scheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    trimmed.isBlank() -> Spacer(modifier = Modifier.height(8.dp))

                    else -> Text(
                        text = trimmed,
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(ZenDimens.bottomNavClearance)) }
        }

        // Frosted Top App Bar (TuneScreen recipe)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zenFrostChild(hazeState, scheme.surface)
                .background(scheme.surface.copy(alpha = 0.85f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        text = "Privacy Policy",
                        color = scheme.onSurface,
                        fontSize = 17.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}