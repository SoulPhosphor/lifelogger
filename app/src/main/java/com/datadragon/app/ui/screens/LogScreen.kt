package com.datadragon.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.export.ExportContent
import com.datadragon.app.export.LogExport
import com.datadragon.app.ui.LogViewModel
import com.datadragon.app.ui.theme.DeleteRed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    logId: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onEditForm: () -> Unit,
    viewModel: LogViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }
    val template by viewModel.template.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val notesByEntry by viewModel.notesByEntry.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locked = template?.locked ?: true
    val allowAppendedNotes = template?.allowAppendedNotes ?: false

    var confirmDeleteLog by remember { mutableStateOf(false) }
    var showFormatChooser by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LogEntry?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    var gearMenuOpen by remember { mutableStateOf(false) }
    // Export dialog: whether to include append-only follow-up notes.
    var includeFollowUps by remember { mutableStateOf(true) }
    // The entry a follow-up note is being added to, plus the in-progress text.
    var noteFor by remember { mutableStateOf<LogEntry?>(null) }
    var noteText by remember { mutableStateOf("") }

    // The file the user is saving. They choose the destination and name via the
    // system "Save to…" sheet; we write the bytes to whatever location it returns.
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
    val startSave: (ExportContent) -> Unit = { content ->
        pendingExport = content
        showFormatChooser = false
        saveDocument.launch(content.suggestedName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(template?.name ?: "Log")
                        // A locked log shows a lock by its name; tapping it offers the
                        // one-way unlock.
                        if (locked) {
                            IconButton(onClick = { showUnlock = true }) {
                                Icon(Icons.Filled.Lock, contentDescription = "Locked — tap to unlock")
                            }
                        }
                    }
                },
                navigationIcon = {
                    // Left cluster: back, then a gear menu holding the log-level
                    // actions (export, edit form, delete).
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        Box {
                            IconButton(onClick = { gearMenuOpen = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Log options")
                            }
                            DropdownMenu(
                                expanded = gearMenuOpen,
                                onDismissRequest = { gearMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = {
                                        gearMenuOpen = false
                                        showFormatChooser = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit form") },
                                    onClick = {
                                        gearMenuOpen = false
                                        onEditForm()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete log") },
                                    onClick = {
                                        gearMenuOpen = false
                                        confirmDeleteLog = true
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddEntry) {
                        Icon(Icons.Filled.Add, contentDescription = "Add entry")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No entries yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        fields = fields,
                        appendedNotes = notesByEntry[entry.id].orEmpty(),
                        editable = !locked,
                        appendable = allowAppendedNotes,
                        onDelete = { entryToDelete = entry },
                        onEdit = { onEditEntry(entry.id) },
                        onAddNote = {
                            noteText = ""
                            noteFor = entry
                        },
                    )
                }
            }
        }
    }

    if (showFormatChooser) {
        val current = template
        AlertDialog(
            onDismissRequest = { showFormatChooser = false },
            title = { Text("Download \"${current?.name ?: "log"}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a format, then pick where to save it:")

                    // Only meaningful when this log actually has follow-up notes.
                    if (notesByEntry.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = includeFollowUps,
                                onCheckedChange = { includeFollowUps = it },
                            )
                            Text("Include follow-up notes")
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    startSave(LogExport.markdown(current, fields, entries, notesByEntry, includeFollowUps))
                                }
                            },
                        ) { Text(".md") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    startSave(LogExport.json(current, entries, notesByEntry, includeFollowUps))
                                }
                            },
                        ) { Text(".json") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    startSave(LogExport.text(current, fields, entries, notesByEntry, includeFollowUps))
                                }
                            },
                        ) { Text(".txt") }
                        OutlinedButton(
                            onClick = { if (current != null) startSave(LogExport.csv(current, fields, entries)) },
                        ) { Text(".csv") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    startSave(LogExport.pdf(current, fields, entries, notesByEntry, includeFollowUps))
                                }
                            },
                        ) { Text(".pdf") }
                    }
                    Text(
                        ".md, .txt and .pdf are readable reports; .json re-imports this log; " +
                            ".csv is for spreadsheets (no follow-up notes).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFormatChooser = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDeleteLog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLog = false },
            title = { Text("Delete \"${template?.name ?: "this log"}\"?") },
            text = { Text("This permanently deletes this log and all of its entries. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteLog = false
                    viewModel.deleteLog(onDeleted = onBack)
                }) {
                    Text("Delete log", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLog = false }) { Text("Cancel") }
            },
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("This permanently deletes this entry. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    entryToDelete = null
                    viewModel.deleteEntry(entry)
                }) {
                    Text("Delete entry", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showUnlock) {
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text("Unlock this log?") },
            text = {
                Text(
                    "Unlocking lets you edit its entries. This is permanent — once " +
                        "unlocked, the log can never be re-locked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnlock = false
                    viewModel.unlockLog()
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlock = false }) { Text("Cancel") }
            },
        )
    }

    noteFor?.let { entry ->
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Add follow-up note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This is added as a separate, time-stamped note. The original " +
                            "entry isn't changed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("What happened…") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = noteText.isNotBlank(),
                    onClick = {
                        viewModel.addNote(entry, noteText)
                        noteFor = null
                    },
                ) { Text("Add note") }
            },
            dismissButton = {
                TextButton(onClick = { noteFor = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One entry card (docs/UI_SPEC.md §3). The top line holds the entry's timestamp
 * with a `⋮` menu across from it — Edit (when unlocked), Add follow-up note (when
 * the log allows them), and Delete. Every field with a value is listed below as
 * `label: value`, then any append-only follow-up notes with their timestamps.
 */
@Composable
private fun EntryRow(
    entry: LogEntry,
    fields: List<FieldDef>,
    appendedNotes: List<EntryNote>,
    editable: Boolean,
    appendable: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddNote: () -> Unit,
) {
    val values = remember(entry.valuesJson) { EntryValues.decode(entry.valuesJson) }
    val notes = remember(values) { EntryValues.notes(values) }
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp),
        ) {
            // Top line: timestamp on the left; a ⋮ menu across from it on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = EntryValues.displayEntryTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (editable) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { menuOpen = false; onEdit() },
                            )
                        }
                        if (appendable) {
                            DropdownMenuItem(
                                text = { Text("Add follow-up note") },
                                onClick = { menuOpen = false; onAddNote() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }

            // One label-over-value block per field that has a value, going down.
            fields.forEach { field ->
                EntryValues.displayValue(field, values)?.let { value ->
                    FieldReadout(label = field.label, value = value)
                }
            }
            notes?.let { FieldReadout(label = "Notes", value = it) }

            appendedNotes.forEach { note ->
                FollowUpNote(note)
            }
        }
    }
}

/** An append-only follow-up note: a "↳" marker, its own timestamp, then the text. */
@Composable
private fun FollowUpNote(note: EntryNote) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("↳ ${EntryValues.displayEntryTimestamp(note.createdAt)}: ")
            }
            append(note.text)
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/**
 * A single field shown inline as `label: value` on one line. The label is muted,
 * the value normal weight, and long values (like notes) wrap onto further lines.
 */
@Composable
private fun FieldReadout(label: String, value: String) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
