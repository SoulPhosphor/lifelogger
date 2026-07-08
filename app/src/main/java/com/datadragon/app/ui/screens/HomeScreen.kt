package com.datadragon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
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
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.HomeView
import com.datadragon.app.ui.HomeLog
import com.datadragon.app.ui.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCreateForm: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
    onOpenChecklist: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Data Dragon")
                        // A gap after the name, then the two view toggles with a
                        // little space between them so neither is easy to mis-tap.
                        Spacer(Modifier.width(16.dp))
                        ViewToggle(
                            icon = Icons.Filled.Description,
                            contentDescription = "Forms",
                            selected = view == HomeView.FORMS,
                            onClick = { viewModel.setView(HomeView.FORMS) },
                        )
                        Spacer(Modifier.width(4.dp))
                        ViewToggle(
                            icon = Icons.Filled.Checklist,
                            contentDescription = "Lists",
                            selected = view == HomeView.LISTS,
                            onClick = { viewModel.setView(HomeView.LISTS) },
                        )
                    }
                },
                navigationIcon = {
                    // Settings holds backup/restore and the global list options;
                    // the top-right "+" creates a form or list per the current view.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                actions = {
                    // Top-right creates a new item in whichever view is showing.
                    IconButton(onClick = {
                        when (view) {
                            HomeView.FORMS -> onCreateForm()
                            HomeView.LISTS -> viewModel.createChecklist(onOpenChecklist)
                        }
                    }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (view == HomeView.FORMS) "New form" else "New list",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (view) {
            HomeView.FORMS -> FormsBody(
                logs = logs,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenLog = onOpenLog,
                onAddEntry = onAddEntry,
            )
            HomeView.LISTS -> ListsBody(
                checklists = checklists,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenChecklist = onOpenChecklist,
            )
        }
    }
}

@Composable
private fun ViewToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun FormsBody(
    logs: List<HomeLog>,
    modifier: Modifier = Modifier,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
) {
    if (logs.isEmpty()) {
        EmptyMessage(
            title = "No logs yet.",
            body = "Tap  +  (top right) to create your first one.",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
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

@Composable
private fun ListsBody(
    checklists: List<Checklist>,
    modifier: Modifier = Modifier,
    onOpenChecklist: (Long) -> Unit,
) {
    if (checklists.isEmpty()) {
        EmptyMessage(
            title = "No lists yet.",
            body = "Tap  +  (top right) to create your first one.",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(checklists, key = { it.id }) { checklist ->
                ChecklistRow(
                    checklist = checklist,
                    onOpen = { onOpenChecklist(checklist.id) },
                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (log.template.locked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = log.template.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

@Composable
private fun ChecklistRow(
    checklist: Checklist,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = checklist.name.ifBlank { "Untitled list" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
