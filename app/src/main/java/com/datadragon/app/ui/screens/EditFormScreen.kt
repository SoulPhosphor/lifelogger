package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.ui.EditFormViewModel

/**
 * Edit a log's fields after creation. To keep stored entries valid, a field's
 * **type** can never change and existing fields can't be deleted. What you *can*
 * do: **add** new fields, **reorder** any field, and open a field to edit its
 * content — its label (spelling), options, and adjustable settings. Renaming a
 * label or an option re-keys the already-submitted entries so their values stay
 * attached under the new spelling; the values themselves are never changed.
 *
 * The list is a set of tappable summary cards; tapping a card (anywhere but its
 * reorder/delete buttons) opens a full-screen **field editor** with the block
 * pre-filled. That editor's **Save** writes the change to the log immediately and
 * returns here.
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

    // Every field is an editable draft; existing ones are flagged so their type
    // stays locked and their original label/options are remembered for re-keying.
    // Seeded once from the loaded schema.
    val rows = remember { mutableStateListOf<EditDraft>() }
    var seeded by remember { mutableStateOf(false) }
    // The schema as last saved, so an unsaved reorder or added field can be caught
    // on the way out. Seeded from the round-tripped rows so a freshly loaded, untouched
    // form is never seen as "changed."
    var savedSnapshot by remember { mutableStateOf<List<FieldDef>?>(null) }
    LaunchedEffect(loadedFields) {
        if (!seeded && loadedFields.isNotEmpty()) {
            rows.clear()
            loadedFields.forEach { rows.add(draftOf(it)) }
            savedSnapshot = rows.map { it.toFieldDef() }
            seeded = true
        }
    }

    // Which field's editor is open, or null for the list. Opening a field shows a
    // full-screen editor in place of the list.
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val canSave = name != null && rows.all { it.isValid() }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target in rows.indices) {
            val row = rows.removeAt(index)
            rows.add(target, row)
        }
    }

    // Rename maps derived from what each existing field started as vs. what it is
    // now, so the ViewModel can re-key submitted entries before writing the schema.
    fun labelRenames(): Map<String, String> =
        rows.filter { it.existing }
            .mapNotNull { d ->
                val original = d.originalLabel ?: return@mapNotNull null
                val current = d.label.trim()
                if (original != current) original to current else null
            }
            .toMap()

    fun optionRenames(): Map<String, Map<String, String>> =
        rows.filter { it.existing && (it.type == FieldType.DROPDOWN || it.type == FieldType.MULTIPLE) }
            .mapNotNull { d ->
                val map = optionRenameMap(d.originalOptions, d.optionList())
                if (map.isEmpty()) null else d.label.trim() to map
            }
            .toMap()

    // Persist the current schema, re-keying submitted entries for any rename, then
    // realign each existing field's "original" to what was just saved so a second
    // edit computes its rename fresh rather than re-applying the last one.
    fun persist(onDone: () -> Unit) {
        viewModel.save(
            fields = rows.map { it.toFieldDef() },
            labelRenames = labelRenames(),
            optionRenames = optionRenames(),
            onSaved = {
                rows.forEach { d ->
                    if (d.existing) {
                        d.originalLabel = d.label.trim()
                        d.originalOptions = d.optionList()
                    }
                }
                savedSnapshot = rows.map { it.toFieldDef() }
                onDone()
            },
        )
    }

    val openIndex = editingIndex
    if (openIndex != null && openIndex in rows.indices) {
        val draft = rows[openIndex]
        FieldEditorScreen(
            source = draft,
            number = openIndex + 1,
            canSave = { it.isValid() },
            onSave = { persist { editingIndex = null } },
            onCancel = {
                // A brand-new field that was never filled in is discarded on cancel
                // so the list isn't left with an empty card.
                if (!draft.existing && draft.label.isBlank()) rows.removeAt(openIndex)
                editingIndex = null
            },
        )
        return
    }

    val dirty = savedSnapshot?.let { snap -> rows.map { it.toFieldDef() } != snap } ?: false
    var showDiscard by remember { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Form") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = { persist(onBack) },
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
            Text(
                "Tap a field to edit its label, options, and settings. Add new fields " +
                    "or reorder them. A field's type can't change and existing fields " +
                    "can't be removed, so past entries stay intact.",
                style = MaterialTheme.typography.bodyMedium,
            )

            rows.forEachIndexed { index, field ->
                FieldSummaryCard(
                    field = field,
                    number = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < rows.lastIndex,
                    onMoveUp = { move(index, -1) },
                    onMoveDown = { move(index, 1) },
                    // Only new fields can be deleted here.
                    deletable = !field.existing,
                    onDelete = { rows.removeAt(index) },
                    onOpen = { editingIndex = index },
                )
            }

            OutlinedButton(
                onClick = {
                    rows.add(EditDraft())
                    editingIndex = rows.lastIndex
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add Field")
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

/**
 * A read-only summary of one field in the list. The whole card is tappable to
 * open the field editor; the reorder chevrons and the delete button handle their
 * own taps and don't trigger the card.
 */
@Composable
private fun FieldSummaryCard(
    field: EditDraft,
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    deletable: Boolean,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = field.label.ifBlank { "Field $number" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        field.summaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ReorderControls(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
                if (deletable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete field")
                    }
                }
            }
            field.validationHint()?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Full-screen editor for a single field, reached by tapping its card. It mirrors
 * the "New Log" field block, fully pre-filled. The top bar shows "Field N" with
 * **Save** across from it; Save commits the edit (the caller persists it) and
 * closes back to the list. Editing works on a copy, so backing out cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorScreen(
    source: EditDraft,
    number: Int,
    canSave: (EditDraft) -> Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = remember(source) { source.copy() }
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Field $number") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave(draft),
                        onClick = {
                            source.applyFrom(draft)
                            // Auto-capitalize only newly added fields; existing
                            // fields are left as-is so their entries aren't re-keyed.
                            if (!source.existing) {
                                if (settings.autoCapitalizeLabels) {
                                    source.label = TitleCase.apply(source.label)
                                }
                                val hasOptions = source.type == FieldType.DROPDOWN ||
                                    source.type == FieldType.MULTIPLE
                                if (settings.autoCapitalizeOptions && hasOptions) {
                                    source.optionsText = TitleCase.applyLines(source.optionsText)
                                }
                            }
                            onSave()
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
            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft.label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.existing) {
                // Type is locked for existing fields so stored values stay valid.
                Text(
                    "Type: ${draft.type.editFriendly()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                EditTypeDropdown(selected = draft.type, onSelected = { draft.type = it })
            }

            SettingsControls(draft)
            RequiredRow(draft)

            draft.validationHint()?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
            label = "Lines (Height, Optional)",
        )
        FieldType.NUMBER -> EditNumberField(
            value = field.digits,
            onChange = { field.digits = it },
            label = "Max Digits (Optional)",
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
            label = { Text("Options (One per Line)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        FieldType.DATETIME -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = field.defaultNow, onCheckedChange = { field.defaultNow = it })
            Text("Default to the Current Date & Time")
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
    /** True for a field that already exists in the saved schema (type locked). */
    val existing: Boolean = false,
    /** The label this field was loaded with, for re-keying entries on rename. */
    originalLabel: String? = null,
    /** The options this field was loaded with, for re-keying entries on rename. */
    originalOptions: List<String> = emptyList(),
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

    // Baselines for rename detection; realigned after each save.
    var originalLabel by mutableStateOf(originalLabel)
    var originalOptions by mutableStateOf(originalOptions)

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

    /** A short summary of the field's type and settings for the list card. */
    fun summaryText(): String {
        val base = when (type) {
            FieldType.TEXT -> "Text (one line)"
            FieldType.MULTILINE -> "Text (multi-line)" + (lines.toIntOrNull()?.let { " · $it lines" } ?: "")
            FieldType.NUMBER -> "Number" + (digits.toIntOrNull()?.let { " · up to $it digits" } ?: "")
            FieldType.DROPDOWN -> "Dropdown" + optionsSummary()
            FieldType.MULTIPLE -> "Multiple" + optionsSummary()
            FieldType.SCALE -> "Scale ${from.ifBlank { "?" }}–${to.ifBlank { "?" }}"
            FieldType.YESNO -> "Yes / No / Unknown / N/A"
            FieldType.DATE -> "Date"
            FieldType.TIME -> "Time"
            FieldType.DATETIME -> "Date & time" + (if (defaultNow) " · defaults to now" else "")
        }
        return if (required) "$base · required" else base
    }

    private fun optionsSummary(): String {
        val opts = optionList()
        return if (opts.isEmpty()) "" else " · " + opts.joinToString(", ")
    }

    /** A detached duplicate so the field editor can cancel without side effects. */
    fun copy(): EditDraft = EditDraft(
        label = label,
        type = type,
        required = required,
        lines = lines,
        digits = digits,
        from = from,
        to = to,
        optionsText = optionsText,
        defaultNow = defaultNow,
        existing = existing,
        originalLabel = originalLabel,
        originalOptions = originalOptions,
    )

    /** Copy the editable values from [other] onto this draft (identity kept). */
    fun applyFrom(other: EditDraft) {
        label = other.label
        type = other.type
        required = other.required
        lines = other.lines
        digits = other.digits
        from = other.from
        to = other.to
        optionsText = other.optionsText
        defaultNow = other.defaultNow
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

/** Seed an [EditDraft] from a saved field (marked existing — type locked). */
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
    originalLabel = f.label,
    originalOptions = f.options,
)

/**
 * Pair up options that were renamed between [old] and [new]. An option present in
 * one list but not the other is treated as removed/added; when the counts of
 * removed and added match, they pair up in order (the common case being a single
 * spelling fix). A pure reorder or a bare add/remove yields no renames, so stored
 * values are never mis-mapped.
 */
private fun optionRenameMap(old: List<String>, new: List<String>): Map<String, String> {
    val removed = old.filter { it !in new }
    val added = new.filter { it !in old }
    return if (removed.isNotEmpty() && removed.size == added.size) {
        removed.zip(added).toMap()
    } else {
        emptyMap()
    }
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
