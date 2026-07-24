package com.datadragon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.RestoreMode
import com.datadragon.app.ui.BackupViewModel
import com.datadragon.app.ui.RestoreResult
import com.datadragon.app.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoCapitalizeLabels by settingsViewModel.autoCapitalizeLabels.collectAsStateWithLifecycle()
    val autoCapitalizeOptions by settingsViewModel.autoCapitalizeOptions.collectAsStateWithLifecycle()
    val completeIcon by settingsViewModel.completeIcon.collectAsStateWithLifecycle()
    val crossOutWhenCompleted by settingsViewModel.crossOutWhenCompleted.collectAsStateWithLifecycle()
    val moveCompletedToBottom by settingsViewModel.moveCompletedToBottom.collectAsStateWithLifecycle()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    // Non-destructive by default: Merge can only add or update, never delete
    // something the chosen backup didn't include.
    var importMode by remember { mutableStateOf(RestoreMode.MERGE) }

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
            // Text-formatting preferences. Their titles are deliberately kept as
            // sentences (not Title Case) because they're long. The "future items"
            // note applies to both toggles, so it sits once under the header.
            SectionHeader("Text Formatting")
            Text(
                "Only applies to future items.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingToggleRow(
                checked = autoCapitalizeLabels,
                onCheckedChange = settingsViewModel::setAutoCapitalizeLabels,
                title = "Auto capitalize major words of label titles",
            )
            SettingToggleRow(
                checked = autoCapitalizeOptions,
                onCheckedChange = settingsViewModel::setAutoCapitalizeOptions,
                title = "Auto capitalize major words of drop-down and multiple choice options",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Global behavior for every list.
            SectionHeader("Lists")
            CompleteIconRow(
                selected = completeIcon,
                onSelected = settingsViewModel::setCompleteIcon,
            )
            SettingToggleRow(
                checked = crossOutWhenCompleted,
                onCheckedChange = settingsViewModel::setCrossOutWhenCompleted,
                title = "Cross Out Item When Completed",
            )
            SettingToggleRow(
                checked = moveCompletedToBottom,
                onCheckedChange = settingsViewModel::setMoveCompletedToBottom,
                title = "Move Completed Item to Bottom When Marked Complete",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader("Back Up All Data")
            OutlinedButton(onClick = {
                status = null
                createDocument.launch("datadragon_backup_${LocalDate.now()}.json")
            }) {
                Text("Back Up Now…")
            }
            Text(
                "Saves every log and entry into a single .json file you choose the location for.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Restore lives at the bottom, away from everyday controls. Times are
            // always 12-hour (AM/PM), so there is no time-format choice here.
            SectionHeader("Restore from Backup")
            ImportModeRow(selected = importMode, onSelected = { importMode = it })
            OutlinedButton(onClick = {
                status = null
                openDocument.launch(
                    arrayOf("application/json", "application/octet-stream", "text/plain"),
                )
            }) {
                Text("Choose Backup File…")
            }
            Text(
                importMode.description(),
                style = MaterialTheme.typography.bodySmall,
            )
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (pendingJson != null) {
        val mode = importMode
        AlertDialog(
            onDismissRequest = { pendingJson = null },
            title = {
                Text(
                    when (mode) {
                        RestoreMode.REPLACE -> "Replace everything with this backup?"
                        RestoreMode.MERGE -> "Merge this backup into your data?"
                    }
                )
            },
            text = {
                Text(
                    when (mode) {
                        RestoreMode.REPLACE ->
                            "All forms, entries, and lists currently in the app will be " +
                                "permanently removed and replaced with the contents of this " +
                                "backup. This can't be undone."
                        RestoreMode.MERGE ->
                            "New forms and lists will be added, and any that already exist " +
                                "will be updated to match the backup. Existing items you didn't " +
                                "include stay untouched. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingJson
                    pendingJson = null
                    if (json != null) {
                        scope.launch {
                            status = when (val result = viewModel.restore(json, mode)) {
                                is RestoreResult.Success ->
                                    restoreSummary(mode, result.logs, result.lists)
                                is RestoreResult.Failure -> result.message
                            }
                        }
                    }
                }) {
                    // Red only for Replace, the destructive mode; Merge is
                    // non-destructive, so it uses the normal button color.
                    Text(
                        when (mode) {
                            RestoreMode.REPLACE -> "Replace All"
                            RestoreMode.MERGE -> "Merge"
                        },
                        color = if (mode == RestoreMode.REPLACE) Color(0xFFC62828) else Color.Unspecified,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null }) { Text("Cancel") }
            },
        )
    }
}

/** The label shown for an import mode in the dropdown and current selection. */
private fun RestoreMode.label(): String = when (this) {
    RestoreMode.REPLACE -> "Replace All Data"
    RestoreMode.MERGE -> "Merge with Existing Data"
}

/** The one-line explanation of what an import mode does. */
private fun RestoreMode.description(): String = when (this) {
    RestoreMode.REPLACE ->
        "Removes everything currently in the app and restores only what's in this backup. " +
            "Any forms or lists not included in the backup are permanently deleted."
    RestoreMode.MERGE ->
        "Adds anything new from the backup and updates forms or lists that already exist, " +
            "while leaving everything else in place. Nothing is deleted."
}

/** The status line shown after a successful restore. */
private fun restoreSummary(mode: RestoreMode, logs: Int, lists: Int): String {
    val forms = "$logs ${if (logs == 1) "form" else "forms"}"
    val listsText = "$lists ${if (lists == 1) "list" else "lists"}"
    return when (mode) {
        RestoreMode.REPLACE -> "Replaced all data — restored $forms and $listsText."
        RestoreMode.MERGE -> "Merge complete — $forms and $listsText added or updated."
    }
}

/**
 * A section heading, sized a touch smaller than the "Settings" title up top so
 * the sections are easy to scan.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
}

/**
 * A settings row: a title (and optional subtitle) on the left with a Switch on
 * the right. The whole row is tappable to toggle, which is an easier target.
 */
@Composable
private fun SettingToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * "Import Mode" chooser for Restore: the label sits above a lightly outlined box
 * showing the current mode, which opens a drop-down to switch between Merge and
 * Replace. Stacked (not side-by-side) because the mode names are long.
 */
@Composable
private fun ImportModeRow(
    selected: RestoreMode,
    onSelected: (RestoreMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Import Mode", style = MaterialTheme.typography.bodyLarge)
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.label(), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RestoreMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Label of the mark shown on a completed item. */
private fun CompleteIcon.label(): String = when (this) {
    CompleteIcon.CHECKMARK -> "Checkmark"
    CompleteIcon.CHECKED_BOX -> "Checked Box"
}

/**
 * "Item Complete Icon" chooser: the current choice on the right opens a small
 * drop-down to pick between a checkmark and a checked box.
 */
@Composable
private fun CompleteIconRow(
    selected: CompleteIcon,
    onSelected: (CompleteIcon) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Item Complete Icon",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box {
            // The current choice sits in a lightly outlined, slightly rounded box
            // that opens the drop-down when tapped.
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.label(), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CompleteIcon.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
