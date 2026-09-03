package com.datadragon.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.export.ChecklistExportFormat
import com.datadragon.app.export.ExportContent
import com.datadragon.app.ui.ChecklistViewModel
import com.datadragon.app.ui.components.ExportFormatDialog
import com.datadragon.app.ui.components.ExportFormatOption
import kotlinx.coroutines.launch
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
    LaunchedEffect(checklistId) { viewModel.load(idLong) }

    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showFormatChooser by remember { mutableStateOf(false) }
    var showDeleteList by remember { mutableStateOf(false) }

    // The file being saved: the user picks the destination and name via the
    // system "Save to…" sheet; we write the bytes to whatever it returns.
    var pendingExport by remember { mutableStateOf<ExportContent?>(null) }
    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(export.bytes) }
                    ?: error("No output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Saved ${export.suggestedName}" else "Couldn't save file",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val startSave: (ChecklistExportFormat) -> Unit = { format ->
        showFormatChooser = false
        exportScope.launch {
            val content = viewModel.buildExport(format)
            if (content != null) {
                pendingExport = content
                saveDocument.launch(content.suggestedName)
            }
        }
    }

    val title by viewModel.title.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val completeIcon by viewModel.completeIcon.collectAsStateWithLifecycle()
    val crossOut by viewModel.crossOut.collectAsStateWithLifecycle()
    // A draft (new or recovered) is logically unsaved: it shows Save, and backing
    // out with content asks to Discard. An established saved list does neither.
    val isDraft by viewModel.isDraft.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    // Established lists auto-save; on leaving, flush pending text then drop rows
    // left blank (cleanup runs after the flush, so a row whose latest text hasn't
    // reached the database yet is never deleted).
    val leaving by rememberUpdatedState(viewModel::onLeave)
    DisposableEffect(Unit) { onDispose { leaving() } }

    // Best-effort flush when the app is backgrounded (not a guarantee against the
    // process being killed outright).
    val backgroundFlush by rememberUpdatedState(viewModel::flushOnBackground)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) backgroundFlush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back on a draft with any content asks to Discard — even if the draft has
    // already been written to the database as crash protection. Back on an
    // established list (or an untouched empty draft) just leaves, flushing any
    // pending text first and waiting for it before navigating.
    val hasText = items.any { it.text.isNotBlank() }
    val hasContent = title.isNotBlank() || hasText
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun leaveFlushing() { scope.launch { viewModel.flushPending(); onBack() } }
    fun attemptBack() { if (isDraft && hasContent) showDiscard = true else leaveFlushing() }
    BackHandler { attemptBack() }

    // Which row is being edited (shows its +/× controls), and which newly-added
    // row should grab focus next.
    var focusedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingFocusId by rememberSaveable { mutableStateOf<Long?>(null) }

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // When the keyboard is open, leave one viewport of trailing scroll room so
    // even the final item can be moved to the top and edited without fighting
    // the automatic bring-into-view behavior.
    val keyboardScrollSpace = with(density) {
        if (imeVisible) lazyListState.layoutInfo.viewportSize.height.toDp() else 0.dp
    }
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
                // The list's name sits in the top bar, immediately right of the
                // double-chevron Back button, and stays editable there.
                title = {
                    EditableTitleField(
                        value = title,
                        onValueChange = viewModel::setTitle,
                        onFocusLost = viewModel::onTitleFocusLost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save finalizes a draft (new or recovered) into a normal saved
                    // list. An established list auto-saves and shows no button — it
                    // gets a ⋮ menu holding Export and Delete List instead.
                    if (isDraft) {
                        TextButton(
                            enabled = hasText,
                            onClick = { viewModel.save(onBack) },
                        ) { Text("Save") }
                    } else {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = {
                                        menuOpen = false
                                        showFormatChooser = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete List") },
                                    onClick = {
                                        menuOpen = false
                                        showDeleteList = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = keyboardScrollSpace),
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
                            onBlur = { viewModel.onItemFocusLost(item.id) },
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
            // Discard deletes the persisted draft and its items, then leaves.
            onConfirm = { showDiscard = false; viewModel.discardDraft(onBack) },
            onDismiss = { showDiscard = false },
        )
    }

    if (showFormatChooser) {
        ExportFormatDialog(
            thing = "List",
            onDismiss = { showFormatChooser = false },
            options = listOf(
                ExportFormatOption(
                    "Text Document (.txt)",
                    "Simple plain text file",
                ) { startSave(ChecklistExportFormat.TEXT) },
                ExportFormatOption(
                    "Markdown (.md)",
                    "Formatted text document",
                ) { startSave(ChecklistExportFormat.MARKDOWN) },
                ExportFormatOption(
                    "PDF Document (.pdf)",
                    "Printable document format",
                ) { startSave(ChecklistExportFormat.PDF) },
                ExportFormatOption(
                    "Application Data (.json)",
                    "Use this file to import or restore this list later",
                ) { startSave(ChecklistExportFormat.JSON) },
            ),
        )
    }

    if (showDeleteList) {
        AlertDialog(
            onDismissRequest = { showDeleteList = false },
            title = { Text("Delete list?") },
            // Button order is fixed: Okay first, Cancel second. Material renders
            // the dismiss slot before the confirm slot, so Okay goes in the
            // dismiss slot to keep that order on screen.
            dismissButton = {
                TextButton(onClick = {
                    showDeleteList = false
                    viewModel.deleteList(onBack)
                }) { Text("Okay") }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteList = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun EditableTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value) }
    // Keep local text in sync if the stored value loads in after first compose.
    LaunchedEffect(value) { if (value != text) text = value }
    // Persist the title immediately when the field loses focus.
    var wasFocused by remember { mutableStateOf(false) }
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
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) wasFocused = true
                    else if (wasFocused) { wasFocused = false; onFocusLost() }
                },
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
    onBlur: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onAddSubItem: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // Tracks focus so we can persist this item's latest text the moment it blurs.
    var wasFocused by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            // Wait for the inserted row to be measured, then move only as much
            // as necessary to keep the whole new row above the keyboard.
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
            onFocusHandled()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
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
                .onFocusChanged {
                    if (it.isFocused) { onFocused(); wasFocused = true }
                    else if (wasFocused) { wasFocused = false; onBlur() }
                },
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
