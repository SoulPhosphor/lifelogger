package com.datadragon.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.CsvBuilder
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.ExportNaming
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.ReportBuilder
import com.datadragon.app.export.PdfReport
import com.datadragon.app.export.shareFile
import com.datadragon.app.export.shareReport
import com.datadragon.app.export.shareTextFile
import com.datadragon.app.ui.LogViewModel
import com.datadragon.app.ui.theme.DeleteRed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    logId: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    viewModel: LogViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }
    val template by viewModel.template.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmDeleteLog by remember { mutableStateOf(false) }
    var showFormatChooser by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LogEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template?.name ?: "Log") },
                navigationIcon = {
                    // Left cluster: back, download, delete — destructive control kept far
                    // from the everyday "+" on the right.
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { showFormatChooser = true }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Download this log")
                        }
                        IconButton(onClick = { confirmDeleteLog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this log")
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
                        onDelete = { entryToDelete = entry },
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
                    Text("Choose a format:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    shareReport(context, ReportBuilder.build(current, fields, entries, markdown = true))
                                }
                                showFormatChooser = false
                            },
                        ) { Text(".md") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    val json = BackupCodec.encodeSingleLog(current, entries, BackupRepository.now())
                                    shareTextFile(
                                        context,
                                        "${ExportNaming.base(current.name)}.json",
                                        "application/json",
                                        json,
                                    )
                                }
                                showFormatChooser = false
                            },
                        ) { Text(".json") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    shareReport(context, ReportBuilder.build(current, fields, entries, markdown = false))
                                }
                                showFormatChooser = false
                            },
                        ) { Text(".txt") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    shareTextFile(
                                        context,
                                        "${ExportNaming.base(current.name)}.csv",
                                        "text/csv",
                                        CsvBuilder.build(fields, entries),
                                    )
                                }
                                showFormatChooser = false
                            },
                        ) { Text(".csv") }
                        OutlinedButton(
                            onClick = {
                                if (current != null) {
                                    val file = PdfReport.writeToFile(context, current, fields, entries)
                                    shareFile(context, file, "application/pdf")
                                }
                                showFormatChooser = false
                            },
                        ) { Text(".pdf") }
                    }
                    Text(
                        ".md, .txt and .pdf are readable reports; .json re-imports this log; " +
                            ".csv is for spreadsheets.",
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
}

/**
 * One entry card (docs/UI_SPEC.md §3). The top line holds the entry's timestamp
 * with the delete `🗑` across from it; every field with a value is then listed
 * below as a clean label-over-value block, stacked vertically. Entries are never
 * edited — only added or deleted.
 */
@Composable
private fun EntryRow(entry: LogEntry, fields: List<FieldDef>, onDelete: () -> Unit) {
    val values = remember(entry.valuesJson) { EntryValues.decode(entry.valuesJson) }
    val notes = remember(values) { EntryValues.notes(values) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp),
        ) {
            // Top line: timestamp on the left, delete across from it on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = EntryValues.displayEntryTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                }
            }

            // One label-over-value block per field that has a value, going down.
            fields.forEach { field ->
                EntryValues.displayValue(field, values)?.let { value ->
                    FieldReadout(label = field.label, value = value)
                }
            }
            notes?.let { FieldReadout(label = "Notes", value = it) }
        }
    }
}

/** A single field shown as a muted label with its full value on the line below. */
@Composable
private fun FieldReadout(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
