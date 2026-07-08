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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldType
import com.datadragon.app.ui.FollowUpNoteViewModel

/**
 * Add or edit a follow-up note on an entry.
 *
 * The screen shows the entry's own data above a note box. When the log is
 * **locked**, that data is a read-only readout — the same `label: value` layout
 * as the entry cards on the log screen. When the log is **unlocked**, the entry's
 * fields are shown as the editable entry form instead, so saving here also saves
 * any field edits (mirroring the New/Edit Entry screen), with "Follow-Up Note" at
 * the top.
 *
 * Follow-up notes themselves are always editable, regardless of the lock state.
 * Save commits and closes; backing out discards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpNoteScreen(
    logId: String?,
    entryId: String?,
    noteId: String?,
    onBack: () -> Unit,
    viewModel: FollowUpNoteViewModel = viewModel(),
) {
    val logIdL = logId?.toLongOrNull()
    val entryIdL = entryId?.toLongOrNull()
    val noteIdL = noteId?.toLongOrNull()
    LaunchedEffect(logIdL, entryIdL, noteIdL) {
        if (logIdL != null && entryIdL != null) viewModel.load(logIdL, entryIdL, noteIdL)
    }

    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entryValues by viewModel.entryValues.collectAsStateWithLifecycle()
    val entryTimestamp by viewModel.entryTimestamp.collectAsStateWithLifecycle()
    val initialNoteText by viewModel.initialNoteText.collectAsStateWithLifecycle()

    // The follow-up note text. Seeded once from the loaded note (or "" when new).
    var noteText by remember { mutableStateOf("") }
    var noteSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(initialNoteText) {
        val loaded = initialNoteText ?: return@LaunchedEffect
        if (!noteSeeded) {
            noteText = loaded
            noteSeeded = true
        }
    }

    // Editable-entry form state, used only when the log is unlocked. Seeded once
    // from the entry's stored values, exactly like the New/Edit Entry screen.
    val textValues = remember { mutableStateMapOf<String, String>() }
    val multiValues = remember { mutableStateMapOf<String, Set<String>>() }
    var entryNotes by remember { mutableStateOf("") }
    var entrySeeded by remember { mutableStateOf(false) }
    LaunchedEffect(entryValues, fields) {
        val values = entryValues ?: return@LaunchedEffect
        if (entrySeeded) return@LaunchedEffect
        fields.forEach { field ->
            if (field.type == FieldType.MULTIPLE) {
                val selected = EntryValues.selectedOptions(values, field.label)
                if (selected.isNotEmpty()) multiValues[field.label] = selected
            } else {
                EntryValues.rawValue(values, field.label)?.let { textValues[field.label] = it }
            }
        }
        EntryValues.notes(values)?.let { entryNotes = it }
        entrySeeded = true
    }

    // When editing the entry (unlocked), don't let a required field be blanked.
    val requiredOk = fields.all { field ->
        when {
            !field.required -> true
            field.type == FieldType.MULTIPLE -> multiValues[field.label].orEmpty().isNotEmpty()
            else -> !textValues[field.label].isNullOrBlank()
        }
    }
    val canSave = noteText.isNotBlank() && (locked || requiredOk)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Follow-Up Note") },
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
                                noteText = noteText,
                                entryValues = if (locked) {
                                    null
                                } else {
                                    collectValues(fields, textValues, multiValues, entryNotes)
                                },
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
                // Keep the note box (and any focused field) above the keyboard.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            entryTimestamp?.let { iso ->
                Text(
                    "Entry · ${EntryValues.displayEntryDateTime(iso)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("Follow-Up Note", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp),
            )

            HorizontalDivider()

            if (locked) {
                // Read-only copy of the entry's data, as shown on the log screen.
                val values = entryValues
                if (values != null) {
                    fields.forEach { field ->
                        EntryValues.displayValue(field, values)?.let { value ->
                            ReadonlyField(label = field.label, value = value)
                        }
                    }
                    EntryValues.notes(values)?.let { ReadonlyField(label = "Notes", value = it) }
                }
            } else {
                // Editable entry form (the log is unlocked), pre-filled.
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
                    value = entryNotes,
                    onValueChange = { entryNotes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        }
    }
}

/** One `label: value` line, matching the read-only readout on the log screen. */
@Composable
private fun ReadonlyField(label: String, value: String) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}
