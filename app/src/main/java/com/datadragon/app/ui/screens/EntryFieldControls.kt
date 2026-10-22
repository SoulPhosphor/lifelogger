package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.CompositionLocalProvider
import com.datadragon.app.ui.components.LocalAccessibleFieldLabel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.TagsEditor
import com.datadragon.app.ui.components.WebpageEntryField
import com.datadragon.app.data.ChoiceStyle
import com.datadragon.app.data.ComparisonRow
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.MAX_RATIO_DIGITS
import com.datadragon.app.data.RatioValues
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.choice
import com.datadragon.app.data.hasItemRows
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import com.datadragon.app.ui.theme.AppTheme
import com.datadragon.app.ui.components.PopupButton

/**
 * Legacy stored yesno values that predate the Yes / No / Unknown radio design.
 * Any of these — plus a lingering "Unknown" on a field whose author has since
 * turned Allow Unknown Option off — collapses to "Yes" in the UI so the entry
 * shows a selected radio instead of a blank one, and is saved as "Yes" the next
 * time the entry is written.
 */
private const val YESNO_YES = "Yes"
private const val YESNO_NO = "No"
private const val YESNO_UNKNOWN = "Unknown"

/** Normalize a stored yesno value against the field's current [allowUnknown]. */
private fun coerceYesNo(stored: String, allowUnknown: Boolean): String = when (stored) {
    YESNO_YES, YESNO_NO -> stored
    YESNO_UNKNOWN -> if (allowUnknown) YESNO_UNKNOWN else YESNO_YES
    "" -> ""
    else -> YESNO_YES
}

private fun FieldDef.displayLabel(): String = if (required) "$label *" else label

/**
 * Renders one generated entry control for [field], reading and writing into the
 * shared form state. Single-valued fields live in [textValues]; `multiple`
 * fields live in [multiValues]; `tags` fields live in [tagValues], which keeps a
 * list rather than a set so tags stay in the order they were added;
 * `comparison` fields live in [comparisonValues], where a field with no entry
 * shows one blank row. Storage forms follow [EntryValues].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryFieldControl(
    field: FieldDef,
    textValues: SnapshotStateMap<String, String>,
    multiValues: SnapshotStateMap<String, Set<String>>,
    tagValues: SnapshotStateMap<String, List<String>>,
    comparisonValues: SnapshotStateMap<String, List<ComparisonRow>>,
    showRequiredError: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    focusRequester: FocusRequester? = null,
    /** Set when the form allows additions: opens Add Option for a Dropdown/Multiple/Comparison field. */
    onAddOption: ((FieldDef) -> Unit)? = null,
    /** Shows a short message to the user, such as a refused decimal point. */
    onNotice: (String) -> Unit = {},
) {
    val label = field.displayLabel()
    val targetModifier = bringIntoViewRequester?.let {
        Modifier.fillMaxWidth().bringIntoViewRequester(it)
    } ?: Modifier
    val validationModifier = if (showRequiredError) {
        Modifier.border(
            width = AppTheme.shapes.validationBorder,
            color = MaterialTheme.colorScheme.error,
            shape = AppTheme.shapes.validation,
        )
    } else {
        Modifier
    }
    Column(modifier = targetModifier.then(validationModifier).semantics {
        if (showRequiredError) {
            if (field.type.hasItemRows) {
                error("${field.label}: Choose an item for each row with numbers.")
            } else {
                error("${field.label}: This field is required.")
            }
        }
    }) {
        when (field.type) {
            FieldType.TEXT -> Labeled(label) {
                OutlinedTextField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { textValues[field.id] = it },
                    singleLine = true,
                    isError = showRequiredError,
                    modifier = Modifier.fillMaxWidth().withFocusRequester(focusRequester),
                )
            }

            FieldType.MULTILINE -> Labeled(label) {
                val minHeight = AppTheme.sizes.textLineHeight * (field.lines ?: 4).coerceIn(2, 12)
                OutlinedTextField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { textValues[field.id] = it },
                    isError = showRequiredError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .withFocusRequester(focusRequester),
                )
            }

            FieldType.NUMBER -> Labeled(label) {
                OutlinedTextField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        textValues[field.id] = field.digits?.let { digitsOnly.take(it) } ?: digitsOnly
                    },
                    singleLine = true,
                    isError = showRequiredError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().withFocusRequester(focusRequester),
                )
            }

            FieldType.DROPDOWN -> {
                // The stored answer is an option id. A retired option this entry
                // already holds still shows (and stays deselectable).
                val stored = textValues[field.id].orEmpty()
                val offered = field.offeredChoices(listOf(stored))
                if (field.choiceStyle == ChoiceStyle.DISPLAYED) {
                    SingleChoiceChipsField(
                        label = label,
                        options = offered,
                        selectedId = stored,
                        onTap = { option ->
                            textValues[field.id] = if (option.id == stored) "" else option.id
                        },
                        onAdd = onAddOption?.let { add -> { add(field) } },
                    )
                } else {
                    DropdownField(
                        label = label,
                        options = offered,
                        selectedLabel = field.choice(stored)?.label ?: stored,
                        onSelected = { textValues[field.id] = it.id },
                        onAdd = onAddOption?.let { add -> { add(field) } },
                    )
                }
            }

            FieldType.YESNO -> {
                val raw = textValues[field.id].orEmpty()
                val current = coerceYesNo(raw, field.allowUnknown)
                LaunchedEffect(field.id, raw, field.allowUnknown) {
                    if (raw.isNotEmpty() && current != raw) {
                        textValues[field.id] = current
                    }
                }
                YesNoField(
                    label = label,
                    selected = current,
                    allowUnknown = field.allowUnknown,
                    onTap = { pick ->
                        textValues[field.id] =
                            if (pick == current && !field.required) "" else pick
                    },
                )
            }

            FieldType.SCALE -> ScaleField(
                label = label,
                from = field.from ?: 1,
                to = field.to ?: 10,
                makeDropdown = field.makeDropdown,
                required = field.required,
                selected = textValues[field.id].orEmpty(),
                onSelected = { textValues[field.id] = it },
            )

            FieldType.MULTIPLE -> {
                val selected = multiValues[field.id].orEmpty()
                MultipleField(
                    label = label,
                    options = field.offeredChoices(selected),
                    selectedIds = selected,
                    onToggle = { option ->
                        val current = multiValues[field.id].orEmpty()
                        multiValues[field.id] =
                            if (option.id in current) current - option.id else current + option.id
                    },
                    onAdd = onAddOption?.let { add -> { add(field) } },
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

            FieldType.TAGS -> Labeled(label) {
                TagsEditor(
                    tags = tagValues[field.id].orEmpty(),
                    onTagsChange = { tagValues[field.id] = it },
                    isError = showRequiredError,
                    focusRequester = focusRequester,
                )
            }

            FieldType.WEBPAGE -> Labeled(label) {
                WebpageEntryField(
                    value = textValues[field.id].orEmpty(),
                    onValueChange = { textValues[field.id] = it },
                    isError = showRequiredError,
                    focusRequester = focusRequester,
                )
            }

            FieldType.BLOOD_PRESSURE -> BloodPressureField(
                label = label,
                stored = textValues[field.id].orEmpty(),
                onChange = { textValues[field.id] = it },
            )

            FieldType.RATIO -> RatioField(
                label = label,
                stored = textValues[field.id].orEmpty(),
                leftDigits = field.leftDigits ?: MAX_RATIO_DIGITS,
                rightDigits = field.rightDigits ?: MAX_RATIO_DIGITS,
                unit = field.unit,
                onChange = { textValues[field.id] = it },
            )

            // No title of its own: the column captions head the table.
            FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES -> ComparisonField(
                field = field,
                rows = comparisonValues[field.id] ?: listOf(ComparisonRow()),
                onRowsChange = { comparisonValues[field.id] = it },
                showError = showRequiredError,
                onNewOption = onAddOption?.let { add -> { add(field) } },
                onNotice = onNotice,
            )
        }
    }
}

private fun Modifier.withFocusRequester(requester: FocusRequester?): Modifier =
    requester?.let { this.focusRequester(it) } ?: this

/**
 * A field's title above its control. When [onAdd] is set (a Dropdown/Multiple
 * field on a form that allows additions), a background-less "+" sits at the far
 * right of the title's line and opens the Add Option pop-up.
 */
@Composable
internal fun Labeled(
    label: String,
    onAdd: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
        if (onAdd == null) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add Option to $label",
                        tint = AppTheme.colors.addOptionIcon,
                    )
                }
            }
        }
        CompositionLocalProvider(LocalAccessibleFieldLabel provides label) { content() }
    }
}

/**
 * The options a choice control offers on an entry: every active option in
 * order, plus any retired option this entry already holds, so a saved answer
 * stays visible and can still be deselected.
 */
internal fun FieldDef.offeredChoices(selectedIds: Collection<String>): List<ChoiceOption> =
    choices.filter { !it.retired || it.id in selectedIds }

/**
 * A Single Choice dropdown over [options]. [selectedLabel] is the current
 * answer's label (blank for none); picking an option hands back that option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    options: List<ChoiceOption>,
    selectedLabel: String,
    onSelected: (ChoiceOption) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Labeled(label, onAdd) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Choose…") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                accessibleLabel = label,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun YesNoField(
    label: String,
    selected: String,
    allowUnknown: Boolean,
    onTap: (String) -> Unit,
) {
    val options = buildList {
        add(YESNO_YES)
        add(YESNO_NO)
        if (allowUnknown) add(YESNO_UNKNOWN)
    }
    Labeled(label) {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.screenInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onTap(option) })
                        .padding(vertical = AppTheme.spacing.compact),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = null,
                        modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget),
                    )
                    Text(option)
                }
            }
        }
    }
}

/** Menu item shown at the top of an optional scale dropdown so the user can clear their pick. */
private const val SCALE_NONE_LABEL = "None"

/**
 * Split a stored blood-pressure value ("systolic/diastolic") into its two sides;
 * missing sides come back as empty strings so the entry field can still edit them.
 */
private fun splitBloodPressure(stored: String): Pair<String, String> {
    val slash = stored.indexOf('/')
    return if (slash < 0) stored to "" else stored.substring(0, slash) to stored.substring(slash + 1)
}

private fun joinBloodPressure(systolic: String, diastolic: String): String =
    if (systolic.isEmpty() && diastolic.isEmpty()) "" else "$systolic/$diastolic"

@Composable
private fun BloodPressureField(
    label: String,
    stored: String,
    onChange: (String) -> Unit,
) {
    val (systolic, diastolic) = splitBloodPressure(stored)
    Labeled(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = systolic,
                accessibleLabel = "$label: Systolic",
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    onChange(joinBloodPressure(digits, diastolic))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(AppTheme.sizes.numberWidth),
            )
            Text(
                "/",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.related),
            )
            OutlinedTextField(
                value = diastolic,
                accessibleLabel = "$label: Diastolic",
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    onChange(joinBloodPressure(systolic, digits))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(AppTheme.sizes.numberWidth),
            )
        }
    }
}

/**
 * A Ratios field: two number boxes separated by "/", each taking no more digits
 * than the field allows, followed by the field's unit of measurement.
 */
@Composable
internal fun RatioField(
    label: String,
    stored: String,
    leftDigits: Int,
    rightDigits: Int,
    unit: String,
    onChange: (String) -> Unit,
) {
    val (left, right) = RatioValues.split(stored)
    Labeled(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = left,
                accessibleLabel = "$label: Left",
                onValueChange = { input ->
                    onChange(RatioValues.join(input.filter { it.isDigit() }.take(leftDigits), right))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Text(
                "/",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.related),
            )
            OutlinedTextField(
                value = right,
                accessibleLabel = "$label: Right",
                onValueChange = { input ->
                    onChange(RatioValues.join(left, input.filter { it.isDigit() }.take(rightDigits)))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            if (unit.isNotBlank()) {
                Text(
                    unit.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = AppTheme.spacing.related),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleField(
    label: String,
    from: Int,
    to: Int,
    makeDropdown: Boolean,
    required: Boolean,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val numbers = if (to >= from) (from..to).toList() else emptyList()
    if (makeDropdown) {
        ScaleDropdown(
            label = label,
            numbers = numbers,
            required = required,
            selected = selected,
            onSelected = onSelected,
        )
    } else {
        Labeled(label) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
                numbers.forEach { n ->
                    val value = n.toString()
                    FilterChip(
                        selected = selected == value,
                        onClick = {
                            onSelected(if (selected == value && !required) "" else value)
                        },
                        label = { Text(value) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleDropdown(
    label: String,
    numbers: List<Int>,
    required: Boolean,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Labeled(label) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(if (required) "Choose…" else "") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                accessibleLabel = label,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (!required) {
                    DropdownMenuItem(
                        text = { Text(SCALE_NONE_LABEL) },
                        onClick = {
                            onSelected("")
                            expanded = false
                        },
                    )
                }
                numbers.forEach { n ->
                    val value = n.toString()
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultipleField(
    label: String,
    options: List<ChoiceOption>,
    selectedIds: Set<String>,
    onToggle: (ChoiceOption) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    Labeled(label, onAdd) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
            options.forEach { option ->
                FilterChip(
                    selected = option.id in selectedIds,
                    onClick = { onToggle(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/**
 * A Single Choice field shown as the Multiple Choices chips, but holding one
 * answer: tapping another chip moves the selection to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChoiceChipsField(
    label: String,
    options: List<ChoiceOption>,
    selectedId: String,
    onTap: (ChoiceOption) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    Labeled(label, onAdd) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
            options.forEach { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onTap(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
internal fun DateField(label: String, stored: String?, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val date = stored?.let { runCatching { LocalDate.parse(it, EntryValues.DATE_STORAGE) }.getOrNull() }
    PickerButton(
        label = label,
        text = date?.let { EntryValues.displayDate(it) },
        placeholder = "Select date",
        onClick = { show = true },
    )
    if (show) {
        DatePickerModal(
            initial = date,
            onDismiss = { show = false },
            onConfirm = {
                onChange(it.format(EntryValues.DATE_STORAGE))
                show = false
            },
        )
    }
}

@Composable
internal fun TimeField(label: String, stored: String?, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val time = stored?.let { runCatching { LocalTime.parse(it, EntryValues.TIME_STORAGE) }.getOrNull() }
    PickerButton(
        label = label,
        text = time?.let { EntryValues.displayTime(it) },
        placeholder = "Select time",
        onClick = { show = true },
    )
    if (show) {
        TimePickerModal(
            initial = time ?: LocalTime.now(),
            onDismiss = { show = false },
            onConfirm = {
                onChange(it.format(EntryValues.TIME_STORAGE))
                show = false
            },
        )
    }
}

@Composable
internal fun DateTimeField(label: String, stored: String?, onChange: (String) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val current = stored?.let {
        runCatching { LocalDateTime.parse(it, EntryValues.DATETIME_STORAGE) }.getOrNull()
    }
    Labeled(label) {
        FlowRowSafe {
            PickerButton(
                label = null,
                text = current?.toLocalDate()?.let { EntryValues.displayDate(it) },
                placeholder = "Select date",
                onClick = { showDate = true },
            )
            PickerButton(
                label = null,
                text = current?.toLocalTime()?.let { EntryValues.displayTime(it) },
                placeholder = "Select time",
                onClick = { showTime = true },
            )
        }
    }
    if (showDate) {
        DatePickerModal(
            initial = current?.toLocalDate(),
            onDismiss = { showDate = false },
            onConfirm = { picked ->
                val time = current?.toLocalTime() ?: LocalTime.now().withSecond(0).withNano(0)
                onChange(LocalDateTime.of(picked, time).format(EntryValues.DATETIME_STORAGE))
                showDate = false
            },
        )
    }
    if (showTime) {
        TimePickerModal(
            initial = current?.toLocalTime() ?: LocalTime.now(),
            onDismiss = { showTime = false },
            onConfirm = { picked ->
                val date = current?.toLocalDate() ?: LocalDate.now()
                onChange(LocalDateTime.of(date, picked).format(EntryValues.DATETIME_STORAGE))
                showTime = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSafe(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) { content() }
}

@Composable
private fun PickerButton(
    label: String?,
    text: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        AppButton(onClick = onClick) {
            Text(text ?: placeholder)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initial
        ?.atStartOfDay(ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PopupButton(onClick = {
                val picked = state.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                }
                if (picked != null) onConfirm(picked) else onDismiss()
            }) { Text("Okay") }
        },
        dismissButton = { PopupButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PopupButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Okay") }
        },
        dismissButton = { PopupButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
