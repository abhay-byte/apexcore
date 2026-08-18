package com.ivarna.apexcore.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.ivarna.apexcore.R

/**
 * Curated Material Symbols loaded via vectorResource (not material-icons-extended).
 */
object ZenIcons {
    val HomeEco: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_home_eco)
    val GamesVintage: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_games_vintage)
    val OverlayLayers: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_overlay_layers)
    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_settings)
    val WaterDrop: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_water_drop)
    val Spa: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_spa)
    val PushPin: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_push_pin)
    val CleanHands: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_clean_hands)
    val Tune: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tune)
}
