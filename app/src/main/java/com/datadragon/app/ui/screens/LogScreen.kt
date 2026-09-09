package com.datadragon.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.components.ExportFormatDialog
import com.datadragon.app.ui.components.ExportFormatOption
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.export.ExportContent
import com.datadragon.app.export.LogExport
import com.datadragon.app.ui.LogViewModel
import com.datadragon.app.ui.SortCategory
import com.datadragon.app.ui.theme.DeleteRed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logId: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onEditForm: () -> Unit,
    onOpenFollowUp: (entryId: Long, noteId: Long?) -> Unit,
    viewModel: LogViewModel = viewModel(),
) {
    val id = logId?.toLongOrNull()
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }
    val template by viewModel.template.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val notesByEntry by viewModel.notesByEntry.collectAsStateWithLifecycle()
    val sortCategories by viewModel.sortCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locked = template?.locked ?: true
    val allowAppendedNotes = template?.allowAppendedNotes ?: false
    val automaticTimestamping = template?.automaticTimestamping ?: false

    var confirmDeleteLog by remember { mutableStateOf(false) }
    var showFormatChooser by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LogEntry?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    var gearMenuOpen by remember { mutableStateOf(false) }
    // Export dialog: whether to include follow-up notes.
    var includeFollowUps by remember { mutableStateOf(true) }

    // "Show marked only" filter. Off by default and ephemeral — it resets to off
    // whenever the screen is reopened. Its toggle only appears when at least one
    // entry is marked; with nothing marked there is no star and every entry shows.
    var showMarkedOnly by remember { mutableStateOf(false) }
    val anyMarked = entries.any { it.marked }
    val visibleEntries = if (showMarkedOnly && anyMarked) entries.filter { it.marked } else entries

    // The file the user is saving. They choose the destination and name via the
    // system "Save to…" sheet; we write the bytes to whatever location it returns.
    var pendingExport by remember { mutableStateOf<ExportContent?>(null) }
    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(export.bytes) }
                    ?: error("No output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Saved ${export.suggestedName}" else "Couldn't save file",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val startSave: (ExportContent) -> Unit = { content ->
        pendingExport = content
        showFormatChooser = false
        saveDocument.launch(content.suggestedName)
    }

    fun attemptBack() { viewModel.leaveAfterTitleFlush(onBack) }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    EditableTitleField(
                        value = template?.name.orEmpty(),
                        onValueChange = viewModel::setTitle,
                        onFocusLost = viewModel::onTitleFocusLost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    // Left cluster: back, then a gear menu holding the log-level
                    // actions (export, edit form, follow-up notes, unlock, delete).
                    Row {
                        IconButton(onClick = { attemptBack() }) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        Box {
                            IconButton(onClick = { gearMenuOpen = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Log options")
                            }
                            DropdownMenu(
                                expanded = gearMenuOpen,
                                onDismissRequest = { gearMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = {
                                        gearMenuOpen = false
                                        showFormatChooser = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Form") },
                                    onClick = {
                                        gearMenuOpen = false
                                        viewModel.leaveAfterTitleFlush(onEditForm)
                                    },
                                )
                                // Toggle follow-up notes on/off (a check marks "on").
                                DropdownMenuItem(
                                    text = { Text("Follow-Up Notes") },
                                    trailingIcon = {
                                        if (allowAppendedNotes) {
                                            Icon(Icons.Filled.Check, contentDescription = "On")
                                        }
                                    },
                                    onClick = {
                                        gearMenuOpen = false
                                        viewModel.setAllowAppendedNotes(!allowAppendedNotes)
                                    },
                                )
                                if (locked) {
                                    DropdownMenuItem(
                                        text = { Text("Unlock Log") },
                                        onClick = {
                                            gearMenuOpen = false
                                            showUnlock = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete Log") },
                                    onClick = {
                                        gearMenuOpen = false
                                        confirmDeleteLog = true
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Filter star, just left of the plus. Only shown when some entry
                    // is marked; a filled star means the list is limited to marked
                    // entries, an outlined star means every entry shows.
                    if (anyMarked) {
                        IconButton(onClick = { showMarkedOnly = !showMarkedOnly }) {
                            Icon(
                                imageVector = if (showMarkedOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (showMarkedOnly) {
                                    "Showing marked entries only — tap to show all"
                                } else {
                                    "Show marked entries only"
                                },
                            )
                        }
                    }
                    IconButton(onClick = onAddEntry) {
                        Icon(Icons.Filled.Add, contentDescription = "Add entry")
                    }
                },
            )
        },
    ) { padding ->
        // Deliberately not saved: the list opens at the top every time rather than
        // coming back to wherever it was left.
        val listState = remember { LazyListState() }
        // Changing the field or the direction re-sorts the entries and puts the
        // user back at the top of the freshly ordered list.
        LaunchedEffect(selectedCategory?.label, newestFirst) { listState.scrollToItem(0) }

        // The sorting controls are always present, entries or not.
        if (entries.isEmpty()) {
            // With nothing to scroll there is no list to sit at the top of.
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                SortFilterBar(
                    categories = sortCategories,
                    selected = selectedCategory,
                    newestFirst = newestFirst,
                    onSelectCategory = viewModel::selectSortCategory,
                    onSelectNewestFirst = viewModel::selectNewestFirst,
                    onClear = viewModel::clearSort,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No entries yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The controls are the first thing in the list, not a fixed header,
                // so they scroll away with the entries.
                item(key = SORT_FILTER_BAR_KEY) {
                    SortFilterBar(
                        categories = sortCategories,
                        selected = selectedCategory,
                        newestFirst = newestFirst,
                        onSelectCategory = viewModel::selectSortCategory,
                        onSelectNewestFirst = viewModel::selectNewestFirst,
                        onClear = viewModel::clearSort,
                    )
                }
                items(visibleEntries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        fields = fields,
                        appendedNotes = notesByEntry[entry.id].orEmpty(),
                        showAutomaticTimestamp = automaticTimestamping,
                        editable = !locked,
                        appendable = allowAppendedNotes,
                        onDelete = { entryToDelete = entry },
                        onEdit = { onEditEntry(entry.id) },
                        onToggleMark = { viewModel.toggleMark(entry) },
                        onAddNote = { onOpenFollowUp(entry.id, null) },
                        onEditNote = { noteId -> onOpenFollowUp(entry.id, noteId) },
                    )
                }
            }
        }
    }

    if (showFormatChooser) {
        val current = template
        ExportFormatDialog(
            thing = "Form",
            onDismiss = { showFormatChooser = false },
            header = {
                // Only meaningful when this log actually has follow-up notes.
                if (notesByEntry.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeFollowUps,
                            onCheckedChange = { includeFollowUps = it },
                        )
                        Text("Include Follow-Up Notes")
                    }
                }
            },
            options = listOf(
                ExportFormatOption(
                    "Text Document (.txt)",
                    "Simple plain text file",
                ) {
                    if (current != null) {
                        startSave(LogExport.text(current, fields, entries, notesByEntry, includeFollowUps))
                    }
                },
                ExportFormatOption(
                    "Markdown (.md)",
                    "Formatted text document",
                ) {
                    if (current != null) {
                        startSave(LogExport.markdown(current, fields, entries, notesByEntry, includeFollowUps))
                    }
                },
                ExportFormatOption(
                    "PDF Document (.pdf)",
                    "Printable document format",
                ) {
                    if (current != null) {
                        startSave(LogExport.pdf(current, fields, entries, notesByEntry, includeFollowUps))
                    }
                },
                ExportFormatOption(
                    "Spreadsheet (.csv)",
                    "Table of entries for a spreadsheet app",
                ) {
                    if (current != null) startSave(LogExport.csv(current, fields, entries))
                },
                ExportFormatOption(
                    "Application Data (.json)",
                    "Use this file to import or restore this form later",
                ) {
                    if (current != null) {
                        startSave(LogExport.json(current, entries, notesByEntry, includeFollowUps))
                    }
                },
            ),
        )
    }

    if (confirmDeleteLog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLog = false },
            title = { Text("Delete \"${template?.name ?: "this log"}\"?") },
            text = { Text("This permanently deletes this log and all of its entries. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteLog = false
                    viewModel.deleteLog(onDeleted = onBack)
                }) {
                    Text("Delete Log", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLog = false }) { Text("Cancel") }
            },
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("This permanently deletes this entry. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    entryToDelete = null
                    viewModel.deleteEntry(entry)
                }) {
                    Text("Delete Entry", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showUnlock) {
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text("Unlock this log?") },
            text = {
                Text(
                    "Unlocking lets you edit its entries. This is permanent — once " +
                        "unlocked, the log can never be re-locked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnlock = false
                    viewModel.unlockLog()
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlock = false }) { Text("Cancel") }
            },
        )
    }

}

/** Identifies the sorting controls' slot at the top of the entry list. */
private const val SORT_FILTER_BAR_KEY = "sortFilterBar"

/**
 * Swallows every interaction, so a control wired to it never picks up a pressed
 * or ripple state. These sorting controls open their menu on touch without first
 * flashing a different colour.
 */
private object NoPressFeedback : MutableInteractionSource {
    override val interactions: Flow<Interaction> = emptyFlow()
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction) = true
}

/**
 * The ordering controls that sit under the form title, at the top of the entries:
 * `[ Timestamp ▾ ] [ Sort: Newest ▾ ] [ ✕ Clear ]`. The first chip carries the
 * label of the field the list is currently ordered by, and its menu lists every
 * other field available to order by. With only the automatic entry timestamp to
 * sort by there is nothing to choose between, so that chip becomes a plain label
 * next to the Sort dropdown. Clear drops both picks and returns the list to the
 * form's own default ordering. The whole row is centred across the screen.
 */
@Composable
private fun SortFilterBar(
    categories: List<SortCategory>,
    selected: SortCategory?,
    newestFirst: Boolean,
    onSelectCategory: (SortCategory) -> Unit,
    onSelectNewestFirst: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (categories.size > 1) {
            Box {
                AssistChip(
                    onClick = { fieldMenuOpen = true },
                    label = { Text((selected ?: categories.first()).label) },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    },
                    interactionSource = NoPressFeedback,
                )
                DropdownMenu(
                    expanded = fieldMenuOpen,
                    onDismissRequest = { fieldMenuOpen = false },
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            trailingIcon = {
                                if (category.label == selected?.label) {
                                    Icon(Icons.Filled.Check, contentDescription = "Sorting by this")
                                }
                            },
                            onClick = {
                                fieldMenuOpen = false
                                onSelectCategory(category)
                            },
                        )
                    }
                }
            }
        } else {
            // Nothing to pick between — name the one ordering instead.
            Text(
                categories.single().label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            AssistChip(
                onClick = { sortOpen = true },
                label = { Text("Sort: " + if (newestFirst) "Newest" else "Oldest") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                interactionSource = NoPressFeedback,
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Newest") },
                    trailingIcon = {
                        if (newestFirst) Icon(Icons.Filled.Check, contentDescription = "Current order")
                    },
                    onClick = { sortOpen = false; onSelectNewestFirst(true) },
                )
                DropdownMenuItem(
                    text = { Text("Oldest") },
                    trailingIcon = {
                        if (!newestFirst) Icon(Icons.Filled.Check, contentDescription = "Current order")
                    },
                    onClick = { sortOpen = false; onSelectNewestFirst(false) },
                )
            }
        }

        AssistChip(
            onClick = onClear,
            label = { Text("Clear") },
            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
            interactionSource = NoPressFeedback,
        )
    }
}

/**
 * One entry card (docs/UI_SPEC.md §3). The top line shows the entry's automatic
 * timestamp with a `⋮` menu across from it — Edit (when unlocked), Mark/Unmark, Add
 * follow-up note (when the log allows them), and Delete. When the entry is
 * marked, a filled star sits just before the `⋮`; tapping the star unmarks it.
 * With the automatic timestamp switched off the top line is not left blank: the
 * entry's first filled-in field moves up into it and wraps beside the `⋮` rather
 * than running under it, keeping the normal field-to-field spacing below it.
 * Every remaining field with a value is listed below as
 * `label: value`, then any append-only follow-up notes with their timestamps.
 */
@Composable
private fun EntryRow(
    entry: LogEntry,
    fields: List<FieldDef>,
    appendedNotes: List<EntryNote>,
    showAutomaticTimestamp: Boolean,
    editable: Boolean,
    appendable: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleMark: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (Long) -> Unit,
) {
    val values = remember(entry.valuesJson) { EntryValues.decode(entry.valuesJson) }
    val notes = remember(values) { EntryValues.notes(values) }
    var menuOpen by remember { mutableStateOf(false) }

    // Every field that actually has a value, in form order.
    val filled = remember(fields, values) {
        fields.mapNotNull { field ->
            EntryValues.displayValue(field, values)?.let { field.label to it }
        }
    }
    // With no timestamp on the top line, the first field takes that space instead
    // of leaving it empty, so it is not repeated in the list below.
    val hoisted = if (showAutomaticTimestamp) null else filled.firstOrNull()
    val remaining = if (hoisted == null) filled else filled.drop(1)

    // Width of the star/⋮ cluster, measured so a hoisted first field can keep
    // clear of it horizontally instead of being pushed down by its height.
    val density = LocalDensity.current
    var actionsWidth by remember { mutableStateOf(0.dp) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp),
            ) {
                if (hoisted != null) {
                    // The first field takes the top line beside the ⋮ menu, wrapping
                    // clear of it rather than running under it. It sits in the normal
                    // field flow, so the gap to the next field is the normal one and
                    // not the height of the menu's touch target.
                    FieldReadout(
                        label = hoisted.first,
                        value = hoisted.second,
                        modifier = Modifier.fillMaxWidth().padding(end = actionsWidth),
                    )
                } else {
                    // Top line: optional timestamp on the left; a ⋮ menu on the right.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showAutomaticTimestamp) {
                            Text(
                                text = EntryValues.displayEntryTimestamp(entry.createdAt),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        EntryActions(
                            marked = entry.marked,
                            editable = editable,
                            appendable = appendable,
                            menuOpen = menuOpen,
                            onMenuOpenChange = { menuOpen = it },
                            onToggleMark = onToggleMark,
                            onEdit = onEdit,
                            onAddNote = onAddNote,
                            onDelete = onDelete,
                        )
                    }
                }

                // Follow-Up Notes sit at the top, under their own heading, so they're
                // visible without scrolling past the rest of the entry's data.
                if (appendedNotes.isNotEmpty()) {
                    Text(
                        "Follow-Up Notes",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    appendedNotes.forEach { note ->
                        FollowUpNote(note, onClick = { onEditNote(note.id) })
                    }
                }

                // One label-over-value block per remaining field that has a value.
                remaining.forEach { (label, value) ->
                    FieldReadout(label = label, value = value)
                }
                notes?.let { FieldReadout(label = "Notes", value = it) }
            }

            if (hoisted != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 4.dp)
                        .onSizeChanged { size ->
                            actionsWidth = with(density) { size.width.toDp() }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntryActions(
                        marked = entry.marked,
                        editable = editable,
                        appendable = appendable,
                        menuOpen = menuOpen,
                        onMenuOpenChange = { menuOpen = it },
                        onToggleMark = onToggleMark,
                        onEdit = onEdit,
                        onAddNote = onAddNote,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * The trailing controls on an entry card's top line: the star (only while the
 * entry is marked; tapping it unmarks) and the `⋮` menu.
 */
@Composable
private fun EntryActions(
    marked: Boolean,
    editable: Boolean,
    appendable: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onToggleMark: () -> Unit,
    onEdit: () -> Unit,
    onAddNote: () -> Unit,
    onDelete: () -> Unit,
) {
    // The star only appears when the entry is marked; tapping it unmarks.
    if (marked) {
        IconButton(onClick = onToggleMark) {
            Icon(Icons.Filled.Star, contentDescription = "Marked — tap to unmark")
        }
    }
    Box {
        IconButton(onClick = { onMenuOpenChange(true) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
            if (editable) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { onMenuOpenChange(false); onEdit() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (marked) "Unmark" else "Mark") },
                onClick = { onMenuOpenChange(false); onToggleMark() },
            )
            if (appendable) {
                DropdownMenuItem(
                    text = { Text("Add Follow-Up Note") },
                    onClick = { onMenuOpenChange(false); onAddNote() },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { onMenuOpenChange(false); onDelete() },
            )
        }
    }
}

/**
 * One Follow-Up Note: its own timestamp (muted) followed by the note text.
 * Tapping it opens the follow-up note screen to edit it.
 */
@Composable
private fun FollowUpNote(note: EntryNote, onClick: () -> Unit) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("${EntryValues.displayEntryTimestamp(note.createdAt)}: ")
            }
            append(note.text)
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
    )
}

/**
 * A single field shown inline as `label: value` on one line. The label is muted,
 * the value normal weight, and long values (like notes) wrap onto further lines.
 */
@Composable
private fun FieldReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}
