package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.ui.ChecklistViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    checklistId: String?,
    onBack: () -> Unit,
    viewModel: ChecklistViewModel = viewModel(),
) {
    val idLong = checklistId?.toLongOrNull()
    val isNew = idLong == null
    LaunchedEffect(checklistId) { viewModel.load(idLong) }

    val title by viewModel.title.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val completeIcon by viewModel.completeIcon.collectAsStateWithLifecycle()
    val crossOut by viewModel.crossOut.collectAsStateWithLifecycle()

    // Established lists auto-save; only drop rows left blank when leaving.
    val leaving by rememberUpdatedState(viewModel::onLeave)
    DisposableEffect(Unit) { onDispose { leaving() } }

    // A brand-new list is a draft: Save persists it, and leaving with anything
    // typed asks before discarding. Save turns on once an item has real text.
    val hasText = items.any { it.text.isNotBlank() }
    val dirty = isNew && (title.isNotBlank() || hasText)
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler(enabled = isNew) { attemptBack() }

    // Which row is being edited (shows its +/× controls), and which newly-added
    // row should grab focus next.
    var focusedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingFocusId by rememberSaveable { mutableStateOf<Long?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val ids = viewModel.items.value.map { it.id }.toMutableList()
        if (from.index in ids.indices && to.index in ids.indices) {
            ids.add(to.index, ids.removeAt(from.index))
            viewModel.reorder(ids)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save exists only while the list is a brand-new draft; once
                    // saved, an established list auto-saves and needs no button.
                    if (isNew) {
                        TextButton(
                            enabled = hasText,
                            onClick = { viewModel.save(onBack) },
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Title lives above the reorderable list so the list holds only
            // draggable items — keeping drag indices simple.
            TitleField(
                value = title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )

            androidx.compose.foundation.lazy.LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                    ReorderableItem(reorderState, key = item.id) { _ ->
                        val handleModifier = Modifier.draggableHandle()
                        ChecklistItemRow(
                            item = item,
                            completeIcon = completeIcon,
                            crossOut = crossOut,
                            dragHandleModifier = handleModifier,
                            isEditing = focusedItemId == item.id,
                            requestFocus = pendingFocusId == item.id,
                            onFocused = { focusedItemId = item.id },
                            onFocusHandled = { if (pendingFocusId == item.id) pendingFocusId = null },
                            onTextChange = { viewModel.updateText(item.id, it) },
                            onToggleComplete = { viewModel.setCompleted(item.id, !item.completed) },
                            onAddSubItem = {
                                viewModel.addSubItem(item.id) { newId -> pendingFocusId = newId }
                            },
                            onDelete = {
                                if (focusedItemId == item.id) focusedItemId = null
                                viewModel.deleteItem(item.id)
                            },
                        )
                    }
                }
            }

            AddItemRow(
                onClick = { viewModel.addItem { newId -> pendingFocusId = newId } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value) }
    // Keep local text in sync if the stored value loads in after first compose.
    LaunchedEffect(value) { if (value != text) text = value }
    Box(modifier = modifier) {
        if (text.isEmpty()) {
            Text(
                text = "Title",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    completeIcon: CompleteIcon,
    crossOut: Boolean,
    dragHandleModifier: Modifier,
    isEditing: Boolean,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onAddSubItem: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (item.indent == 1) 32.dp else 0.dp),
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
                imageVector = completedVector(item.completed, completeIcon),
                contentDescription = if (item.completed) "Mark not done" else "Mark done",
                tint = if (item.completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        val struck = item.completed && crossOut
        BasicTextField(
            value = text,
            onValueChange = { text = it; onTextChange(it) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.completed) {
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
                .onFocusChanged { if (it.isFocused) onFocused() },
        )
        // While a row is being edited: + adds a sub-item, × deletes the item.
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

@Composable
private fun AddItemRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = "List Item",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun completedVector(completed: Boolean, completeIcon: CompleteIcon): ImageVector = when {
    !completed -> Icons.Outlined.CheckBoxOutlineBlank
    completeIcon == CompleteIcon.CHECKED_BOX -> Icons.Filled.CheckBox
    else -> Icons.Filled.Check
}
