package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.preselectedMultipleChoices
import com.datadragon.app.data.preselectedSingleChoice
import com.datadragon.app.data.MAX_RATIO_DIGITS
import com.datadragon.app.data.RatioValues
import com.datadragon.app.data.WebAddress
import com.datadragon.app.ui.NewEntryViewModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import com.datadragon.app.ui.theme.AppTheme

/**
 * Saves the in-progress form as a JSON string in the instance-state Bundle, so
 * typed-but-unsaved values survive the process being killed while backgrounded
 * (not just configuration changes) — otherwise a form filled in, then lost to a
 * background kill, would silently come back blank.
 */
private val textValuesSaver = Saver<SnapshotStateMap<String, String>, String>(
    save = { map -> Json.encodeToString(map.toMap()) },
    restore = { text ->
        val decoded: Map<String, String> = Json.decodeFromString(text)
        mutableStateMapOf<String, String>().apply { putAll(decoded) }
    },
)

private val multiValuesSaver = Saver<SnapshotStateMap<String, Set<String>>, String>(
    save = { map -> Json.encodeToString(map.toMap()) },
    restore = { text ->
        val decoded: Map<String, Set<String>> = Json.decodeFromString(text)
        mutableStateMapOf<String, Set<String>>().apply { putAll(decoded) }
    },
)

private val tagValuesSaver = Saver<SnapshotStateMap<String, List<String>>, String>(
    save = { map -> Json.encodeToString(map.toMap()) },
    restore = { text ->
        val decoded: Map<String, List<String>> = Json.decodeFromString(text)
        mutableStateMapOf<String, List<String>>().apply { putAll(decoded) }
    },
)

private val baselineSaver = Saver<Map<String, JsonElement>?, String>(
    save = { it?.let(EntryValues::encode) ?: "" },
    restore = { text -> if (text.isEmpty()) null else EntryValues.decode(text) },
)

/**
 * New Entry screen (Phase 4).
 *
 * A full-screen [Scaffold] destination — never a dialog or bottom sheet (locked
 * by docs/UI_SPEC.md §6). The body is generated from the log's field
 * definitions; the timestamp auto-fills and Save writes a real entry.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewEntryScreen(
    logId: String?,
    onBack: () -> Unit,
    entryId: String? = null,
    viewModel: NewEntryViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    val editEntryId = entryId?.toLongOrNull()
    val isEditing = editEntryId != null
    LaunchedEffect(id, editEntryId) { id?.let { viewModel.load(it, editEntryId) } }

    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val initialValues by viewModel.initialValues.collectAsStateWithLifecycle()
    val allowAdditionsFromForm by viewModel.allowAdditionsFromForm.collectAsStateWithLifecycle()
    // The Dropdown/Multiple field whose Add Option pop-up is open, by field id.
    var addingOptionTo by rememberSaveable { mutableStateOf<String?>(null) }

    val timestamp = remember {
        LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.getDefault())
        )
    }

    // Form state: single-valued fields (and date/time/scale as strings) in one
    // map, multi-select fields in the other, plus the free-text Notes box.
    val textValues = rememberSaveable(saver = textValuesSaver) { mutableStateMapOf() }
    val multiValues = rememberSaveable(saver = multiValuesSaver) { mutableStateMapOf() }
    val tagValues = rememberSaveable(saver = tagValuesSaver) { mutableStateMapOf() }
    var notes by rememberSaveable { mutableStateOf("") }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val fieldTargets = remember(fields.map { it.id to it.type }) {
        fields.associate { field ->
            field.id to FieldValidationTarget(
                bringIntoViewRequester = BringIntoViewRequester(),
                focusRequester = if (field.supportsDirectFocus()) FocusRequester() else null,
            )
        }
    }

    // When editing, pre-fill the form from the entry's stored values once loaded.
    // Keyed on the fields' ids and types, not their options, so adding an
    // option from this form never overwrites what has already been changed.
    LaunchedEffect(initialValues, fields.map { it.id to it.type }) {
        val values = initialValues ?: return@LaunchedEffect
        fields.forEach { field ->
            when (field.type) {
                FieldType.MULTIPLE -> {
                    val selected = EntryValues.selectedOptions(values, field)
                    if (selected.isNotEmpty()) multiValues[field.id] = selected
                }
                FieldType.TAGS -> {
                    val tags = EntryValues.stringList(values, field)
                    if (tags.isNotEmpty()) tagValues[field.id] = tags
                }
                else -> EntryValues.rawValue(values, field)?.let { textValues[field.id] = it }
            }
        }
        EntryValues.notes(values)?.let { notes = it }
    }

    // Pre-select the options marked pre-selected (any number for Multiple
    // Choice, at most one for Single Choice), for new entries only. The map
    // check prevents this initialization from restoring a choice after the user
    // has deliberately deselected every option.
    LaunchedEffect(fields, isEditing) {
        if (isEditing) return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.MULTIPLE && multiValues[field.id] == null) {
                val preselected = field.preselectedMultipleChoices()
                if (preselected.isNotEmpty()) multiValues[field.id] = preselected
            }
            if (field.type == FieldType.DROPDOWN && textValues[field.id] == null) {
                field.preselectedSingleChoice()?.let { textValues[field.id] = it }
            }
        }
    }

    // Pre-fill any `datetime` field flagged `default: now` with the current time
    // (new entries only — when editing, the stored value is loaded above instead).
    LaunchedEffect(fields, isEditing) {
        if (isEditing) return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.DATETIME && field.defaultNow && textValues[field.id] == null) {
                textValues[field.id] = LocalDateTime.now()
                    .withSecond(0)
                    .withNano(0)
                    .format(EntryValues.DATETIME_STORAGE)
            }
        }
    }

    // Snapshot the entry's values once loaded, so backing out can warn only when
    // something was actually entered or changed since. Guarded on baseline still
    // being null so a baseline restored after a process death isn't clobbered by
    // this effect re-running against the now-restored (already-dirty) form state.
    var baseline by rememberSaveable(stateSaver = baselineSaver) { mutableStateOf<Map<String, JsonElement>?>(null) }
    LaunchedEffect(fields, initialValues) {
        if (baseline != null) return@LaunchedEffect
        if (fields.isEmpty()) return@LaunchedEffect
        if (isEditing && initialValues == null) return@LaunchedEffect
        baseline = collectValues(fields, textValues, multiValues, tagValues, notes)
    }

    val missingRequiredIds = missingRequiredFieldIds(fields, textValues, multiValues, tagValues)

    val dirty = baseline != null &&
        collectValues(fields, textValues, multiValues, tagValues, notes) != baseline
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Entry" else "New Entry") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = fields.isNotEmpty(),
                        onClick = {
                            if (missingRequiredIds.isEmpty()) {
                                viewModel.save(
                                    values = collectValues(fields, textValues, multiValues, tagValues, notes),
                                    onSaved = onBack,
                                )
                            } else {
                                validationAttempted = true
                                fieldTargets[missingRequiredIds.first()]?.let { target ->
                                    coroutineScope.launch {
                                        target.focusRequester?.requestFocus()
                                        withFrameNanos { }
                                        target.bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
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
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.screenInset),
        ) {
            Text(
                text = if (isEditing) {
                    "Editing entry · its original date & time is kept"
                } else {
                    "Date / time:  $timestamp (auto)"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            fields.forEach { field ->
                val target = fieldTargets[field.id]
                EntryFieldControl(
                    field = field,
                    textValues = textValues,
                    multiValues = multiValues,
                    tagValues = tagValues,
                    showRequiredError = validationAttempted && field.id in missingRequiredIds,
                    bringIntoViewRequester = target?.bringIntoViewRequester,
                    focusRequester = target?.focusRequester,
                    onAddOption = if (allowAdditionsFromForm) {
                        { addingOptionTo = it.id }
                    } else {
                        null
                    },
                )
            }

            HorizontalDivider()

            Text("Notes", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = notes,
                accessibleLabel = "Notes",
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.notesMinHeight),
            )
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }

    fields.firstOrNull { it.id == addingOptionTo }?.let { field ->
        AddOptionDialog(
            field = field,
            onCancel = { addingOptionTo = null },
            onConfirm = { additions ->
                viewModel.addOptions(field.id, additions)
                addingOptionTo = null
            },
            onRestore = { option -> viewModel.restoreOption(field.id, option.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private data class FieldValidationTarget(
    val bringIntoViewRequester: BringIntoViewRequester,
    val focusRequester: FocusRequester?,
)

private fun com.datadragon.app.data.FieldDef.supportsDirectFocus(): Boolean =
    type == FieldType.TEXT || type == FieldType.MULTILINE || type == FieldType.NUMBER ||
        type == FieldType.TAGS || type == FieldType.WEBPAGE

/**
 * Fields that block Save, in visible form order: a required field with nothing
 * submitted, plus any `webpage` field holding something that isn't a valid
 * webpage address — blank is fine when the field is optional, but text that
 * isn't an address is never storable as one.
 */
internal fun missingRequiredFieldIds(
    fields: List<com.datadragon.app.data.FieldDef>,
    textValues: Map<String, String>,
    multiValues: Map<String, Set<String>>,
    tagValues: Map<String, List<String>> = emptyMap(),
): List<String> = fields.mapNotNull { field ->
    val value = textValues[field.id]
    val missing = when {
        field.type == FieldType.WEBPAGE ->
            if (value.isNullOrBlank()) field.required else !WebAddress.isValid(value)
        field.type == FieldType.BLOOD_PRESSURE ->
            !bloodPressureAcceptable(value, field.required)
        field.type == FieldType.RATIO ->
            !RatioValues.acceptable(
                value,
                field.required,
                field.leftDigits ?: MAX_RATIO_DIGITS,
                field.rightDigits ?: MAX_RATIO_DIGITS,
            )
        !field.required -> false
        field.type == FieldType.MULTIPLE -> multiValues[field.id].orEmpty().isEmpty()
        field.type == FieldType.TAGS -> tagValues[field.id].orEmpty().isEmpty()
        else -> value.isNullOrBlank()
    }
    field.id.takeIf { missing }
}

/**
 * A blood-pressure value blocks Save unless both sides carry a 1–3 digit number,
 * or (when the field is optional) both sides are completely blank.
 */
private fun bloodPressureAcceptable(value: String?, required: Boolean): Boolean {
    val raw = value.orEmpty()
    if (raw.isEmpty()) return !required
    val slash = raw.indexOf('/')
    if (slash < 0) return false
    val systolic = raw.substring(0, slash)
    val diastolic = raw.substring(slash + 1)
    val bothBlank = systolic.isEmpty() && diastolic.isEmpty()
    if (bothBlank) return !required
    val validSide: (String) -> Boolean = { it.length in 1..3 && it.all(Char::isDigit) }
    return validSide(systolic) && validSide(diastolic)
}

/** Build the value map that gets serialized into the entry's valuesJson. */
internal fun collectValues(
    fields: List<com.datadragon.app.data.FieldDef>,
    textValues: Map<String, String>,
    multiValues: Map<String, Set<String>>,
    tagValues: Map<String, List<String>>,
    notes: String,
): Map<String, JsonElement> = buildMap {
    fields.forEach { field ->
        when (field.type) {
            FieldType.MULTIPLE -> {
                // Store option ids in the schema's option order; an id the
                // schema doesn't know is kept rather than dropped.
                val selected = multiValues[field.id].orEmpty()
                val known = field.choices.map { it.id }.filter { it in selected }
                val ordered = known + selected.filter { it !in known }
                if (ordered.isNotEmpty()) put(field.id, EntryValues.stringArray(ordered))
            }
            FieldType.TAGS -> {
                // Tags keep the order they were added in.
                val tags = tagValues[field.id].orEmpty()
                if (tags.isNotEmpty()) put(field.id, EntryValues.stringArray(tags))
            }
            else -> {
                val value = textValues[field.id]?.trim().orEmpty()
                if (value.isNotEmpty()) put(field.id, EntryValues.string(value))
            }
        }
    }
    val trimmedNotes = notes.trim()
    if (trimmedNotes.isNotEmpty()) put(EntryValues.NOTES_KEY, EntryValues.string(trimmedNotes))
}
