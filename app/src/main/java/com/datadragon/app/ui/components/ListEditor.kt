package com.datadragon.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * The one editable list-item row, shared by every list-style editor (ordinary
 * Lists and Daily Tasks) so they behave and look identical and change in one
 * place. A drag handle, a completion toggle, the editable text, and — only while
 * the row is being edited — a "+" that adds a sub-item and an "×" that deletes
 * the row.
 *
 * [rowKey] resets the local text buffer when the underlying row identity changes.
 * [completedIcon] is the icon shown when [completed] is true (the caller picks it,
 * e.g. a check or a filled box); an unchecked row always shows the blank box.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListEditorItemRow(
    rowKey: Any,
    text: String,
    completed: Boolean,
    indent: Int,
    completedIcon: ImageVector,
    crossOut: Boolean,
    dragHandleModifier: Modifier,
    isEditing: Boolean,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onFocusHandled: () -> Unit,
    onBlur: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onAddSubItem: () -> Unit,
    onDelete: () -> Unit,
) {
    var buffer by remember(rowKey) { mutableStateOf(text) }
    // Keep the buffer in step if the stored text changes underneath (e.g. a
    // carried-in row arriving after first compose).
    LaunchedEffect(text) { if (text != buffer && text.isNotBlank()) buffer = text }
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var wasFocused by remember(rowKey) { mutableStateOf(false) }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
            onFocusHandled()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(start = if (indent == 1) 32.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandleModifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        IconButton(onClick = onToggleComplete) {
            Icon(
                imageVector = if (completed) completedIcon else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (completed) "Mark not done" else "Mark done",
                tint = if (completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        val struck = completed && crossOut
        BasicTextField(
            value = buffer,
            onValueChange = { buffer = it; onTextChange(it) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) { onFocused(); wasFocused = true }
                    else if (wasFocused) { wasFocused = false; onBlur() }
                },
        )
        if (isEditing) {
            IconButton(onClick = onAddSubItem) {
                Icon(Icons.Filled.Add, contentDescription = "Add sub-item")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Delete item")
            }
        }
    }
}

/**
 * The "add a new item" row that sits at the bottom of a list-style editor, shared
 * by Lists and Daily Tasks. Its content is right-aligned; change it here and both
 * editors change together.
 */
@Composable
fun AddItemRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "List Item",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
