package com.datadragon.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.datadragon.app.ui.theme.AppTheme

/** Grayed-out strength for a disabled control, per Material's disabled alpha. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The app's button — the only button shape there is.
 *
 * It is framed exactly like a drop-down box: the shared control outline and the
 * shared control corner. It is never a pill and never a filled capsule.
 *
 * A disabled button stays visible and grays out; it is never hidden.
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    val outlineColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = DISABLED_ALPHA)
    }
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.control)
            .clickable(enabled = enabled, onClick = onClick)
            .border(AppTheme.shapes.controlBorder, outlineColor, AppTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(AppTheme.textStyles.controlLabel) { content() }
        }
    }
}

/**
 * A drop-down box: the current value in a framed box that opens a menu.
 *
 * **Its width never changes.** The box is sized to the widest label it could
 * ever show, plus a little slack, so picking a different option does not make
 * the control grow, shrink, or shove its neighbors around.
 *
 * An option has exactly one name: the box and the menu show the same
 * [optionLabel], never a short version in one and a long version in the other.
 */
@Composable
fun <T> AppDropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(AppTheme.shapes.control)
                .clickable { expanded = true }
                .border(
                    AppTheme.shapes.controlBorder,
                    MaterialTheme.colorScheme.outline,
                    AppTheme.shapes.control,
                )
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every label is laid out here, all but the selected one invisible, so
            // this box is always as wide as its widest possible value.
            Box {
                options.forEach { option ->
                    Text(
                        optionLabel(option),
                        style = AppTheme.textStyles.controlLabel,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alpha(0f),
                    )
                }
                Text(
                    optionLabel(selected),
                    style = AppTheme.textStyles.controlLabel,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            // The slack that keeps the box a touch wider than its widest label.
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A labelled row holding a drop-down: the label on the left, the drop-down on
 * the right, on one line.
 *
 * [hint] sits directly under the **label**, inside the label's column — never
 * under the whole row and never under the control.
 */
@Composable
fun <T> AppDropdownRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = AppTheme.textStyles.settingTitle)
            if (hint != null) {
                Text(
                    hint,
                    style = AppTheme.textStyles.settingDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppDropdown(
            options = options,
            selected = selected,
            onSelected = onSelected,
            optionLabel = optionLabel,
        )
    }
}
