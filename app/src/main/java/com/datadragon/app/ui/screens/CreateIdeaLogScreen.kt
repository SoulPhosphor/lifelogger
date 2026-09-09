package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.DEFAULT_IDEA_LINES
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.ui.IdeaLogEditorViewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Create a new Idea Log. It uses the visual builder only — Ideas has no Paste
 * tab — and starts with the one Text (Multiline) field every Idea Log begins
 * with, whether or not Use Default Fields is switched on.
 */
@Composable
fun CreateIdeaLogScreen(
    onBack: () -> Unit,
    viewModel: IdeaLogEditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    var name by rememberSaveable { mutableStateOf("") }
    var automaticTimestamping by rememberSaveable { mutableStateOf(false) }
    var allowArchiving by rememberSaveable { mutableStateOf(false) }
    var showEntireIdeaCard by rememberSaveable { mutableStateOf(false) }
    var previewLines by rememberSaveable { mutableStateOf(DEFAULT_IDEA_LINES.toString()) }
    var useDefaultFields by rememberSaveable { mutableStateOf(false) }
    var sortNewestFirst by rememberSaveable { mutableStateOf(true) }

    val fields = rememberSaveable(saver = ideaFieldsSaver) { startingIdeaFields() }

    val canSave = name.isNotBlank() && fields.isNotEmpty() && fields.all { it.isValid() }
    // Backing out warns once the log has been given a name or its fields touched
    // beyond the single one it opens with.
    val dirty = name.isNotBlank() || fields.size > 1 || fields.any { it.label != "Text" }

    IdeaLogEditorScaffold(
        screenTitle = "New Idea Log",
        name = name,
        onNameChange = { name = it },
        automaticTimestamping = automaticTimestamping,
        onAutomaticTimestampingChange = { automaticTimestamping = it },
        allowArchiving = allowArchiving,
        onAllowArchivingChange = { allowArchiving = it },
        showEntireIdeaCard = showEntireIdeaCard,
        onShowEntireIdeaCardChange = { showEntireIdeaCard = it },
        previewLines = previewLines,
        onPreviewLinesChange = { previewLines = it },
        useDefaultFields = useDefaultFields,
        onUseDefaultFieldsChange = { on ->
            useDefaultFields = on
            // Switching on fills in whichever defaults are missing. Switching off
            // is only the switch: fields already added are never taken away.
            if (on) addMissingDefaultFields(fields)
        },
        sortNewestFirst = sortNewestFirst,
        onSortDirectionChange = { sortNewestFirst = it },
        fields = fields,
        canSave = canSave,
        dirty = dirty,
        onSave = {
            // Every field here is newly created, so they all honor the
            // auto-capitalize settings, exactly as the Forms builder does.
            val saved = fields.filter { it.isValid() }
                .map { it.toFieldDef().autoCapitalized(settings) }
            viewModel.create(
                name = name,
                fields = saved,
                automaticTimestamping = automaticTimestamping,
                allowArchiving = allowArchiving,
                showEntireIdeaCard = showEntireIdeaCard,
                previewLines = clampedLineCount(previewLines),
                sortTimestampFieldId = sortTimestampFieldIdOf(fields),
                sortNewestFirst = sortNewestFirst,
                onSaved = onBack,
            )
        },
        onBack = onBack,
    )
}
