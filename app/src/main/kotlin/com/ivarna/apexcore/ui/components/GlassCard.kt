package com.ivarna.apexcore.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ivarna.apexcore.ui.theme.ZenDimens

/**
 * Solid glass card surface (normative v1 — no RenderEffect).
 * Optional [organicStyle] draws looping flowers/leaves behind content.
 *
 * Decor uses [Modifier.matchParentSize] so it sizes with the content column
 * (fillMaxSize in a wrap-content Box collapses to 0 height).
 */
@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** When non-null, draws OrganicCardDecor with this style seed */
    organicStyle: Int? = null,
    organicSizeScale: Float = 1.25f,
    organicAlpha: Float = 0.85f,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(ZenDimens.roundedLg)
    // Slightly less opaque so organic décor can read through the plate
    val fill = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .zenGlassBackground(
                shape = shape,
                fill = fill,
                borderColor = borderColor
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        if (organicStyle != null) {
            OrganicCardDecor(
                style = organicStyle,
                sizeScale = organicSizeScale,
                alphaScale = organicAlpha,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZenDimens.elementGap),
            content = content
        )
    }
}
