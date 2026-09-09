package com.datadragon.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.datadragon.app.data.DEFAULT_IDEA_LINES
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.ui.IdeaLogEditorViewModel

/**
 * Edit an existing Idea Log: the same settings and the same field builder as
 * creation, seeded from what was saved.
 *
 * Turning **Allow Archiving** off while archived ideas still exist is refused —
 * those ideas would have nowhere to be reached from, and unarchiving them
 * silently would move data the user never asked to move. The switch stays on and
 * the screen says what has to happen first.
 */
@Composable
fun EditIdeaLogScreen(
    ideaLogId: String?,
    onBack: () -> Unit,
    viewModel: IdeaLogEditorViewModel = viewModel(),
) {
    val id = ideaLogId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val loadedFields by viewModel.loadedFields.collectAsStateWithLifecycle()

    // Null until the saved log has been read in; that also gates Save, so an
    // empty schema can never be written over a real one.
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var automaticTimestamping by rememberSaveable { mutableStateOf(false) }
    var allowArchiving by rememberSaveable { mutableStateOf(false) }
    var showEntireIdeaCard by rememberSaveable { mutableStateOf(false) }
    var previewLines by rememberSaveable { mutableStateOf(DEFAULT_IDEA_LINES.toString()) }
    var useDefaultFields by rememberSaveable { mutableStateOf(false) }
    var sortNewestFirst by rememberSaveable { mutableStateOf(true) }
    var showArchivedIdeasExist by rememberSaveable { mutableStateOf(false) }

    val fields = rememberSaveable(saver = ideaFieldsSaver) { mutableStateListOf() }
    var seeded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(loaded, loadedFields) {
        val log = loaded ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        name = log.name
        automaticTimestamping = log.automaticTimestamping
        allowArchiving = log.allowArchiving
        showEntireIdeaCard = log.showEntireIdeaCard
        previewLines = log.previewLines.toString()
        sortNewestFirst = log.sortNewestFirst
        fields.clear()
        loadedFields.forEach { fields.add(it.toDraft(sortByTimestamp = it.id == log.sortTimestampFieldId)) }
        seeded = true
    }

    val canSave = seeded && !name.isNullOrBlank() && fields.isNotEmpty() && fields.all { it.isValid() }
    // Any edit at all counts; the saved values are only known once seeded.
    val dirty = seeded && (
        name != loaded?.name ||
            automaticTimestamping != loaded?.automaticTimestamping ||
            allowArchiving != loaded?.allowArchiving ||
            showEntireIdeaCard != loaded?.showEntireIdeaCard ||
            previewLines != loaded?.previewLines?.toString() ||
            sortNewestFirst != loaded?.sortNewestFirst ||
            fields.map { it.toFieldDef() } != loadedFields
        )

    IdeaLogEditorScaffold(
        screenTitle = "Edit Idea Log",
        name = name.orEmpty(),
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
            // Adds only what's missing; nothing already in the log is replaced.
            if (on) addMissingDefaultFields(fields)
        },
        sortNewestFirst = sortNewestFirst,
        onSortDirectionChange = { sortNewestFirst = it },
        fields = fields,
        canSave = canSave,
        dirty = dirty,
        onSave = {
            // Auto-capitalize only fields added in this session; a field that was
            // already saved keeps the spelling it has.
            val existingIds = loadedFields.map { it.id }.toSet()
            viewModel.update(
                name = name.orEmpty(),
                fields = fields.filter { it.isValid() }
                    .map { it.toFieldDef() }
                    .map { def ->
                        if (def.id in existingIds) def else def.autoCapitalized(settings)
                    },
                automaticTimestamping = automaticTimestamping,
                allowArchiving = allowArchiving,
                showEntireIdeaCard = showEntireIdeaCard,
                previewLines = clampedLineCount(previewLines),
                sortTimestampFieldId = sortTimestampFieldIdOf(fields),
                sortNewestFirst = sortNewestFirst,
                onBlocked = {
                    // Keep the log archivable and explain why nothing was saved.
                    allowArchiving = true
                    showArchivedIdeasExist = true
                },
                onSaved = onBack,
            )
        },
        onBack = onBack,
    )

    if (showArchivedIdeasExist) {
        AlertDialog(
            onDismissRequest = { showArchivedIdeasExist = false },
            title = { Text("Archived Ideas Exist") },
            text = {
                Text(
                    "Archived ideas must be unarchived or deleted before Allow " +
                        "Archiving can be turned off.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showArchivedIdeasExist = false }) { Text("OK") }
            },
        )
    }
}
