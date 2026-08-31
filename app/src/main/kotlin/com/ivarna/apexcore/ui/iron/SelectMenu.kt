package com.ivarna.apexcore.ui.iron

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class IronSelectOption(
    val key: String,
    val label: String,
    val supportingText: String? = null,
)

/**
 * Iron-native select field: theme-aware anchor + fully themed popup.
 * Material3 DropdownMenu is wrapped only with explicit Iron colors/shape — no purple leakage.
 */
@Composable
fun IronSelectField(
    value: String,
    options: List<IronSelectOption>,
    onSelect: (IronSelectOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    val skin = ironSkin()
    val clack = rememberClack()
    var expanded by remember { mutableStateOf(false) }
    val display = value.ifBlank { placeholder.orEmpty() }
    val valueColor = when {
        value.isBlank() -> skin.textDim
        enabled -> skin.text
        else -> skin.disabledText()
    }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(IronShape.Slot)
                .background(skin.inputSurface())
                .border(1.dp, skin.inputBorder(), IronShape.Slot)
                .clickable(
                    enabled = enabled && options.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    clack.tick()
                    expanded = true
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                display,
                style = IronType.Label.copy(fontSize = 12.sp, letterSpacing = 0.6.sp),
                color = valueColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "▾",
                style = IronType.Label,
                color = if (enabled) skin.textDim else skin.disabledText(),
            )
        }

        IronDropdownMenu(
            expanded = expanded && enabled && options.isNotEmpty(),
            onDismissRequest = { expanded = false },
            selectedKey = options.find { it.label == value || it.key == value }?.key,
            options = options,
            onSelect = { opt ->
                expanded = false
                clack.confirm()
                onSelect(opt)
            },
        )
    }
}

@Composable
fun IronDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<IronSelectOption>,
    onSelect: (IronSelectOption) -> Unit,
    selectedKey: String? = null,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
) {
    val skin = ironSkin()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = 200.dp, max = 360.dp)
            .border(1.dp, skin.popupBorder(), IronShape.Plate)
            .background(skin.popupSurface()),
        offset = offset,
        shape = IronShape.Plate,
        containerColor = skin.popupSurface(),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        options.forEachIndexed { index, opt ->
            val selected = selectedKey != null && (opt.key == selectedKey)
            DropdownMenuItem(
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            opt.label,
                            style = IronType.Body,
                            color = skin.text,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (!opt.supportingText.isNullOrBlank()) {
                            Text(
                                opt.supportingText,
                                style = IronType.Caption,
                                color = skin.textDim,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                onClick = { onSelect(opt) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .then(
                        if (selected) Modifier.background(skin.selectedRow())
                        else Modifier
                    ),
                leadingIcon = if (selected) {
                    {
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 18.dp)
                                .background(Iron.Brass400)
                        )
                    }
                } else null,
                colors = MenuDefaults.itemColors(
                    textColor = skin.text,
                    leadingIconColor = Iron.Brass400,
                    trailingIconColor = skin.textDim,
                    disabledTextColor = skin.disabledText(),
                    disabledLeadingIconColor = skin.disabledText(),
                    disabledTrailingIconColor = skin.disabledText(),
                ),
            )
            if (index < options.lastIndex) {
                HorizontalDivider(color = skin.hairline, thickness = 1.dp)
            }
        }
    }
}
