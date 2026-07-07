package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldType
import com.datadragon.app.ui.NewEntryViewModel
import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * New Entry screen (Phase 4).
 *
 * A full-screen [Scaffold] destination — never a dialog or bottom sheet (locked
 * by docs/UI_SPEC.md §6). The body is generated from the log's field
 * definitions; the timestamp auto-fills and Save writes a real entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val timestamp = remember {
        LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault())
        )
    }

    // Form state: single-valued fields (and date/time/scale as strings) in one
    // map, multi-select fields in the other, plus the free-text Notes box.
    val textValues = remember { mutableStateMapOf<String, String>() }
    val multiValues = remember { mutableStateMapOf<String, Set<String>>() }
    var notes by remember { mutableStateOf("") }

    // When editing, pre-fill the form from the entry's stored values once loaded.
    LaunchedEffect(initialValues, fields) {
        val values = initialValues ?: return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.MULTIPLE) {
                val selected = EntryValues.selectedOptions(values, field.label)
                if (selected.isNotEmpty()) multiValues[field.label] = selected
            } else {
                EntryValues.rawValue(values, field.label)?.let { textValues[field.label] = it }
            }
        }
        EntryValues.notes(values)?.let { notes = it }
    }

    // Pre-fill any `datetime` field flagged `default: now` with the current time
    // (new entries only — when editing, the stored value is loaded above instead).
    LaunchedEffect(fields, isEditing) {
        if (isEditing) return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.DATETIME && field.defaultNow && textValues[field.label] == null) {
                textValues[field.label] = LocalDateTime.now()
                    .withSecond(0)
                    .withNano(0)
                    .format(EntryValues.DATETIME_STORAGE)
            }
        }
    }

    val canSave = fields.all { field ->
        when {
            !field.required -> true
            field.type == FieldType.MULTIPLE -> multiValues[field.label].orEmpty().isNotEmpty()
            else -> !textValues[field.label].isNullOrBlank()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Entry" else "New Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            viewModel.save(
                                values = collectValues(fields, textValues, multiValues, notes),
                                onSaved = onBack,
                            )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                EntryFieldControl(
                    field = field,
                    textValues = textValues,
                    multiValues = multiValues,
                )
            }

            HorizontalDivider()

            Text("Notes", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        }
    }
}

/** Build the value map that gets serialized into the entry's valuesJson. */
private fun collectValues(
    fields: List<com.datadragon.app.data.FieldDef>,
    textValues: Map<String, String>,
    multiValues: Map<String, Set<String>>,
    notes: String,
): Map<String, JsonElement> = buildMap {
    fields.forEach { field ->
        if (field.type == FieldType.MULTIPLE) {
            // Keep the option order defined in the schema.
            val selected = multiValues[field.label].orEmpty()
            val ordered = field.options.filter { it in selected }
            if (ordered.isNotEmpty()) put(field.label, EntryValues.stringArray(ordered))
        } else {
            val value = textValues[field.label]?.trim().orEmpty()
            if (value.isNotEmpty()) put(field.label, EntryValues.string(value))
        }
    }
    val trimmedNotes = notes.trim()
    if (trimmedNotes.isNotEmpty()) put(EntryValues.NOTES_KEY, EntryValues.string(trimmedNotes))
}
