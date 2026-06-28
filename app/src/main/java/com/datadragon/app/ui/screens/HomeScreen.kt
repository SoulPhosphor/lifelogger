package com.datadragon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.ui.HomeLog
import com.datadragon.app.ui.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onCreateLog: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Dragon") },
                navigationIcon = {
                    // Top-left settings, then download/backup, in that order.
                    Row {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onOpenBackup) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Back up all data")
                        }
                    }
                },
                actions = {
                    // Top-right is reserved exclusively for "create a new log".
                    IconButton(onClick = onCreateLog) {
                        Icon(Icons.Filled.Add, contentDescription = "New log")
                    }
                },
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            EmptyHome(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(logs, key = { it.template.id }) { log ->
                    LogRow(
                        log = log,
                        onOpen = { onOpenLog(log.template.id) },
                        onAddEntry = { onAddEntry(log.template.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    log: HomeLog,
    onOpen: () -> Unit,
    onAddEntry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.template.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entrySummaryLine(log),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Per-row add-entry button stays on the far right so the everyday
            // action is consistent and far from any destructive control.
            IconButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry to ${log.template.name}")
            }
        }
    }
}

/** "No entries yet" / "1 entry" / "14 entries · last entry today" (docs/UI_SPEC.md §2). */
private fun entrySummaryLine(log: HomeLog): String {
    if (log.entryCount == 0) return "No entries yet"
    val count = if (log.entryCount == 1) "1 entry" else "${log.entryCount} entries"
    val last = EntryValues.displayLastEntry(log.lastEntryAt)
    return if (last != null) "$count · last entry $last" else count
}

@Composable
private fun EmptyHome(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No logs yet.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap  +  (top right) to create your first one.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
