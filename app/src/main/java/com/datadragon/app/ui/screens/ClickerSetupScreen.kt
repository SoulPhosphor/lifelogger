package com.datadragon.app.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
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
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.hasDuplicateLabels
import com.datadragon.app.ui.components.ChoiceEditorWordings
import com.datadragon.app.ui.components.ChoiceOptionsEditor
import com.datadragon.app.ui.components.ChoicePreselection
import com.datadragon.app.ui.components.DuplicateItemDialog
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

internal const val STARTING_NUMBER_DIGITS = 5
internal const val INCREMENT_DIGITS = 4
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
    choices: List<ChoiceOption> = emptyList(),
    itemLabel: String = "",
    firstLabel: String = "",
    secondLabel: String = "",
    totalLabel: String = "",
    wholeNumbersOnly: Boolean = true,
    useClickerCounting: Boolean = false,
) : ComparisonSettingsDraft {
    var type by mutableStateOf(type)
    var label by mutableStateOf(label)
    var buttonLabel by mutableStateOf(buttonLabel)
    var startingNumber by mutableStateOf(startingNumber)
    var incrementDirection by mutableStateOf(incrementDirection)
    var incrementAmount by mutableStateOf(incrementAmount)
    var maxDigits by mutableStateOf(maxDigits)
    var autoIncrement by mutableStateOf(autoIncrement)
    var choices by mutableStateOf(choices)
    override var itemLabel by mutableStateOf(itemLabel)
    override var firstLabel by mutableStateOf(firstLabel)
    override var secondLabel by mutableStateOf(secondLabel)
    override var totalLabel by mutableStateOf(totalLabel)
    override var wholeNumbersOnly by mutableStateOf(wholeNumbersOnly)
    override var clickerCounting by mutableStateOf(useClickerCounting)

    // A Comparison field's clicker counting reuses this field's Click Tracker settings.
    override var digits: String
        get() = maxDigits
        set(value) { maxDigits = value }
    override var clickerButtonLabel: String
        get() = buttonLabel
        set(value) { buttonLabel = value }
    override var clickerStartingNumber: String
        get() = startingNumber
        set(value) { startingNumber = value }
    override var clickerIncrement: String
        get() = incrementAmount
        set(value) { incrementAmount = value }
    override var clickerDirection: ClickerIncrementDirection
        get() = incrementDirection
        set(value) { incrementDirection = value }

    /** The Comparison items that will be saved: a row left blank is never stored. */
    fun savedChoices(): List<ChoiceOption> =
        choices.filter { it.label.isNotBlank() }.map { it.copy(label = it.label.trim()) }

    /** A Comparison field needs at least one active item; every other type is ready once labeled. */
    fun hasItems(): Boolean = type != ClickerFieldType.COMPARISON || savedChoices().activeChoices.isNotEmpty()

    fun toField(): ClickerField = ClickerField(
        id = id,
        type = type,
        label = label.trim(),
        buttonLabel = buttonLabel.trim(),
        startingNumber = startingNumber.toIntOrNull() ?: 0,
        incrementDirection = incrementDirection,
        incrementAmount = (incrementAmount.toIntOrNull() ?: 1).coerceAtLeast(1),
        maxDigits = if (type == ClickerFieldType.COMPARISON) {
            maxDigits.toIntOrNull()?.takeIf { it > 0 }
        } else {
            maxDigits.toIntOrNull()
        },
        autoIncrement = autoIncrement,
        choices = if (type == ClickerFieldType.COMPARISON) savedChoices() else emptyList(),
        itemLabel = if (type == ClickerFieldType.COMPARISON) itemLabel.trim() else "",
        firstLabel = if (type == ClickerFieldType.COMPARISON) firstLabel.trim() else "",
        secondLabel = if (type == ClickerFieldType.COMPARISON) secondLabel.trim() else "",
        totalLabel = if (type == ClickerFieldType.COMPARISON) totalLabel.trim() else "",
        wholeNumbersOnly = type != ClickerFieldType.COMPARISON || wholeNumbersOnly,
        useClickerCounting = type == ClickerFieldType.COMPARISON && clickerCounting,
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
    choices = choices,
    itemLabel = itemLabel,
    firstLabel = firstLabel,
    secondLabel = secondLabel,
    totalLabel = totalLabel,
    wholeNumbersOnly = wholeNumbersOnly,
    useClickerCounting = useClickerCounting,
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
    ClickerFieldType.COMPARISON -> "Comparison"
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
    var allowAdditionsFromForm by rememberSaveable { mutableStateOf(false) }
    val fields = remember { mutableStateListOf<ClickerDraftField>() }
    var loaded by remember { mutableStateOf(existingLogId == null) }
    // Per Comparison field, the most digits a saved number already has.
    var savedComparisonDigits by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showDuplicate by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val capitalizeNewOptions = remember { SettingsRepository(context).autoCapitalizeOptions }

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
                allowAdditionsFromForm = log.allowAdditionsFromForm
                savedComparisonDigits = viewModel.savedComparisonDigits(existingLogId)
                fields.clear()
                fields.addAll(viewModel.decodeFields(log.fieldsJson).map { it.toDraft() })
            }
            loaded = true
        }
    }

    val canSave = title.isNotBlank() && fields.isNotEmpty() && fields.all { it.label.isNotBlank() && it.hasItems() }
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
                            // Refuse an item list that repeats an item.
                            if (fields.any { it.savedChoices().hasDuplicateLabels() }) {
                                showDuplicate = true
                                return@TextButton
                            }
                            // Max Digits never drops below what saved numbers already use.
                            fields.filter { it.type == ClickerFieldType.COMPARISON }.forEach { field ->
                                field.maxDigits = digitsAtLeast(field.maxDigits, savedComparisonDigits[field.id])
                            }
                            viewModel.save(
                                existingId = existingLogId,
                                title = title.trim(),
                                displayOnlyClickerDateTime = displayOnlyClickerDateTime,
                                autoDateStamp = autoDateStamp,
                                autoTimeStamp = autoTimeStamp,
                                allowFollowUp = allowFollowUp,
                                allowAdditionsFromForm = allowAdditionsFromForm,
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
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            Labeled("Clicker Data Log Title") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
            ToggleRow(
                title = "Allow Additions from Form",
                checked = allowAdditionsFromForm,
                onCheckedChange = { allowAdditionsFromForm = it },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.spacing.related))

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
                    capitalizeNewOptions = capitalizeNewOptions,
                    minimumDigits = savedComparisonDigits[field.id],
                    onDeleteOption = { option ->
                        // Committed at once for a saved log, like a form's option.
                        existingLogId?.let { viewModel.deleteComparisonOptionPermanently(it, field.id, option.id) }
                    },
                )
            }

            AddFieldButton(onAdd = { type ->
                fields.add(ClickerDraftField(id = StableUuid.createNew(), type = type))
            })
        }
    }

    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
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
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = AppTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AppTheme.textStyles.settingTitle)
            subtitle?.let {
                Text(
                    it,
                    style = AppTheme.textStyles.settingDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(AppTheme.spacing.rowInset))
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
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
    capitalizeNewOptions: Boolean,
    minimumDigits: Int?,
    onDeleteOption: (ChoiceOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.related),
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

        Labeled("Label") {
            OutlinedTextField(
                value = field.label,
                onValueChange = { field.label = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (field.type) {
            ClickerFieldType.CLICK_TRACKER -> {
                Labeled("Button Label") {
                    OutlinedTextField(
                        value = field.buttonLabel,
                        onValueChange = { field.buttonLabel = it },
                        singleLine = true,
                        placeholder = { Text("Add") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Labeled("Starting Number") {
                    OutlinedTextField(
                        value = field.startingNumber,
                        onValueChange = { field.startingNumber = digitsOnly(it, STARTING_NUMBER_DIGITS) },
                        singleLine = true,
                        placeholder = { Text("0") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DirectionRow(
                    selected = field.incrementDirection,
                    onSelected = { field.incrementDirection = it },
                )
                Labeled("In Increments Of") {
                    OutlinedTextField(
                        value = field.incrementAmount,
                        onValueChange = { field.incrementAmount = digitsOnly(it, INCREMENT_DIGITS) },
                        singleLine = true,
                        placeholder = { Text("1") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ClickerFieldType.WRITE_IN_NUMBER -> {
                Labeled("Maximum Amount of Numbers") {
                    OutlinedTextField(
                        value = field.maxDigits,
                        onValueChange = { field.maxDigits = digitsOnly(it, MAX_DIGITS_DIGITS) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                    Labeled("In Increments Of") {
                        OutlinedTextField(
                            value = field.incrementAmount,
                            onValueChange = { field.incrementAmount = digitsOnly(it, INCREMENT_DIGITS) },
                            singleLine = true,
                            placeholder = { Text("1") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Labeled("Button Label") {
                        OutlinedTextField(
                            value = field.buttonLabel,
                            onValueChange = { field.buttonLabel = it },
                            singleLine = true,
                            placeholder = { Text("Okay") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            // The same item editor and settings as a form's Comparison field.
            ClickerFieldType.COMPARISON -> {
                ChoiceOptionsEditor(
                    choices = field.choices,
                    onChoicesChange = { field.choices = it },
                    wording = ChoiceEditorWordings.formOptions,
                    preselection = ChoicePreselection.NONE,
                    capitalizeNew = capitalizeNewOptions,
                    onDeletePermanently = onDeleteOption,
                )
                ComparisonFieldSettings(field, minimumDigits)
                if (!field.hasItems()) {
                    Text(
                        "Add at least one option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
internal fun DirectionRow(
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
