package com.datadragon.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.export.DailyTaskExportFormat
import com.datadragon.app.export.ExportContent
import com.datadragon.app.ui.DAILY_LIST_DATE_FORMAT
import com.datadragon.app.ui.DailyListViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.AddItemRow
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDestructiveButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.components.ExportFormatDialog
import com.datadragon.app.ui.components.ExportFormatOption
import com.datadragon.app.ui.components.ListEditorItemRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * The Daily Task editor: one day's log. It opens either as a brand-new card
 * (from the "+", route arg not a date) or an existing saved card (tapped on the
 * Daily Tasks screen). A new card shows Save and persists nothing until pressed;
 * once saved it autosaves like an ordinary List and shows a ⋮ menu (Export,
 * Delete). The date sits on top as an always-editable picker; the three-arrow
 * icon carries the previous day's unfinished tasks onto this card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyListEditorScreen(
    date: String?,
    onBack: () -> Unit,
    viewModel: DailyListViewModel = viewModel(),
) {
    // A parseable date opens that day's card (existing or fresh for the date);
    // anything else (the "+" sentinel) opens a brand-new card.
    LaunchedEffect(date) {
        val parsed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (parsed != null) viewModel.openForEditorDate(parsed) else viewModel.openNewCard()
    }

    val editorDate by viewModel.editorDate.collectAsStateWithLifecycle()
    val title by viewModel.editorTitle.collectAsStateWithLifecycle()
    val rows by viewModel.editorRows.collectAsStateWithLifecycle()
    val isSaved by viewModel.editorIsSaved.collectAsStateWithLifecycle()
    val card by viewModel.editorCard.collectAsStateWithLifecycle()
    val allowTitle by viewModel.allowTitle.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var focusedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingFocusId by rememberSaveable { mutableStateOf<Long?>(null) }

    var menuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTodayTaken by remember { mutableStateOf(false) }
    var pastTakenDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    // Export: the user picks the destination via the system "Save to…" sheet.
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
    val startSave: (DailyTaskExportFormat) -> Unit = { format ->
        showExport = false
        scope.launch {
            val content = viewModel.buildExport(format)
            if (content != null) {
                pendingExport = content
                saveDocument.launch(content.suggestedName)
            }
        }
    }

    // Opening a new card never persists anything, so Back just leaves.
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSaved) "Daily Task Log" else "New Daily Task Log",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    // Carry the previous day's unfinished tasks onto this card.
                    IconButton(onClick = { viewModel.runManualRenewal() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cycle),
                            contentDescription = "Carry over unfinished tasks",
                        )
                    }
                    if (isSaved) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Daily Task options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = { menuOpen = false; showExport = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menuOpen = false; showDelete = true },
                                )
                            }
                        }
                    } else {
                        TextButton(
                            enabled = editorDate != null && rows.any { it.text.isNotBlank() },
                            onClick = { viewModel.saveNewCard(onBack) },
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            // The date, always editable, in the forms' picker style.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                AppButton(onClick = { showDatePicker = true }) {
                    Text(editorDate?.format(DAILY_LIST_DATE_FORMAT) ?: "Select Date")
                }
            }

            // The optional per-day title — shown only when the preference is on.
            if (allowTitle) {
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::setEditorTitle,
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            val lazyListState = rememberLazyListState()
            val reorderState = sh.calvin.reorderable.rememberReorderableLazyListState(lazyListState) { from, to ->
                val ids = rows.map { it.localId }.toMutableList()
                if (from.index in ids.indices && to.index in ids.indices) {
                    ids.add(to.index, ids.removeAt(from.index))
                    viewModel.reorder(ids)
                }
            }
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            val keyboardScrollSpace = with(density) {
                if (imeVisible) lazyListState.layoutInfo.viewportSize.height.toDp() else 0.dp
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = keyboardScrollSpace),
            ) {
                itemsIndexed(rows, key = { _, item -> item.localId }) { _, item ->
                    sh.calvin.reorderable.ReorderableItem(reorderState, key = item.localId) { _ ->
                        ListEditorItemRow(
                            rowKey = item.localId,
                            text = item.text,
                            completed = item.completed,
                            indent = item.indent,
                            completedIcon = Icons.Filled.Check,
                            crossOut = false,
                            dragHandleModifier = Modifier.draggableHandle(),
                            isEditing = focusedItemId == item.localId,
                            requestFocus = pendingFocusId == item.localId,
                            onFocused = { focusedItemId = item.localId },
                            onFocusHandled = { if (pendingFocusId == item.localId) pendingFocusId = null },
                            onBlur = { },
                            onTextChange = { viewModel.updateText(item.localId, it) },
                            onToggleComplete = { viewModel.setCompleted(item.localId, !item.completed) },
                            onAddSubItem = {
                                viewModel.addSubItem(item.localId) { newId -> pendingFocusId = newId }
                            },
                            onDelete = {
                                if (focusedItemId == item.localId) focusedItemId = null
                                viewModel.deleteItem(item.localId)
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

    if (showDatePicker) {
        val initialMillis = editorDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                    if (picked != null && picked != editorDate) {
                        scope.launch {
                            if (viewModel.hasCardForDate(picked)) {
                                if (picked == LocalDate.now()) showTodayTaken = true else pastTakenDate = picked
                            } else {
                                viewModel.setEditorDate(picked)
                            }
                        }
                    }
                }) { Text("Okay") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTodayTaken) {
        AppDialog(
            onDismissRequest = { showTodayTaken = false },
            title = "You can only have one daily task log per day.",
            body = "Please choose a new date or use the current day's task log.",
            dismissButton = {
                DialogActionButton("Go to Today's Log") {
                    showTodayTaken = false
                    viewModel.openForEditorDate(LocalDate.now())
                }
            },
            confirmButton = {
                DialogActionButton("Okay") { showTodayTaken = false }
            },
        )
    }

    pastTakenDate?.let { taken ->
        AppDialog(
            onDismissRequest = { pastTakenDate = null },
            title = "You can only have one daily task log per day.",
            body = "Please use a different date or go to that date's log.",
            dismissButton = {
                DialogDismissButton("Cancel") { pastTakenDate = null }
            },
            confirmButton = {
                DialogActionButton("Go to Past Log") {
                    pastTakenDate = null
                    viewModel.openForEditorDate(taken)
                }
            },
        )
    }

    if (showDelete) {
        AppDialog(
            onDismissRequest = { showDelete = false },
            title = "Delete daily task log?",
            dismissButton = { DialogDismissButton("Cancel") { showDelete = false } },
            confirmButton = {
                DialogDestructiveButton("Okay") {
                    showDelete = false
                    card?.let { viewModel.deleteCard(it) { onBack() } } ?: onBack()
                }
            },
        )
    }

    if (showExport) {
        ExportFormatDialog(
            thing = "Daily Task Log",
            onDismiss = { showExport = false },
            options = listOf(
                ExportFormatOption(
                    "Text Document (.txt)",
                    "Simple plain text file",
                ) { startSave(DailyTaskExportFormat.TEXT) },
                ExportFormatOption(
                    "Markdown (.md)",
                    "Formatted text document",
                ) { startSave(DailyTaskExportFormat.MARKDOWN) },
                ExportFormatOption(
                    "PDF Document (.pdf)",
                    "Printable document format",
                ) { startSave(DailyTaskExportFormat.PDF) },
                ExportFormatOption(
                    "Application Data (.json)",
                    "Use this file to import or restore this daily task log later",
                ) { startSave(DailyTaskExportFormat.JSON) },
            ),
        )
    }
}
