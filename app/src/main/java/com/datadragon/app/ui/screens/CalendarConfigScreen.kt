package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The calendar configuration screen, opened by "Edit Calendar" in the Form
 * Editor (docs/UI_SPEC.md — Calendar feature).
 *
 * Phase 1 wires only the entry point: this is a back-out skeleton. The real
 * configuration UI — Choose Calendar Type, Calendar Label, Description, the
 * per-type color/range controls, and multiple-calendar handling — is built in
 * the following phases. Its top-bar title is intentionally left blank until its
 * wording is confirmed, so nothing here is invented copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarConfigScreen(
    logId: String?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Body is built out in later phases.
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}
