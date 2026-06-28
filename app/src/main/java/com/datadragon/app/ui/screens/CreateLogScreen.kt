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
    val canSave = name.isNotBlank()

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
                    // Save persists the template, then returns to Home where it
                    // appears in the list. Name is required.
                    TextButton(
                        onClick = { viewModel.save(name, description, onSaved = onBack) },
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
                onValueChange = { formMarkdown = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("Paste Form Markdown here…") },
            )

            OutlinedButton(
                onClick = { /* Phase 3: parse + preview the form */ },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preview form")
            }
        }
    }
}
