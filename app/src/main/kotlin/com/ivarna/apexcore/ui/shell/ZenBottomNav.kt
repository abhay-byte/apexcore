package com.ivarna.apexcore.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.R
import com.ivarna.apexcore.ui.components.zenBloom
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans

/**
 * Floating glass island bottom navigation (~90% width).
 * Labels: Boost / Games / Overlay (KD-3).
 */
@Composable
fun ZenBottomNav(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
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
                .zenBloom(shape)
                .zenGlassBackground(
                    shape = shape,
                    fill = scheme.surfaceContainer.copy(alpha = 0.92f),
                    borderColor = scheme.outlineVariant.copy(alpha = 0.6f)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZenNavItem(
                label = "Boost",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_home_eco),
                isActive = currentTab == Tab.HOME,
                onClick = { onTabSelected(Tab.HOME) }
            )
            ZenNavItem(
                label = "Games",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_games_vintage),
                isActive = currentTab == Tab.GAMES,
                onClick = { onTabSelected(Tab.GAMES) }
            )
            ZenNavItem(
                label = "Overlay",
                icon = ImageVector.vectorResource(R.drawable.ic_nav_overlay_layers),
                isActive = currentTab == Tab.OVERLAY,
                onClick = { onTabSelected(Tab.OVERLAY) }
            )
        }
    }
}

@Composable
fun ZenNavItem(
    label: String,
    icon: ImageVector,
    selectedIcon: ImageVector = icon,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor by animateColorAsState(
        targetValue = if (isActive) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
        label = "zenNavContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = 300f
        ),
        label = "zenNavBounce"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) scheme.primaryContainer.copy(alpha = 0.85f) else scheme.primaryContainer.copy(alpha = 0f),
        label = "zenNavIndicator"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(indicatorColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) selectedIcon else icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (isActive) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans
                )
            }
        }
    }
}
