package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerIncrementDirection
import com.datadragon.app.data.StableUuid
import com.datadragon.app.data.editOnlyFromCardMenu
import com.datadragon.app.ui.ClickerSetupViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDropdownRow
import com.datadragon.app.ui.theme.AppTheme

/** The note shown under a display-only field type in a clicker log's setup. */
private const val EDIT_ONLY_NOTE =
    "These can only be changed by directly editing the clicker card using the dropdown ellipses."

private const val STARTING_NUMBER_DIGITS = 5
private const val INCREMENT_DIGITS = 4
private const val MAX_DIGITS_DIGITS = 2

/** A mutable draft of one tracker/field while the log is being set up. */
private class ClickerDraftField(
    val id: String,
    type: ClickerFieldType,
    label: String = "",
    buttonLabel: String = "",
    startingNumber: String = "",
    incrementDirection: ClickerIncrementDirection = ClickerIncrementDirection.ADD,
    incrementAmount: String = "",
    maxDigits: String = "",
    autoIncrement: Boolean = false,
) {
    var type by mutableStateOf(type)
    var label by mutableStateOf(label)
    var buttonLabel by mutableStateOf(buttonLabel)
    var startingNumber by mutableStateOf(startingNumber)
    var incrementDirection by mutableStateOf(incrementDirection)
    var incrementAmount by mutableStateOf(incrementAmount)
    var maxDigits by mutableStateOf(maxDigits)
    var autoIncrement by mutableStateOf(autoIncrement)

    fun toField(): ClickerField = ClickerField(
        id = id,
        type = type,
        label = label.trim(),
        buttonLabel = buttonLabel.trim(),
        startingNumber = startingNumber.toIntOrNull() ?: 0,
        incrementDirection = incrementDirection,
        incrementAmount = (incrementAmount.toIntOrNull() ?: 1).coerceAtLeast(1),
        maxDigits = maxDigits.toIntOrNull(),
        autoIncrement = autoIncrement,
    )
}

private fun ClickerField.toDraft(): ClickerDraftField = ClickerDraftField(
    id = id,
    type = type,
    label = label,
    buttonLabel = buttonLabel,
    startingNumber = if (startingNumber != 0) startingNumber.toString() else "",
    incrementDirection = incrementDirection,
    incrementAmount = if (incrementAmount != 1) incrementAmount.toString() else "",
    maxDigits = maxDigits?.toString() ?: "",
    autoIncrement = autoIncrement,
)

/** The user-facing name of each field type, in Title Case. */
private fun ClickerFieldType.displayName(): String = when (this) {
    ClickerFieldType.CLICK_TRACKER -> "Click Tracker"
    ClickerFieldType.WRITE_IN_NUMBER -> "Write-In Number Tracker"
    ClickerFieldType.DATE -> "Date"
    ClickerFieldType.TIME -> "Time"
    ClickerFieldType.DATE_TIME -> "Date & Time"
    ClickerFieldType.TEXT -> "Text"
    ClickerFieldType.MULTITEXT -> "Multitext"
}

/** Keep only digits, capped at [maxLen] characters. */
private fun digitsOnly(input: String, maxLen: Int): String =
    input.filter { it.isDigit() }.take(maxLen)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickerSetupScreen(
    existingLogId: Long?,
    onBack: () -> Unit,
    viewModel: ClickerSetupViewModel = viewModel(),
) {
    var title by rememberSaveable { mutableStateOf("") }
    var displayOnlyClickerDateTime by rememberSaveable { mutableStateOf(false) }
    var autoDateStamp by rememberSaveable { mutableStateOf(true) }
    var autoTimeStamp by rememberSaveable { mutableStateOf(false) }
    var allowFollowUp by rememberSaveable { mutableStateOf(false) }
    val fields = remember { mutableStateListOf<ClickerDraftField>() }
    var loaded by remember { mutableStateOf(existingLogId == null) }

    // Seed the editor from an existing log the first time it opens (edit mode).
    androidx.compose.runtime.LaunchedEffect(existingLogId) {
        if (existingLogId != null && !loaded) {
            val log = viewModel.load(existingLogId)
            if (log != null) {
                title = log.title
                displayOnlyClickerDateTime = log.displayOnlyClickerDateTime
                autoDateStamp = log.autoDateStamp
                autoTimeStamp = log.autoTimeStamp
                allowFollowUp = log.allowFollowUp
                fields.clear()
                fields.addAll(viewModel.decodeFields(log.fieldsJson).map { it.toDraft() })
            }
            loaded = true
        }
    }

    val canSave = title.isNotBlank() && fields.isNotEmpty() && fields.all { it.label.isNotBlank() }
    val dirty = title.isNotBlank() || fields.isNotEmpty()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty && existingLogId == null) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingLogId == null) "New Clicker Data Log" else "Edit Clicker Data Log") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            viewModel.save(
                                existingId = existingLogId,
                                title = title.trim(),
                                displayOnlyClickerDateTime = displayOnlyClickerDateTime,
                                autoDateStamp = autoDateStamp,
                                autoTimeStamp = autoTimeStamp,
                                allowFollowUp = allowFollowUp,
                                fields = fields.map { it.toField() },
                                onSaved = onBack,
                            )
                        },
                    ) { Text("Save") }
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
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Clicker Data Log Title") },
                modifier = Modifier.fillMaxWidth(),
            )

            ToggleRow(
                title = "Display only clicker and date/time related fields?",
                subtitle = "When turned off, text Fields will still be visible when editing the card, but hidden on the main screen.",
                checked = displayOnlyClickerDateTime,
                onCheckedChange = { displayOnlyClickerDateTime = it },
            )
            ToggleRow(
                title = "Automatic Date Stamp",
                subtitle = "Labels each data card with the date the card was created.",
                checked = autoDateStamp,
                onCheckedChange = { autoDateStamp = it },
            )
            ToggleRow(
                title = "Automatic Time Stamp",
                subtitle = "Automatically labels each card with the time that the card was created.",
                checked = autoTimeStamp,
                onCheckedChange = { autoTimeStamp = it },
            )
            ToggleRow(
                title = "Follow-Up Notes",
                subtitle = "Notes that can be added to each data card through the edit menu.",
                checked = allowFollowUp,
                onCheckedChange = { allowFollowUp = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Fields", style = AppTheme.textStyles.sectionHeader)
            if (fields.isEmpty()) {
                Text(
                    "No fields yet. Tap “Add Field” to add a tracker or field.",
                    style = AppTheme.textStyles.settingDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fields.forEach { field ->
                ClickerFieldEditor(
                    field = field,
                    onRemove = { fields.remove(field) },
                )
            }

            AddFieldButton(onAdd = { type ->
                fields.add(ClickerDraftField(id = StableUuid.createNew(), type = type))
            })
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

/** A title + subtitle row with a Switch on the right; the whole row toggles. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AppTheme.textStyles.settingTitle)
            Text(
                subtitle,
                style = AppTheme.textStyles.settingDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The "Add Field" button, opening a menu of the field types to add. */
@Composable
private fun AddFieldButton(onAdd: (ClickerFieldType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        AppButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Add Field")
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ClickerFieldType.entries.forEach { type ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(type.displayName()) },
                    onClick = { open = false; onAdd(type) },
                )
            }
        }
    }
}

/** The editor for one draft field; its inputs depend on the field's type. */
@Composable
private fun ClickerFieldEditor(
    field: ClickerDraftField,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                field.type.displayName(),
                style = AppTheme.textStyles.subsectionHeader,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove Field")
            }
        }

        OutlinedTextField(
            value = field.label,
            onValueChange = { field.label = it },
            singleLine = true,
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth(),
        )

        when (field.type) {
            ClickerFieldType.CLICK_TRACKER -> {
                OutlinedTextField(
                    value = field.buttonLabel,
                    onValueChange = { field.buttonLabel = it },
                    singleLine = true,
                    label = { Text("Button Label") },
                    placeholder = { Text("Add") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = field.startingNumber,
                    onValueChange = { field.startingNumber = digitsOnly(it, STARTING_NUMBER_DIGITS) },
                    singleLine = true,
                    label = { Text("Starting Number") },
                    placeholder = { Text("0") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                DirectionRow(
                    selected = field.incrementDirection,
                    onSelected = { field.incrementDirection = it },
                )
                OutlinedTextField(
                    value = field.incrementAmount,
                    onValueChange = { field.incrementAmount = digitsOnly(it, INCREMENT_DIGITS) },
                    singleLine = true,
                    label = { Text("In Increments Of") },
                    placeholder = { Text("1") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ClickerFieldType.WRITE_IN_NUMBER -> {
                OutlinedTextField(
                    value = field.maxDigits,
                    onValueChange = { field.maxDigits = digitsOnly(it, MAX_DIGITS_DIGITS) },
                    singleLine = true,
                    label = { Text("Maximum Amount of Numbers") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow(
                    title = "Have Button to Automatically Increment Write-In Value?",
                    subtitle = "Adds a card button that steps the value up or down by a set amount.",
                    checked = field.autoIncrement,
                    onCheckedChange = { field.autoIncrement = it },
                )
                if (field.autoIncrement) {
                    DirectionRow(
                        selected = field.incrementDirection,
                        onSelected = { field.incrementDirection = it },
                    )
                    OutlinedTextField(
                        value = field.incrementAmount,
                        onValueChange = { field.incrementAmount = digitsOnly(it, INCREMENT_DIGITS) },
                        singleLine = true,
                        label = { Text("In Increments Of") },
                        placeholder = { Text("1") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = field.buttonLabel,
                        onValueChange = { field.buttonLabel = it },
                        singleLine = true,
                        label = { Text("Button Label") },
                        placeholder = { Text("Okay") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> {
                if (field.type.editOnlyFromCardMenu) {
                    Text(
                        EDIT_ONLY_NOTE,
                        style = AppTheme.textStyles.settingDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()
    }
}

/** The "Add / Subtract" chooser for a stepping button. */
@Composable
private fun DirectionRow(
    selected: ClickerIncrementDirection,
    onSelected: (ClickerIncrementDirection) -> Unit,
) {
    AppDropdownRow(
        label = "Direction:",
        options = ClickerIncrementDirection.entries,
        selected = selected,
        onSelected = onSelected,
        optionLabel = { if (it == ClickerIncrementDirection.ADD) "Add" else "Subtract" },
    )
}
