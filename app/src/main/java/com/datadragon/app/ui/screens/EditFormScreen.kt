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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
 * Edit a log's fields after creation. Only two changes are allowed, so stored
 * entries stay valid: **add** new fields and **reorder** any field. Existing
 * fields are shown read-only (they can be moved but not renamed, retyped, or
 * removed). New entries get the new fields immediately; if the log is unlocked,
 * old entries can be edited to fill them in too.
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

    // One row per field: existing fields are read-only, new ones are editable.
    val rows = remember { mutableStateListOf<FormRow>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(loadedFields) {
        if (!seeded && loadedFields.isNotEmpty()) {
            rows.clear()
            loadedFields.forEach { rows.add(FormRow(existing = it, draft = null)) }
            seeded = true
        }
    }

    val canSave = name != null && rows.all { it.existing != null || it.draft?.isValid() == true }

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
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val fields = rows.map { it.existing ?: it.draft!!.toFieldDef() }
                            viewModel.save(fields, onSaved = onBack)
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
                "You can add new fields and reorder them. Existing fields can be " +
                    "moved but not renamed or removed, so past entries stay intact.",
                style = MaterialTheme.typography.bodyMedium,
            )

            rows.forEachIndexed { index, row ->
                val existing = row.existing
                if (existing != null) {
                    ExistingFieldCard(
                        field = existing,
                        title = "Field ${index + 1}",
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onMoveUp = { move(index, -1) },
                        onMoveDown = { move(index, 1) },
                    )
                } else {
                    NewFieldCard(
                        field = row.draft!!,
                        title = "Field ${index + 1} (new)",
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onMoveUp = { move(index, -1) },
                        onMoveDown = { move(index, 1) },
                        onDelete = { rows.removeAt(index) },
                    )
                }
            }

            OutlinedButton(
                onClick = { rows.add(FormRow(existing = null, draft = EditDraft())) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add field")
            }
        }
    }
}

/** A row in the editor: either a pre-existing field or a new editable draft. */
private class FormRow(val existing: FieldDef?, val draft: EditDraft?)

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
private fun ExistingFieldCard(
    field: FieldDef,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                ReorderControls(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
            }
            Text(field.label, style = MaterialTheme.typography.titleSmall)
            Text(
                field.editSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewFieldCard(
    field: EditDraft,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                ReorderControls(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
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

            EditTypeDropdown(selected = field.type, onSelected = { field.type = it })

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

/** Mutable, Compose-observable editing state for one new field. */
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

/** A short read-only description of an existing field. */
private fun FieldDef.editSummary(): String {
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
