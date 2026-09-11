package com.datadragon.app.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.TagsEditor
import com.datadragon.app.ui.components.WebpageEntryField
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

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
 * list rather than a set so tags stay in the order they were added. Storage
 * forms follow [EntryValues].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryFieldControl(
    field: FieldDef,
    textValues: SnapshotStateMap<String, String>,
    multiValues: SnapshotStateMap<String, Set<String>>,
    tagValues: SnapshotStateMap<String, List<String>>,
    showRequiredError: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    focusRequester: FocusRequester? = null,
) {
    val label = field.displayLabel()
    val targetModifier = bringIntoViewRequester?.let {
        Modifier.fillMaxWidth().bringIntoViewRequester(it)
    } ?: Modifier
    val validationModifier = if (showRequiredError) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(8.dp),
        )
    } else {
        Modifier
    }
    Column(modifier = targetModifier.then(validationModifier)) {
        when (field.type) {
            FieldType.TEXT -> Labeled(label) {
                OutlinedTextField(
                    value = textValues[field.label].orEmpty(),
                    onValueChange = { textValues[field.label] = it },
                    singleLine = true,
                    isError = showRequiredError,
                    modifier = Modifier.fillMaxWidth().withFocusRequester(focusRequester),
                )
            }

            FieldType.MULTILINE -> Labeled(label) {
                val minHeight = ((field.lines ?: 4).coerceIn(2, 12) * 24).dp
                OutlinedTextField(
                    value = textValues[field.label].orEmpty(),
                    onValueChange = { textValues[field.label] = it },
                    isError = showRequiredError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .withFocusRequester(focusRequester),
                )
            }

            FieldType.NUMBER -> Labeled(label) {
                OutlinedTextField(
                    value = textValues[field.label].orEmpty(),
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        textValues[field.label] = field.digits?.let { digitsOnly.take(it) } ?: digitsOnly
                    },
                    singleLine = true,
                    isError = showRequiredError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().withFocusRequester(focusRequester),
                )
            }

            FieldType.DROPDOWN -> DropdownField(
                label = label,
                options = field.options,
                selected = textValues[field.label].orEmpty(),
                onSelected = { textValues[field.label] = it },
            )

            FieldType.YESNO -> {
                val raw = textValues[field.label].orEmpty()
                val current = coerceYesNo(raw, field.allowUnknown)
                LaunchedEffect(field.label, raw, field.allowUnknown) {
                    if (raw.isNotEmpty() && current != raw) {
                        textValues[field.label] = current
                    }
                }
                YesNoField(
                    label = label,
                    selected = current,
                    allowUnknown = field.allowUnknown,
                    onTap = { pick ->
                        textValues[field.label] =
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
                selected = textValues[field.label].orEmpty(),
                onSelected = { textValues[field.label] = it },
            )

            FieldType.MULTIPLE -> MultipleField(
                label = label,
                options = field.options,
                selected = multiValues[field.label].orEmpty(),
                onToggle = { option ->
                    val current = multiValues[field.label].orEmpty()
                    multiValues[field.label] =
                        if (option in current) current - option else current + option
                },
            )

            FieldType.DATE -> DateField(
                label = label,
                stored = textValues[field.label],
                onChange = { textValues[field.label] = it },
            )

            FieldType.TIME -> TimeField(
                label = label,
                stored = textValues[field.label],
                onChange = { textValues[field.label] = it },
            )

            FieldType.DATETIME -> DateTimeField(
                label = label,
                stored = textValues[field.label],
                onChange = { textValues[field.label] = it },
            )

            FieldType.TAGS -> Labeled(label) {
                TagsEditor(
                    tags = tagValues[field.label].orEmpty(),
                    onTagsChange = { tagValues[field.label] = it },
                    isError = showRequiredError,
                    focusRequester = focusRequester,
                )
            }

            FieldType.WEBPAGE -> Labeled(label) {
                WebpageEntryField(
                    value = textValues[field.label].orEmpty(),
                    onValueChange = { textValues[field.label] = it },
                    isError = showRequiredError,
                    focusRequester = focusRequester,
                )
            }

            FieldType.BLOOD_PRESSURE -> BloodPressureField(
                label = label,
                stored = textValues[field.label].orEmpty(),
                onChange = { textValues[field.label] = it },
            )
        }
    }
}

private fun Modifier.withFocusRequester(requester: FocusRequester?): Modifier =
    requester?.let { this.focusRequester(it) } ?: this

@Composable
internal fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    options: List<String>,
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
                placeholder = { Text("Choose…") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .clickable { onTap(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = { onTap(option) },
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
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    onChange(joinBloodPressure(digits, diastolic))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            Text(
                "/",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedTextField(
                value = diastolic,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    onChange(joinBloodPressure(systolic, digits))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Labeled(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(option) },
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun PickerButton(
    label: String?,
    text: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            TextButton(onClick = {
                val picked = state.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                }
                if (picked != null) onConfirm(picked) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
