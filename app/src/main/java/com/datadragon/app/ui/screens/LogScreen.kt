package com.datadragon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.datadragon.app.data.PlaceholderData
import com.datadragon.app.data.PlaceholderEntry
import com.datadragon.app.ui.theme.DeleteRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logId: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
) {
    val log = PlaceholderData.logById(logId)
    val entries = PlaceholderData.entriesFor(logId)

    // Phase 1 only proves the confirmation flow exists; real deletion arrives in Phase 7.
    var confirmDeleteLog by remember { mutableStateOf(false) }
    var entryPendingDelete by remember { mutableStateOf<PlaceholderEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(log?.name ?: "Log") },
                navigationIcon = {
                    // Left cluster: back, download, delete — destructive control kept far
                    // from the everyday "+" on the right.
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { /* Phase 5: download chooser */ }) {
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onOpen = { /* Phase 7: open entry for view/edit */ },
                        onDelete = { entryPendingDelete = entry },
                    )
                }
            }
        }
    }

    if (confirmDeleteLog) {
        ConfirmDialog(
            title = "Delete \"${log?.name ?: "this log"}\"?",
            body = "This permanently deletes this log and all of its entries. This can't be undone.",
            confirmLabel = "Delete log",
            onConfirm = { confirmDeleteLog = false },
            onDismiss = { confirmDeleteLog = false },
        )
    }

    entryPendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete this entry?",
            body = "${entry.timestampLabel}\nThis can't be undone.",
            confirmLabel = "Delete entry",
            onConfirm = { entryPendingDelete = null },
            onDismiss = { entryPendingDelete = null },
        )
    }
}

@Composable
private fun EntryRow(
    entry: PlaceholderEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.timestampLabel, style = MaterialTheme.typography.titleSmall)
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                entry.notesPreview?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = DeleteRed)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Color(0xFFC62828))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
