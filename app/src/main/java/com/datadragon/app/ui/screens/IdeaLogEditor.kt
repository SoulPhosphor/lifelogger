package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datadragon.app.data.DEFAULT_IDEA_LINES
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaFieldKind
import com.datadragon.app.data.MAX_IDEA_LINES
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.data.ideaLineCount
import com.datadragon.app.data.sortEligible
import com.datadragon.app.ui.components.AppButton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableColumn
import java.util.UUID

/** The default fields "Use Default Fields" fills in, in the order they belong. */
private val DEFAULT_FIELD_KINDS = listOf(
    IdeaFieldKind.TITLE to "Title",
    IdeaFieldKind.TEXT to "Text",
    IdeaFieldKind.TAGS to "Tags",
)

/**
 * Mutable, Compose-observable editing state for one Idea field.
 *
 * [id] is the field's permanent identity — carried through an edit unchanged, so
 * renaming or reordering a field never disconnects it from its stored values.
 */
class IdeaDraftField(
    val id: String = UUID.randomUUID().toString(),
    label: String = "",
    kind: IdeaFieldKind = IdeaFieldKind.TEXT,
    required: Boolean = false,
    includeInPreview: Boolean = true,
    optionsText: String = "",
    lines: String = "",
    allowOrderFiltering: Boolean = false,
    sortByTimestamp: Boolean = false,
    wordCount: Boolean = false,
    characterCount: Boolean = false,
    allowCopying: Boolean = false,
    truncateInEntireCard: Boolean = false,
    entireCardLines: String = DEFAULT_IDEA_LINES.toString(),
) {
    var label by mutableStateOf(label)
    var kind by mutableStateOf(kind)
    var required by mutableStateOf(required)
    var includeInPreview by mutableStateOf(includeInPreview)
    var optionsText by mutableStateOf(optionsText)
    var lines by mutableStateOf(lines)
    var allowOrderFiltering by mutableStateOf(allowOrderFiltering)
    var sortByTimestamp by mutableStateOf(sortByTimestamp)
    var wordCount by mutableStateOf(wordCount)
    var characterCount by mutableStateOf(characterCount)
    var allowCopying by mutableStateOf(allowCopying)
    var truncateInEntireCard by mutableStateOf(truncateInEntireCard)
    var entireCardLines by mutableStateOf(entireCardLines)

    fun optionList(): List<String> =
        optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun isValid(): Boolean = when {
        label.trim().isEmpty() -> false
        kind == IdeaFieldKind.CATEGORIES -> optionList().isNotEmpty()
        else -> true
    }

    fun validationHint(): String? = when {
        label.trim().isEmpty() -> "Add a label."
        kind == IdeaFieldKind.CATEGORIES && optionList().isEmpty() -> "Add at least one option."
        else -> null
    }

    fun toFieldDef(): IdeaFieldDef = IdeaFieldDef(
        id = id,
        label = label.trim(),
        type = kind.fieldType,
        required = required,
        includeInPreview = includeInPreview,
        options = if (kind == IdeaFieldKind.CATEGORIES) optionList() else emptyList(),
        lines = if (kind == IdeaFieldKind.TEXT) lines.toIntOrNull() else null,
        allowOrderFiltering = kind.fieldType.sortEligible && allowOrderFiltering,
        wordCount = kind == IdeaFieldKind.TEXT && wordCount,
        characterCount = kind == IdeaFieldKind.TEXT && characterCount,
        allowCopying = kind == IdeaFieldKind.TEXT && allowCopying,
        truncateInEntireCard = kind == IdeaFieldKind.TEXT && truncateInEntireCard,
        entireCardLines = ideaLineCount(entireCardLines),
    )

    fun toSnapshot(): IdeaDraftSnapshot = IdeaDraftSnapshot(
        id = id,
        label = label,
        kind = kind,
        required = required,
        includeInPreview = includeInPreview,
        optionsText = optionsText,
        lines = lines,
        allowOrderFiltering = allowOrderFiltering,
        sortByTimestamp = sortByTimestamp,
        wordCount = wordCount,
        characterCount = characterCount,
        allowCopying = allowCopying,
        truncateInEntireCard = truncateInEntireCard,
        entireCardLines = entireCardLines,
    )
}

/** Plain serializable snapshot of an [IdeaDraftField], for [ideaFieldsSaver]. */
@Serializable
data class IdeaDraftSnapshot(
    val id: String,
    val label: String,
    val kind: IdeaFieldKind,
    val required: Boolean,
    val includeInPreview: Boolean,
    val optionsText: String,
    val lines: String,
    val allowOrderFiltering: Boolean,
    val sortByTimestamp: Boolean,
    val wordCount: Boolean,
    val characterCount: Boolean,
    val allowCopying: Boolean,
    val truncateInEntireCard: Boolean,
    val entireCardLines: String,
)

fun IdeaDraftSnapshot.toDraft(): IdeaDraftField = IdeaDraftField(
    id = id,
    label = label,
    kind = kind,
    required = required,
    includeInPreview = includeInPreview,
    optionsText = optionsText,
    lines = lines,
    allowOrderFiltering = allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    wordCount = wordCount,
    characterCount = characterCount,
    allowCopying = allowCopying,
    truncateInEntireCard = truncateInEntireCard,
    entireCardLines = entireCardLines,
)

/** Seed a draft from a saved field, keeping its permanent id. */
fun IdeaFieldDef.toDraft(sortByTimestamp: Boolean): IdeaDraftField = IdeaDraftField(
    id = id,
    label = label,
    kind = IdeaFieldKind.of(type),
    required = required,
    includeInPreview = includeInPreview,
    optionsText = options.joinToString("\n"),
    lines = lines?.toString() ?: "",
    allowOrderFiltering = allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    wordCount = wordCount,
    characterCount = characterCount,
    allowCopying = allowCopying,
    truncateInEntireCard = truncateInEntireCard,
    entireCardLines = entireCardLines.toString(),
)

/** Saves the in-progress field list so it survives a background process kill. */
val ideaFieldsSaver = Saver<SnapshotStateList<IdeaDraftField>, String>(
    save = { list -> Json.encodeToString(list.map { it.toSnapshot() }) },
    restore = { text ->
        mutableStateListOf<IdeaDraftField>().apply {
            addAll(Json.decodeFromString<List<IdeaDraftSnapshot>>(text).map { it.toDraft() })
        }
    },
)

/**
 * The one field every new Idea Log starts with: a Text (Multiline) field, there
 * whether or not Use Default Fields is switched on. It is also the default Text
 * field, so switching Use Default Fields on never adds a second one.
 */
fun startingIdeaFields(): SnapshotStateList<IdeaDraftField> =
    mutableStateListOf(IdeaDraftField(label = "Text", kind = IdeaFieldKind.TEXT))

/**
 * Fill in whichever of Title, Text and Tags this log doesn't already have,
 * leaving every existing field exactly where it is. Each missing default lands
 * next to the defaults that are already present, so the three read in their
 * intended order.
 */
fun addMissingDefaultFields(fields: SnapshotStateList<IdeaDraftField>) {
    DEFAULT_FIELD_KINDS.forEachIndexed { position, (kind, label) ->
        if (fields.any { it.kind == kind }) return@forEachIndexed
        val laterKinds = DEFAULT_FIELD_KINDS.drop(position + 1).map { it.first }
        val earlierKinds = DEFAULT_FIELD_KINDS.take(position).map { it.first }
        val insertAt = fields.indexOfFirst { it.kind in laterKinds }
            .takeIf { it >= 0 }
            ?: fields.indexOfLast { it.kind in earlierKinds }.takeIf { it >= 0 }?.plus(1)
            ?: fields.size
        fields.add(insertAt, IdeaDraftField(label = label, kind = kind))
    }
}

/** Which single field, if any, is the log's default sort timestamp. */
fun sortTimestampFieldIdOf(fields: List<IdeaDraftField>): String? =
    fields.firstOrNull { it.sortByTimestamp && it.kind.fieldType.sortEligible }?.id

/**
 * The shared body of "New Idea Log" and "Edit Idea Log". Both screens show the
 * same settings in the same order and the same field builder — only the title,
 * the Save action and how the state was seeded differ.
 *
 * There is no Paste tab here: Idea Logs are built visually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaLogEditorScaffold(
    screenTitle: String,
    name: String,
    onNameChange: (String) -> Unit,
    automaticTimestamping: Boolean,
    onAutomaticTimestampingChange: (Boolean) -> Unit,
    allowArchiving: Boolean,
    onAllowArchivingChange: (Boolean) -> Unit,
    showEntireIdeaCard: Boolean,
    onShowEntireIdeaCardChange: (Boolean) -> Unit,
    previewLines: String,
    onPreviewLinesChange: (String) -> Unit,
    useDefaultFields: Boolean,
    onUseDefaultFieldsChange: (Boolean) -> Unit,
    sortNewestFirst: Boolean,
    onSortDirectionChange: (Boolean) -> Unit,
    fields: SnapshotStateList<IdeaDraftField>,
    canSave: Boolean,
    dirty: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    var pendingSortReplacement by remember {
        mutableStateOf<Pair<IdeaDraftField, IdeaDraftField>?>(null)
    }

    fun setSort(field: IdeaDraftField, checked: Boolean) {
        if (!checked) {
            field.sortByTimestamp = false
            return
        }
        field.sortByTimestamp = true
        fields.firstOrNull { it !== field && it.sortByTimestamp }?.let { existing ->
            pendingSortReplacement = field to existing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(enabled = canSave, onClick = onSave) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Shrink the scroll viewport to the space above the on-screen
                // keyboard so a focused field is never hidden behind it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Idea Log Name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SettingSwitchRow(
                checked = automaticTimestamping,
                onCheckedChange = onAutomaticTimestampingChange,
                title = "Automatic Timestamping",
            )
            SettingSwitchRow(
                checked = allowArchiving,
                onCheckedChange = onAllowArchivingChange,
                title = "Allow Archiving",
            )
            SettingSwitchRow(
                checked = showEntireIdeaCard,
                onCheckedChange = onShowEntireIdeaCardChange,
                title = "Show Entire Idea Card",
            )
            LineCountRow(
                title = "Number of Lines Shown in Preview Mode (#)",
                value = previewLines,
                onChange = onPreviewLinesChange,
            )
            SettingSwitchRow(
                checked = useDefaultFields,
                onCheckedChange = onUseDefaultFieldsChange,
                title = "Use Default Fields",
            )

            Text("Fields", style = MaterialTheme.typography.labelLarge)
            // ReorderableColumn sizes its internal per-item state to the list once,
            // keyed only on the list *reference* — mutating this SnapshotStateList in
            // place (adding a field, or filling in the default fields) never rebuilds
            // it, so the extra rows would index past that stale state and crash.
            // Keying on the field count rebuilds it whenever the list grows or shrinks;
            // drag-reordering keeps the count the same, so it is left untouched.
            key(fields.size) {
                ReorderableColumn(
                    list = fields,
                    onSettle = { from, to -> fields.add(to, fields.removeAt(from)) },
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { index, field, _ ->
                    IdeaFieldCard(
                        field = field,
                        index = index,
                        sortNewestFirst = sortNewestFirst,
                        onSortChanged = { setSort(field, it) },
                        onSortDirectionChange = onSortDirectionChange,
                        onDelete = { fields.remove(field) },
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                }
            }

            AppButton(
                onClick = { fields.add(IdeaDraftField()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add Field")
            }
        }
    }

    pendingSortReplacement?.let { (newField, existingField) ->
        AlertDialog(
            onDismissRequest = {
                newField.sortByTimestamp = false
                pendingSortReplacement = null
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
                    pendingSortReplacement = null
                }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    existingField.sortByTimestamp = false
                    pendingSortReplacement = null
                }) { Text("Okay") }
            },
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

/**
 * A labelled numeric box on one row: the setting on the left, a short digits-only
 * field on the right. Three digits at most, so nothing beyond 999 can be typed.
 */
@Composable
private fun LineCountRow(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(3)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
    }
}

/** One field's editor card in the Idea field builder. */
@Composable
private fun IdeaFieldCard(
    field: IdeaDraftField,
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

            IdeaKindDropdown(selected = field.kind, onSelected = { kind ->
                field.kind = kind
                // Only a date-bearing field can take part in ordering.
                if (!kind.fieldType.sortEligible) {
                    field.allowOrderFiltering = false
                    field.sortByTimestamp = false
                }
            })

            when (field.kind) {
                IdeaFieldKind.CATEGORIES -> OutlinedTextField(
                    value = field.optionsText,
                    onValueChange = { field.optionsText = it },
                    label = { Text("Options (One per Line)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                IdeaFieldKind.TEXT -> MultilineFieldSettings(field)
                else -> Unit
            }

            if (field.kind.fieldType.sortEligible) {
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
            CheckboxSettingRow(
                checked = field.includeInPreview,
                onCheckedChange = { field.includeInPreview = it },
                title = "Include in Preview Mode",
            )

            field.validationHint()?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The settings only a Text (Multiline) field in an Idea Log gets. */
@Composable
private fun MultilineFieldSettings(field: IdeaDraftField) {
    OutlinedTextField(
        value = field.lines,
        onValueChange = { input -> field.lines = input.filter { it.isDigit() }.take(3) },
        label = { Text("Lines (Height, Optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    CheckboxSettingRow(
        checked = field.wordCount,
        onCheckedChange = { field.wordCount = it },
        title = "Word Count",
    )
    CheckboxSettingRow(
        checked = field.characterCount,
        onCheckedChange = { field.characterCount = it },
        title = "Character Count",
    )
    CheckboxSettingRow(
        checked = field.allowCopying,
        onCheckedChange = { field.allowCopying = it },
        title = "Allow Copying",
    )
    CheckboxSettingRow(
        checked = field.truncateInEntireCard,
        onCheckedChange = { field.truncateInEntireCard = it },
        title = "Allow Truncation in Entire Idea Card Mode",
    )
    if (field.truncateInEntireCard) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Only Show", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = field.entireCardLines,
                onValueChange = { input ->
                    field.entireCardLines = input.filter { it.isDigit() }.take(3)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Lines", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaKindDropdown(selected: IdeaFieldKind, onSelected: (IdeaFieldKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IdeaFieldKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.label) },
                    onClick = {
                        onSelected(kind)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Title-case a newly created field's label and its Categories options, per the
 * app-wide auto-capitalize settings — the same treatment the Forms builder gives
 * a new field. An already-saved field is never put through this.
 */
internal fun IdeaFieldDef.autoCapitalized(settings: SettingsRepository): IdeaFieldDef = copy(
    label = if (settings.autoCapitalizeLabels) TitleCase.apply(label) else label,
    options = if (settings.autoCapitalizeOptions) options.map { TitleCase.apply(it) } else options,
)

/** Guard so a typed line count can never exceed the accepted maximum on save. */
internal fun clampedLineCount(raw: String): Int =
    ideaLineCount(raw).coerceAtMost(MAX_IDEA_LINES)
