package com.datadragon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.ui.CreateLogViewModel

/** Which editor is showing. Build (visual taps) is the default. */
private enum class BuilderMode { BUILD, PASTE }

private const val FIELD_TYPES_REFERENCE = """text         — a single line of text
multiline    — multi-line text box. Set "lines" for visible height
date         — month/day/year picker
time         — 12-hour time with AM/PM
dropdown     — pick one item from a list
scale        — pick a number in a range. Set "from" and "to"
yesno        — Yes / No / Unknown / Not Applicable
number       — type a number. Set "digits" for max digits allowed
multiple     — pick several items from a list (tappable chips)

Any field can add "required" to prevent saving without it."""

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
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(BuilderMode.BUILD) }

    // Build tab: the editable field list is the source of truth.
    val draftFields = remember { mutableStateListOf<DraftField>() }

    // Paste tab: raw Form Markdown plus an optional preview (a helper, never
    // required to save).
    var pasteText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FormMarkdownParser.ParseResult?>(null) }

    val canSave = when (mode) {
        BuilderMode.BUILD -> name.isNotBlank() && draftFields.all { it.isValid() }
        BuilderMode.PASTE -> name.isNotBlank() || firstMarkdownName(pasteText) != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val fields: List<FieldDef>
                            val markdown: String
                            if (mode == BuilderMode.BUILD) {
                                fields = draftFields.filter { it.isValid() }.map { it.toFieldDef() }
                                markdown = FormMarkdownGenerator.generate(name, fields)
                            } else {
                                fields = FormMarkdownParser.parse(pasteText).fields
                                markdown = pasteText
                            }
                            val finalName = name.ifBlank { firstMarkdownName(pasteText) ?: "" }
                            viewModel.save(
                                name = finalName,
                                schemaJson = FormMarkdownParser.encodeFields(fields),
                                formMarkdown = markdown,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
                    onAdd = { draftFields.add(DraftField()) },
                    onDelete = { draftFields.remove(it) },
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
}

// ---- Build (visual) editor -------------------------------------------------

@Composable
private fun BuildEditor(
    fields: List<DraftField>,
    onAdd: () -> Unit,
    onDelete: (DraftField) -> Unit,
) {
    Text("Fields", style = MaterialTheme.typography.labelLarge)
    if (fields.isEmpty()) {
        Text(
            "No fields yet. Tap “Add field” to build your form. You can also save a log with no fields.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    fields.forEachIndexed { index, field ->
        FieldEditorCard(field = field, index = index, onDelete = { onDelete(field) })
    }
    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("  Add field")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorCard(field: DraftField, index: Int, onDelete: () -> Unit) {
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
            }

            OutlinedTextField(
                value = field.label,
                onValueChange = { field.label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TypeDropdown(selected = field.type, onSelected = { field.type = it })

            when (field.type) {
                FieldType.MULTILINE -> NumberField(
                    value = field.lines,
                    onChange = { field.lines = it },
                    label = "Lines (height, optional)",
                )
                FieldType.NUMBER -> NumberField(
                    value = field.digits,
                    onChange = { field.digits = it },
                    label = "Max digits (optional)",
                )
                FieldType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = field.from,
                        onChange = { field.from = it },
                        label = "From",
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = field.to,
                        onChange = { field.to = it },
                        label = "To",
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldType.DROPDOWN, FieldType.MULTIPLE -> OutlinedTextField(
                    value = field.optionsText,
                    onValueChange = { field.optionsText = it },
                    label = { Text("Options (one per line)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                FieldType.DATETIME -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = field.defaultNow, onCheckedChange = { field.defaultNow = it })
                    Text("Default to the current date & time")
                }
                else -> Unit
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = field.required, onCheckedChange = { field.required = it })
                Text("Required")
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
            Text("How to write it")
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
                Text("Field types", style = MaterialTheme.typography.labelMedium)
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

    OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
        Text("Preview form (optional)")
    }

    preview?.let { PreviewSection(it) }
}

@Composable
private fun PreviewSection(result: FormMarkdownParser.ParseResult) {
    HorizontalDivider()
    Text("Preview", style = MaterialTheme.typography.titleMedium)

    if (result.fields.isEmpty()) {
        Text(
            "No fields defined yet. You can still save a log with no fields.",
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
        Text("Skipped lines", style = MaterialTheme.typography.titleSmall)
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

    fun optionList(): List<String> =
        optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun isValid(): Boolean {
        if (label.trim().isEmpty()) return false
        return when (type) {
            FieldType.SCALE -> {
                val f = from.toIntOrNull()
                val t = to.toIntOrNull()
                f != null && t != null && t >= f
            }
            FieldType.DROPDOWN, FieldType.MULTIPLE -> optionList().isNotEmpty()
            else -> true
        }
    }

    /** A short error message when the field isn't yet valid, else null. */
    fun validationHint(): String? = when {
        label.trim().isEmpty() -> "Add a label."
        type == FieldType.SCALE && !isValid() -> "Scale needs a From and a To (To ≥ From)."
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
        from = if (type == FieldType.SCALE) from.toIntOrNull() else null,
        to = if (type == FieldType.SCALE) to.toIntOrNull() else null,
        options = if (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE) optionList() else emptyList(),
        defaultNow = type == FieldType.DATETIME && defaultNow,
    )
}

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
    FieldType.SCALE -> "Scale (number range)"
    FieldType.YESNO -> "Yes / No / Unknown / N/A"
    FieldType.DATE -> "Date"
    FieldType.TIME -> "Time"
    FieldType.DATETIME -> "Date & time"
}

/** A short human-readable description of a parsed field for the preview. */
private fun FieldDef.summary(): String {
    val base = when (type) {
        FieldType.TEXT -> "Single line of text"
        FieldType.MULTILINE -> "Multi-line text" + (lines?.let { " ($it lines)" } ?: "")
        FieldType.NUMBER -> "Number" + (digits?.let { " (up to $it digits)" } ?: "")
        FieldType.DROPDOWN -> "Pick one: " + options.joinToString(", ")
        FieldType.MULTIPLE -> "Pick several: " + options.joinToString(", ")
        FieldType.SCALE -> "Scale $from–$to"
        FieldType.YESNO -> "Yes / No / Unknown / Not Applicable"
        FieldType.DATE -> "Date"
        FieldType.TIME -> "Time"
        FieldType.DATETIME -> "Date & time" + (if (defaultNow) " (defaults to now)" else "")
    }
    return if (required) "$base · required" else base
}
