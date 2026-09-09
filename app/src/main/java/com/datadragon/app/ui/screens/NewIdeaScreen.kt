package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaValues
import com.datadragon.app.data.WebAddress
import com.datadragon.app.ui.NewIdeaViewModel
import com.datadragon.app.ui.components.CountsLine
import com.datadragon.app.ui.components.TagsEditor
import com.datadragon.app.ui.components.WebpageEntryField
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ideaTextSaver = Saver<SnapshotStateMap<String, String>, String>(
    save = { map -> Json.encodeToString(map.toMap()) },
    restore = { text ->
        val decoded: Map<String, String> = Json.decodeFromString(text)
        mutableStateMapOf<String, String>().apply { putAll(decoded) }
    },
)

private val ideaTagsSaver = Saver<SnapshotStateMap<String, List<String>>, String>(
    save = { map -> Json.encodeToString(map.toMap()) },
    restore = { text ->
        val decoded: Map<String, List<String>> = Json.decodeFromString(text)
        mutableStateMapOf<String, List<String>>().apply { putAll(decoded) }
    },
)

private val ideaBaselineSaver = Saver<Map<String, JsonElement>?, String>(
    save = { it?.let(IdeaValues::encode) ?: "" },
    restore = { text -> if (text.isEmpty()) null else IdeaValues.decode(text) },
)

/**
 * New Idea / Edit Idea. The body is generated from the Idea Log's configured
 * fields, in the Forms entry-screen style.
 *
 * Ideas are always editable, so this same screen handles both cases: saving an
 * edit updates the existing idea rather than creating a second one. Required
 * validation runs on both.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewIdeaScreen(
    ideaLogId: String?,
    onBack: () -> Unit,
    ideaId: String? = null,
    viewModel: NewIdeaViewModel = viewModel(),
) {
    val logId = ideaLogId?.toLongOrNull()
    val editIdeaId = ideaId?.toLongOrNull()
    val isEditing = editIdeaId != null
    LaunchedEffect(logId, editIdeaId) { logId?.let { viewModel.load(it, editIdeaId) } }

    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val initialValues by viewModel.initialValues.collectAsStateWithLifecycle()
    val automaticTimestamping by viewModel.automaticTimestamping.collectAsStateWithLifecycle()

    val timestamp = remember {
        LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.getDefault())
        )
    }

    // Form state, keyed by each field's permanent id rather than its label.
    val textValues = rememberSaveable(saver = ideaTextSaver) { mutableStateMapOf() }
    val tagValues = rememberSaveable(saver = ideaTagsSaver) { mutableStateMapOf() }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val fieldTargets = remember(fields.map { it.id to it.type }) {
        fields.associate { field ->
            field.id to IdeaValidationTarget(
                bringIntoViewRequester = BringIntoViewRequester(),
                focusRequester = if (field.supportsDirectFocus()) FocusRequester() else null,
            )
        }
    }

    // When editing, pre-fill from the idea's stored values once loaded.
    LaunchedEffect(initialValues, fields) {
        val values = initialValues ?: return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.TAGS) {
                val tags = IdeaValues.tags(values, field)
                if (tags.isNotEmpty()) tagValues[field.id] = tags
            } else {
                IdeaValues.rawValue(values, field)?.let { textValues[field.id] = it }
            }
        }
    }

    // Snapshot once loaded so backing out warns only when something changed.
    var baseline by rememberSaveable(stateSaver = ideaBaselineSaver) {
        mutableStateOf<Map<String, JsonElement>?>(null)
    }
    LaunchedEffect(fields, initialValues) {
        if (baseline != null) return@LaunchedEffect
        if (fields.isEmpty()) return@LaunchedEffect
        if (isEditing && initialValues == null) return@LaunchedEffect
        baseline = collectIdeaValues(fields, textValues, tagValues)
    }

    val blockingFieldIds = blockingIdeaFieldIds(fields, textValues, tagValues)
    val dirty = baseline != null && collectIdeaValues(fields, textValues, tagValues) != baseline
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Idea" else "New Idea") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = fields.isNotEmpty(),
                        onClick = {
                            if (blockingFieldIds.isEmpty()) {
                                viewModel.save(
                                    values = collectIdeaValues(fields, textValues, tagValues),
                                    onSaved = onBack,
                                )
                            } else {
                                // Bring the first field that still needs attention
                                // into view rather than just refusing to save.
                                validationAttempted = true
                                fieldTargets[blockingFieldIds.first()]?.let { target ->
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (automaticTimestamping) {
                Text(
                    text = if (isEditing) {
                        "Editing idea · its original date & time is kept"
                    } else {
                        "Date / time:  $timestamp (auto)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            fields.forEach { field ->
                val target = fieldTargets[field.id]
                IdeaFieldControl(
                    field = field,
                    textValues = textValues,
                    tagValues = tagValues,
                    showError = validationAttempted && field.id in blockingFieldIds,
                    bringIntoViewRequester = target?.bringIntoViewRequester,
                    focusRequester = target?.focusRequester,
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

@OptIn(ExperimentalFoundationApi::class)
private data class IdeaValidationTarget(
    val bringIntoViewRequester: BringIntoViewRequester,
    val focusRequester: FocusRequester?,
)

private fun IdeaFieldDef.supportsDirectFocus(): Boolean =
    type == FieldType.TEXT || type == FieldType.MULTILINE ||
        type == FieldType.TAGS || type == FieldType.WEBPAGE

/**
 * One generated entry control for an Idea field, reading and writing the shared
 * form state. Tags live in [tagValues] as an ordered list; everything else lives
 * in [textValues] under the field's permanent id.
 *
 * The date, time and date-and-time pickers are the Forms ones, reused as-is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdeaFieldControl(
    field: IdeaFieldDef,
    textValues: SnapshotStateMap<String, String>,
    tagValues: SnapshotStateMap<String, List<String>>,
    showError: Boolean,
    bringIntoViewRequester: BringIntoViewRequester?,
    focusRequester: FocusRequester?,
) {
    val label = if (field.required) "${field.label} *" else field.label
    val targetModifier = bringIntoViewRequester?.let {
        Modifier.fillMaxWidth().bringIntoViewRequester(it)
    } ?: Modifier
    val errorModifier = if (showError) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(8.dp),
        )
    } else {
        Modifier
    }

    Column(modifier = targetModifier.then(errorModifier)) {
        when (field.type) {
            // "Title" is the one-line text field, under the Ideas name for it.
            FieldType.TEXT -> Labeled(label) {
                OutlinedTextField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { textValues[field.id] = it },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
                )
            }

            FieldType.MULTILINE -> Labeled(label) {
                val text = textValues[field.id].orEmpty()
                val minHeight = ((field.lines ?: 4).coerceIn(2, 12) * 24).dp
                OutlinedTextField(
                    value = text,
                    onValueChange = { textValues[field.id] = it },
                    isError = showError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
                )
                // The counts track what's typed, so they update as you write.
                IdeaValues.countsLine(field, text)?.let { CountsLine(it) }
            }

            FieldType.TAGS -> Labeled(label) {
                TagsEditor(
                    tags = tagValues[field.id].orEmpty(),
                    onTagsChange = { tagValues[field.id] = it },
                    isError = showError,
                    focusRequester = focusRequester,
                )
            }

            // "Categories" is the single-choice dropdown, under the Ideas name.
            FieldType.DROPDOWN -> DropdownField(
                label = label,
                options = field.options,
                selected = textValues[field.id].orEmpty(),
                onSelected = { textValues[field.id] = it },
            )

            FieldType.WEBPAGE -> Labeled(label) {
                WebpageEntryField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { textValues[field.id] = it },
                    isError = showError,
                    focusRequester = focusRequester,
                )
            }

            FieldType.DATE -> DateField(
                label = label,
                stored = textValues[field.id],
                onChange = { textValues[field.id] = it },
            )

            FieldType.TIME -> TimeField(
                label = label,
                stored = textValues[field.id],
                onChange = { textValues[field.id] = it },
            )

            FieldType.DATETIME -> DateTimeField(
                label = label,
                stored = textValues[field.id],
                onChange = { textValues[field.id] = it },
            )

            // Not offered by the Ideas field picker.
            else -> Unit
        }
    }
}

/**
 * The fields that stop Save, in visible order: a required field with nothing in
 * it, plus any Webpages field holding text that isn't a valid webpage address.
 * Blank is fine for an optional Webpages field; prose is never storable as one.
 */
internal fun blockingIdeaFieldIds(
    fields: List<IdeaFieldDef>,
    textValues: Map<String, String>,
    tagValues: Map<String, List<String>>,
): List<String> = fields.mapNotNull { field ->
    val value = textValues[field.id]
    val blocked = when (field.type) {
        FieldType.WEBPAGE ->
            if (value.isNullOrBlank()) field.required else !WebAddress.isValid(value)
        FieldType.TAGS -> field.required && tagValues[field.id].orEmpty().isEmpty()
        else -> field.required && value.isNullOrBlank()
    }
    field.id.takeIf { blocked }
}

/** Build the value map serialized into an idea's valuesJson, keyed by field id. */
internal fun collectIdeaValues(
    fields: List<IdeaFieldDef>,
    textValues: Map<String, String>,
    tagValues: Map<String, List<String>>,
): Map<String, JsonElement> = buildMap {
    fields.forEach { field ->
        if (field.type == FieldType.TAGS) {
            val tags = tagValues[field.id].orEmpty()
            if (tags.isNotEmpty()) put(field.id, EntryValues.stringArray(tags))
        } else {
            val value = textValues[field.id]?.trim().orEmpty()
            if (value.isNotEmpty()) put(field.id, EntryValues.string(value))
        }
    }
}
