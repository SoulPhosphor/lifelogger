package com.datadragon.app.ui.screens

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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.sortEligible
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.ui.CreateLogViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableColumn

/** Which editor is showing. Build (visual taps) is the default. */
private enum class BuilderMode { BUILD, PASTE }

private const val FIELD_TYPES_REFERENCE = """text         — a single line of text
multiline    — multi-line text box. Set "lines" for visible height
date         — month/day/year picker
time         — 12-hour time with AM/PM
dropdown     — pick one item from a list
scale          — pick a number in a range. Set "from" and "to" (default 1 to 10). Add "make_dropdown: true" for a dropdown
yesno          — Yes / No radios. Add "allow_unknown: true" for an Unknown radio
number         — type a number. Set "digits" for max digits allowed
multiple       — pick several items from a list (tappable chips)
tags           — type a tag and add it; each becomes a removable chip
webpage        — a web address, with a button that opens it
blood_pressure — two 3-digit boxes separated by "/" (systolic / diastolic)

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

    // Per-log behavior, chosen once at creation. Locked (create-once entries) is
    // the default; it can later be unlocked one-way from the log screen.
    var locked by rememberSaveable { mutableStateOf(true) }
    var allowAppendedNotes by rememberSaveable { mutableStateOf(false) }
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
                            if (mode == BuilderMode.BUILD) {
                                // Every field here is newly created, so apply the
                                // auto-capitalize settings before saving.
                                fields = draftFields.filter { it.isValid() }
                                    .map { it.toFieldDef() }
                                    .map {
                                        it.autoCapitalized(
                                            labels = settings.autoCapitalizeLabels,
                                            options = settings.autoCapitalizeOptions,
                                        )
                                    }
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
                            val finalName = name.ifBlank { firstMarkdownName(pasteText) ?: "" }
                            val selectedSortLabel = draftFields
                                .singleOrNull { it.sortByTimestamp }
                                ?.label
                                ?.trim()
                            val sortTimestampLabel = selectedSortLabel?.let { selected ->
                                fields.firstOrNull {
                                    it.type.sortEligible &&
                                        it.label.equals(selected, ignoreCase = true)
                                }?.label
                            }
                            viewModel.save(
                                name = finalName,
                                schemaJson = FormMarkdownParser.encodeFields(fields),
                                formMarkdown = markdown,
                                locked = locked,
                                allowAppendedNotes = allowAppendedNotes,
                                automaticTimestamping = automaticTimestamping,
                                sortTimestampLabel = sortTimestampLabel,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log Name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SettingSwitchRow(
                checked = automaticTimestamping,
                onCheckedChange = { automaticTimestamping = it },
                title = "Automatic Timestamping",
            )
            SettingSwitchRow(
                checked = locked,
                onCheckedChange = { locked = it },
                title = "Locked Log",
                subtitle = "Entries can't be edited after saving. You can unlock it " +
                    "later, but only once — it can never be re-locked.",
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
}

/** A settings row with a label + description on the left and a Switch on the
 *  right. The whole row is tappable to toggle, which is easier to hit. */
@Composable
internal fun SettingSwitchRow(
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
            subtitle?.let {
                Text(
                    it,
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
 * The form's default entry order, shown under "Use as Default Sort Timestamp"
 * once a field is chosen as the sort timestamp — a default is only meaningful
 * once we also know which way it runs.
 */
@Composable
internal fun SortDirectionRadios(
    newestFirst: Boolean,
    onNewestFirstChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
        Text("Default Sorting:", style = MaterialTheme.typography.bodyLarge)
        // Both choices share the width so a narrow screen wraps the labels
        // rather than clipping them.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable { onNewestFirstChange(true) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = newestFirst, onClick = { onNewestFirstChange(true) })
                Text("Newest to Oldest")
            }
            Row(
                modifier = Modifier.weight(1f).clickable { onNewestFirstChange(false) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = !newestFirst, onClick = { onNewestFirstChange(false) })
                Text("Oldest to Newest")
            }
        }
    }
}

/** A checkbox whose entire labeled row is one toggle target. */
@Composable
internal fun CheckboxSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(title)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { index, field, _ ->
            FieldEditorCard(
                field = field,
                index = index,
                sortNewestFirst = sortNewestFirst,
                onSortChanged = { setSort(field, it) },
                onSortDirectionChange = onSortDirectionChange,
                onDelete = { onDelete(field) },
                dragHandleModifier = Modifier.draggableHandle(),
            )
        }
    }
    AppButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("  Add Field")
    }

    pendingReplacement?.let { (newField, existingField) ->
        AlertDialog(
            onDismissRequest = {
                newField.sortByTimestamp = false
                pendingReplacement = null
            },
            text = {
                Text(
                    "Default timestamp sorting is currently set to be ${existingField.label}. " +
                        "Do you want to change it?",
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    newField.sortByTimestamp = false
                    pendingReplacement = null
                }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    existingField.sortByTimestamp = false
                    pendingReplacement = null
                }) { Text("Okay") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorCard(
    field: DraftField,
    index: Int,
    sortNewestFirst: Boolean,
    onSortChanged: (Boolean) -> Unit,
    onSortDirectionChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = dragHandleModifier.padding(start = 4.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                FieldType.DROPDOWN, FieldType.MULTIPLE -> OutlinedTextField(
                    value = field.optionsText,
                    onValueChange = { field.optionsText = it },
                    label = { Text("Options (One per Line)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
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
            CheckboxSettingRow(
                checked = field.required,
                onCheckedChange = { field.required = it },
                title = "Required",
            )

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
            FieldType.entries.forEach { type ->
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
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
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
                Column(modifier = Modifier.padding(12.dp)) {
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
) {
    var label by mutableStateOf(label)
    var type by mutableStateOf(type)
    var required by mutableStateOf(required)
    var lines by mutableStateOf(lines)
    var digits by mutableStateOf(digits)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
    var optionsText by mutableStateOf(optionsText)
    var defaultNow by mutableStateOf(defaultNow)
    var allowOrderFiltering by mutableStateOf(allowOrderFiltering)
    var sortByTimestamp by mutableStateOf(sortByTimestamp)
    var allowUnknown by mutableStateOf(allowUnknown)
    var makeDropdown by mutableStateOf(makeDropdown)

    fun optionList(): List<String> =
        optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** Effective scale bounds: blank boxes fall back to the 1..10 defaults. */
    private fun effectiveFrom(): Int = from.toIntOrNull() ?: SCALE_DEFAULT_FROM
    private fun effectiveTo(): Int = to.toIntOrNull() ?: SCALE_DEFAULT_TO

    fun isValid(): Boolean {
        if (label.trim().isEmpty()) return false
        return when (type) {
            FieldType.SCALE -> effectiveTo() >= effectiveFrom()
            FieldType.DROPDOWN, FieldType.MULTIPLE -> optionList().isNotEmpty()
            else -> true
        }
    }

    /** A short error message when the field isn't yet valid, else null. */
    fun validationHint(): String? = when {
        label.trim().isEmpty() -> "Add a label."
        type == FieldType.SCALE && !isValid() -> "Scale To must be ≥ From."
        (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE) && optionList().isEmpty() ->
            "Add at least one option."
        else -> null
    }

    fun toFieldDef(): FieldDef = FieldDef(
        label = label.trim(),
        type = type,
        required = required,
        lines = if (type == FieldType.MULTILINE) lines.toIntOrNull() else null,
        digits = if (type == FieldType.NUMBER) digits.toIntOrNull() else null,
        from = if (type == FieldType.SCALE) effectiveFrom() else null,
        to = if (type == FieldType.SCALE) effectiveTo() else null,
        options = if (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE) optionList() else emptyList(),
        defaultNow = type == FieldType.DATETIME && defaultNow,
        allowOrderFiltering = type.sortEligible && allowOrderFiltering,
        allowUnknown = type == FieldType.YESNO && allowUnknown,
        makeDropdown = type == FieldType.SCALE && makeDropdown,
    )

    fun toSnapshot(): DraftFieldSnapshot = DraftFieldSnapshot(
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
    )
}

/** Scale bounds used when the user leaves the From/To boxes blank. */
private const val SCALE_DEFAULT_FROM = 1
private const val SCALE_DEFAULT_TO = 10

/** Plain serializable snapshot of a [DraftField], for [draftFieldsSaver]. */
@Serializable
private data class DraftFieldSnapshot(
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
)

private fun DraftFieldSnapshot.toDraftField(): DraftField = DraftField(
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
    options = if (options && (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE)) {
        this.options.map { TitleCase.apply(it) }
    } else {
        this.options
    },
)

private fun FieldDef.toDraft(): DraftField = DraftField(
    label = label,
    type = type,
    required = required,
    lines = lines?.toString() ?: "",
    digits = digits?.toString() ?: "",
    from = from?.toString() ?: "",
    to = to?.toString() ?: "",
    optionsText = options.joinToString("\n"),
    defaultNow = defaultNow,
    allowOrderFiltering = allowOrderFiltering,
    allowUnknown = allowUnknown,
    makeDropdown = makeDropdown,
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
    FieldType.DROPDOWN -> "Dropdown (pick one)"
    FieldType.MULTIPLE -> "Multiple (pick several)"
    FieldType.SCALE -> "Scale"
    FieldType.YESNO -> "Yes / No (with optional Unknown)"
    FieldType.DATE -> "Date"
    FieldType.TIME -> "Time"
    FieldType.DATETIME -> "Date & time"
    FieldType.TAGS -> "Tags"
    FieldType.WEBPAGE -> "Webpages"
    FieldType.BLOOD_PRESSURE -> "Blood Pressure"
}

/** A short human-readable description of a parsed field for the preview. */
private fun FieldDef.summary(): String {
    val base = when (type) {
        FieldType.TEXT -> "Single line of text"
        FieldType.MULTILINE -> "Multi-line text" + (lines?.let { " ($it lines)" } ?: "")
        FieldType.NUMBER -> "Number" + (digits?.let { " (up to $it digits)" } ?: "")
        FieldType.DROPDOWN -> "Pick one: " + options.joinToString(", ")
        FieldType.MULTIPLE -> "Pick several: " + options.joinToString(", ")
        FieldType.SCALE -> "Scale $from–$to" + (if (makeDropdown) " · dropdown" else "")
        FieldType.YESNO -> if (allowUnknown) "Yes / No / Unknown" else "Yes / No"
        FieldType.DATE -> "Date"
        FieldType.TIME -> "Time"
        FieldType.DATETIME -> "Date & time" + (if (defaultNow) " (defaults to now)" else "")
        FieldType.TAGS -> "Tags"
        FieldType.WEBPAGE -> "Webpage address"
        FieldType.BLOOD_PRESSURE -> "Blood pressure (###/###)"
    }
    return if (required) "$base · required" else base
}
