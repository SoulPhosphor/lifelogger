package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * New Entry screen.
 *
 * This is intentionally a full-screen [Scaffold] destination, never a dialog or
 * bottom sheet (locked by docs/UI_SPEC.md §6). In Phase 4 the body is generated
 * from the log's schemaJson; for now it shows the auto date/time line plus a
 * placeholder Notes box to prove the full-screen form route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntryScreen(
    logId: String?,
    onBack: () -> Unit,
) {
    val timestamp = remember {
        LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault())
        )
    }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Phase 4 wires Save to persistence; for now it returns to the log.
                    TextButton(onClick = onBack) { Text("Save") }
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
            Text(
                text = "Date / time:  $timestamp (auto)",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Notes", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )

            Text(
                text = "Entry fields generate from this log's definition in a later phase.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
