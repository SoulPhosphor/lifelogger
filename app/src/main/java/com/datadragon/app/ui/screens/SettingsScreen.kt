package com.datadragon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.BackupViewModel
import com.datadragon.app.ui.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) status = "Couldn't read that file." else pendingJson = text
            }
        }
    }

    // Backup writes the whole database to a .json file the user places via the
    // system "Save to…" sheet. Lives here in Settings (not on the Home bar).
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildBackupJson()
                status = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("No output stream")
                }.fold(
                    onSuccess = { "Backup saved." },
                    onFailure = { "Couldn't save backup: ${it.message}" },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Back up all data", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = {
                status = null
                createDocument.launch("datadragon_backup_${LocalDate.now()}.json")
            }) {
                Text("Back up now…")
            }
            Text(
                "Saves every log and entry into a single .json file you choose the location for.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Restore lives at the bottom, away from everyday controls. Times are
            // always 12-hour (AM/PM), so there is no time-format choice here.
            Text("Restore from backup", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = {
                status = null
                openDocument.launch(
                    arrayOf("application/json", "application/octet-stream", "text/plain"),
                )
            }) {
                Text("Choose backup file…")
            }
            Text(
                "Loads data from a .json backup file. This replaces everything currently in the app.",
                style = MaterialTheme.typography.bodySmall,
            )
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (pendingJson != null) {
        AlertDialog(
            onDismissRequest = { pendingJson = null },
            title = { Text("Restore from this backup?") },
            text = {
                Text(
                    "This replaces all current logs and entries with the contents of the " +
                        "backup file. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingJson
                    pendingJson = null
                    if (json != null) {
                        scope.launch {
                            status = when (val result = viewModel.restore(json)) {
                                is RestoreResult.Success ->
                                    "Restored ${result.logs} ${if (result.logs == 1) "log" else "logs"}."
                                is RestoreResult.Failure -> result.message
                            }
                        }
                    }
                }) {
                    Text("Restore", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null }) { Text("Cancel") }
            },
        )
    }
}
