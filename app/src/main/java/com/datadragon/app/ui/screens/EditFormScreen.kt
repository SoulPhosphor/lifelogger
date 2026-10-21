package com.datadragon.app.ui.screens

import com.datadragon.app.ui.components.accessibleReorder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.components.DuplicateItemDialog
import com.datadragon.app.data.ChoiceEdits
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.StableUuid
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.hasOptionList
import com.datadragon.app.data.hasDuplicateLabels
import com.datadragon.app.ui.components.ChoiceEditorWordings
import com.datadragon.app.ui.components.ChoiceOptionsEditor
import com.datadragon.app.ui.components.ChoicePreselection
import com.datadragon.app.data.ChoiceStyle
import com.datadragon.app.data.ClickerIncrementDirection
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.RatioValues
import com.datadragon.app.data.AutoFill
import com.datadragon.app.data.AutoFill.autoFillable
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.sortEligible
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.ui.EditFormViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.datadragon.app.ui.theme.AppTheme

/**
 * Saves the in-progress field list as JSON in the instance-state Bundle, so an
 * unsaved schema edit (added/reordered/renamed fields) survives the process
 * being killed while backgrounded — otherwise it would silently reset to the
 * last-saved schema.
 */
private val rowsSaver = Saver<SnapshotStateList<EditDraft>, String>(
    save = { list -> Json.encodeToString(list.map { it.toSnapshot() }) },
    restore = { text ->
        mutableStateListOf<EditDraft>().apply {
            addAll(Json.decodeFromString<List<EditDraftSnapshot>>(text).map { it.toEditDraft() })
        }
    },
)

private val savedSnapshotSaver = Saver<List<FieldDef>?, String>(
    save = { it?.let { list -> Json.encodeToString(list) } ?: "" },
    restore = { text -> if (text.isEmpty()) null else Json.decodeFromString<List<FieldDef>>(text) },
)

/**
 * Edit a log's fields after creation. To keep stored entries valid, a field's
 * **type** can never change and existing fields can't be deleted. What you *can*
 * do: **add** new fields, **reorder** any field, and open a field to edit its
 * content — its label (spelling), options, and adjustable settings. Entries
 * store answers by each field's and option's permanent id, so renaming or
 * reordering never touches them. A Single / Multiple Choice field's options are
 * edited as rows ([ChoiceOptionsEditor]); permanently deleting an option there
 * is committed at once, separately from this screen's Save.
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
    onEditCalendar: () -> Unit,
    onOpenCalendar: (Long) -> Unit,
    viewModel: EditFormViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }

    val loadedFields by viewModel.fields.collectAsStateWithLifecycle()
    // Non-null once the template has loaded; gates Save so we never write an empty
    // schema before the existing fields have been read in.
    val name by viewModel.name.collectAsStateWithLifecycle()
    val loadedAutomaticTimestamping by viewModel.automaticTimestamping.collectAsStateWithLifecycle()
    val loadedSortTimestampFieldId by viewModel.sortTimestampFieldId.collectAsStateWithLifecycle()
    val loadedSortNewestFirst by viewModel.sortNewestFirst.collectAsStateWithLifecycle()
    val loadedIntegrateCalendar by viewModel.integrateCalendar.collectAsStateWithLifecycle()
    val loadedAdvancedSearchEnabled by viewModel.advancedSearchEnabled.collectAsStateWithLifecycle()
    val loadedAllowAdditionsFromForm by viewModel.allowAdditionsFromForm.collectAsStateWithLifecycle()
    val loadedLocked by viewModel.locked.collectAsStateWithLifecycle()
    val neverAllowUnlocking by viewModel.neverAllowUnlocking.collectAsStateWithLifecycle()
    val hasEntries by viewModel.hasEntries.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val savedComparisonDigits by viewModel.savedComparisonDigits.collectAsStateWithLifecycle()
    // The existing form title is editable alongside the field list and is saved
    // by this screen's existing Save action.
    var formTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var savedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var automaticTimestamping by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedAutomaticTimestamping by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var sortNewestFirst by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedSortNewestFirst by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var integrateCalendar by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedIntegrateCalendar by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var advancedSearchEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedAdvancedSearchEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var allowAdditionsFromForm by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedAllowAdditionsFromForm by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var locked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var savedLocked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(name) {
        if (formTitle == null && name != null) {
            formTitle = name
            savedTitle = name
        }
    }
    LaunchedEffect(name, loadedAutomaticTimestamping) {
        if (automaticTimestamping == null && name != null) {
            automaticTimestamping = loadedAutomaticTimestamping
            savedAutomaticTimestamping = loadedAutomaticTimestamping
        }
    }
    LaunchedEffect(name, loadedSortNewestFirst) {
        if (sortNewestFirst == null && name != null) {
            sortNewestFirst = loadedSortNewestFirst
            savedSortNewestFirst = loadedSortNewestFirst
        }
    }
    LaunchedEffect(name, loadedIntegrateCalendar) {
        if (integrateCalendar == null && name != null) {
            integrateCalendar = loadedIntegrateCalendar
            savedIntegrateCalendar = loadedIntegrateCalendar
        }
    }
    LaunchedEffect(name, loadedAdvancedSearchEnabled) {
        if (advancedSearchEnabled == null && name != null) {
            advancedSearchEnabled = loadedAdvancedSearchEnabled
            savedAdvancedSearchEnabled = loadedAdvancedSearchEnabled
        }
    }
    LaunchedEffect(name, loadedLocked) {
        if (locked == null && name != null) {
            locked = loadedLocked
            savedLocked = loadedLocked
        }
    }
    LaunchedEffect(name, loadedAllowAdditionsFromForm) {
        if (allowAdditionsFromForm == null && name != null) {
            allowAdditionsFromForm = loadedAllowAdditionsFromForm
            savedAllowAdditionsFromForm = loadedAllowAdditionsFromForm
        }
    }

    // Every field is an editable draft; existing ones are flagged so their type
    // stays locked. Seeded once from the loaded schema.
    val rows = rememberSaveable(saver = rowsSaver) { mutableStateListOf() }
    var seeded by rememberSaveable { mutableStateOf(false) }
    // The schema as last saved, so an unsaved reorder or added field can be caught
    // on the way out. Seeded from the round-tripped rows so a freshly loaded, untouched
    // form is never seen as "changed."
    var savedSnapshot by rememberSaveable(stateSaver = savedSnapshotSaver) { mutableStateOf<List<FieldDef>?>(null) }
    LaunchedEffect(loadedFields, loadedSortTimestampFieldId) {
        if (!seeded && loadedFields.isNotEmpty()) {
            rows.clear()
            loadedFields.forEach {
                rows.add(draftOf(it, sortByTimestamp = it.id == loadedSortTimestampFieldId))
            }
            savedSnapshot = rows.map { it.toFieldDef() }
            seeded = true
        }
    }

    // Which field's editor is open, or null for the list. Opening a field shows a
    // full-screen editor in place of the list.
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val canSave = name != null && formTitle != null && automaticTimestamping != null &&
        sortNewestFirst != null && integrateCalendar != null && allowAdditionsFromForm != null && advancedSearchEnabled != null &&
        locked != null && rows.all { it.isValid() }

    // Fields whose "Fill Existing Cards One Time" is on: an
    // instruction for the next Save only, never a stored setting. It is cleared
    // once saved, and leaving without saving converts nothing.
    fun pendingConversionIds(): Set<String> =
        if (hasEntries) rows.filter { it.convertsOnSave() }.map { it.id }.toSet() else emptySet()

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target in rows.indices) {
            val row = rows.removeAt(index)
            rows.add(target, row)
        }
    }

    // Persist the current schema and form settings.
    fun persist(onDone: () -> Unit) {
        val titleToSave = formTitle.orEmpty().trim()
        viewModel.save(
            name = titleToSave,
            fields = rows.map { it.toFieldDef() },
            automaticTimestamping = automaticTimestamping ?: false,
            sortTimestampFieldId = rows.firstOrNull { it.sortByTimestamp }?.id,
            sortNewestFirst = sortNewestFirst ?: true,
            integrateCalendar = integrateCalendar ?: false,
            allowAdditionsFromForm = allowAdditionsFromForm ?: false,
            advancedSearchEnabled = advancedSearchEnabled ?: false,
            locked = locked ?: false,
            convertFieldIds = pendingConversionIds(),
            onSaved = {
                formTitle = titleToSave
                savedTitle = titleToSave
                savedAutomaticTimestamping = automaticTimestamping
                savedSortNewestFirst = sortNewestFirst
                savedIntegrateCalendar = integrateCalendar
                savedAllowAdditionsFromForm = allowAdditionsFromForm
                savedAdvancedSearchEnabled = advancedSearchEnabled
                savedLocked = locked
                rows.forEach { it.convertOnce = false }
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
            otherSortLabel = rows.firstOrNull { it !== draft && it.sortByTimestamp }?.label,
            sortNewestFirst = sortNewestFirst ?: true,
            canSave = { it.isValid() },
            onSortSelected = {
                rows.filter { it !== draft }.forEach { it.sortByTimestamp = false }
            },
            onSortDirectionChange = { sortNewestFirst = it },
            onSave = { persist { editingIndex = null } },
            onDeleteOption = { option ->
                // Committed right away; also drop it from the screen's own copy
                // and its saved baseline so neither Save nor Discard brings it back.
                viewModel.deleteOptionPermanently(draft.id, option.id)
                draft.choices = ChoiceEdits.withoutId(draft.choices, option.id)
                savedSnapshot = savedSnapshot?.map { saved ->
                    if (saved.id == draft.id) {
                        saved.copy(choices = ChoiceEdits.withoutId(saved.choices, option.id))
                    } else {
                        saved
                    }
                }
            },
            onCancel = {
                // A brand-new field that was never filled in is discarded on cancel
                // so the list isn't left with an empty card.
                if (!draft.existing && draft.label.isBlank()) rows.removeAt(openIndex)
                editingIndex = null
            },
            minimumDigits = savedComparisonDigits[draft.id],
            otherFields = rows.filter { it !== draft }.map { it.toFieldDef() },
            hasEntries = hasEntries,
        )
        return
    }

    val fieldsDirty = savedSnapshot?.let { snap -> rows.map { it.toFieldDef() } != snap } ?: false
    val savedSortFieldId = loadedSortTimestampFieldId
    val currentSortFieldId = rows.firstOrNull { it.sortByTimestamp }?.id
    val dirty = fieldsDirty ||
        (savedAdvancedSearchEnabled != null && advancedSearchEnabled != savedAdvancedSearchEnabled) ||
        (savedTitle != null && formTitle != savedTitle) ||
        (savedAutomaticTimestamping != null && automaticTimestamping != savedAutomaticTimestamping) ||
        (savedSortNewestFirst != null && sortNewestFirst != savedSortNewestFirst) ||
        (savedIntegrateCalendar != null && integrateCalendar != savedIntegrateCalendar) ||
        (savedAllowAdditionsFromForm != null && allowAdditionsFromForm != savedAllowAdditionsFromForm) ||
        (savedLocked != null && locked != savedLocked) ||
        pendingConversionIds().isNotEmpty() ||
        (seeded && currentSortFieldId != savedSortFieldId)
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key
        val toKey = to.key
        // Only the field rows carry Long keys; the header/add rows are ignored.
        if (fromKey is Long && toKey is Long) {
            val fromIdx = rows.indexOfFirst { it.uid == fromKey }
            val toIdx = rows.indexOfFirst { it.uid == toKey }
            if (fromIdx >= 0 && toIdx >= 0) {
                rows.add(toIdx, rows.removeAt(fromIdx))
            }
        }
    }

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
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
        ) {
            item(key = "title") {
                OutlinedTextField(
                    value = formTitle.orEmpty(),
                    onValueChange = { formTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "integrate-calendar") {
                SettingSwitchRow(
                    checked = integrateCalendar ?: false,
                    onCheckedChange = { integrateCalendar = it },
                    title = "Integrate Calendar",
                )
            }

            item(key = "edit-calendar") {
                // Stays visible when the toggle is off; AppButton grays out while
                // disabled. Enabled only once Integrate Calendar is turned on.
                AppButton(
                    onClick = onEditCalendar,
                    enabled = integrateCalendar == true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add Calendar")
                }
            }

            item(key = "current-calendars-title") {
                Text("Current Calendars", style = MaterialTheme.typography.titleMedium)
            }

            items(calendars, key = { "calendar-${it.id}" }) { calendar ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCalendar(calendar.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppTheme.spacing.rowInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            calendar.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_square),
                            contentDescription = "Edit Calendar",
                        )
                    }
                }
            }

            item(key = "automatic-timestamping") {
                SettingSwitchRow(
                    checked = automaticTimestamping ?: false,
                    onCheckedChange = { automaticTimestamping = it },
                    title = "Show Timestamp",
                )
            }

            item(key = "advanced-search") {
                SettingSwitchRow(
                    checked = advancedSearchEnabled ?: false,
                    onCheckedChange = { advancedSearchEnabled = it },
                    title = "Enable Advanced Search",
                )
            }

            item(key = "allow-additions-from-form") {
                // Lets a Dropdown, Multiple or Comparison field gain options from
                // the entry form itself (the "+" beside a choice field's title
                // there, or a Comparison field's New Option button).
                SettingSwitchRow(
                    checked = allowAdditionsFromForm ?: false,
                    onCheckedChange = { allowAdditionsFromForm = it },
                    title = "Allow Additions from Form",
                )
            }

            item(key = "locked-log") {
                // Always shown. A form created with Never Allow Form Unlocking
                // shows it on and disabled, so its status is visible.
                SettingSwitchRow(
                    checked = neverAllowUnlocking || locked == true,
                    onCheckedChange = { locked = it },
                    title = "Locked Log",
                    subtitle = "Prevent saved entries from being edited while this is enabled.",
                    enabled = !neverAllowUnlocking,
                )
            }

            item(key = "header") {
                Text(
                    "Tap a field to edit its label, options, and settings. Add new fields " +
                        "or reorder them. A field's type can't change and existing fields " +
                        "can't be removed, so past entries stay intact.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            itemsIndexed(rows, key = { _, field -> field.uid }) { index, field ->
                ReorderableItem(reorderState, key = field.uid) { _ ->
                    val handle = Modifier.draggableHandle().accessibleReorder(index, rows.size) { offset ->
                        val current = rows.indexOf(field)
                        if (current >= 0 && current + offset in rows.indices) {
                            move(current, offset)
                            true
                        } else false
                    }
                    FieldSummaryCard(
                        field = field,
                        number = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onMoveUp = { move(index, -1) },
                        onMoveDown = { move(index, 1) },
                        // Only new fields can be deleted here.
                        deletable = !field.existing,
                        onDelete = { rows.remove(field) },
                        onOpen = { editingIndex = rows.indexOf(field) },
                        dragHandleModifier = handle,
                    )
                }
            }

            item(key = "add") {
                AppButton(
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
    dragHandleModifier: Modifier = Modifier,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(AppTheme.spacing.rowInset), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
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
                // Press-and-drag handle at the far right to reorder the field.
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(start = AppTheme.spacing.compact),
                )
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
    otherSortLabel: String?,
    sortNewestFirst: Boolean,
    canSave: (EditDraft) -> Boolean,
    onSortSelected: () -> Unit,
    onSortDirectionChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDeleteOption: (ChoiceOption) -> Unit,
    onCancel: () -> Unit,
    /** Comparison: the most digits a saved number of this field has, or null. */
    minimumDigits: Int?,
    /** The form's other fields, for the Auto Fill drop-down. */
    otherFields: List<FieldDef>,
    /** Whether the form has saved entries (offers the one-time conversion). */
    hasEntries: Boolean,
) {
    val draft = remember(source) { source.copy() }
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    // Editing works on a copy; warn before dropping unsaved edits to this field.
    var showDiscard by remember { mutableStateOf(false) }
    var showSortReplacement by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    // The sort direction belongs to the form, not the field, so it is held here
    // and only handed back on Save — cancelling this editor leaves it untouched.
    var newestFirst by remember { mutableStateOf(sortNewestFirst) }
    val dirty = !draft.sameContentAs(source) || newestFirst != sortNewestFirst
    fun attemptCancel() { if (dirty) showDiscard = true else onCancel() }
    BackHandler { attemptCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Field $number") },
                navigationIcon = {
                    IconButton(onClick = { attemptCancel() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave(draft),
                        onClick = {
                            // Refuse an option list that repeats an item.
                            if (draft.toFieldDef().choices.hasDuplicateLabels()) {
                                showDuplicate = true
                                return@TextButton
                            }
                            // Max Digits never drops below what saved numbers already use.
                            if (draft.type == FieldType.COMPARISON) {
                                draft.digits = digitsAtLeast(draft.digits, minimumDigits)
                            }
                            draft.autoFillSource = validAutoFillSource(
                                draft.autoFillSource,
                                AutoFill.sourceFields(draft.toFieldDef(), otherFields),
                            )
                            source.applyFrom(draft)
                            if (source.sortByTimestamp) onSortSelected()
                            onSortDirectionChange(newestFirst)
                            // Auto-capitalize only a newly added field's label;
                            // new option rows are capitalized as they're added.
                            if (!source.existing && settings.autoCapitalizeLabels) {
                                source.label = TitleCase.apply(source.label)
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
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
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
                EditTypeDropdown(selected = draft.type, onSelected = {
                    draft.type = it
                    // Time-only and non-date fields can never take part in ordering.
                    if (!it.sortEligible) {
                        draft.allowOrderFiltering = false
                        draft.sortByTimestamp = false
                    }
                })
            }

            SettingsControls(
                field = draft,
                capitalizeNewOptions = settings.autoCapitalizeOptions,
                onDeleteOption = onDeleteOption,
                minimumDigits = minimumDigits,
            )
            if (draft.type.autoFillable) {
                AutoFillSettings(
                    checked = draft.autoFill,
                    onCheckedChange = { draft.autoFill = it },
                    source = draft.autoFillSource,
                    onSourceChange = { draft.autoFillSource = it },
                    sourceFields = AutoFill.sourceFields(draft.toFieldDef(), otherFields),
                )
                // Offered once the form has entries: fills them from the
                // selected source on Save, only where this field is empty.
                if (draft.autoFill && hasEntries) {
                    SettingSwitchRow(
                        checked = draft.convertOnce,
                        onCheckedChange = { draft.convertOnce = it },
                        title = "Fill Existing Cards One Time",
                    )
                }
            }
            if (draft.type.sortEligible) {
                CheckboxSettingRow(
                    checked = draft.allowOrderFiltering,
                    onCheckedChange = { draft.allowOrderFiltering = it },
                    title = "Allow Order Filtering",
                )
                CheckboxSettingRow(
                    checked = draft.sortByTimestamp,
                    onCheckedChange = { checked ->
                        draft.sortByTimestamp = checked
                        if (checked && otherSortLabel != null) showSortReplacement = true
                    },
                    title = "Use as Default Sort Timestamp",
                )
                if (draft.sortByTimestamp) {
                    SortDirectionRadios(
                        newestFirst = newestFirst,
                        onNewestFirstChange = { newestFirst = it },
                    )
                }
            }
            // A Comparison field is never required.
            if (draft.type != FieldType.COMPARISON) RequiredRow(draft)
            if (draft.type == FieldType.DROPDOWN) {
                ChoiceStyleRadios(
                    style = draft.choiceStyle,
                    onStyleChange = { draft.choiceStyle = it },
                )
            }

            draft.validationHint()?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onCancel() },
            onDismiss = { showDiscard = false },
        )
    }

    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
    }


    if (showSortReplacement && otherSortLabel != null) {
        AppDialog(
            onDismissRequest = {
                draft.sortByTimestamp = false
                showSortReplacement = false
            },
            body = "Default timestamp sorting is currently set to be $otherSortLabel. " +
                "Do you want to change it?",
            dismissButton = {
                DialogDismissButton("Cancel") {
                    draft.sortByTimestamp = false
                    showSortReplacement = false
                }
            },
            confirmButton = {
                DialogActionButton("Okay") { showSortReplacement = false }
            },
        )
    }
}

/** The type-specific adjustable settings shared by new and existing fields. */
@Composable
private fun SettingsControls(
    field: EditDraft,
    capitalizeNewOptions: Boolean,
    onDeleteOption: (ChoiceOption) -> Unit,
    minimumDigits: Int?,
) {
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
        FieldType.SCALE -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditScaleBoundField(
                    value = field.from,
                    onChange = { field.from = it },
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
                Text("to")
                EditScaleBoundField(
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
        FieldType.DROPDOWN, FieldType.MULTIPLE -> ChoiceOptionsEditor(
            choices = field.choices,
            onChoicesChange = { field.choices = it },
            wording = ChoiceEditorWordings.formOptions,
            preselection = if (field.type == FieldType.DROPDOWN) {
                ChoicePreselection.SINGLE
            } else {
                ChoicePreselection.MULTIPLE
            },
            capitalizeNew = capitalizeNewOptions,
            onDeletePermanently = onDeleteOption,
        )
        FieldType.COMPARISON -> {
            ChoiceOptionsEditor(
                choices = field.choices,
                onChoicesChange = { field.choices = it },
                wording = ChoiceEditorWordings.formOptions,
                preselection = ChoicePreselection.NONE,
                capitalizeNew = capitalizeNewOptions,
                onDeletePermanently = onDeleteOption,
            )
            ComparisonFieldSettings(field, minimumDigits)
        }
        FieldType.RATIO -> RatioFieldSettings(
            leftDigits = field.leftDigits,
            onLeftDigitsChange = { field.leftDigits = it },
            rightDigits = field.rightDigits,
            onRightDigitsChange = { field.rightDigits = it },
            unit = field.unit,
            onUnitChange = { field.unit = it },
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
}

@Composable
private fun RequiredRow(field: EditDraft) {
    SettingSwitchRow(
        checked = field.required,
        onCheckedChange = { field.required = it },
        title = "Required",
    )
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

/** A 3-digit box for a scale bound. Shows the default as placeholder text. */
@Composable
private fun EditScaleBoundField(
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

/** Hands out a stable id per draft so the reorderable list has unique keys. */
private var editDraftCounter = 0L

/**
 * Mutable, Compose-observable editing state for one field.
 *
 * [id] is the field's permanent identity: an existing field keeps the id it was
 * saved with, and a new field gets a fresh one here. [choices] holds a Single /
 * Multiple Choice field's options, each with its own permanent id.
 */
private class EditDraft(
    val id: String = StableUuid.createNew(),
    label: String = "",
    type: FieldType = FieldType.TEXT,
    required: Boolean = false,
    lines: String = "",
    digits: String = "",
    from: String = "",
    to: String = "",
    choices: List<ChoiceOption> = emptyList(),
    defaultNow: Boolean = false,
    allowOrderFiltering: Boolean = false,
    sortByTimestamp: Boolean = false,
    allowUnknown: Boolean = false,
    makeDropdown: Boolean = false,
    choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
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
    convertOnce: Boolean = false,
    /** True for a field that already exists in the saved schema (type locked). */
    val existing: Boolean = false,
) : ComparisonSettingsDraft {
    var label by mutableStateOf(label)
    var type by mutableStateOf(type)
    var required by mutableStateOf(required)
    var lines by mutableStateOf(lines)
    override var digits by mutableStateOf(digits)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
    var choices by mutableStateOf(choices)
    var defaultNow by mutableStateOf(defaultNow)
    var allowOrderFiltering by mutableStateOf(allowOrderFiltering)
    var sortByTimestamp by mutableStateOf(sortByTimestamp)
    var allowUnknown by mutableStateOf(allowUnknown)
    var makeDropdown by mutableStateOf(makeDropdown)
    var choiceStyle by mutableStateOf(choiceStyle)
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
    override var clickerIncrement by mutableStateOf(clickerIncrement)
    override var clickerDirection by mutableStateOf(clickerDirection)
    var autoFill by mutableStateOf(autoFill)
    /** The Auto Fill drop-down's pick, kept while the checkbox is off. */
    var autoFillSource by mutableStateOf(autoFillSource)
    /** One-time "Fill Existing Cards One Time"; never saved with the field. */
    var convertOnce by mutableStateOf(convertOnce)

    /** True when the next Save should fill this field across existing entries. */
    fun convertsOnSave(): Boolean = convertOnce && autoFill && type.autoFillable

    /** Stable identity for list keys; survives reordering, unique per draft. */
    val uid: Long = editDraftCounter++

    /** True when [o] holds the same editable content (used to detect edits). */
    fun sameContentAs(o: EditDraft): Boolean =
        label == o.label && type == o.type && required == o.required &&
            lines == o.lines && digits == o.digits && from == o.from && to == o.to &&
            choices == o.choices && defaultNow == o.defaultNow &&
            allowOrderFiltering == o.allowOrderFiltering &&
            sortByTimestamp == o.sortByTimestamp &&
            allowUnknown == o.allowUnknown &&
            makeDropdown == o.makeDropdown &&
            choiceStyle == o.choiceStyle &&
            leftDigits == o.leftDigits && rightDigits == o.rightDigits && unit == o.unit &&
            itemLabel == o.itemLabel && firstLabel == o.firstLabel &&
            secondLabel == o.secondLabel && totalLabel == o.totalLabel &&
            wholeNumbersOnly == o.wholeNumbersOnly && clickerCounting == o.clickerCounting &&
            clickerButtonLabel == o.clickerButtonLabel && clickerStartingNumber == o.clickerStartingNumber &&
            clickerIncrement == o.clickerIncrement && clickerDirection == o.clickerDirection &&
            autoFill == o.autoFill && autoFillSource == o.autoFillSource && convertOnce == o.convertOnce

    /** The options that will be saved: a row left blank is never stored. */
    fun savedChoices(): List<ChoiceOption> =
        choices.filter { it.label.isNotBlank() }.map { it.copy(label = it.label.trim()) }

    private fun effectiveFrom(): Int = from.toIntOrNull() ?: EDIT_SCALE_DEFAULT_FROM
    private fun effectiveTo(): Int = to.toIntOrNull() ?: EDIT_SCALE_DEFAULT_TO

    fun isValid(): Boolean {
        if (label.trim().isEmpty()) return false
        return when (type) {
            FieldType.SCALE -> effectiveTo() >= effectiveFrom()
            FieldType.DROPDOWN, FieldType.MULTIPLE, FieldType.COMPARISON -> savedChoices().activeChoices.isNotEmpty()
            FieldType.RATIO -> ratioDigitsValid()
            else -> true
        }
    }

    fun validationHint(): String? = when {
        label.trim().isEmpty() -> "Add a label."
        type == FieldType.SCALE && !isValid() -> "Scale To must be ≥ From."
        type.hasOptionList && savedChoices().activeChoices.isEmpty() ->
            "Add at least one option."
        type == FieldType.RATIO && !ratioDigitsValid() -> RATIO_DIGITS_HINT
        else -> null
    }

    private fun ratioDigitsValid(): Boolean =
        RatioValues.parseDigits(leftDigits) != null && RatioValues.parseDigits(rightDigits) != null

    /** A short summary of the field's type and settings for the list card. */
    fun summaryText(): String {
        val base = when (type) {
            FieldType.TEXT -> "Text (one line)"
            FieldType.MULTILINE -> "Text (multi-line)" + (lines.toIntOrNull()?.let { " · $it lines" } ?: "")
            FieldType.NUMBER -> "Number" + (digits.toIntOrNull()?.let { " · up to $it digits" } ?: "")
            FieldType.DROPDOWN -> "Dropdown" + optionsSummary()
            FieldType.MULTIPLE -> "Multiple" + optionsSummary()
            FieldType.SCALE -> {
                val lo = from.ifBlank { EDIT_SCALE_DEFAULT_FROM.toString() }
                val hi = to.ifBlank { EDIT_SCALE_DEFAULT_TO.toString() }
                "Scale $lo–$hi" + (if (makeDropdown) " · Dropdown" else "")
            }
            FieldType.YESNO -> if (allowUnknown) "Yes / No / Unknown" else "Yes / No"
            FieldType.DATE -> "Date"
            FieldType.TIME -> "Time"
            FieldType.DATETIME -> "Date & time" + (if (defaultNow) " · defaults to now" else "")
            FieldType.TAGS -> "Tags"
            FieldType.WEBPAGE -> "Webpage address"
            FieldType.BLOOD_PRESSURE -> "Blood pressure (###/###)"
            FieldType.RATIO -> "Ratios"
            FieldType.COMPARISON -> "Comparison" + optionsSummary()
        }
        return if (required) "$base · required" else base
    }

    private fun optionsSummary(): String {
        val opts = savedChoices().activeChoices
        return if (opts.isEmpty()) "" else " · " + opts.joinToString(", ") { it.label }
    }

    /** A detached duplicate so the field editor can cancel without side effects. */
    fun copy(): EditDraft = EditDraft(
        id = id,
        label = label,
        type = type,
        required = required,
        lines = lines,
        digits = digits,
        from = from,
        to = to,
        choices = choices,
        defaultNow = defaultNow,
        allowOrderFiltering = allowOrderFiltering,
        sortByTimestamp = sortByTimestamp,
        allowUnknown = allowUnknown,
        makeDropdown = makeDropdown,
        choiceStyle = choiceStyle,
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
        convertOnce = convertOnce,
        existing = existing,
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
        choices = other.savedChoices()
        defaultNow = other.defaultNow
        allowOrderFiltering = other.allowOrderFiltering
        sortByTimestamp = other.sortByTimestamp
        allowUnknown = other.allowUnknown
        makeDropdown = other.makeDropdown
        choiceStyle = other.choiceStyle
        leftDigits = other.leftDigits
        rightDigits = other.rightDigits
        unit = other.unit
        itemLabel = other.itemLabel
        firstLabel = other.firstLabel
        secondLabel = other.secondLabel
        totalLabel = other.totalLabel
        wholeNumbersOnly = other.wholeNumbersOnly
        clickerCounting = other.clickerCounting
        clickerButtonLabel = other.clickerButtonLabel
        clickerStartingNumber = other.clickerStartingNumber
        clickerIncrement = other.clickerIncrement
        clickerDirection = other.clickerDirection
        autoFill = other.autoFill
        autoFillSource = other.autoFillSource
        convertOnce = other.convertOnce
    }

    fun toFieldDef(): FieldDef = FieldDef(
        id = id,
        label = label.trim(),
        type = type,
        required = required && type != FieldType.COMPARISON,
        lines = if (type == FieldType.MULTILINE) lines.toIntOrNull() else null,
        digits = when (type) {
            FieldType.NUMBER -> digits.toIntOrNull()
            // The box caps what can be typed; only saved data's own floor goes higher.
            FieldType.COMPARISON -> digits.toIntOrNull()?.takeIf { it > 0 }
            else -> null
        },
        from = if (type == FieldType.SCALE) effectiveFrom() else null,
        to = if (type == FieldType.SCALE) effectiveTo() else null,
        choices = if (type.hasOptionList) savedChoices() else emptyList(),
        defaultNow = type == FieldType.DATETIME && defaultNow,
        allowOrderFiltering = type.sortEligible && allowOrderFiltering,
        allowUnknown = type == FieldType.YESNO && allowUnknown,
        makeDropdown = type == FieldType.SCALE && makeDropdown,
        choiceStyle = if (type == FieldType.DROPDOWN) choiceStyle else ChoiceStyle.DROPDOWN,
        leftDigits = if (type == FieldType.RATIO) RatioValues.parseDigits(leftDigits) else null,
        rightDigits = if (type == FieldType.RATIO) RatioValues.parseDigits(rightDigits) else null,
        unit = if (type == FieldType.RATIO) unit.trim() else "",
        itemLabel = if (type == FieldType.COMPARISON) itemLabel.trim() else "",
        firstLabel = if (type == FieldType.COMPARISON) firstLabel.trim() else "",
        secondLabel = if (type == FieldType.COMPARISON) secondLabel.trim() else "",
        totalLabel = if (type == FieldType.COMPARISON) totalLabel.trim() else "",
        wholeNumbersOnly = type != FieldType.COMPARISON || wholeNumbersOnly,
        clickerCounting = type == FieldType.COMPARISON && clickerCounting,
        clickerButtonLabel = if (type == FieldType.COMPARISON) clickerButtonLabel.trim() else "",
        clickerStartingNumber = if (type == FieldType.COMPARISON) clickerStartingNumber.toIntOrNull() ?: 0 else 0,
        clickerIncrement = if (type == FieldType.COMPARISON) clickerIncrement.toIntOrNull()?.takeIf { it > 0 } ?: 1 else 1,
        clickerDirection = if (type == FieldType.COMPARISON) clickerDirection else ClickerIncrementDirection.ADD,
        autoFillSource = if (type.autoFillable && autoFill) autoFillSource else null,
    )

    fun toSnapshot(): EditDraftSnapshot = EditDraftSnapshot(
        id = id,
        label = label,
        type = type,
        required = required,
        lines = lines,
        digits = digits,
        from = from,
        to = to,
        choices = choices,
        defaultNow = defaultNow,
        allowOrderFiltering = allowOrderFiltering,
        sortByTimestamp = sortByTimestamp,
        allowUnknown = allowUnknown,
        makeDropdown = makeDropdown,
        choiceStyle = choiceStyle,
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
        convertOnce = convertOnce,
        existing = existing,
    )
}

/** Scale bounds used when the user leaves the From/To boxes blank. */
private const val EDIT_SCALE_DEFAULT_FROM = 1
private const val EDIT_SCALE_DEFAULT_TO = 10

/** Plain serializable snapshot of an [EditDraft], for [rowsSaver]. */
@Serializable
private data class EditDraftSnapshot(
    val id: String,
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val lines: String,
    val digits: String,
    val from: String,
    val to: String,
    val choices: List<ChoiceOption> = emptyList(),
    val defaultNow: Boolean,
    val allowOrderFiltering: Boolean = false,
    val sortByTimestamp: Boolean = false,
    val allowUnknown: Boolean = false,
    val makeDropdown: Boolean = false,
    val choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
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
    val convertOnce: Boolean = false,
    val existing: Boolean,
)

private fun EditDraftSnapshot.toEditDraft(): EditDraft = EditDraft(
    id = id,
    label = label,
    type = type,
    required = required,
    lines = lines,
    digits = digits,
    from = from,
    to = to,
    choices = choices,
    defaultNow = defaultNow,
    allowOrderFiltering = allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    allowUnknown = allowUnknown,
    makeDropdown = makeDropdown,
    choiceStyle = choiceStyle,
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
    convertOnce = convertOnce,
    existing = existing,
)

/** Seed an [EditDraft] from a saved field (marked existing — type locked), keeping its ids. */
private fun draftOf(f: FieldDef, sortByTimestamp: Boolean): EditDraft = EditDraft(
    id = f.id,
    label = f.label,
    type = f.type,
    required = f.required,
    lines = f.lines?.toString() ?: "",
    digits = f.digits?.toString() ?: "",
    from = f.from?.toString() ?: "",
    to = f.to?.toString() ?: "",
    choices = f.choices,
    defaultNow = f.defaultNow,
    allowOrderFiltering = f.allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    allowUnknown = f.allowUnknown,
    makeDropdown = f.makeDropdown,
    choiceStyle = f.choiceStyle,
    leftDigits = f.leftDigits?.toString() ?: "",
    rightDigits = f.rightDigits?.toString() ?: "",
    unit = f.unit,
    itemLabel = f.itemLabel,
    firstLabel = f.firstLabel,
    secondLabel = f.secondLabel,
    totalLabel = f.totalLabel,
    wholeNumbersOnly = f.wholeNumbersOnly,
    clickerCounting = f.clickerCounting,
    clickerButtonLabel = f.clickerButtonLabel,
    clickerStartingNumber = f.clickerStartingNumber.takeIf { it != 0 }?.toString() ?: "",
    clickerIncrement = f.clickerIncrement.takeIf { it != 1 }?.toString() ?: "",
    clickerDirection = f.clickerDirection,
    autoFill = f.autoFillSource != null,
    autoFillSource = f.autoFillSource ?: AutoFill.TIMESTAMP,
    existing = true,
)

private fun FieldType.editFriendly(): String = when (this) {
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
}
