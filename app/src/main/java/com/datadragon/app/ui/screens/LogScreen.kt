package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.LogViewModel
import com.datadragon.app.ui.theme.DeleteRed

@OptIn(ExperimentalMaterial3Api::class)
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

    // Phase 1 proved the confirmation flow exists; wiring real deletion is Phase 7.
    var confirmDeleteLog by remember { mutableStateOf(false) }

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
        // Entries arrive in Phase 4; for now every log shows the empty state.
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text("No entries yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
        }
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
