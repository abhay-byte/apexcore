package com.ivarna.apexcore.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.ivarna.apexcore.ui.components.OrganicCardDecor
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.ZenDimens
// matchParentSize is a BoxScope extension — available inside Box content below

/**
 * Home alias — shared organic flowers + leaves with looping motion.
 */
@Composable
fun HomeCardFlowerDecor(
    modifier: Modifier = Modifier,
    style: Int = 0,
    sizeScale: Float = 1f,
    alphaScale: Float = 1f
) {
    OrganicCardDecor(
        modifier = modifier,
        style = style,
        sizeScale = sizeScale,
        alphaScale = alphaScale
    )
}

/**
 * Glass card surface with looping organic décor behind [content].
 */
@Composable
fun HomeFlowerCard(
    modifier: Modifier = Modifier,
    style: Int = 0,
    sizeScale: Float = 1f,
    flowerAlpha: Float = 1f,
    cornerRadius: Dp = ZenDimens.roundedLg,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .zenGlassBackground(
                shape = shape,
                fill = scheme.surfaceContainerLow.copy(alpha = 0.90f),
                borderColor = scheme.outlineVariant.copy(alpha = 0.42f)
            )
    ) {
        // matchParentSize — fillMaxSize collapses to 0 in wrap-content Box
        OrganicCardDecor(
            modifier = Modifier.matchParentSize(),
            style = style,
            sizeScale = sizeScale,
            alphaScale = flowerAlpha
        )
        content()
    }
}
