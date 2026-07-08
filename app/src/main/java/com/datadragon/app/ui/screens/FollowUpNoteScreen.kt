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
import com.datadragon.app.ui.FollowUpNoteViewModel

/**
 * Add or edit a follow-up note on an entry.
 *
 * The screen shows the follow-up note text box at the top (always editable),
 * then the entry's form data below as a read-only readout — the same
 * `label: value` layout as the entry cards on the log screen.
 *
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

    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entryValues by viewModel.entryValues.collectAsStateWithLifecycle()
    val entryTimestamp by viewModel.entryTimestamp.collectAsStateWithLifecycle()
    val initialNoteText by viewModel.initialNoteText.collectAsStateWithLifecycle()

    var noteText by remember { mutableStateOf("") }
    var noteSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(initialNoteText) {
        val loaded = initialNoteText ?: return@LaunchedEffect
        if (!noteSeeded) {
            noteText = loaded
            noteSeeded = true
        }
    }

    val canSave = noteText.isNotBlank()

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
                                entryValues = null,
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

            val values = entryValues
            if (values != null) {
                fields.forEach { field ->
                    EntryValues.displayValue(field, values)?.let { value ->
                        ReadonlyField(label = field.label, value = value)
                    }
                }
                EntryValues.notes(values)?.let { ReadonlyField(label = "Notes", value = it) }
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
