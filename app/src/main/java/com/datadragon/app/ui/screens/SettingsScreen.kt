package com.datadragon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.OnlinePrediction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.datadragon.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.HomeView
import com.datadragon.app.data.NavStyle
import com.datadragon.app.data.RestoreConflict
import com.datadragon.app.data.RestoreConflictChoice
import com.datadragon.app.data.RestoreConflictKind
import com.datadragon.app.data.RestoreConflictPolicy
import com.datadragon.app.data.RestoreCounts
import com.datadragon.app.data.RestoreMode
import com.datadragon.app.ui.BackupViewModel
import com.datadragon.app.ui.RestoreResult
import com.datadragon.app.ui.SettingsViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDropdownRow
import com.datadragon.app.ui.theme.AppTheme
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
    val navStyle by settingsViewModel.navStyle.collectAsStateWithLifecycle()
    val useModeLabelInDropdown by settingsViewModel.useModeLabelInDropdown.collectAsStateWithLifecycle()
    val enabledModes by settingsViewModel.enabledModes.collectAsStateWithLifecycle()
    val completeIcon by settingsViewModel.completeIcon.collectAsStateWithLifecycle()
    val crossOutWhenCompleted by settingsViewModel.crossOutWhenCompleted.collectAsStateWithLifecycle()
    val moveCompletedToBottom by settingsViewModel.moveCompletedToBottom.collectAsStateWithLifecycle()
    // Not saveable: a chosen backup file's full contents can be large enough to
    // overflow the instance-state Bundle (TransactionTooLargeException), so a
    // process death simply asks the user to re-choose the file rather than risk
    // a crash. status is a short message, so it's safe and worth restoring.
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    // Non-destructive by default: Merge can only add or update, never delete
    // something the chosen backup didn't include.
    var importMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var conflictPolicy by rememberSaveable { mutableStateOf(viewModel.restoreConflictPolicy) }
    var restoreType by remember { mutableStateOf(RestoreType.EVERYTHING) }
    var hasUndoSnapshot by remember { mutableStateOf(false) }
    var pendingUndo by remember { mutableStateOf(false) }
    var pendingConflicts by remember { mutableStateOf<List<RestoreConflict>?>(null) }

    LaunchedEffect(Unit) {
        hasUndoSnapshot = viewModel.hasUndoSnapshot()
    }

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

    // Restore Individual Item: one exported list or form, merged straight in.
    // The type is read from the file, so there is nothing for the user to pick
    // and no confirmation to give — nothing is replaced or deleted.
    val openSingleItem = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                status = if (text.isNullOrBlank()) {
                    "Couldn't read that file."
                } else {
                    when (val result = viewModel.restoreSingleItem(text)) {
                        is RestoreResult.Success -> singleItemSummary(result.logs, result.lists)
                        is RestoreResult.NeedsConflictResolution ->
                            "This individual item conflicts with current data."
                        is RestoreResult.Failure -> result.message
                    }
                }
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

    pendingConflicts?.let { conflicts ->
        ConflictReviewScreen(
            conflicts = conflicts,
            onCancel = {
                viewModel.cancelPendingRestore()
                pendingConflicts = null
            },
            onContinue = { choices ->
                scope.launch {
                    when (val result = viewModel.continueRestore(choices)) {
                        is RestoreResult.Success -> {
                            hasUndoSnapshot = true
                            status = restoreSummary(RestoreMode.MERGE, result.counts)
                            pendingConflicts = null
                        }
                        is RestoreResult.Failure -> status = result.message
                        is RestoreResult.NeedsConflictResolution -> pendingConflicts = result.conflicts
                    }
                }
            },
        )
        return
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
        // Settings is taller than the screen, so the whole page scrolls — the last
        // section (Restore from Backup) must always be reachable.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // How the Home bar presents the data modes: a row of icons, or a
            // dropdown that shows one mode at a time.
            SectionHeader("Main Navigation Menu")
            NavStyleRadioRow(
                label = "Icons",
                selected = navStyle == NavStyle.ICONS,
                onSelect = { settingsViewModel.setNavStyle(NavStyle.ICONS) },
            )
            NavStyleRadioRow(
                label = "Dropdown",
                selected = navStyle == NavStyle.DROPDOWN,
                onSelect = { settingsViewModel.setNavStyle(NavStyle.DROPDOWN) },
            )
            SettingToggleRow(
                checked = useModeLabelInDropdown,
                onCheckedChange = settingsViewModel::setUseModeLabelInDropdown,
                title = "Use mode label instead of single icon in drop-down mode.",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Which data modes appear in the navigation. Unchecking one only hides
            // it from the bar — the mode's data is untouched and returns when shown.
            SectionHeader("Choose Your Data Modes")
            DataModeCheckRow(
                icon = Icons.Filled.Description,
                label = "Forms",
                subtext = "Create dynamic forms that allow you to use text fields, multiselection options, drop-downs and more.",
                checked = HomeView.FORMS in enabledModes,
                onCheckedChange = { settingsViewModel.setModeEnabled(HomeView.FORMS, it) },
            )
            DataModeCheckRow(
                icon = Icons.Filled.Checklist,
                label = "Lists",
                subtext = "Create lists that allow for sub items that helps you keep track of things that you've completed.",
                checked = HomeView.LISTS in enabledModes,
                onCheckedChange = { settingsViewModel.setModeEnabled(HomeView.LISTS, it) },
            )
            DataModeCheckRow(
                icon = Icons.Filled.OnlinePrediction,
                label = "Idea Logs",
                subtext = "Keep track of your unique ideas. Somewhat like forms but also allows for storing web pages, unique item views and archiving.",
                checked = HomeView.IDEAS in enabledModes,
                onCheckedChange = { settingsViewModel.setModeEnabled(HomeView.IDEAS, it) },
            )
            DataModeCheckRow(
                icon = Icons.Filled.EventNote,
                label = "Daily Tasks",
                subtext = "Keep track of everything you've done in a day. Allows for unfinished items to be brought to the next day. Special celebration icon can be selected to remember the days you've managed to do it all.",
                checked = HomeView.DAILY_LIST in enabledModes,
                onCheckedChange = { settingsViewModel.setModeEnabled(HomeView.DAILY_LIST, it) },
            )
            DataModeCheckRow(
                iconRes = R.drawable.ic_chart_data,
                label = "Clicker Data",
                subtext = "Allows you to quickly keep track of increasing number of events or items.",
                checked = HomeView.CLICKER in enabledModes,
                onCheckedChange = { settingsViewModel.setModeEnabled(HomeView.CLICKER, it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Text-formatting preferences. Their titles are deliberately kept as
            // sentences (not Title Case) because they're long. The "future items"
            // note applies to both toggles, so it sits once under the header.
            SectionHeader("Text Formatting")
            Text(
                "Only applies to future items.",
                style = AppTheme.textStyles.settingDescription,
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

            // The hint sits under its heading, above the control it describes —
            // never below the control (docs/STYLE.md).
            SectionHeader("Back Up All Data")
            Text(
                "Saves every log and entry into a single .json file you choose the location for.",
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppButton(onClick = {
                status = null
                createDocument.launch("datadragon_backup_${LocalDate.now()}.json")
            }) {
                Text("Back Up Now…")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Restore lives at the bottom, away from everyday controls. Times are
            // always 12-hour (AM/PM), so there is no time-format choice here.
            SectionHeader("Restore from Backup")

            // Whole-database restore: the file is the one "Back Up All Data"
            // writes, and Import Mode decides how it lands.
            SubsectionHeader("Restore from Database")
            Text(
                "Alters entire app contents based on previous snapshot.",
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // First which kinds of data to take from the backup, then how they
            // land, then the file itself.
            RestoreTypeRow(selected = restoreType, onSelected = { restoreType = it })
            ImportModeRow(selected = importMode, onSelected = { importMode = it })
            if (importMode == RestoreMode.MERGE) {
                Text(
                    "If there is a conflict, what would you like to have happen?",
                    style = AppTheme.textStyles.settingTitle,
                )
                ConflictPolicyRadioRow(
                    label = "Keep Current Data",
                    selected = conflictPolicy == RestoreConflictPolicy.KEEP_CURRENT,
                    onSelect = {
                        conflictPolicy = RestoreConflictPolicy.KEEP_CURRENT
                        viewModel.setRestoreConflictPolicy(conflictPolicy)
                    },
                )
                ConflictPolicyRadioRow(
                    label = "Use Backup Data",
                    selected = conflictPolicy == RestoreConflictPolicy.USE_BACKUP,
                    onSelect = {
                        conflictPolicy = RestoreConflictPolicy.USE_BACKUP
                        viewModel.setRestoreConflictPolicy(conflictPolicy)
                    },
                )
                ConflictPolicyRadioRow(
                    label = "Ask Me",
                    selected = conflictPolicy == RestoreConflictPolicy.ASK,
                    onSelect = {
                        conflictPolicy = RestoreConflictPolicy.ASK
                        viewModel.setRestoreConflictPolicy(conflictPolicy)
                    },
                )
            }
            AppButton(onClick = {
                status = null
                openDocument.launch(BACKUP_MIME_TYPES)
            }) {
                Text("Choose Backup File…")
            }
            // Extra room here: this button and the one below are two separate
            // actions, not one control's label and value, so they get more space
            // than the default row gap (docs/STYLE.md).
            Spacer(Modifier.height(AppTheme.spacing.distinctControls - AppTheme.spacing.related))
            // Undo lives at the end of the whole-database controls, so it always
            // sits with the large changes it can put back, and it puts back the
            // same kinds of data chosen above. It does not apply to single-item
            // restores below.
            AppButton(
                onClick = { pendingUndo = true },
                enabled = hasUndoSnapshot,
            ) {
                Text(if (hasUndoSnapshot) "Restore" else "Nothing to Restore")
            }

            // Single-item restore: one exported list or form. No type to pick —
            // the file says which it is.
            SubsectionHeader("Restore Individual Item")
            Text(
                "Re-adds a single item from any data type. Requires json formatting.",
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppButton(onClick = {
                status = null
                openSingleItem.launch(BACKUP_MIME_TYPES)
            }) {
                Text("Choose Backup File…")
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (pendingJson != null) {
        val mode = importMode
        val type = restoreType
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
                            "Selected categories present in the backup will be replaced. " +
                                "Undo Last Import will preserve their current state first."
                        RestoreMode.MERGE ->
                            "New data will be added. Matching data follows your conflict choice, " +
                                "and anything absent from the backup stays untouched."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingJson
                    pendingJson = null
                    if (json != null) {
                        scope.launch {
                            status = when (
                                val result = viewModel.restore(
                                    json,
                                    mode,
                                    forms = type.forms(),
                                    lists = type.lists(),
                                    conflictPolicy = conflictPolicy,
                                )
                            ) {
                                is RestoreResult.Success -> {
                                    hasUndoSnapshot = true
                                    restoreSummary(mode, result.counts)
                                }
                                is RestoreResult.NeedsConflictResolution -> {
                                    pendingConflicts = result.conflicts
                                    null
                                }
                                is RestoreResult.Failure -> result.message
                            }
                        }
                    }
                }) {
                    // No red: every dialog button shares one color for now.
                    Text(
                        when (mode) {
                            RestoreMode.REPLACE -> "Replace All"
                            RestoreMode.MERGE -> "Merge"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingJson = null }) { Text("Cancel") }
            },
        )
    }

    if (pendingUndo) {
        AlertDialog(
            onDismissRequest = { pendingUndo = false },
            text = {
                Text(
                    when (restoreType) {
                        RestoreType.EVERYTHING ->
                            "Restore previous state prior to import? This can't be undone."
                        RestoreType.LIST ->
                            "Restore previous list state prior to import? This can't be undone."
                        RestoreType.FORM ->
                            "Restore previous form state prior to import? This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUndo = false
                    scope.launch {
                        status = viewModel.undoImport()
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUndo = false }) { Text("Cancel") }
            },
        )
    }
}

/** The file types the system picker offers for a backup or a single-item export. */
private val BACKUP_MIME_TYPES =
    arrayOf("application/json", "application/octet-stream", "text/plain")

/** The status line shown after restoring one exported list or form. */
private fun singleItemSummary(logs: Int, lists: Int): String = when {
    lists > 0 -> "Restored 1 list."
    logs > 0 -> "Restored 1 form."
    else -> "Nothing to restore."
}

/**
 * Which kinds of data a whole-database restore touches — and, with it, which
 * kinds Undo puts back. Everything covers every type at once; the named types
 * leave the others exactly as they are. A new data type gets an entry here.
 */
private enum class RestoreType { EVERYTHING, LIST, FORM }

private fun RestoreType.label(): String = when (this) {
    RestoreType.EVERYTHING -> "Everything"
    RestoreType.LIST -> "List"
    RestoreType.FORM -> "Form"
}

/** True when this choice includes forms. */
private fun RestoreType.forms(): Boolean = this != RestoreType.LIST

/** True when this choice includes lists. */
private fun RestoreType.lists(): Boolean = this != RestoreType.FORM

/**
 * "Restore Type:" chooser for Undo Last Import (docs/STYLE.md — a drop-down
 * shares its label's line, and its width never changes with the value picked).
 */
@Composable
private fun RestoreTypeRow(
    selected: RestoreType,
    onSelected: (RestoreType) -> Unit,
) {
    AppDropdownRow(
        label = "Restore Type:",
        options = RestoreType.entries,
        selected = selected,
        onSelected = onSelected,
        optionLabel = { it.label() },
    )
}

/** The full label shown for an import mode in the drop-down menu. */
private fun RestoreMode.label(): String = when (this) {
    RestoreMode.REPLACE -> "Replace All Data"
    RestoreMode.MERGE -> "Merge with Existing Data"
}

/** The status line shown after a successful restore. */
private fun restoreSummary(mode: RestoreMode, counts: RestoreCounts): String = when (mode) {
    RestoreMode.REPLACE ->
        "Replace complete — ${counts.replaced} restored, ${counts.skipped} skipped."
    RestoreMode.MERGE ->
        "Merge complete — ${counts.added} added, ${counts.replaced} updated, " +
            "${counts.skipped} skipped, ${counts.conflicted} conflicts handled."
}

/**
 * A section heading. Its size comes from the theme (`sectionHeader`), which sits
 * one step below the "Settings" title in the top bar — never a hard-coded size.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(text, style = AppTheme.textStyles.sectionHeader)
}

/** A heading for one block inside a section, a step below [SectionHeader]. */
@Composable
private fun SubsectionHeader(text: String) {
    Text(text, style = AppTheme.textStyles.subsectionHeader)
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
            Text(title, style = AppTheme.textStyles.settingTitle)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = AppTheme.textStyles.settingDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One navigation-style choice: a radio button and its label. The whole row is
 * tappable, so the label selects it too.
 */
@Composable
private fun NavStyleRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = AppTheme.textStyles.settingTitle)
    }
}

@Composable
private fun ConflictPolicyRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) = NavStyleRadioRow(label = label, selected = selected, onSelect = onSelect)

/** One preflight review containing every conflict; no restore has started yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictReviewScreen(
    conflicts: List<RestoreConflict>,
    onCancel: () -> Unit,
    onContinue: (Map<String, RestoreConflictChoice>) -> Unit,
) {
    var choices by remember(conflicts) {
        mutableStateOf(conflicts.associate { it.id to RestoreConflictChoice.KEEP_CURRENT })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Merge Conflicts") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Cancel")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Nothing will change until you continue.",
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        choices = conflicts.associate { it.id to RestoreConflictChoice.KEEP_CURRENT }
                    },
                ) { Text("Keep Current for All") }
                TextButton(
                    onClick = {
                        choices = conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP }
                    },
                ) { Text("Use Backup for All") }
            }
            conflicts.groupBy { it.category }.forEach { (category, categoryConflicts) ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SubsectionHeader(category.restoreLabel())
                categoryConflicts.forEach { conflict ->
                    Text(conflict.title, style = AppTheme.textStyles.settingTitle)
                    Text(
                        "Current: ${conflict.currentDescription}",
                        style = AppTheme.textStyles.settingDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Backup: ${conflict.backupDescription}",
                        style = AppTheme.textStyles.settingDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val currentLabel = if (conflict.kind == RestoreConflictKind.DAILY_DATE_CARD) {
                        "Keep Current Card"
                    } else {
                        "Keep Current"
                    }
                    val backupLabel = if (conflict.kind == RestoreConflictKind.DAILY_DATE_CARD) {
                        "Merge Backup Tasks"
                    } else {
                        "Use Backup"
                    }
                    ConflictPolicyRadioRow(
                        label = currentLabel,
                        selected = choices[conflict.id] == RestoreConflictChoice.KEEP_CURRENT,
                        onSelect = { choices = choices + (conflict.id to RestoreConflictChoice.KEEP_CURRENT) },
                    )
                    ConflictPolicyRadioRow(
                        label = backupLabel,
                        selected = choices[conflict.id] == RestoreConflictChoice.USE_BACKUP,
                        onSelect = { choices = choices + (conflict.id to RestoreConflictChoice.USE_BACKUP) },
                    )
                }
            }
            AppButton(onClick = { onContinue(choices) }) { Text("Continue Merge") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun com.datadragon.app.data.BackupCategory.restoreLabel(): String = when (this) {
    com.datadragon.app.data.BackupCategory.FORMS -> "Forms"
    com.datadragon.app.data.BackupCategory.LISTS -> "Lists"
    com.datadragon.app.data.BackupCategory.IDEA_LOGS -> "Idea Logs"
    com.datadragon.app.data.BackupCategory.DAILY_TASKS -> "Daily Tasks"
    com.datadragon.app.data.BackupCategory.CLICKER_DATA -> "Clicker Data"
    com.datadragon.app.data.BackupCategory.SAVED_COLOR_PRESETS -> "Saved Color Presets"
    com.datadragon.app.data.BackupCategory.PORTABLE_PREFERENCES -> "Preferences"
}

/**
 * One "Choose Your Data Modes" row: the mode's icon and label with its
 * description underneath, and a checkbox. Tapping anywhere on the row — icon,
 * label, or description — toggles the checkbox, not just the box itself.
 */
@Composable
private fun DataModeCheckRow(
    label: String,
    subtext: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconRes != null -> Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = AppTheme.textStyles.settingTitle)
            Text(
                subtext,
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "Import Mode:" chooser for Restore. */
@Composable
private fun ImportModeRow(
    selected: RestoreMode,
    onSelected: (RestoreMode) -> Unit,
) {
    AppDropdownRow(
        label = "Import Mode:",
        options = RestoreMode.entries,
        selected = selected,
        onSelected = onSelected,
        // No short label: the box shows the same full wording as the menu, so
        // there is no second name for the same option.
        optionLabel = { it.label() },
    )
}

/** Label of the mark shown on a completed item. */
private fun CompleteIcon.label(): String = when (this) {
    CompleteIcon.CHECKMARK -> "Checkmark"
    CompleteIcon.CHECKED_BOX -> "Checked Box"
}

/**
 * "Item Complete Icon" chooser: the current choice sits on the label's line and
 * opens a drop-down to pick between a checkmark and a checked box.
 */
@Composable
private fun CompleteIconRow(
    selected: CompleteIcon,
    onSelected: (CompleteIcon) -> Unit,
) {
    AppDropdownRow(
        label = "Item Complete Icon:",
        options = CompleteIcon.entries,
        selected = selected,
        onSelected = onSelected,
        optionLabel = { it.label() },
    )
}
