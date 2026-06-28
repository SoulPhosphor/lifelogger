package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** The four fixed yesno options (docs/UI_SPEC.md §10). */
private val YESNO_OPTIONS = listOf("Yes", "No", "Unknown", "Not Applicable")

/** Scales with this many or fewer steps render as tappable pills; more → dropdown. */
private const val SCALE_PILL_LIMIT = 5

private fun FieldDef.displayLabel(): String = if (required) "$label *" else label

/**
 * Renders one generated entry control for [field], reading and writing into the
 * shared form state. Single-valued fields live in [textValues]; `multiple`
 * fields live in [multiValues]. Storage forms follow [EntryValues].
 */
@Composable
fun EntryFieldControl(
    field: FieldDef,
    textValues: SnapshotStateMap<String, String>,
    multiValues: SnapshotStateMap<String, Set<String>>,
) {
    val label = field.displayLabel()
    when (field.type) {
        FieldType.TEXT -> Labeled(label) {
            OutlinedTextField(
                value = textValues[field.label].orEmpty(),
                onValueChange = { textValues[field.label] = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldType.MULTILINE -> Labeled(label) {
            val minHeight = ((field.lines ?: 4).coerceIn(2, 12) * 24).dp
            OutlinedTextField(
                value = textValues[field.label].orEmpty(),
                onValueChange = { textValues[field.label] = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldType.DROPDOWN -> DropdownField(
            label = label,
            options = field.options,
            selected = textValues[field.label].orEmpty(),
            onSelected = { textValues[field.label] = it },
        )

        FieldType.YESNO -> DropdownField(
            label = label,
            options = YESNO_OPTIONS,
            selected = textValues[field.label].orEmpty(),
            onSelected = { textValues[field.label] = it },
        )

        FieldType.SCALE -> ScaleField(
            label = label,
            from = field.from ?: 0,
            to = field.to ?: 0,
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
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleField(
    label: String,
    from: Int,
    to: Int,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val numbers = if (to >= from) (from..to).toList() else emptyList()
    if (numbers.size in 1..SCALE_PILL_LIMIT) {
        Labeled(label) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.forEach { n ->
                    val value = n.toString()
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(value) },
                    )
                }
            }
        }
    } else {
        DropdownField(
            label = label,
            options = numbers.map { it.toString() },
            selected = selected,
            onSelected = onSelected,
        )
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
private fun DateField(label: String, stored: String?, onChange: (String) -> Unit) {
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
private fun TimeField(label: String, stored: String?, onChange: (String) -> Unit) {
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
private fun DateTimeField(label: String, stored: String?, onChange: (String) -> Unit) {
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
        OutlinedButton(onClick = onClick) {
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
