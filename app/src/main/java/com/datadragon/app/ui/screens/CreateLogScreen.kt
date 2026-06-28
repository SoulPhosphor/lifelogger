package com.datadragon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.ui.CreateLogViewModel

private const val FIELD_TYPES_REFERENCE = """text         — a single line of text
multiline    — multi-line text box. Set "lines" for visible height
date         — month/day/year picker
time         — 12-hour time with AM/PM
dropdown     — pick one item from a list
scale        — pick a number in a range. Set "from" and "to"
yesno        — Yes / No / Unknown / Not Applicable
number       — type a number. Set "digits" for max digits allowed
multiple     — pick several items from a list (tappable chips)

Any field can add "required" to prevent saving without it."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLogScreen(
    onBack: () -> Unit,
    viewModel: CreateLogViewModel = viewModel(),
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var formMarkdown by remember { mutableStateOf("") }
    var showFieldTypes by remember { mutableStateOf(false) }
    // Null until the user previews; cleared whenever the Form Markdown changes so
    // the preview always reflects the current text (FORM_MARKDOWN_SPEC §5).
    var preview by remember { mutableStateOf<FormMarkdownParser.ParseResult?>(null) }

    val canSave = name.isNotBlank() && preview != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save persists the template (name from the box, fields from the
                    // parsed schema, plus the original Form Markdown), then returns
                    // Home. Enabled only after a preview.
                    TextButton(
                        onClick = {
                            viewModel.save(
                                name = name,
                                description = description,
                                schemaJson = preview?.toSchemaJson() ?: "[]",
                                formMarkdown = formMarkdown,
                                onSaved = onBack,
                            )
                        },
                        enabled = canSave,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log name", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Description (optional)", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Define fields", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showFieldTypes = !showFieldTypes }) {
                    Text("Field types")
                    Icon(
                        imageVector = if (showFieldTypes) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showFieldTypes) "Hide field types" else "Show field types",
                    )
                }
            }

            AnimatedVisibility(visible = showFieldTypes) {
                Card {
                    Text(
                        text = FIELD_TYPES_REFERENCE,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            OutlinedTextField(
                value = formMarkdown,
                onValueChange = {
                    formMarkdown = it
                    preview = null // force a fresh preview before saving
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("Paste Form Markdown here…") },
            )

            OutlinedButton(
                onClick = {
                    val result = FormMarkdownParser.parse(formMarkdown)
                    // Boxes win, but auto-fill an empty box from the pasted #/> lines.
                    if (name.isBlank()) result.name?.let { name = it }
                    if (description.isBlank()) result.description?.let { description = it }
                    preview = result
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preview form")
            }

            preview?.let { PreviewSection(it) }
        }
    }
}

@Composable
private fun PreviewSection(result: FormMarkdownParser.ParseResult) {
    HorizontalDivider()
    Text("Preview", style = MaterialTheme.typography.titleMedium)

    if (result.fields.isEmpty()) {
        Text(
            "No fields defined yet. You can still save a log with no fields.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        result.fields.forEach { field ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(field.label, style = MaterialTheme.typography.titleSmall)
                    Text(field.summary(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (result.issues.isNotEmpty()) {
        Text(
            "Problems",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        result.issues.forEach { issue ->
            Text("• $issue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (result.skipped.isNotEmpty()) {
        Text("Skipped lines", style = MaterialTheme.typography.titleSmall)
        result.skipped.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A short human-readable description of a parsed field for the preview. */
private fun FieldDef.summary(): String {
    val base = when (type) {
        FieldType.TEXT -> "Single line of text"
        FieldType.MULTILINE -> "Multi-line text" + (lines?.let { " ($it lines)" } ?: "")
        FieldType.NUMBER -> "Number" + (digits?.let { " (up to $it digits)" } ?: "")
        FieldType.DROPDOWN -> "Pick one: " + options.joinToString(", ")
        FieldType.MULTIPLE -> "Pick several: " + options.joinToString(", ")
        FieldType.SCALE -> "Scale $from–$to"
        FieldType.YESNO -> "Yes / No / Unknown / Not Applicable"
        FieldType.DATE -> "Date"
        FieldType.TIME -> "Time"
        FieldType.DATETIME -> "Date & time" + (if (defaultNow) " (defaults to now)" else "")
    }
    return if (required) "$base · required" else base
}
