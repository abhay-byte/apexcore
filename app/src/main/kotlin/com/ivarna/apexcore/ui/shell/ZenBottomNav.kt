package com.ivarna.apexcore.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ivarna.apexcore.R
import com.ivarna.apexcore.ui.components.zenFrostChild
import dev.chrisbanes.haze.HazeState

/**
 * Floating frosted-glass bottom navigation (~90% width).
 *
 * Same [zenFrostChild] material as [ZenTopBar] (shared tint / blur / noise).
 * Icon-only tabs; active = primary pill + onPrimary icon.
 */
@Composable
fun ZenBottomNav(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(percent = 50)
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    spotColor = scheme.primary.copy(alpha = 0.35f)
                )
                .clip(shape)
                .zenFrostChild(hazeState = hazeState, surface = scheme.surface)
                .border(
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.25f)),
                    shape = shape
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZenNavItem(
                contentDescription = "Boost",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_home_eco),
                isActive = currentTab == Tab.HOME,
                onClick = { onTabSelected(Tab.HOME) }
            )
            ZenNavItem(
                contentDescription = "Games",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_games_vintage),
                isActive = currentTab == Tab.GAMES,
                onClick = { onTabSelected(Tab.GAMES) }
            )
            ZenNavItem(
                contentDescription = "Overlay",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_overlay_layers),
                isActive = currentTab == Tab.OVERLAY,
                onClick = { onTabSelected(Tab.OVERLAY) }
            )
            ZenNavItem(
                contentDescription = "Settings",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_settings),
                isActive = currentTab == Tab.SETTINGS,
                onClick = { onTabSelected(Tab.SETTINGS) }
            )
        }
    }
}

@Composable
fun ZenNavItem(
    contentDescription: String,
    icon: ImageVector,
    selectedIcon: ImageVector = icon,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor by animateColorAsState(
        targetValue = if (isActive) scheme.onPrimary else scheme.onSurfaceVariant,
        label = "zenNavContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = 300f
        ),
        label = "zenNavBounce"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) scheme.primary else scheme.primary.copy(alpha = 0f),
        label = "zenNavIndicator"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(indicatorColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isActive) selectedIcon else icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
