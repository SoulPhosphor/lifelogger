package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
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

/**
 * Minimum height of a Multi-Line Text box and the Follow-Up Notes box: the same
 * as a form entry's Notes box. Like that box, there is no cap on how many lines
 * can be typed — the box grows to fit.
 */

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
    var allowFollowUp by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        val card = viewModel.loadCard(cardId)
        if (card != null) {
            original = card
            allowFollowUp = viewModel.loadLog(card.clickerLogId)?.allowFollowUp ?: false
            fields.clear()
            fields.addAll(viewModel.loadFields(card.clickerLogId))
            values.clear()
            values.putAll(ClickerValues.decode(card.valuesJson))
            displayDate = card.displayDate
            displayTime = card.displayTime
        }
    }

    // Leaving with unsaved edits asks first, the same as editing a form entry.
    // Cleared values are dropped from the map, so a field emptied back out
    // compares equal to one that was never filled in.
    val current = original
    val dirty = current != null && (
        displayDate != current.displayDate ||
            displayTime != current.displayTime ||
            values.filterValues { it.isNotEmpty() } != ClickerValues.decode(current.valuesJson).filterValues { it.isNotEmpty() }
        )
    var showDiscard by remember { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Card") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = current != null,
                        onClick = {
                            if (current != null) {
                                viewModel.save(
                                    card = current.copy(
                                        displayDate = displayDate,
                                        displayTime = displayTime,
                                        valuesJson = ClickerValues.encode(values.filterValues { it.isNotEmpty() }),
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.screenInset),
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
                HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.spacing.related))
            }

            // Editable text boxes carry their label above the box, never inside it
            // (docs/STYLE.md §1 rule 7), the same as a form entry.
            fields.forEach { field ->
                when (field.type) {
                    ClickerFieldType.CLICK_TRACKER, ClickerFieldType.WRITE_IN_NUMBER -> Labeled(field.label) {
                        val maxDigits = field.maxDigits ?: 6
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = numberInput(it, maxDigits) },
                            singleLine = true,
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
                    ClickerFieldType.TEXT -> Labeled(field.label) {
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ClickerFieldType.MULTITEXT -> Labeled(field.label) {
                        OutlinedTextField(
                            value = ClickerValues.text(values, field.id),
                            onValueChange = { values[field.id] = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.notesMinHeight),
                        )
                    }
                }
            }

            // Follow-Up Notes sit at the bottom, in the same box as a form entry's Notes.
            if (allowFollowUp) {
                HorizontalDivider()
                Labeled("Follow-Up Notes") {
                    OutlinedTextField(
                        value = ClickerValues.text(values, ClickerValues.FOLLOW_UP_KEY),
                        onValueChange = { values[ClickerValues.FOLLOW_UP_KEY] = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppTheme.sizes.notesMinHeight),
                    )
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
