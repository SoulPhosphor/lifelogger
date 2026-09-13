package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CalendarType
import com.datadragon.app.ui.CalendarConfigViewModel
import com.datadragon.app.ui.components.AppButton

/**
 * The single, vertically scrollable Edit Calendar screen (owner direction: one
 * screen, never a wizard). It opens for a new calendar from the Form Editor's
 * "Edit Calendar" button, or for an existing one from the calendars list at the
 * bottom of Edit Form, with that calendar's saved values loaded.
 *
 * This phase builds the top of the screen — Choose Calendar Type, Calendar Label,
 * Description — plus Save Calendar and Add Another Calendar. The type-specific
 * sections (data source, calculation rule, colors) are added by later phases,
 * on this same screen and in the same scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarConfigScreen(
    logId: String?,
    calendarId: String?,
    onBack: () -> Unit,
    viewModel: CalendarConfigViewModel = viewModel(),
) {
    val templateId = logId?.toLongOrNull()
    val existingCalendarId = calendarId?.toLongOrNull()
    LaunchedEffect(templateId, existingCalendarId) {
        templateId?.let { viewModel.load(it, existingCalendarId) }
    }

    val initial by viewModel.initial.collectAsStateWithLifecycle()

    // Screen-owned editable state, seeded once from the loaded values. Type is
    // held as its token string so it survives process death in the saved state.
    var seeded by rememberSaveable { mutableStateOf(false) }
    var typeToken by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var savedTypeToken by rememberSaveable { mutableStateOf<String?>(null) }
    var savedLabel by rememberSaveable { mutableStateOf("") }
    var savedDescription by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(initial) {
        val i = initial
        if (!seeded && i != null) {
            typeToken = i.type?.token
            label = i.label
            description = i.description
            savedTypeToken = typeToken
            savedLabel = label
            savedDescription = description
            seeded = true
        }
    }

    val type = typeToken?.let { CalendarType.fromToken(it) }
    val canSave = type != null && label.isNotBlank()
    val dirty = seeded &&
        (typeToken != savedTypeToken || label != savedLabel || description != savedDescription)

    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    fun markSaved() {
        savedTypeToken = typeToken
        savedLabel = label
        savedDescription = description
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Calendar") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
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
            CalendarTypeDropdown(
                selected = type,
                onSelected = { typeToken = it.token },
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Calendar Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Optional: Describes what is tracked. Shows at top of calendar.") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )

            AppButton(
                onClick = {
                    val chosen = type ?: return@AppButton
                    viewModel.save(chosen, label, description) {
                        markSaved()
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Calendar")
            }

            AppButton(
                onClick = {
                    val chosen = type ?: return@AppButton
                    viewModel.save(chosen, label, description) {
                        // Keep the just-saved calendar; start a fresh, blank one on
                        // this same screen. The next Save inserts a new calendar.
                        viewModel.prepareNew()
                        typeToken = null
                        label = ""
                        description = ""
                        markSaved()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Another Calendar")
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

/** The "Choose Calendar Type" dropdown. Its floating label is the prompt; the box
 *  is empty until a type is picked. Option names are the exact owner-facing labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTypeDropdown(
    selected: CalendarType?,
    onSelected: (CalendarType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Choose Calendar Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CalendarType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The exact owner-facing type names shown in "Choose Calendar Type". */
private fun CalendarType.displayName(): String = when (this) {
    CalendarType.HEAT_MAP -> "Heat Map"
    CalendarType.YES_NO -> "Yes/No"
    CalendarType.MIN_MAX -> "Min/Max Value"
}
