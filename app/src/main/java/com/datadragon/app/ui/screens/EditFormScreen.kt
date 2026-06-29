package com.datadragon.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.ui.EditFormViewModel

/**
 * Edit a log's fields after creation. To keep stored entries valid, the field's
 * **name** and **type** can never change, and existing fields can't be deleted.
 * What you *can* do: **add** new fields, **reorder** any field, and change the
 * adjustable **settings** of fields that have them — options for dropdown/multiple,
 * the range for a scale, line height for multiline, max digits for number, and
 * whether the field is required. Fixed types (text, date, time, yes/no) have
 * nothing to adjust and show read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFormScreen(
    logId: String?,
    onBack: () -> Unit,
    viewModel: EditFormViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }

    val loadedFields by viewModel.fields.collectAsStateWithLifecycle()
    // Non-null once the template has loaded; gates Save so we never write an empty
    // schema before the existing fields have been read in.
    val name by viewModel.name.collectAsStateWithLifecycle()

    // Every field is an editable draft; existing ones are flagged so their name
    // and type stay locked. Seeded once from the loaded schema.
    val rows = remember { mutableStateListOf<EditDraft>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(loadedFields) {
        if (!seeded && loadedFields.isNotEmpty()) {
            rows.clear()
            loadedFields.forEach { rows.add(draftOf(it)) }
            seeded = true
        }
    }

    val canSave = name != null && rows.all { it.isValid() }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target in rows.indices) {
            val row = rows.removeAt(index)
            rows.add(target, row)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit form") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            viewModel.save(rows.map { it.toFieldDef() }, onSaved = onBack)
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
            Text(
                "Add new fields, reorder them, and adjust the settings of existing " +
                    "fields. A field's name and type can't change, and existing fields " +
                    "can't be removed, so past entries stay intact.",
                style = MaterialTheme.typography.bodyMedium,
            )

            rows.forEachIndexed { index, field ->
                FieldCard(
                    field = field,
                    canMoveUp = index > 0,
                    canMoveDown = index < rows.lastIndex,
                    onMoveUp = { move(index, -1) },
                    onMoveDown = { move(index, 1) },
                    // Only new fields can be deleted here.
                    deletable = !field.existing,
                    onDelete = { rows.removeAt(index) },
                )
            }

            OutlinedButton(
                onClick = { rows.add(EditDraft()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add field")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCard(
    field: EditDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    deletable: Boolean,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Header says "Edit" for fields whose settings can change, and
                // nothing for fixed ones. The field name is shown as content below,
                // not as the header.
                Text(
                    text = when {
                        !field.existing -> "New field"
                        field.type.hasEditableSettings() -> "Edit"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ReorderControls(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
                if (deletable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete field")
                    }
                }
            }

            if (field.existing) {
                // The field name as plain content (name and type are locked).
                Text(field.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Type: ${field.type.editFriendly()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (field.type.hasEditableSettings()) {
                    SettingsControls(field)
                    RequiredRow(field)
                    field.validationHint()?.let { hint ->
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = { field.label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EditTypeDropdown(selected = field.type, onSelected = { field.type = it })
                SettingsControls(field)
                RequiredRow(field)
                field.validationHint()?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** The type-specific adjustable settings shared by new and existing fields. */
@Composable
private fun SettingsControls(field: EditDraft) {
    when (field.type) {
        FieldType.MULTILINE -> EditNumberField(
            value = field.lines,
            onChange = { field.lines = it },
            label = "Lines (height, optional)",
        )
        FieldType.NUMBER -> EditNumberField(
            value = field.digits,
            onChange = { field.digits = it },
            label = "Max digits (optional)",
        )
        FieldType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditNumberField(
                value = field.from,
                onChange = { field.from = it },
                label = "From",
                modifier = Modifier.weight(1f),
            )
            EditNumberField(
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
}

@Composable
private fun RequiredRow(field: EditDraft) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = field.required, onCheckedChange = { field.required = it })
        Text("Required")
    }
}

@Composable
private fun ReorderControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    // Chevron up (∧) moves the field up; chevron down (∨) moves it down.
    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
        Icon(Icons.Filled.ExpandLess, contentDescription = "Move up")
    }
    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
        Icon(Icons.Filled.ExpandMore, contentDescription = "Move down")
    }
}

@Composable
private fun EditNumberField(
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
private fun EditTypeDropdown(selected: FieldType, onSelected: (FieldType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.editFriendly(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FieldType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.editFriendly()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---- Draft model + helpers (self-contained for this screen) -----------------

/** Mutable, Compose-observable editing state for one field. */
private class EditDraft(
    label: String = "",
    type: FieldType = FieldType.TEXT,
    required: Boolean = false,
    lines: String = "",
    digits: String = "",
    from: String = "",
    to: String = "",
    optionsText: String = "",
    defaultNow: Boolean = false,
    /** True for a field that already exists in the saved schema (name/type locked). */
    val existing: Boolean = false,
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

/** Seed an [EditDraft] from a saved field (marked existing — name/type locked). */
private fun draftOf(f: FieldDef): EditDraft = EditDraft(
    label = f.label,
    type = f.type,
    required = f.required,
    lines = f.lines?.toString() ?: "",
    digits = f.digits?.toString() ?: "",
    from = f.from?.toString() ?: "",
    to = f.to?.toString() ?: "",
    optionsText = f.options.joinToString("\n"),
    defaultNow = f.defaultNow,
    existing = true,
)

/** Types whose settings can be adjusted after creation. */
private fun FieldType.hasEditableSettings(): Boolean = when (this) {
    FieldType.MULTILINE, FieldType.NUMBER, FieldType.DROPDOWN, FieldType.MULTIPLE, FieldType.SCALE -> true
    else -> false
}

private fun FieldType.editFriendly(): String = when (this) {
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
