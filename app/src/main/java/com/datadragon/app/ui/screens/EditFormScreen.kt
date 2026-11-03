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
import com.datadragon.app.data.hasItemRows
import com.datadragon.app.data.hasChoices
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
import com.datadragon.app.data.Weekday
import com.datadragon.app.data.WeekdaySettings
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
    var savedSnapshot by rememberSav…11329 tokens truncated…tartingNumber,
        clickerIncrement = clickerIncrement,
        clickerDirection = clickerDirection,
        autoFill = autoFill,
        autoFillSource = autoFillSource,
        convertOnce = convertOnce,
        addQuantities = addQuantities,
        addCheckbox = addCheckbox,
        showCheckTotal = showCheckTotal,
        showAverage = showAverage,
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
        orderOptionsAlphabetically = other.orderOptionsAlphabetically
        defaultNow = other.defaultNow
        allowOrderFiltering = other.allowOrderFiltering
        sortByTimestamp = other.sortByTimestamp
        allowUnknown = other.allowUnknown
        makeDropdown = other.makeDropdown
        choiceStyle = other.choiceStyle
        weekday = other.weekday
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
        addQuantities = other.addQuantities
        addCheckbox = other.addCheckbox
        showCheckTotal = other.showCheckTotal
        showAverage = other.showAverage
    }

    fun toFieldDef(): FieldDef = FieldDef(
        id = id,
        label = label.trim().ifBlank { if (type == FieldType.DAY_OF_WEEK) Weekday.LABEL else "" },
        type = type,
        required = required && !type.hasItemRows,
        lines = if (type == FieldType.MULTILINE) lines.toIntOrNull() else null,
        digits = when (type) {
            FieldType.NUMBER -> digits.toIntOrNull()
            // The box caps what can be typed; only saved data's own floor goes higher.
            FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES -> digits.toIntOrNull()?.takeIf { it > 0 }
            else -> null
        },
        from = if (type == FieldType.SCALE) effectiveFrom() else null,
        to = if (type == FieldType.SCALE) effectiveTo() else null,
        choices = if (type.hasOptionList) savedChoices() else emptyList(),
        orderOptionsAlphabetically = type.hasChoices && orderOptionsAlphabetically,
        defaultNow = type == FieldType.DATETIME && defaultNow,
        allowOrderFiltering = type.sortEligible && allowOrderFiltering,
        allowUnknown = type == FieldType.YESNO && allowUnknown,
        makeDropdown = type == FieldType.SCALE && makeDropdown,
        choiceStyle = if (type == FieldType.DROPDOWN) choiceStyle else ChoiceStyle.DROPDOWN,
        weekday = weekday,
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
        addCheckbox = type == FieldType.LIST_ITEM_QUANTITIES && addCheckbox,
        showCheckTotal = type == FieldType.LIST_ITEM_QUANTITIES && showCheckTotal,
        showAverage = type.hasItemRows && showAverage,
    )

    fun toSnapshot(): EditDraftSnapshot = EditDraftSnapshot(
        id = id,
        persistedIds = persistedChoiceIds,
        label = label,
        type = type,
        required = required,
        lines = lines,
        digits = digits,
        from = from,
        to = to,
        choices = choices,
        orderOptionsAlphabetically = orderOptionsAlphabetically,
        defaultNow = defaultNow,
        allowOrderFiltering = allowOrderFiltering,
        sortByTimestamp = sortByTimestamp,
        allowUnknown = allowUnknown,
        makeDropdown = makeDropdown,
        choiceStyle = choiceStyle,
        weekday = weekday,
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
        addQuantities = addQuantities,
        addCheckbox = addCheckbox,
        showCheckTotal = showCheckTotal,
        showAverage = showAverage,
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
    val orderOptionsAlphabetically: Boolean = false,
    val defaultNow: Boolean,
    val allowOrderFiltering: Boolean = false,
    val sortByTimestamp: Boolean = false,
    val allowUnknown: Boolean = false,
    val makeDropdown: Boolean = false,
    val choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
    val weekday: WeekdaySettings = WeekdaySettings(),
    val persistedIds: Set<String>? = null,
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
    val addQuantities: Boolean = false,
    val addCheckbox: Boolean = false,
    val showCheckTotal: Boolean = false,
    val showAverage: Boolean = false,
    val existing: Boolean,
)

private fun EditDraftSnapshot.toEditDraft(): EditDraft = EditDraft(
    id = id,
    persistedIds = persistedIds,
    label = label,
    type = type,
    required = required,
    lines = lines,
    digits = digits,
    from = from,
    to = to,
    choices = choices,
    orderOptionsAlphabetically = orderOptionsAlphabetically,
    defaultNow = defaultNow,
    allowOrderFiltering = allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    allowUnknown = allowUnknown,
    makeDropdown = makeDropdown,
    choiceStyle = choiceStyle,
    weekday = weekday,
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
    addQuantities = addQuantities,
    addCheckbox = addCheckbox,
    showCheckTotal = showCheckTotal,
    showAverage = showAverage,
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
    orderOptionsAlphabetically = f.orderOptionsAlphabetically,
    defaultNow = f.defaultNow,
    allowOrderFiltering = f.allowOrderFiltering,
    sortByTimestamp = sortByTimestamp,
    allowUnknown = f.allowUnknown,
    makeDropdown = f.makeDropdown,
    choiceStyle = f.choiceStyle,
    weekday = f.weekday,
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
    addQuantities = f.addQuantities,
    addCheckbox = f.addCheckbox,
    showCheckTotal = f.showCheckTotal,
    showAverage = f.showAverage,
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
    FieldType.DAY_OF_WEEK -> Weekday.LABEL
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
