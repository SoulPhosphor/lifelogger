package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.HomeStorage
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.IdeaEntry
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
import com.datadragon.app.data.IdeaValues
import com.datadragon.app.ui.IdeaLocation
import com.datadragon.app.ui.IdeaLogViewModel
import com.datadragon.app.ui.components.SortFilterBar
import com.datadragon.app.ui.theme.DeleteRed

/**
 * One Idea Log: its ideas as cards, in the log's own order.
 *
 * The top bar's trailing controls keep a fixed relationship — the marked-only
 * star (when anything is marked) comes first, then the archive toggle (only when
 * the log allows archiving), then Search, then "+". Nothing is ever inserted
 * between the archive toggle, Search and "+".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaLogScreen(
    ideaLogId: String?,
    onBack: () -> Unit,
    onAddIdea: () -> Unit,
    onOpenIdea: (Long) -> Unit,
    onEditIdea: (Long) -> Unit,
    onEditIdeaLog: () -> Unit,
    viewModel: IdeaLogViewModel = viewModel(),
) {
    val id = ideaLogId?.toLongOrNull()
    // Re-runs when this screen re-enters composition, so an edit made in the
    // Idea Log editor is picked up on the way back.
    LaunchedEffect(id) { id?.let { viewModel.load(it) } }

    val log by viewModel.log.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortCategories by viewModel.sortCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()

    val allowArchiving = log?.allowArchiving ?: false
    val automaticTimestamping = log?.automaticTimestamping ?: false
    val inArchive = allowArchiving && location == IdeaLocation.ARCHIVE

    var gearMenuOpen by remember { mutableStateOf(false) }
    var confirmDeleteLog by remember { mutableStateOf(false) }
    var ideaToDelete by remember { mutableStateOf<IdeaEntry?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    // "Show marked only". Off by default and ephemeral; its star only appears
    // when something in the current result set is marked.
    var showMarkedOnly by remember { mutableStateOf(false) }
    val anyMarked = entries.any { it.marked }
    val visibleEntries = if (showMarkedOnly && anyMarked) entries.filter { it.marked } else entries

    fun attemptBack() { viewModel.leaveAfterTitleFlush(onBack) }
    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    EditableTitleField(
                        value = log?.name.orEmpty(),
                        onValueChange = viewModel::setTitle,
                        onFocusLost = viewModel::onTitleFocusLost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { attemptBack() }) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        Box {
                            IconButton(onClick = { gearMenuOpen = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Idea Log options")
                            }
                            DropdownMenu(
                                expanded = gearMenuOpen,
                                onDismissRequest = { gearMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Idea Log") },
                                    onClick = {
                                        gearMenuOpen = false
                                        viewModel.leaveAfterTitleFlush(onEditIdeaLog)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Idea Log") },
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
                    // The star goes before the archive toggle, never between the
                    // archive toggle, Search and "+".
                    if (anyMarked) {
                        IconButton(onClick = { showMarkedOnly = !showMarkedOnly }) {
                            Icon(
                                imageVector = if (showMarkedOnly) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Filled.StarBorder
                                },
                                contentDescription = if (showMarkedOnly) {
                                    "Showing marked ideas only — tap to show all"
                                } else {
                                    "Show marked ideas only"
                                },
                            )
                        }
                    }
                    if (allowArchiving) {
                        IconButton(
                            onClick = {
                                viewModel.setLocation(
                                    if (inArchive) IdeaLocation.ACTIVE else IdeaLocation.ARCHIVE,
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (inArchive) {
                                    Icons.Filled.HomeStorage
                                } else {
                                    Icons.Filled.FolderCopy
                                },
                                contentDescription = if (inArchive) {
                                    "Back to active ideas"
                                } else {
                                    "Archived ideas"
                                },
                            )
                        }
                    }
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = {
                        // A new idea is always active, so the archive view hands
                        // the user back to the active list to see it.
                        if (inArchive) viewModel.setLocation(IdeaLocation.ACTIVE)
                        onAddIdea()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add idea")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                IdeaSearchPanel(
                    // "Search All Locations" is only meaningful in the active view
                    // of a log that has an archive to also look through.
                    showAllLocations = allowArchiving && !inArchive,
                    onSearch = viewModel::submitSearch,
                    onClear = viewModel::clearSearch,
                )
                HorizontalDivider()
            }

            query?.let {
                Text(
                    text = searchSummary(it.text, visibleEntries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Deliberately not saved: the list opens at the top every time.
            val listState = remember { LazyListState() }
            LaunchedEffect(selectedCategory.label, newestFirst, location, query) {
                listState.scrollToItem(0)
            }

            if (visibleEntries.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SortFilterBar(
                        categoryLabels = sortCategories.map { it.label },
                        selectedLabel = selectedCategory.label,
                        newestFirst = newestFirst,
                        onSelectCategory = viewModel::selectSortCategoryLabel,
                        onSelectNewestFirst = viewModel::selectNewestFirst,
                        onClear = viewModel::clearSort,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                query != null -> "No ideas match that search."
                                inArchive -> "No archived ideas."
                                else -> "No ideas yet. Tap + to add one."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "ideaSortFilterBar") {
                        SortFilterBar(
                            categoryLabels = sortCategories.map { it.label },
                            selectedLabel = selectedCategory.label,
                            newestFirst = newestFirst,
                            onSelectCategory = viewModel::selectSortCategoryLabel,
                            onSelectNewestFirst = viewModel::selectNewestFirst,
                            onClear = viewModel::clearSort,
                        )
                    }
                    items(visibleEntries, key = { it.id }) { entry ->
                        IdeaCard(
                            entry = entry,
                            fields = fields,
                            log = log,
                            showAutomaticTimestamp = automaticTimestamping,
                            allowArchiving = allowArchiving,
                            onOpen = { onOpenIdea(entry.id) },
                            onEdit = { onEditIdea(entry.id) },
                            onToggleMark = { viewModel.toggleMark(entry) },
                            onSetArchived = { viewModel.setArchived(entry, it) },
                            onDelete = { ideaToDelete = entry },
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteLog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLog = false },
            title = { Text("Delete \"${log?.name ?: "this Idea Log"}\"?") },
            text = {
                Text("This permanently deletes this Idea Log and all of its ideas. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteLog = false
                    viewModel.deleteLog(onDeleted = onBack)
                }) { Text("Delete Idea Log", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLog = false }) { Text("Cancel") }
            },
        )
    }

    ideaToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { ideaToDelete = null },
            title = { Text("Delete this idea?") },
            text = { Text("This permanently deletes this idea. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    ideaToDelete = null
                    viewModel.deleteEntry(entry)
                }) { Text("Delete Idea", color = DeleteRed) }
            },
            dismissButton = {
                TextButton(onClick = { ideaToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private fun searchSummary(text: String, matches: Int): String =
    if (matches == 1) "1 result for \"$text\"" else "$matches results for \"$text\""

/**
 * The search area, directly beneath the top app bar. A search runs only when the
 * user asks for one — the Search button beside the box, or Enter on the keyboard
 * — never on every keystroke.
 */
@Composable
private fun IdeaSearchPanel(
    showAllLocations: Boolean,
    onSearch: (text: String, wholeWord: Boolean, matchCase: Boolean, allLocations: Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var wholeWord by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var allLocations by remember { mutableStateOf(false) }

    fun run() {
        if (text.isBlank()) {
            onClear()
        } else {
            // The checkbox is only offered in the active view of an archiving log,
            // so a stale "on" can never widen a search made from the archive.
            onSearch(text, wholeWord, matchCase, showAllLocations && allLocations)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Search", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run() }),
            trailingIcon = {
                IconButton(onClick = { run() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Run search")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        CheckboxSettingRow(
            checked = wholeWord,
            onCheckedChange = { wholeWord = it },
            title = "Whole Word",
        )
        CheckboxSettingRow(
            checked = matchCase,
            onCheckedChange = { matchCase = it },
            title = "Match Case",
        )
        if (showAllLocations) {
            CheckboxSettingRow(
                checked = allLocations,
                onCheckedChange = { allLocations = it },
                title = "Search All Locations",
            )
        }
    }
}

/**
 * One idea card. The top line carries the automatic timestamp (when the log
 * shows it) with the star and `⋮` menu across from it; with no timestamp the
 * first shown field takes that space instead of leaving it blank.
 *
 * Tapping the card body opens the full Idea Detail view — never the editor.
 */
@Composable
private fun IdeaCard(
    entry: IdeaEntry,
    fields: List<IdeaFieldDef>,
    log: IdeaLog?,
    showAutomaticTimestamp: Boolean,
    allowArchiving: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleMark: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val values = remember(entry.valuesJson) { IdeaValues.decode(entry.valuesJson) }
    val mode = cardDisplayMode(log)
    val shown = remember(fields, values, mode) { visibleIdeaFields(fields, values, mode) }
    var menuOpen by remember { mutableStateOf(false) }

    // With no timestamp on the top line, the first field takes that space rather
    // than leaving it empty, so it is not repeated in the list below.
    val hoisted = if (showAutomaticTimestamp) null else shown.firstOrNull()
    val remaining = if (hoisted == null) shown else shown.drop(1)

    // Width of the star/⋮ cluster, so a hoisted first field keeps clear of it.
    val density = LocalDensity.current
    var actionsWidth by remember { mutableStateOf(0.dp) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp),
            ) {
                if (hoisted != null) {
                    IdeaFieldReadout(
                        field = hoisted,
                        values = values,
                        log = log,
                        mode = mode,
                        modifier = Modifier.fillMaxWidth().padding(end = actionsWidth),
                    )
                } else {
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
                        IdeaActions(
                            marked = entry.marked,
                            archived = entry.archived,
                            allowArchiving = allowArchiving,
                            menuOpen = menuOpen,
                            onMenuOpenChange = { menuOpen = it },
                            onToggleMark = onToggleMark,
                            onEdit = onEdit,
                            onSetArchived = onSetArchived,
                            onDelete = onDelete,
                        )
                    }
                }

                remaining.forEach { field ->
                    IdeaFieldReadout(field = field, values = values, log = log, mode = mode)
                }
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
                    IdeaActions(
                        marked = entry.marked,
                        archived = entry.archived,
                        allowArchiving = allowArchiving,
                        menuOpen = menuOpen,
                        onMenuOpenChange = { menuOpen = it },
                        onToggleMark = onToggleMark,
                        onEdit = onEdit,
                        onSetArchived = onSetArchived,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * An idea's trailing controls: the star (only while marked; tapping it unmarks)
 * and the `⋮` menu. Archive and Unarchive appear only when the log allows
 * archiving; Delete is always there, archived or not — archiving never replaces
 * deletion.
 */
@Composable
internal fun IdeaActions(
    marked: Boolean,
    archived: Boolean,
    allowArchiving: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onToggleMark: () -> Unit,
    onEdit: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    if (marked) {
        IconButton(onClick = onToggleMark) {
            Icon(Icons.Filled.Star, contentDescription = "Marked — tap to unmark")
        }
    }
    Box {
        IconButton(onClick = { onMenuOpenChange(true) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Idea options")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { onMenuOpenChange(false); onEdit() },
            )
            DropdownMenuItem(
                text = { Text(if (marked) "Unmark" else "Mark") },
                onClick = { onMenuOpenChange(false); onToggleMark() },
            )
            if (allowArchiving) {
                DropdownMenuItem(
                    text = { Text(if (archived) "Unarchive" else "Archive") },
                    onClick = { onMenuOpenChange(false); onSetArchived(!archived) },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { onMenuOpenChange(false); onDelete() },
            )
        }
    }
}
