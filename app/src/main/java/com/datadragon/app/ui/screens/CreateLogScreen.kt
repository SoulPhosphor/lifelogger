package com.datadragon.app.ui.screens

import com.datadragon.app.ui.components.accessibleReorder
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.components.DuplicateItemDialog
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.OptionDuplicates
import com.datadragon.app.data.StableUuid
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.hasItemRows
import com.datadragon.app.data.hasOptionList
import com.datadragon.app.data.ComparisonValues
import com.datadragon.app.data.ClickerIncrementDirection
import androidx.compose.ui.focus.onFocusChanged
import com.datadragon.app.data.hasDuplicateLabels
import com.datadragon.app.data.matchingLabel
import com.datadragon.app.data.ChoiceStyle
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.AutoFill
import com.datadragon.app.data.AutoFill.autoFillable
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.sortEligible
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.data.RatioValues
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.ui.CreateLogViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableColumn
import com.datadragon.app.ui.theme.AppTheme

/** Which editor is showing. Build (visual taps) is the default. */
private enum class BuilderMode { BUILD, PASTE }

private const val FIELD_TYPES_REFERENCE = """text           — a single line of text
multiline      — multi-line text box. Set "lines" for visible height
date           — month/day/year picker
time           — 12-hour time with AM/PM
dropdown       — pick one item from a list
scale          — Pick a Number in a Range. Set "from" and "to" (Default 1 to 10). Add "make_dropdown: true" for a Dropdown
yesno          — Yes / No radios. Add "allow_unknown: true" for an Unknown radio
number         — type a number. Set "digits" for max digits allowed
multiple       — pick several items from a list (tappable chips)
tags           — type a tag and add it; each becomes a removable chip
webpage        — a web address, with a button that opens it
blood_pressure — Two 3-Digit Boxes Separated by "/" (Systolic / Diastolic)
ratio          — two numbers separated by "/". Set "left_digits" and "right_digits" (1 to 10). Add "unit" for a unit of measurement
comparison     — rows of an item from a list with two numbers and their difference. Needs "options:". Set "digits" for max digits. Add "whole_numbers: false" to allow decimals
list_item_quantities — rows of an item from a list with its amount. Needs "options:". Set "digits" for max digits. Add "add_quantities: true" for a total line. Add "whole_numbers: false" to allow decimals

Any field can add "required" to prevent saving without it."""

/**
 * Saves the in-progress new-log draft as JSON in the instance-state Bundle, so
 * a form built (or pasted) here survives the process being killed while
 * backgrounded — otherwise it would silently reset to empty.
 */
private val builderModeSaver = Saver<BuilderMode, String>(
    save = { it.name },
    restore = { BuilderMode.valueOf(it) },
)

private val draftFieldsSaver = Saver<SnapshotStateList<DraftField>, String>(
    save = { list -> Json.encodeToString(list.map { it.toSnapshot() }) },
    restore = { text ->
        mutableStateListOf<DraftField>().apply {
            addAll(Json.decodeFromString<List<DraftFieldSnapshot>>(text).map { it.toDraftField() })
        }
    },
)

private const val FORM_MARKDOWN_HELP =
    """Write one field per "##" heading. The first single "#" line is the log name (optional).

# Sleep tracker

## Mood
type: scale
from: 1
to: 5
required

## Activities
type: multiple
options:
- Exercise
- Reading
- Outside

## Notes about today
type: multiline
lines: 4

Rules:
• "## Label" starts a new field.
• "type: <type>" sets the field type (see the list below).
• scale needs "from:" and "to:"; number can set "digits:"; multiline can set "lines:".
• dropdown and multiple need an "options:" line followed by "- item" lines.
• Put "required" on its own line to make a field required."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLogScreen(
    onBack: () -> Unit,
    viewModel: CreateLogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable(stateSaver = builderModeSaver) { mutableStateOf(BuilderMode.BUILD) }

    // Per-log behavior. Locked Log starts off and can be turned on and off later
    // from Edit Form; Never Allow Form Unlocking is chosen only here and makes
    // the form permanently locked.
    var locked by rememberSaveable { mutableStateOf(false) }
    var neverAllowUnlocking by rememberSaveable { mutableStateOf(false) }
    var allowAppendedNotes by rememberSaveable { mutableStateOf(false) }
    var advancedSearchEnabled by rememberSaveable { mutableStateOf(false) }
    var automaticTimestamping by rememberSaveable { mutableStateOf(false) }
    var sortNewestFirst by rememberSaveable { mutableStateOf(true) }

    // Build tab: the editable field list is the source of truth.
    val draftFields = rememberSaveable(saver = draftFieldsSaver) { mutableStateListOf() }

    // Paste tab: raw Form Markdown plus an optional preview (a helper, never
    // required to save — regenerated by tapping Preview again, so it's not worth
    // persisting; pasteText itself, the actual authored content, is).
    var pasteText by rememberSaveable { mutableStateOf("") }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FormMarkdownParser.ParseResult?>(null) }

    val canSave = when (mode) {
        BuilderMode.BUILD ->
            name.isNotBlank() && draftFields.isNotEmpty() && draftFields.all { it.isValid() }
        BuilderMode.PASTE -> {
            val hasName = name.isNotBlank() || firstMarkdownName(pasteText) != null
            hasName && FormMarkdownParser.parse(pasteText).fields.isNotEmpty()
        }
    }

    // Backing out of a brand-new form warns only once at least one field exists.
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    val dirty = draftFields.isNotEmpty() || pasteText.isNotBlank()
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Log") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val fields: List<FieldDef>
                            val markdown: String
                            // Build mode knows which field the user picked, so the
                            // default sort keeps that field's id even when another
                            // field shares its label.
                            var builtSortFieldId: String? = null
                            if (mode == BuilderMode.BUILD) {
                                // Every field here is newly created, so apply the
                                // auto-capitalize settings before saving.
                                val built = draftFields.filter { it.isValid() }
                                    .map { draft ->
                                        draft to draft.toFieldDef().autoCapitalized(
                                            labels = settings.autoCapitalizeLabels,
                                            options = settings.autoCapitalizeOptions,
                                        )
                                    }
                                fields = built.map { it.second }.withValidAutoFill()
                                builtSortFieldId = built
                                    .singleOrNull { (draft, _) -> draft.sortByTimestamp }
                                    ?.second
                                    ?.takeIf { it.type.sortEligible }
                                    ?.id
                                markdown = FormMarkdownGenerator.generate(name, fields)
                            } else {
                                // Fix the casing of pasted markdown in place (labels
                                // and options), leaving all other text untouched, then
                                // parse it so the saved schema matches.
                                markdown = capitalizeMarkdown(
                                    text = pasteText,
                                    labels = settings.autoCapitalizeLabels,
                                    options = settings.autoCapitalizeOptions,
                                )
                                fields = FormMarkdownParser.parse(markdown).fields
                            }
                            // A new form's options may not repeat an item (ignoring
                            // capitalization), whichever way they were entered.
                            if (fields.any { it.choices.hasDuplicateLabels() }) {
                                showDuplicate = true
                                return@TextButton
                            }
                            val finalName = name.ifBlank { firstMarkdownName(pasteText) ?: "" }
                            val selectedSortLabel = draftFields
                                .singleOrNull { it.sortByTimestamp }
                                ?.label
                                ?.trim()
                            val sortTimestampFieldId = if (mode == BuilderMode.BUILD) {
                                builtSortFieldId
                            } else {
                                selectedSortLabel?.let { selected ->
                                    fields.firstOrNull {
                                        it.type.sortEligible &&
                                            it.label.equals(selected, ignoreCase = true)
                                    }?.id
                                }
                            }
                            viewModel.save(
                                name = finalName,
                                schemaJson = FormMarkdownParser.encodeFields(fields),
                                formMarkdown = markdown,
                                locked = locked,
                                neverAllowUnlocking = neverAllowUnlocking,
                                allowAppendedNotes = allowAppendedNotes,
                                automaticTimestamping = automaticTimestamping,
                                advancedSearchEnabled = advancedSearchEnabled,
                                sortTimestampFieldId = sortTimestampFieldId,
                                sortNewestFirst = sortNewestFirst,
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
                // Shrink the scroll viewport to the space above the on-screen
                // keyboard so a focused field is never hidden behind it — the
                // text field's own bring-into-view then scrolls it into sight.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
        ) {
            Text("Log Name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SettingSwitchRow(
                checked = advancedSearchEnabled,
                onCheckedChange = { advancedSearchEnabled = it },
                title = "Enable Advanced Search",
            )
            SettingSwitchRow(
                checked = automaticTimestamping,
                onCheckedChange = { automaticTimestamping = it },
                title = "Show Timestamp",
            )
            SettingSwitchRow(
                checked = locked,
                onCheckedChange = { locked = it },
                title = "Locked Log",
                subtitle = "Prevent saved entries from being edited while this is enabled.",
                // Never Allow Form Unlocking holds the lock on.
                enabled = !neverAllowUnlocking,
            )
            SettingSwitchRow(
                checked = neverAllowUnlocking,
                onCheckedChange = {
                    neverAllowUnlocking = it
                    if (it) locked = true
                },
                title = "Never Allow Form Unlocking",
                subtitle = "This option is only available on form creation. If toggled off " +
                    "you will always be able to turn locking on and off.",
            )
            SettingSwitchRow(
                checked = allowAppendedNotes,
                onCheckedChange = { allowAppendedNotes = it },
                title = "Follow-Up Notes",
                subtitle = "Let entries get time-stamped notes added later, without " +
                    "changing the original. (Can also be toggled later.)",
            )

            // Style toggle. Switching converts between the two representations so
            // the field list and the Markdown stay in step.
            TabRow(selectedTabIndex = if (mode == BuilderMode.BUILD) 0 else 1) {
                Tab(
                    selected = mode == BuilderMode.BUILD,
                    onClick = {
                        if (mode == BuilderMode.PASTE) {
                            val result = FormMarkdownParser.parse(pasteText)
                            if (name.isBlank()) result.name?.let { name = it }
                            draftFields.clear()
                            result.fields.forEach { draftFields.add(it.toDraft()) }
                            mode = BuilderMode.BUILD
                        }
                    },
                    text = { Text("Build") },
                )
                Tab(
                    selected = mode == BuilderMode.PASTE,
                    onClick = {
                        if (mode == BuilderMode.BUILD) {
                            val fields = draftFields
                                .filter { it.label.trim().isNotEmpty() }
                                .map { it.toFieldDef() }
                                .withValidAutoFill()
                            pasteText = FormMarkdownGenerator.generate(name, fields)
                            preview = null
                            mode = BuilderMode.PASTE
                        }
                    },
                    text = { Text("Paste") },
                )
            }

            when (mode) {
                BuilderMode.BUILD -> BuildEditor(
                    fields = draftFields,
                    sortNewestFirst = sortNewestFirst,
                    onSortDirectionChange = { sortNewestFirst = it },
                    onAdd = { draftFields.add(DraftField()) },
                    onDelete = { draftFields.remove(it) },
                    onReorder = { from, to -> draftFields.add(to, draftFields.removeAt(from)) },
                )
                BuilderMode.PASTE -> PasteEditor(
                    text = pasteText,
                    onTextChange = {
                        pasteText = it
                        preview = null
                    },
                    showHelp = showHelp,
                    onToggleHelp = { showHelp = !showHelp },
                    preview = preview,
                    onPreview = { preview = FormMarkdownParser.parse(pasteText) },
                )
            }
        }
    }
    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
    }
}

/** A settings row with a label + description on the left and a Switch on the
 *  right. The whole row is tappable to toggle, which is easier to hit. */
@Composable
internal fun SettingSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = AppTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(AppTheme.spacing.rowInset))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
    }
}

/**
 * The form's default entry order, shown under "Use as Default Sort Timestamp"
 * once a field is chosen as the sort timestamp — a default is only meaningful
 * once we also know which way it runs.
 */
@Composable
internal fun SortDirectionRadios(
    newestFirst: Boolean,
    onNewestFirstChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = AppTheme.spacing.rowInset)) {
        Text("Default Sorting:", style = MaterialTheme.typography.bodyLarge)
        // Both choices share the width so a narrow screen wraps the labels
        // rather than clipping them.
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            Row(
                modifier = Modifier.weight(1f).selectable(selected = newestFirst, role = Role.RadioButton, onClick = { onNewestFirstChange(true) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = newestFirst, onClick = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
                Text("Newest to Oldest")
            }
            Row(
                modifier = Modifier.weight(1f).selectable(selected = !newestFirst, role = Role.RadioButton, onClick = { onNewestFirstChange(false) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = !newestFirst, onClick = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
                Text("Oldest to Newest")
            }
        }
    }
}

/** A Single Choice field's "Choice Style:" pick, shown below Required. */
@Composable
internal fun ChoiceStyleRadios(
    style: ChoiceStyle,
    onStyleChange: (ChoiceStyle) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Choice Style:", style = MaterialTheme.typography.bodyLarge)
        // Both choices share the width so a narrow screen wraps the labels
        // rather than clipping them.
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            listOf(ChoiceStyle.DROPDOWN to "Dropdown", ChoiceStyle.DISPLAYED to "Displayed").forEach { (option, text) ->
                Row(
                    modifier = Modifier.weight(1f).selectable(selected = style == option, role = Role.RadioButton, onClick = { onStyleChange(option) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = style == option, onClick = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
                    Text(text)
                }
            }
        }
    }
}

/**
 * A Ratios field's settings: the digit limit for each side of the "/", then an
 * optional unit of measurement. Each digit box takes 1–10.
 */
@Composable
internal fun RatioFieldSettings(
    leftDigits: String,
    onLeftDigitsChange: (String) -> Unit,
    rightDigits: String,
    onRightDigitsChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
) {
    RatioDigitsRow(title = "Number of Digits on Left", value = leftDigits, onChange = onLeftDigitsChange)
    RatioDigitsRow(title = "Number of Digits on Right", value = rightDigits, onChange = onRightDigitsChange)
    Text("Unit of Measurement", style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = unit,
        onValueChange = onUnitChange,
        placeholder = { Text("Optional") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The editable Comparison settings held by both form builders' field drafts. */
internal interface ComparisonSettingsDraft {
    var itemLabel: String
    var firstLabel: String
    var secondLabel: String
    var totalLabel: String
    var digits: String
    var wholeNumbersOnly: Boolean
    var clickerCounting: Boolean
    var clickerButtonLabel: String
    var clickerStartingNumber: String
    var clickerIncrement: String
    var clickerDirection: ClickerIncrementDirection
}

/**
 * A Comparison field's settings: the captions (each box shows its default,
 * grayed, until something is typed over it), with "Use Clicker Style Counting"
 * above the second column's caption — when it is on, the Clicker card's Click
 * Tracker settings sit under that caption — then the optional digit limit and
 * whether typed numbers are whole numbers only.
 *
 * [minimumDigits] is the most digits a saved number already has: Max Digits
 * can't go below it, says so while it is typed lower, and goes back to it when
 * the box is left.
 */
@Composable
internal fun ComparisonFieldSettings(field: ComparisonSettingsDraft, minimumDigits: Int? = null) {
    CaptionSetting("Item Column Label", field.itemLabel, { field.itemLabel = it }, ComparisonValues.DEFAULT_ITEM_LABEL)
    CaptionSetting("First Column Label", field.firstLabel, { field.firstLabel = it }, ComparisonValues.DEFAULT_FIRST_LABEL)
    SettingSwitchRow(
        checked = field.clickerCounting,
        onCheckedChange = { field.clickerCounting = it },
        title = "Use Clicker Style Counting",
    )
    CaptionSetting("Second Column Label", field.secondLabel, { field.secondLabel = it }, ComparisonValues.DEFAULT_SECOND_LABEL)
    if (field.clickerCounting) {
        CaptionSetting("Button Label", field.clickerButtonLabel, { field.clickerButtonLabel = it }, ComparisonValues.DEFAULT_BUTTON_LABEL)
        CaptionSetting(
            title = "Starting Number",
            value = field.clickerStartingNumber,
            onChange = { input -> field.clickerStartingNumber = input.filter { it.isDigit() }.take(STARTING_NUMBER_DIGITS) },
            default = "0",
            number = true,
        )
        DirectionRow(selected = field.clickerDirection, onSelected = { field.clickerDirection = it })
        CaptionSetting(
            title = "In Increments Of",
            value = field.clickerIncrement,
            onChange = { input -> field.clickerIncrement = input.filter { it.isDigit() }.take(INCREMENT_DIGITS) },
            default = "1",
            number = true,
        )
    }
    CaptionSetting("Total Label", field.totalLabel, { field.totalLabel = it }, ComparisonValues.DEFAULT_TOTAL_LABEL)
    NumberColumnSettings(field, minimumDigits)
}

/** The List Item Quantities settings, which add "Add Item Quantities" to the Comparison ones. */
internal interface ListItemQuantitiesSettingsDraft : ComparisonSettingsDraft {
    var addQuantities: Boolean
}

/**
 * A List Item Quantities field's settings: the Comparison settings without the
 * second column and clicker counting. The Total Label shows only while "Add
 * Item Quantities" is on, since the total line shows only then.
 */
@Composable
internal fun ListItemQuantitiesFieldSettings(field: ListItemQuantitiesSettingsDraft, minimumDigits: Int? = null) {
    CaptionSetting("Item Column Label", field.itemLabel, { field.itemLabel = it }, ComparisonValues.DEFAULT_ITEM_LABEL)
    CaptionSetting("First Column Label", field.firstLabel, { field.firstLabel = it }, ComparisonValues.DEFAULT_AMOUNT_LABEL)
    SettingSwitchRow(
        checked = field.addQuantities,
        onCheckedChange = { field.addQuantities = it },
        title = "Add Item Quantities",
    )
    if (field.addQuantities) {
        CaptionSetting("Total Label", field.totalLabel, { field.totalLabel = it }, ComparisonValues.DEFAULT_TOTAL_QUANTITY_LABEL)
    }
    NumberColumnSettings(field, minimumDigits)
}

/** Max Digits and "Only Allow Whole Numbers", shared by Comparison and List Item Quantities. */
@Composable
private fun NumberColumnSettings(field: ComparisonSettingsDraft, minimumDigits: Int?) {
    var maxDigitsFocused by remember { mutableStateOf(false) }
    Labeled("Max Digits (Optional)") {
        OutlinedTextField(
            value = field.digits,
            onValueChange = { input ->
                // Nothing above the largest Max Digits is accepted; the box keeps what it had.
                val digits = input.filter { it.isDigit() }.take(2)
                if ((digits.toIntOrNull() ?: 0) <= ComparisonValues.MAX_DIGITS) field.digits = digits
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                if (maxDigitsFocused && !state.isFocused) field.digits = digitsAtLeast(field.digits, minimumDigits)
                maxDigitsFocused = state.isFocused
            },
        )
    }
    if (minimumDigits != null && digitsAtLeast(field.digits, minimumDigits) != field.digits) {
        Text(
            "Data already includes a value of $minimumDigits number places. You can not go below that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    SettingSwitchRow(
        checked = field.wholeNumbersOnly,
        onCheckedChange = { field.wholeNumbersOnly = it },
        title = "Only Allow Whole Numbers",
    )
}

/**
 * A typed Max Digits value raised to [minimum] when it is below it. Blank (no
 * limit) and values at or above [minimum] are kept as typed.
 */
internal fun digitsAtLeast(digits: String, minimum: Int?): String {
    val typed = digits.toIntOrNull() ?: return digits
    return if (minimum != null && typed < minimum) minimum.toString() else digits
}

/** One caption box: its label above, its default shown grayed inside while blank. */
@Composable
private fun CaptionSetting(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    default: String,
    number: Boolean = false,
) {
    Labeled(title) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(default) },
            singleLine = true,
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One "Number of Digits" setting: the title on the left, a short digits box on the right. */
@Composable
private fun RatioDigitsRow(title: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(AppTheme.spacing.rowInset))
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(RatioValues.digitsSetting(input, value)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(AppTheme.sizes.numberWidth),
        )
    }
}

/** A checkbox whose entire labeled row is one toggle target. */
@Composable
internal fun CheckboxSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtext: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = AppTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        if (subtext == null) {
            Text(title)
        } else {
            Column {
                Text(title)
                Text(
                    subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Build (visual) editor -------------------------------------------------

@Composable
private fun BuildEditor(
    fields: List<DraftField>,
    sortNewestFirst: Boolean,
    onSortDirectionChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onDelete: (DraftField) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    var pendingReplacement by remember { mutableStateOf<Pair<DraftField, DraftField>?>(null) }

    fun setSort(field: DraftField, checked: Boolean) {
        if (!checked) {
            field.sortByTimestamp = false
            return
        }
        field.sortByTimestamp = true
        fields.firstOrNull { it !== field && it.sortByTimestamp }?.let { existing ->
            pendingReplacement = field to existing
        }
    }

    Text("Fields", style = MaterialTheme.typography.labelLarge)
    if (fields.isEmpty()) {
        Text(
            "No fields yet. Tap “Add Field” to build your form. Add at least one field to save this form.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    // Fields drag by their handle into the order they'll be saved in — the same
    // reorderable list the rest of the app uses, so nobody taps Up and Down.
    // ReorderableColumn sizes its internal per-item state to the list once, keyed
    // only on the list reference, so adding or deleting a field in place would index
    // past that stale state and crash. Keying on the field count rebuilds it on a
    // grow or shrink; drag-reordering keeps the count the same and is left untouched.
    key(fields.size) {
        ReorderableColumn(
            list = fields,
            onSettle = onReorder,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
        ) { index, field, _ ->
            ReorderableItem {
                FieldEditorCard(
                    field = field,
                    otherFields = fields.filter { it !== field }.map { it.toFieldDef() },
                    index = index,
                    sortNewestFirst = sortNewestFirst,
                    onSortChanged = { setSort(field, it) },
                    onSortDirectionChange = onSortDirectionChange,
                    onDelete = { onDelete(field) },
                    dragHandleModifier = Modifier.draggableHandle().accessibleReorder(index, fields.size) { offset ->
                        val current = fields.indexOf(field)
                        val destination = current + offset
                        if (current >= 0 && destination in fields.indices) {
                            onReorder(current, destination)
                            true
                        } else false
                    },
                )
            }
        }
    }
    AppButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("  Add Field")
    }

    pendingReplacement?.let { (newField, existingField) ->
        AppDialog(
            onDismissRequest = {
                newField.sortByTimestamp = false
                pendingReplacement = null
            },
            body = "Default timestamp sorting is currently set to be ${existingField.label}. " +
                "Do you want to change it?",
            dismissButton = {
                DialogDismissButton("Cancel") {
                    newField.sortByTimestamp = false
                    pendingReplacement = null
                }
            },
            confirmButton = {
                DialogActionButton("Okay") {
                    existingField.sortByTimestamp = false
                    pendingReplacement = null
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorCard(
    field: DraftField,
    /** The form's other fields, for the Auto Fill drop-down. */
    otherFields: List<FieldDef>,
    index: Int,
    sortNewestFirst: Boolean,
    onSortChanged: (Boolean) -> Unit,
    onSortDirectionChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.rowInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Field ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete field")
                }
                // Press-and-drag handle at the far right to reorder the field.
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(start = AppTheme.spacing.compact),
                )
            }

            OutlinedTextField(
                value = field.label,
                onValueChange = { field.label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TypeDropdown(selected = field.type, onSelected = {
                field.type = it
                // Time-only and non-date fields can never take part in ordering.
                if (!it.sortEligible) {
                    field.allowOrderFiltering = false
                    field.sortByTimestamp = false
                }
            })

            when (field.type) {
                FieldType.MULTILINE -> NumberField(
                    value = field.lines,
                    onChange = { field.lines = it },
                    label = "Lines (Height, Optional)",
                )
                FieldType.NUMBER -> NumberField(
                    value = field.digits,
                    onChange = { field.digits = it },
                    label = "Max Digits (Optional)",
                )
                FieldType.SCALE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScaleBoundField(
                            value = field.from,
                            onChange = { field.from = it },
                            placeholder = "1",
                            modifier = Modifier.weight(1f),
                        )
                        Text("to")
                        ScaleBoundField(
                            value = field.to,
                            onChange = { field.to = it },
                            placeholder = "10",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CheckboxSettingRow(
                        checked = field.makeDropdown,
                        onCheckedChange = { field.makeDropdown = it },
                        title = "Make Dropdown Instead",
                    )
                }
                FieldType.DROPDOWN, FieldType.MULTIPLE -> {
                    if (field.type == FieldType.MULTIPLE) {
                        Text(
                            "Put an * before a list item to make it pre-selected when opening the form. " +
                                "It will still deselect if you tap it a second time.",
                            style = AppTheme.textStyles.settingDescription,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = field.optionsText,
                        onValueChange = { field.optionsText = it },
                        label = { Text("Options (One per Line)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.editorMinHeight),
                    )
                    if (field.type == FieldType.DROPDOWN) {
                        Text("Pre-Selected Option", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = field.preselectedOption,
                            onValueChange = { field.preselectedOption = it },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            isError = !field.preselectedOptionValid(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                FieldType.RATIO -> RatioFieldSettings(
                    leftDigits = field.leftDigits,
                    onLeftDigitsChange = { field.leftDigits = it },
                    rightDigits = field.rightDigits,
                    onRightDigitsChange = { field.rightDigits = it },
                    unit = field.unit,
                    onUnitChange = { field.unit = it },
                )
                FieldType.COMPARISON -> {
                    OutlinedTextField(
                        value = field.optionsText,
                        onValueChange = { field.optionsText = it },
                        label = { Text("Options (One per Line)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.editorMinHeight),
                    )
                    ComparisonFieldSettings(field)
                }
                FieldType.LIST_ITEM_QUANTITIES -> {
                    OutlinedTextField(
                        value = field.optionsText,
                        onValueChange = { field.optionsText = it },
                        label = { Text("Options (One per Line)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.editorMinHeight),
                    )
                    ListItemQuantitiesFieldSettings(field)
                }
                FieldType.DATETIME -> CheckboxSettingRow(
                    checked = field.defaultNow,
                    onCheckedChange = { field.defaultNow = it },
                    title = "Default to the Current Date & Time",
                )
                FieldType.YESNO -> CheckboxSettingRow(
                    checked = field.allowUnknown,
                    onCheckedChange = { field.allowUnknown = it },
                    title = "Allow Unknown Option",
                )
                else -> Unit
            }

            if (field.type.autoFillable) {
                AutoFillSettings(
                    checked = field.autoFill,
                    onCheckedChange = { field.autoFill = it },
                    source = field.autoFillSource,
                    onSourceChange = { field.autoFillSource = it },
                    sourceFields = AutoFill.sourceFields(field.toFieldDef(), otherFields),
                )
            }

            if (field.type.sortEligible) {
                CheckboxSettingRow(
                    checked = field.allowOrderFiltering,
                    onCheckedChange = { field.allowOrderFiltering = it },
                    title = "Allow Order Filtering",
                )
                CheckboxSettingRow(
                    checked = field.sortByTimestamp,
                    onCheckedChange = onSortChanged,
                    title = "Use as Default Sort Timestamp",
                )
                if (field.sortByTimestamp) {
                    SortDirectionRadios(
                        newestFirst = sortNewestFirst,
                        onNewestFirstChange = onSortDirectionChange,
                    )
                }
            }
            // A Comparison or List Item Quantities field is never required.
            if (!field.type.hasItemRows) {
                SettingSwitchRow(
                    checked = field.required,
                    onCheckedChange = { field.required = it },
                    title = "Required",
                )
            }
            if (field.type == FieldType.DROPDOWN) {
                ChoiceStyleRadios(
                    style = field.choiceStyle,
                    onStyleChange = { field.choiceStyle = it },
                )
            }

            field.validationHint()?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A 3-digit box for a scale bound. Shows the default as placeholder text. */
@Composable
private fun ScaleBoundField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(3)) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: FieldType, onSelected: (FieldType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.friendly(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FieldType.pickerOrder.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.friendly()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---- Paste (Form Markdown) editor ------------------------------------------

@Composable
private fun PasteEditor(
    text: String,
    onTextChange: (String) -> Unit,
    showHelp: Boolean,
    onToggleHelp: () -> Unit,
    preview: FormMarkdownParser.ParseResult?,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Form Markdown", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onToggleHelp) {
            Text("How to Write It")
            Icon(
                imageVector = if (showHelp) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (showHelp) "Hide help" else "Show help",
            )
        }
    }

    AnimatedVisibility(visible = showHelp) {
        Card {
            Column(modifier = Modifier.padding(AppTheme.spacing.rowInset), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
                Text(FORM_MARKDOWN_HELP, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Field Types", style = MaterialTheme.typography.labelMedium)
                Text(FIELD_TYPES_REFERENCE, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.largeEditorMinHeight),
        placeholder = { Text("Paste or type Form Markdown here…") },
    )

    AppButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
        Text("Preview Form (Optional)")
    }

    preview?.let { PreviewSection(it) }
}

@Composable
private fun PreviewSection(result: FormMarkdownParser.ParseResult) {
    HorizontalDivider()
    Text("Preview", style = MaterialTheme.typography.titleMedium)

    if (result.fields.isEmpty()) {
        Text(
            "No fields defined yet. Add at least one field to save this form.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        result.fields.forEach { field ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AppTheme.spacing.rowInset)) {
                    Text(field.label, style = MaterialTheme.typography.titleSmall)
                    Text(field.summary(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (result.issues.isNotEmpty()) {
        Text(
            "Problems",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        result.issues.forEach { issue ->
            Text("• $issue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (result.skipped.isNotEmpty()) {
        Text("Skipped Lines", style = MaterialTheme.typography.titleSmall)
        result.skipped.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Draft model + helpers -------------------------------------------------

/** Mutable, Compose-observable editing state for one field in the Build tab. */
private class DraftField(
    /**
     * The new field's permanent id, generated once with the draft so another
     * field's Auto Fill can name it; it becomes the saved field's id.
     */
    val id: String = StableUuid.createNew(),
    label: String = "",
    type: FieldType = FieldType.TEXT,
    required: Boolean = false,
    lines: String = "",
    digits: String = "",
    from: String = "",
    to: String = "",
    optionsText: String = "",
    defaultNow: Boolean = false,
    allowOrderFiltering: Boolean = false,
    sortByTimestamp: Boolean = false,
    allowUnknown: Boolean = false,
    makeDropdown: Boolean = false,
    choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
    preselectedOption: String = "",
    leftDigits: String = "",
    rightDigits: String = "",
    unit: String = "",
    itemLabel: String = "",
    firstLabel: String = "",
    secondLabel: String = "",
    totalLabel: String = "",
    wholeNumbersOnly: Boolean = true,
    clickerCounting: Boolean = false,
    clickerButtonLabel: String = "",
    clickerStartingNumber: String = "",
    clickerIncrement: String = "",
    clickerDirection: ClickerIncrementDirection = ClickerIncrementDirection.ADD,
    autoFill: Boolean = false,
    autoFillSource: String = AutoFill.TIMESTAMP,
    addQuantities: Boolean = false,
) : ListItemQuantitiesSettingsDraft {
    var label by mutableStateOf(label)
    var type by mutableStateOf(type)
    var required by mutableStateOf(required)
    var lines by mutableStateOf(lines)
    override var digits by mutableStateOf(digits)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
    var optionsText by mutableStateOf(optionsText)
    var defaultNow by mutableStateOf(defaultNow)
    var allowOrderFiltering by mutableStateOf(allowOrderFiltering)
    var sortByTimestamp by mutableStateOf(sortByTimestamp)
    var allowUnknown by mutableStateOf(allowUnknown)
    var makeDropdown by mutableStateOf(makeDropdown)
    var choiceStyle by mutableStateOf(choiceStyle)
    /** Single Choice: the name of the option pre-selected on new entries (blank for none). */
    var preselectedOption by mutableStateOf(preselectedOption)
    var leftDigits by mutableStateOf(leftDigits)
    var rightDigits by mutableStateOf(rightDigits)
    var unit by mutableStateOf(unit)
    override var itemLabel by mutableStateOf(itemLabel)
    override var firstLabel by mutableStateOf(firstLabel)
    override var secondLabel by mutableStateOf(secondLabel)
    override var totalLabel by mutableStateOf(totalLabel)
    override var wholeNumbersOnly by mutableStateOf(wholeNumbersOnly)
    override var clickerCounting by mutableStateOf(clickerCounting)
    override var clickerButtonLabel by mutableStateOf(clickerButtonLabel)
    override var clickerStartingNumber by mutableStateOf(clickerStartingNumber)
    var autoFill by mutableStateOf(autoFill)
    /** The Auto Fill drop-down's pick, kept while the checkbox is off. */
    var autoFillSource by mutableStateOf(autoFillSource)
    override var clickerIncrement by mutableStateOf(clickerIncrement)
    override var clickerDirection by mutableStateOf(clickerDirection)
    override var addQuantities by mutableStateOf(addQuantities)

    fun optionList(): List<String> =
        optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * The typed options as new [ChoiceOption]s, each with a fresh permanent id.
     * For Multiple Choice a leading `*` marks the option as pre-selected and is
     * not part of its name. For Single Choice the [preselectedOption] (matched
     * ignoring capitalization) is the one pre-selected option.
     */
    fun choiceList(): List<ChoiceOption> {
        val choices = optionList().map { written ->
            val marked = type == FieldType.MULTIPLE && written.startsWith("*") &&
                written.removePrefix("*").isNotBlank()
            ChoiceOption.createNew(
                label = if (marked) written.removePrefix("*").trim() else written,
                preselected = marked,
            )
        }
        if (type != FieldType.DROPDOWN || preselectedOption.isBlank()) return choices
        val match = choices.matchingLabel(preselectedOption) ?: return choices
        return choices.map { it.copy(preselected = it.id == match.id) }
    }

    /** A Single Choice pre-selected option must name one of the typed options (blank is fine). */
    fun preselectedOptionValid(): Boolean =
        type != FieldType.DROPDOWN || preselectedOption.isBlank() ||
            optionList().any { OptionDuplicates.key(it) == OptionDuplicates.key(preselectedOption) }

    /** Effective scale bounds: blank boxes fall back to the 1..10 defaults. */
    private fun effectiveFrom(): Int = from.toIntOrNull() ?: SCALE_DEFAULT_FROM
    private fun effectiveTo(): Int = to.toIntOrNull() ?: SCALE_DEFAULT_TO

    fun isValid(): Boolean {
        if (label.trim().isEmpty()) return false
        return when (type) {
            FieldType.SCALE -> effectiveTo() >= effectiveFrom()
            FieldType.DROPDOWN -> optionList().isNotEmpty() && preselectedOptionValid()
            FieldType.MULTIPLE, FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES -> optionList().isNotEmpty()
            FieldType.RATIO -> ratioDigitsValid()
            else -> true
        }
    }

    /** A short error message when the field isn't yet valid, else null. */
    fun validationHint(): String? = when {
        label.trim().isEmpty() -> "Add a label."
        type == FieldType.SCALE && !isValid() -> "Scale To must be ≥ From."
        type.hasOptionList && optionList().isEmpty() ->
            "Add at least one option."
        !preselectedOptionValid() -> "Enter an option from the list."
        type == FieldType.RATIO && !ratioDigitsValid() -> RATIO_DIGITS_HINT
        else -> null
    }

    private fun ratioDigitsValid(): Boolean =
        RatioValues.parseDigits(leftDigits) != null && RatioValues.parseDigits(rightDigits) != null

    /** A brand-new field: it keeps the draft's [id]; each option gets a fresh permanent id. */
    fun toFieldDef(): FieldDef = FieldDef(
        id = id,
        label = label.trim(),
        type = type,
        required = required && !type.hasItemRows,
        lines = if (type == FieldType.MULTILINE) lines.toIntOrNull() else null,
        digits = when (type) {
            FieldType.NUMBER -> digits.toIntOrNull()
            FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES -> ComparisonValues.parseMaxDigits(digits)
            else -> null
        },
        from = if (type == FieldType.SCALE) effectiveFrom() else null,
        to = if (type == FieldType.SCALE) effectiveTo() else null,
        choices = if (type.hasOptionList) choiceList() else emptyList(),
        defaultNow = type == FieldType.DATETIME && defaultNow,
        allowOrderFiltering = type.sortEligible && allowOrderFiltering,
        allowUnknown = type == FieldType.YESNO && allowUnknown,
        makeDropdown = type == FieldType.SCALE && makeDropdown,
        choiceStyle = if (type == FieldType.DROPDOWN) choiceStyle else ChoiceStyle.DROPDOWN,
        leftDigits = if (type == FieldType.RATIO) RatioValues.parseDigits(leftDigits) else null,
        rightDigits = if (type == FieldType.RATIO) RatioValues.parseDigits(rightDigits) else null,
        unit = if (type == FieldType.RATIO) unit.trim() else "",
        itemLabel = if (type.hasItemRows) itemLabel.trim() else "",
        firstLabel = if (type.hasItemRows) firstLabel.trim() else "",
        secondLabel = if (type == FieldType.COMPARISON) secondLabel.trim() else "",
        totalLabel = if (type.hasItemRows) totalLabel.trim() else "",
        wholeNumbersOnly = !type.hasItemRows || wholeNumbersOnly,
        clickerCounting = type == FieldType.COMPARISON && clickerCounting,
        clickerButtonLabel = if (type == FieldType.COMPARISON) clickerButtonLabel.trim() else "",
        clickerStartingNumber = if (type == FieldType.COMPARISON) clickerStartingNumber.toIntOrNull() ?: 0 else 0,
        clickerIncrement = if (type == FieldType.COMPARISON) clickerIncrement.toIntOrNull()?.takeIf { it > 0 } ?: 1 else 1,
        clickerDirection = if (type == FieldType.COMPARISON) clickerDirection else ClickerIncrementDirection.ADD,
        autoFillSource = if (type.autoFillable && autoFill) autoFillSource else null,
        addQuantities = type == FieldType.LIST_ITEM_QUANTITIES && addQuantities,
    )

    fun toSnapshot(): DraftFieldSnapshot = DraftFieldSnapshot(
        id = id,
        label = label,
        type = type,
        required = required,
        lines = lines,
        digits = digits,
        from = from,
        to = to,
        optionsText = optionsText,
        defaultNow = defaultNow,
        allowOrderFiltering = allowOrderFiltering,
        sortByTimestamp = sortByTimestamp,
        allowUnknown = allowUnknown,
        makeDropdown = makeDropdown,
        choiceStyle = choiceStyle,
        preselectedOption = preselectedOption,
        leftDigits = leftDigits,
        rightDigits = rightDigits,
        unit = unit,
        itemLabel = itemLabel,
        firstLabel = firstLabel,
        secondLabel = secondLabel,
        totalLabel = totalLabel,
        wholeNumbersOnly = wholeNumbersOnly,
        clickerCounting = clickerCounting,
        clickerButtonLabel = clickerButtonLabel,
        clickerStartingNumber = clickerStartingNumber,
        clickerIncrement = clickerIncrement,
        clickerDirection = clickerDirection,
        autoFill = autoFill,
        autoFillSource = autoFillSource,
        addQuantities = addQuantities,
    )
}

/** Shown under a Ratios field until both digit boxes hold 1–10. */
internal const val RATIO_DIGITS_HINT = "Add the number of digits on each side."

/** Scale bounds used when the user leaves the From/To boxes blank. */
private const val SCALE_DEFAULT_FROM = 1
private const val SCALE_DEFAULT_TO = 10

/** Plain serializable snapshot of a [DraftField], for [draftFieldsSaver]. */
@Serializable
private data class DraftFieldSnapshot(
    val id: String = "",
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val lines: String,
    val digits: String,
    val from: String,
    val to: String,
    val optionsText: String,
    val defaultNow: Boolean,
    val allowOrderFiltering: Boolean = false,
    val sortByTimestamp: Boolean = false,
    val allowUnknown: Boolean = false,
    val makeDropdown: Boolean = false,
    val choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
    val preselectedOption: String = "",
    val leftDigits: String = "",
    val rightDigits: String = "",
    val unit: String = "",
    val itemLabel: String = "",
    val firstLabel: String = "",
    val secondLabel: String = "",
    val totalLabel: String = "",
    val wholeNumbersOnly: Boolean = true,
    val clickerCounting: Boolean = false,
    val clickerButtonLabel: String = "",
    val clickerStartingNumber: String = "",
    val clickerIncrement: String = "",
    val clickerDirection: ClickerIncrementDirection = ClickerIncrementDirection.ADD,
    val autoFill: Boolean = false,
    val autoFillSource: String = AutoFill.TIMESTAMP,
    val addQuantities: Boolean = false,
)

private fun DraftFieldSnapshot.toDraftField(): DraftField = DraftField(
    id = id.ifBlank { StableUuid.createNew() },
    label = label,
    type = type,
    required = required,
    lines = lines,
    digits = digits,
    from = from,
    to = to,
    optionsText = optionsText,
    defaultNow = defaultNow,
    allowOrderFiltering = allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    allowUnknown = allowUnknown,
    makeDropdown = makeDropdown,
    choiceStyle = choiceStyle,
    preselectedOption = preselectedOption,
    leftDigits = leftDigits,
    rightDigits = rightDigits,
    unit = unit,
    itemLabel = itemLabel,
    firstLabel = firstLabel,
    secondLabel = secondLabel,
    totalLabel = totalLabel,
    wholeNumbersOnly = wholeNumbersOnly,
    clickerCounting = clickerCounting,
    clickerButtonLabel = clickerButtonLabel,
    clickerStartingNumber = clickerStartingNumber,
    clickerIncrement = clickerIncrement,
    clickerDirection = clickerDirection,
    autoFill = autoFill,
    autoFillSource = autoFillSource,
    addQuantities = addQuantities,
)

/**
 * Return the pasted Form Markdown unchanged except for casing: each field-label
 * line ("## …") is title-cased when [labels] is on, and each option line
 * ("- …") when [options] is on. All other text, spacing, blank lines, and line
 * breaks are left exactly as pasted. Leading indentation and the spacing after
 * the "##"/"-" marker are preserved; only the letters of the label/option change.
 */
private fun capitalizeMarkdown(text: String, labels: Boolean, options: Boolean): String {
    if (!labels && !options) return text
    return text.split("\n").joinToString("\n") { raw ->
        val indent = raw.takeWhile { it == ' ' || it == '\t' }
        val content = raw.substring(indent.length)
        when {
            // "## Label" (field header) — but not a lone "#" log-name line.
            labels && content.startsWith("##") -> {
                val after = content.substring(2)
                val gap = after.takeWhile { it == ' ' || it == '\t' }
                indent + "##" + gap + TitleCase.apply(after.substring(gap.length))
            }
            // "- Option" (list item).
            options && content.startsWith("-") -> {
                val after = content.substring(1)
                val gap = after.takeWhile { it == ' ' || it == '\t' }
                indent + "-" + gap + TitleCase.apply(after.substring(gap.length))
            }
            else -> raw
        }
    }
}

/**
 * Return a copy with major words title-cased, per the auto-capitalize settings:
 * [labels] title-cases the field label; [options] title-cases each dropdown or
 * multiple option. Other field types keep their (empty) option list unchanged.
 */
private fun FieldDef.autoCapitalized(labels: Boolean, options: Boolean): FieldDef = copy(
    label = if (labels) TitleCase.apply(label) else label,
    choices = if (options) choices.map { it.copy(label = TitleCase.apply(it.label)) } else choices,
)

private fun FieldDef.toDraft(): DraftField = DraftField(
    id = id.ifBlank { StableUuid.createNew() },
    label = label,
    type = type,
    required = required,
    lines = lines?.toString() ?: "",
    digits = digits?.toString() ?: "",
    from = from?.toString() ?: "",
    to = to?.toString() ?: "",
    // Back to the creation syntax: `*` marks a pre-selected Multiple Choice
    // option; a Single Choice default goes in Pre-Selected Option.
    optionsText = activeChoices.joinToString("\n") { choice ->
        if (type == FieldType.MULTIPLE && choice.preselected) "*${choice.label}" else choice.label
    },
    defaultNow = defaultNow,
    allowOrderFiltering = allowOrderFiltering,
    allowUnknown = allowUnknown,
    makeDropdown = makeDropdown,
    choiceStyle = choiceStyle,
    preselectedOption = if (type == FieldType.DROPDOWN) {
        activeChoices.firstOrNull { it.preselected }?.label.orEmpty()
    } else {
        ""
    },
    leftDigits = leftDigits?.toString() ?: "",
    rightDigits = rightDigits?.toString() ?: "",
    unit = unit,
    itemLabel = itemLabel,
    firstLabel = firstLabel,
    secondLabel = secondLabel,
    totalLabel = totalLabel,
    wholeNumbersOnly = wholeNumbersOnly,
    clickerCounting = clickerCounting,
    clickerButtonLabel = clickerButtonLabel,
    clickerStartingNumber = clickerStartingNumber.takeIf { it != 0 }?.toString() ?: "",
    clickerIncrement = clickerIncrement.takeIf { it != 1 }?.toString() ?: "",
    clickerDirection = clickerDirection,
    autoFill = autoFillSource != null,
    autoFillSource = autoFillSource ?: AutoFill.TIMESTAMP,
    addQuantities = addQuantities,
)

/** The first single-`#` line of [text], used as the log name when the box is empty. */
private fun firstMarkdownName(text: String): String? =
    text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("#") && !it.startsWith("##") }
        ?.removePrefix("#")?.trim()?.ifEmpty { null }

private fun FieldType.friendly(): String = when (this) {
    FieldType.TEXT -> "Text (one line)"
    FieldType.MULTILINE -> "Text (multi-line)"
    FieldType.NUMBER -> "Number"
    FieldType.DROPDOWN -> "Single Choice"
    FieldType.MULTIPLE -> "Multiple Choices"
    FieldType.SCALE -> "Scale (Number Range)"
    FieldType.YESNO -> "Yes / No (with optional Unknown)"
    FieldType.DATE -> "Date"
    FieldType.TIME -> "Time"
    FieldType.DATETIME -> "Date & time"
    FieldType.TAGS -> "Tags"
    FieldType.WEBPAGE -> "Webpages"
    FieldType.BLOOD_PRESSURE -> "Blood Pressure"
    FieldType.RATIO -> "Ratios"
    FieldType.COMPARISON -> "Comparison"
    FieldType.LIST_ITEM_QUANTITIES -> "List Item Quantities"
}

/** A short human-readable description of a parsed field for the preview. */
private fun FieldDef.summary(): String {
    val base = when (type) {
        FieldType.TEXT -> "Single line of text"
        FieldType.MULTILINE -> "Multi-line text" + (lines?.let { " ($it lines)" } ?: "")
        FieldType.NUMBER -> "Number" + (digits?.let { " (up to $it digits)" } ?: "")
        FieldType.DROPDOWN -> "Pick one: " + activeChoices.joinToString(", ") { it.label }
        FieldType.MULTIPLE -> "Pick several: " + activeChoices.joinToString(", ") { it.label }
        FieldType.SCALE -> "Scale $from–$to" + (if (makeDropdown) " · Dropdown" else "")
        FieldType.YESNO -> if (allowUnknown) "Yes / No / Unknown" else "Yes / No"
        FieldType.DATE -> "Date"
        FieldType.TIME -> "Time"
        FieldType.DATETIME -> "Date & time" + (if (defaultNow) " (defaults to now)" else "")
        FieldType.TAGS -> "Tags"
        FieldType.WEBPAGE -> "Webpage address"
        FieldType.BLOOD_PRESSURE -> "Blood pressure (###/###)"
        FieldType.RATIO -> "Ratios"
        FieldType.COMPARISON -> "Compare: " + activeChoices.joinToString(", ") { it.label }
        FieldType.LIST_ITEM_QUANTITIES -> "Total: " + activeChoices.joinToString(", ") { it.label }
    }
    return if (required) "$base · required" else base
}
