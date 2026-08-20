package com.ivarna.apexcore.ui.tune

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.apexcore.tune.*
import com.ivarna.apexcore.ui.components.zenGlassBackground
import com.ivarna.apexcore.ui.theme.PlusJakartaSans
import com.ivarna.apexcore.ui.theme.ZenDimens

@Composable
fun TuneCategorySection(
    category: TuneCategory,
    specs: List<TuneSpec>,
    capabilities: Map<TuneId, TuneCapability>,
    intents: Map<TuneId, TuneValue>,
    enabled: Boolean,
    onIntentChange: (TuneId, TuneValue) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ZenDimens.roundedLg)
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow_rot")

    val availableCount = remember(specs, capabilities) {
        specs.count { capabilities[it.id]?.available == true }
    }

    val sortedSpecs = remember(specs, capabilities) {
        specs.sortedWith(
            compareByDescending<TuneSpec> { capabilities[it.id]?.available == true }
                .thenBy { it.title }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .zenGlassBackground(
                shape = shape,
                fill = scheme.surfaceContainerLow.copy(alpha = 0.9f),
                borderColor = scheme.outlineVariant.copy(alpha = 0.4f)
            )
            .animateContentSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.displayName.uppercase(),
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                val countText = if (availableCount > 0) "$availableCount/${specs.size} supported" else "Not supported"
                Text(
                    text = countText,
                    color = if (availableCount > 0) scheme.primary.copy(alpha = 0.85f) else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                sortedSpecs.forEachIndexed { index, spec ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = scheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    val cap = capabilities[spec.id]
                    val intent = intents[spec.id] ?: TuneValue(on = false, raw = null)
                    val isCpuFloorOn = intents[TuneId.CPU_FLOOR]?.on == true
                    val isSplitCpu = spec.id == TuneId.CPU_FLOOR_LITTLE || spec.id == TuneId.CPU_FLOOR_BIG || spec.id == TuneId.CPU_FLOOR_PRIME
                    val isOverridden = isCpuFloorOn && isSplitCpu

                    TuneOptionRow(
                        spec = spec,
                        capability = cap,
                        intent = if (isOverridden) TuneValue(on = false, raw = intent.raw) else intent,
                        enabled = enabled,
                        isOverridden = isOverridden,
                        overrideSubtitle = if (isOverridden) "Covered by CPU frequency floor" else null,
                        onIntentChange = { newIntent ->
                            onIntentChange(spec.id, newIntent)
                        }
                    )
                }
            }
        }
    }
}
