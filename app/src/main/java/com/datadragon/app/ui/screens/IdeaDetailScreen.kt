package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.IdeaValues
import com.datadragon.app.ui.IdeaDetailViewModel
import com.datadragon.app.ui.theme.DeleteRed

/**
 * The full, read-only view of one idea, opened by tapping its card.
 *
 * Because Preview Mode deliberately hides some of an idea, this view shows all
 * of it: every configured field that has a value, multiline text in full, tags,
 * webpage buttons, any counts and copy buttons, and the automatic timestamp when
 * the log shows it. Every card-display setting — Include in Preview Mode, the
 * Preview Mode line count, and a field's Entire Idea Card truncation limit — is
 * ignored here. Those settings govern cards, not this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaDetailScreen(
    ideaLogId: String?,
    ideaId: String?,
    onBack: () -> Unit,
    onEditIdea: (Long) -> Unit,
    viewModel: IdeaDetailViewModel = viewModel(),
) {
    val logId = ideaLogId?.toLongOrNull()
    val id = ideaId?.toLongOrNull()
    LaunchedEffect(logId, id) {
        if (logId != null && id != null) viewModel.load(logId, id)
    }

    val log by viewModel.log.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entry by viewModel.entry.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = entry
    val values = remember(current?.valuesJson) {
        current?.let { IdeaValues.decode(it.valuesJson) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idea") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    if (current != null) {
                        IdeaActions(
                            marked = current.marked,
                            archived = current.archived,
                            allowArchiving = log?.allowArchiving ?: false,
                            menuOpen = menuOpen,
                            onMenuOpenChange = { menuOpen = it },
                            onToggleMark = viewModel::toggleMark,
                            onEdit = { onEditIdea(current.id) },
                            onSetArchived = viewModel::setArchived,
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (current != null && values != null) {
                if (log?.automaticTimestamping == true) {
                    Text(
                        text = EntryValues.displayEntryTimestamp(current.createdAt),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val shown = visibleIdeaFields(fields, values, IdeaDisplayMode.DETAIL)
                if (shown.isEmpty()) {
                    Text(
                        "This idea has no filled-in fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                shown.forEach { field ->
                    IdeaFieldReadout(
                        field = field,
                        values = values,
                        log = log,
                        mode = IdeaDisplayMode.DETAIL,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this idea?") },
            text = { Text("This permanently deletes this idea. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete Idea", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
