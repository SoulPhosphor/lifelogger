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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

    // Phase 1 proved the confirmation flow exists; wiring real deletion is Phase 7.
    var confirmDeleteLog by remember { mutableStateOf(false) }
    var showFormatChooser by remember { mutableStateOf(false) }

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
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this log", tint = DeleteRed)
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
                    EntryRow(entry = entry, fields = fields)
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
                    }
                    Text(
                        ".md and .txt are readable reports; .json re-imports this log; " +
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
                TextButton(onClick = { confirmDeleteLog = false }) {
                    Text("Delete log", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One entry row: timestamp, a one-line summary of field values, and a notes
 * preview if present (docs/UI_SPEC.md §3). Tap-to-edit and per-row delete arrive
 * in Phase 7.
 */
@Composable
private fun EntryRow(entry: LogEntry, fields: List<FieldDef>) {
    val values = remember(entry.valuesJson) { EntryValues.decode(entry.valuesJson) }
    val summary = remember(values, fields) { EntryValues.summaryLine(fields, values) }
    val notes = remember(values) { EntryValues.notes(values) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = EntryValues.displayEntryTimestamp(entry.createdAt),
                style = MaterialTheme.typography.titleSmall,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notes != null) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
