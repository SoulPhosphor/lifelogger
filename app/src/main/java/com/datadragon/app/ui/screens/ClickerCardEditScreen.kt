package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerValues
import com.datadragon.app.ui.ClickerCardEditViewModel
import com.datadragon.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_STORE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val TIME_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun numberInput(input: String, maxDigits: Int): String {
    val negative = input.startsWith("-")
    val digits = input.filter { it.isDigit() }.take(maxDigits)
    return (if (negative) "-" else "") + digits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickerCardEditScreen(
    cardId: Long,
    onBack: () -> Unit,
    viewModel: ClickerCardEditViewModel = viewModel(),
) {
    val fields = remember { mutableStateListOf<ClickerField>() }
    val values = remember { mutableStateMapOf<String, String>() }
    var displayDate by remember { mutableStateOf<String?>(null) }
    var displayTime by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf<ClickerCard?>(null) }

    LaunchedEffect(cardId) {
        val card = viewModel.loadCard(cardId)
        if (card != null) {
            original = card
            fields.clear()
            fields.addAll(viewModel.loadFields(card.clickerLogId))
            values.clear()
            values.putAll(ClickerValues.decode(card.valuesJson))
            displayDate = card.displayDate
            displayTime = card.displayTime
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    val current = original
                    TextButton(
                        enabled = current != null,
                        onClick = {
                            if (current != null) {
                                viewModel.save(
                                    card = current.copy(
                                        displayDate = displayDate,
                                        displayTime = displayTime,
                                        valuesJson = ClickerValues.encode(values.toMap()),
                                    ),
                                    onSaved = onBack,
                                )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            // Card stamp override — only the parts the card actually carries.
            if (displayDate != null || displayTime != null) {
                Text("Card Stamp", style = AppTheme.textStyles.sectionHeader)
                if (displayDate != null) {
                    DateEditRow(label = "Date", iso = displayDate, onSet = { displayDate = it })
                }
                if (displayTime != null) {
                    TimeEditRow(label = "Time", iso = displayTime, onSet = { displayTime = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            fields.forEach { field ->
                when (field.type) {
                    ClickerFieldType.CLICK_TRACKER, ClickerFieldType.WRITE_IN_NUMBER -> {
                        val maxDigits = field.maxDigits ?: 6
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = numberInput(it, maxDigits) },
                            singleLine = true,
                            label = { Text(field.label) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ClickerFieldType.DATE ->
                        DateEditRow(label = field.label, iso = values[field.id], onSet = { values[field.id] = it })
                    ClickerFieldType.TIME ->
                        TimeEditRow(label = field.label, iso = values[field.id], onSet = { values[field.id] = it })
                    ClickerFieldType.DATE_TIME ->
                        DateTimeEditRow(label = field.label, value = values[field.id], onSet = { values[field.id] = it })
                    ClickerFieldType.TEXT ->
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = it },
                            singleLine = true,
                            label = { Text(field.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    ClickerFieldType.MULTITEXT ->
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = it },
                            label = { Text(field.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                }
            }
        }
    }
}

@Composable
private fun DateEditRow(label: String, iso: String?, onSet: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val shown = iso?.let { runCatching { LocalDate.parse(it).format(DATE_DISPLAY_FORMAT) }.getOrNull() } ?: "Not set"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $shown", modifier = Modifier.weight(1f))
        IconButton(onClick = { show = true }) {
            Icon(painterResource(R.drawable.ic_edit_calendar), contentDescription = "Edit $label")
        }
    }
    if (show) {
        DatePickerModal(
            initial = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { show = false },
            onConfirm = { show = false; onSet(it.toString()) },
        )
    }
}

@Composable
private fun TimeEditRow(label: String, iso: String?, onSet: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val parsed = iso?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val shown = parsed?.format(TIME_DISPLAY_FORMAT) ?: "Not set"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $shown", modifier = Modifier.weight(1f))
        IconButton(onClick = { show = true }) {
            Icon(painterResource(R.drawable.ic_edit_calendar), contentDescription = "Edit $label")
        }
    }
    if (show) {
        TimePickerModal(
            initial = parsed ?: LocalTime.of(12, 0),
            onDismiss = { show = false },
            onConfirm = { show = false; onSet(it.format(DATE_STORE_FORMAT)) },
        )
    }
}

@Composable
private fun DateTimeEditRow(label: String, value: String?, onSet: (String) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    val parts = value?.split(" ")
    val existingDate = parts?.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val existingTime = parts?.getOrNull(1)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val shown = when {
        existingDate != null && existingTime != null ->
            "${existingDate.format(DATE_DISPLAY_FORMAT)} at ${existingTime.format(TIME_DISPLAY_FORMAT)}"
        else -> "Not set"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $shown", modifier = Modifier.weight(1f))
        IconButton(onClick = { showDate = true }) {
            Icon(painterResource(R.drawable.ic_edit_calendar), contentDescription = "Edit $label")
        }
    }
    if (showDate) {
        DatePickerModal(
            initial = existingDate,
            onDismiss = { showDate = false },
            onConfirm = { pendingDate = it; showDate = false; showTime = true },
        )
    }
    if (showTime) {
        TimePickerModal(
            initial = existingTime ?: LocalTime.of(12, 0),
            onDismiss = { showTime = false },
            onConfirm = { t ->
                val d = pendingDate ?: existingDate ?: LocalDate.now()
                onSet("$d ${t.format(DATE_STORE_FORMAT)}")
                showTime = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                }
                if (picked != null) onConfirm(picked) else onDismiss()
            }) { Text("Okay") }
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
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Okay") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
