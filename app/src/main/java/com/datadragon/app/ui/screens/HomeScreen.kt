package com.datadragon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OnlinePrediction
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.HomeView
import com.datadragon.app.ui.HomeIdeaLog
import com.datadragon.app.ui.HomeLog
import com.datadragon.app.ui.HomeViewModel
import com.datadragon.app.ui.theme.DeleteRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCreateForm: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
    onCreateChecklist: () -> Unit,
    onOpenChecklist: (Long) -> Unit,
    onCreateIdeaLog: () -> Unit,
    onOpenIdeaLog: (Long) -> Unit,
    onAddIdea: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val ideaLogs by viewModel.ideaLogs.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Data Dragon")
                        // A gap after the name, then the view toggles with a little
                        // space between them so none is easy to mis-tap.
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
                        Spacer(Modifier.width(4.dp))
                        ViewToggle(
                            icon = Icons.Filled.OnlinePrediction,
                            contentDescription = "Ideas",
                            selected = view == HomeView.IDEAS,
                            onClick = { viewModel.setView(HomeView.IDEAS) },
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
                            HomeView.LISTS -> onCreateChecklist()
                            HomeView.IDEAS -> onCreateIdeaLog()
                        }
                    }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = when (view) {
                                HomeView.FORMS -> "New form"
                                HomeView.LISTS -> "New list"
                                HomeView.IDEAS -> "New Idea Log"
                            },
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
            HomeView.IDEAS -> IdeasBody(
                ideaLogs = ideaLogs,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenIdeaLog = onOpenIdeaLog,
                onAddIdea = onAddIdea,
            )
        }
    }

    // Offer to recover a list left unfinished when the app was last killed. The
    // draft was written for crash safety but never saved, so it's hidden from Home
    // until the user chooses. Recover opens it in the editor (still a draft);
    // Discard deletes it. Tapping outside keeps it for next time.
    pendingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingDraft() },
            title = { Text("Recover Unfinished List?") },
            text = {
                Text(
                    "You have a list that wasn't saved. Recover it to keep editing, " +
                        "or discard it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPendingDraft()
                    onOpenChecklist(draft.id)
                }) { Text("Recover") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardPendingDraft() }) {
                    Text("Discard", color = DeleteRed)
                }
            },
        )
    }
}

@Composable
private fun ViewToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // These three toggles take no tap coloration: the tap gives no ripple.
    // The active one inverts — a filled circle in the icon's normal (unselected)
    // color, with the icon itself in the color of the bar behind it.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (selected) {
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
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
private fun IdeasBody(
    ideaLogs: List<HomeIdeaLog>,
    modifier: Modifier = Modifier,
    onOpenIdeaLog: (Long) -> Unit,
    onAddIdea: (Long) -> Unit,
) {
    if (ideaLogs.isEmpty()) {
        EmptyMessage(
            title = "No idea logs yet.",
            body = "Tap  +  (top right) to create your first one.",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ideaLogs, key = { it.log.id }) { ideaLog ->
                IdeaLogRow(
                    ideaLog = ideaLog,
                    onOpen = { onOpenIdeaLog(ideaLog.log.id) },
                    onAddIdea = { onAddIdea(ideaLog.log.id) },
                )
            }
        }
    }
}

/**
 * One Idea Log card: its name, then the same entry summary line a form card
 * shows. The "+" on the right adds a new active idea straight into that log.
 */
@Composable
private fun IdeaLogRow(
    ideaLog: HomeIdeaLog,
    onOpen: () -> Unit,
    onAddIdea: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ideaLog.log.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summaryLine(ideaLog.entryCount, ideaLog.lastEntryAt),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onAddIdea) {
                Icon(Icons.Filled.Add, contentDescription = "Add idea to ${ideaLog.log.name}")
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

/** "No Entries Yet" / "1 Entry" / "14 Entries · Last Entry Today" (docs/UI_SPEC.md §2). */
private fun entrySummaryLine(log: HomeLog): String =
    summaryLine(log.entryCount, log.lastEntryAt)

/** The shared count-and-last-entry line, used by form and Idea Log cards alike. */
private fun summaryLine(entryCount: Int, lastEntryAt: String?): String {
    if (entryCount == 0) return "No Entries Yet"
    val count = if (entryCount == 1) "1 Entry" else "$entryCount Entries"
    val last = EntryValues.displayLastEntry(lastEntryAt)
    return if (last != null) "$count · Last Entry $last" else count
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
