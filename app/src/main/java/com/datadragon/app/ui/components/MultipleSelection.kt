package com.datadragon.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import com.datadragon.app.R
import com.datadragon.app.ui.CardSelection
import com.datadragon.app.ui.theme.AppTheme

/**
 * The Multiple Selection bar shown under a Main screen's top bar: the live
 * count, the hint, Copy, Delete and Export (greyed out while nothing is
 * selected), and the ⋮ menu of selection commands.
 */
@Composable
fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectNewest: () -> Unit,
    onSelectOldest: () -> Unit,
    onExit: () -> Unit,
) {
    val style = AppTheme.selection
    var menuOpen by remember { mutableStateOf(false) }
    val any = count > 0
    Surface(
        shape = style.barShape,
        color = style.barColor,
        contentColor = style.barContentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = style.barOuterInset, vertical = AppTheme.spacing.compact),
    ) {
        Row(
            modifier = Modifier.padding(start = style.barInnerInset, top = style.barInnerInset, bottom = style.barInnerInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$count Selected", style = style.countText)
                Text("Tap a card to toggle selection", style = style.subtitleText, color = style.barSubtitleColor)
            }
            IconButton(onClick = onCopy, enabled = any) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onDelete, enabled = any) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = onExport, enabled = any) {
                Icon(painterResource(R.drawable.ic_file_save), contentDescription = "Export")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Selection options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Select All") }, onClick = { menuOpen = false; onSelectAll() })
                    DropdownMenuItem(text = { Text("Deselect All") }, onClick = { menuOpen = false; onDeselectAll() })
                    DropdownMenuItem(text = { Text("Select Newest X") }, onClick = { menuOpen = false; onSelectNewest() })
                    DropdownMenuItem(text = { Text("Select Oldest X") }, onClick = { menuOpen = false; onSelectOldest() })
                    DropdownMenuItem(text = { Text("Exit Selection") }, onClick = { menuOpen = false; onExit() })
                }
            }
        }
    }
}

/**
 * One card on a Main screen that Multiple Selection can pick. While [selecting],
 * a layer over the card takes every tap and toggles the card instead, so
 * nothing on it — its menu, a tracker button, a text box — reacts. A selected
 * card gets the dashed coupon outline just outside its edge.
 */
@Composable
fun SelectableCard(
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val style = AppTheme.selection
    Box(
        modifier = Modifier.drawWithContent {
            drawContent()
            if (selecting && selected) {
                val offset = style.outlineOffset.toPx()
                val stroke = style.outlineWidth.toPx()
                val corner = style.outlineCorner.toPx()
                drawRoundRect(
                    color = style.outlineColor,
                    topLeft = Offset(-offset, -offset),
                    size = Size(size.width + offset * 2, size.height + offset * 2),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(style.outlineDash.toPx(), style.outlineGap.toPx()),
                        ),
                    ),
                )
            }
        },
    ) {
        content()
        if (selecting) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics { this.selected = selected }
                    .clickable(onClickLabel = if (selected) "Deselect" else "Select", onClick = onToggle),
            )
        }
    }
}

/**
 * Select Newest / Oldest X: "Select Newest" (or "Oldest") followed by a number
 * box sized for the card count's digits, then Cancel | Okay. Okay stays off
 * until a whole number above zero is typed.
 */
@Composable
fun SelectCountDialog(
    newest: Boolean,
    cardCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val digits = CardSelection.digitsFor(cardCount)
    var text by remember { mutableStateOf("") }
    val count = CardSelection.parseCount(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val numberStyle = MaterialTheme.typography.bodyLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boxWidth = with(density) {
        measurer.measure(text = "0".repeat(digits), style = numberStyle, softWrap = false, maxLines = 1).size.width.toDp()
    } + AppTheme.sizes.fieldContentInset * 2

    AppDialog(
        onDismissRequest = onDismiss,
        bodyContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (newest) "Select Newest" else "Select Oldest",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = CardSelection.countInput(it, digits) },
                    singleLine = true,
                    textStyle = numberStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(boxWidth).focusRequester(focus),
                )
            }
        },
        dismissButton = { DialogDismissButton("Cancel", onClick = onDismiss) },
        confirmButton = {
            DialogActionButton("Okay", enabled = count != null) { count?.let(onConfirm) }
        },
    )
}
