package com.datadragon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OnlinePrediction
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.HomeView
import com.datadragon.app.data.NavStyle
import com.datadragon.app.ui.DailyListViewModel
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDestructiveButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.theme.AppTheme
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.datadragon.app.ui.HomeClickerLog
import com.datadragon.app.ui.HomeIdeaLog
import com.datadragon.app.ui.HomeLog
import com.datadragon.app.ui.HomeViewModel
import com.datadragon.app.ui.components.HomeCard
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCreateForm: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
    onCreateChecklist: () -> Unit,
    onOpenChecklist: (Long) -> Unit,
    onCreateIdeaLog: () -> Unit,
    onOpenIdeaLog: (Long) -> Unit,
    onAddIdea: (Long) -> Unit,
    onDailyListToday: () -> Unit,
    onDailyListPickDate: () -> Unit,
    onDailyListDateConfirmed: (java.time.LocalDate) -> Unit,
    onOpenDailyListCard: (Long) -> Unit,
    onCreateClicker: () -> Unit,
    onOpenClicker: (Long) -> Unit,
    dailyListViewModel: DailyListViewModel = viewModel(),
    viewModel: HomeViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val ideaLogs by viewModel.ideaLogs.collectAsStateWithLifecycle()
    val clickerLogs by viewModel.clickerLogs.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val navStyle by viewModel.navStyle.collectAsStateWithLifecycle()
    val useModeLabel by viewModel.useModeLabelInDropdown.collectAsStateWithLifecycle()
    val enabledModes by viewModel.enabledModes.collectAsStateWithLifecycle()

    // Only the modes the user has chosen appear in the bar, in their fixed order.
    // The current mode is the one being viewed if it's still shown, otherwise the
    // first shown one; null means nothing is chosen and Home shows its empty note.
    val visibleModes = MODE_META.filter { it.view in enabledModes }
    val currentMode = visibleModes.firstOrNull { it.view == view } ?: visibleModes.firstOrNull()

    Scaffold(
        topBar = {
            // Daily Tasks has no Home list of its own — entering the mode drops
            // straight into its Main screen, so its bar mirrors a grouping's Main
            // screen (back, cog, title, "+") rather than the mode-toggle bar.
            if (currentMode?.view == HomeView.DAILY_LIST) {
                // Back returns to the first other chosen mode, bringing the
                // mode-toggle bar back. With no other mode chosen there is
                // nowhere to go, so no back arrow is shown.
                val backMode = visibleModes.firstOrNull { it.view != HomeView.DAILY_LIST }
                DailyTasksTopBar(
                    onBack = backMode?.let { mode -> { viewModel.setView(mode.view) } },
                    onOpenSettings = onOpenSettings,
                    onAddCard = onDailyListToday,
                )
            } else {
                TopAppBar(
                    title = {
                        if (currentMode != null) {
                            if (navStyle == NavStyle.ICONS) {
                                // A row of the chosen modes' icons, left-aligned, with a
                                // little space between them so none is easy to mis-tap.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    visibleModes.forEachIndexed { index, mode ->
                                        if (index > 0) Spacer(Modifier.width(4.dp))
                                        ViewToggle(
                                            painter = mode.iconPainter(),
                                            contentDescription = mode.label,
                                            selected = view == mode.view,
                                            onClick = { viewModel.setView(mode.view) },
                                        )
                                    }
                                }
                            } else {
                                // Dropdown mode: the menu icon opens a small popup of the
                                // chosen modes; the current one shows as its label or its
                                // single icon, immediately after the menu icon.
                                NavModeDropdown(
                                    current = currentMode,
                                    modes = visibleModes,
                                    useLabel = useModeLabel,
                                    onSelect = { viewModel.setView(it) },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        // Settings holds backup/restore and the global list options;
                        // the top-right "+" creates a form or list per the current view.
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.SettingsApplications,
                                contentDescription = "Settings",
                                modifier = Modifier.size(AppTheme.sizes.settingsCog),
                            )
                        }
                    },
                    actions = {
                        // The trailing "+" creates a new item in whichever mode is
                        // showing; with no mode chosen there is nothing to add.
                        val activeView = currentMode?.view
                        if (activeView != null) {
                            IconButton(onClick = {
                                when (activeView) {
                                    HomeView.FORMS -> onCreateForm()
                                    HomeView.LISTS -> onCreateChecklist()
                                    HomeView.IDEAS -> onCreateIdeaLog()
                                    HomeView.CLICKER -> onCreateClicker()
                                    HomeView.DAILY_LIST -> Unit
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = when (activeView) {
                                        HomeView.FORMS -> "New form"
                                        HomeView.LISTS -> "New list"
                                        HomeView.IDEAS -> "New Idea Log"
                                        HomeView.CLICKER -> "New Clicker Data Log"
                                        HomeView.DAILY_LIST -> "New Daily Task"
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        when (currentMode?.view) {
            HomeView.FORMS -> FormsBody(
                logs = logs,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenLog = onOpenLog,
                onAddEntry = onAddEntry,
            )
            HomeView.LISTS -> ListsBody(
                checklists = checklists,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenChecklist = onOpenChecklist,
            )
            HomeView.IDEAS -> IdeasBody(
                ideaLogs = ideaLogs,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenIdeaLog = onOpenIdeaLog,
                onAddIdea = onAddIdea,
            )
            HomeView.DAILY_LIST -> DailyListHomeBody(
                dailyListViewModel = dailyListViewModel,
                onOpenCard = onOpenDailyListCard,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            HomeView.CLICKER -> ClickerBody(
                logs = clickerLogs,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenClicker = onOpenClicker,
            )
            // No modes chosen: point the user at the cog to turn some on.
            null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Click the cog in the upper left corner to select what data modes you'd like to use.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    // Daily List date picker: Calendar Add On opens it; the picked date is
    // the new card's actual date (any past, present, or future date). A date
    // that already has a card asks with the exact duplicate-date dialog.
    val dailyListCards by dailyListViewModel.cards.collectAsStateWithLifecycle()
    val datePickerRequest by dailyListViewModel.datePickerRequested.collectAsStateWithLifecycle()
    var showDailyListDatePicker by remember { mutableStateOf(false) }
    var duplicateDailyListDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val homeScope = rememberCoroutineScope()

    LaunchedEffect(datePickerRequest) {
        if (datePickerRequest > 0) showDailyListDatePicker = true
    }

    if (showDailyListDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDailyListDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = datePickerState.selectedDateMillis?.let {
                        java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDailyListDatePicker = false
                    if (picked != null) {
                        homeScope.launch {
                            if (dailyListViewModel.hasCardForDate(picked)) {
                                duplicateDailyListDate = picked
                            } else {
                                onDailyListDateConfirmed(picked)
                            }
                        }
                    }
                }) { Text("Okay") }
            },
            dismissButton = {
                TextButton(onClick = { showDailyListDatePicker = false }) { Text("Cancel") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    duplicateDailyListDate?.let { dupDate ->
        AppDialog(
            onDismissRequest = { duplicateDailyListDate = null },
            title = "Task list already exists on this date. Open current card?",
            dismissButton = { DialogDismissButton("Cancel") { duplicateDailyListDate = null } },
            confirmButton = {
                DialogActionButton("Open Card") {
                    duplicateDailyListDate = null
                    onDailyListDateConfirmed(dupDate)
                }
            },
        )
    }

    // Offer to recover a list left unfinished when the app was last killed. The
    // draft was written for crash safety but never saved, so it's hidden from Home
    // until the user chooses. Recover opens it in the editor (still a draft);
    // Discard deletes it. Tapping outside keeps it for next time.
    pendingDraft?.let { draft ->
        AppDialog(
            onDismissRequest = { viewModel.clearPendingDraft() },
            title = "Recover Unfinished List?",
            body = "You have a list that wasn't saved. Recover it to keep editing, " +
                "or discard it.",
            dismissButton = {
                DialogDestructiveButton("Discard") { viewModel.discardPendingDraft() }
            },
            confirmButton = {
                DialogActionButton("Recover") {
                    viewModel.clearPendingDraft()
                    onOpenChecklist(draft.id)
                }
            },
        )
    }
}

/**
 * Daily Tasks' top bar. Daily Tasks has no Home list of its own, so entering the
 * mode looks like opening a grouping: this bar mirrors a grouping's Main screen —
 * a back chevron, the app cog, the "Daily Task" title, then a single "+" on the
 * right that opens today's card. [onBack] is null when Daily Tasks is the only
 * chosen mode, in which case no back chevron is shown (there is nowhere to go).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTasksTopBar(
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onAddCard: () -> Unit,
) {
    TopAppBar(
        title = {
            Text("Daily Tasks", style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.SettingsApplications,
                        contentDescription = "Settings",
                        modifier = Modifier.size(AppTheme.sizes.settingsCog),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onAddCard) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Daily Task",
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

@Composable
private fun ViewToggle(
    painter: Painter,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // These three toggles take no tap coloration: the tap gives no ripple.
    // The active one inverts — a filled circle in the icon's normal (unselected)
    // color, with the icon itself in the color of the bar behind it.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (selected) {
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * One data mode's bar presence: which [HomeView] it is, the icon that stands for
 * it, and its label. The single source both the icon row and the dropdown draw
 * from, so a future "choose which modes show" filter only has to narrow this list.
 */
private data class ModeMeta(
    val view: HomeView,
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
)

/** Every data mode, in the order they appear in the bar. */
private val MODE_META = listOf(
    ModeMeta(HomeView.FORMS, "Forms", icon = Icons.Filled.Description),
    ModeMeta(HomeView.LISTS, "Lists", icon = Icons.Filled.Checklist),
    ModeMeta(HomeView.IDEAS, "Ideas", icon = Icons.Filled.OnlinePrediction),
    ModeMeta(HomeView.DAILY_LIST, "Daily Tasks", icon = Icons.Filled.EventNote),
    ModeMeta(HomeView.CLICKER, "Clicker Data", iconRes = R.drawable.ic_chart_data),
)

/** The painter for a mode's bar icon, whether it's a built-in vector or a bundled drawable. */
@Composable
private fun ModeMeta.iconPainter(): Painter =
    iconRes?.let { painterResource(it) } ?: rememberVectorPainter(icon!!)

/**
 * Dropdown navigation: the Material menu icon, then the current mode shown as its
 * label (when the setting is on) or its single icon. Tapping the menu icon opens
 * a small popup of the chosen modes to switch between.
 */
@Composable
private fun NavModeDropdown(
    current: ModeMeta,
    modes: List<ModeMeta>,
    useLabel: Boolean,
    onSelect: (HomeView) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Choose data mode",
                modifier = Modifier.size(24.dp),
            )
        }
        if (useLabel) {
            Text(current.label, style = MaterialTheme.typography.titleLarge)
        } else {
            Icon(
                painter = current.iconPainter(),
                contentDescription = current.label,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSelect(mode.view)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FormsBody(
    logs: List<HomeLog>,
    modifier: Modifier = Modifier,
    onOpenLog: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
) {
    if (logs.isEmpty()) {
        EmptyMessage(
            title = "No logs yet.",
            body = "Click the plus in the top right to begin",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(logs, key = { it.template.id }) { log ->
                LogRow(
                    log = log,
                    onOpen = { onOpenLog(log.template.id) },
                    onAddEntry = { onAddEntry(log.template.id) },
                )
            }
        }
    }
}

@Composable
private fun ListsBody(
    checklists: List<Checklist>,
    modifier: Modifier = Modifier,
    onOpenChecklist: (Long) -> Unit,
) {
    if (checklists.isEmpty()) {
        EmptyMessage(
            title = "No lists yet.",
            body = "Click the plus in the top right to begin",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(checklists, key = { it.id }) { checklist ->
                ChecklistRow(
                    checklist = checklist,
                    onOpen = { onOpenChecklist(checklist.id) },
                )
            }
        }
    }
}

@Composable
private fun IdeasBody(
    ideaLogs: List<HomeIdeaLog>,
    modifier: Modifier = Modifier,
    onOpenIdeaLog: (Long) -> Unit,
    onAddIdea: (Long) -> Unit,
) {
    if (ideaLogs.isEmpty()) {
        EmptyMessage(
            title = "No idea logs yet.",
            body = "Click the plus in the top right to begin",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ideaLogs, key = { it.log.id }) { ideaLog ->
                IdeaLogRow(
                    ideaLog = ideaLog,
                    onOpen = { onOpenIdeaLog(ideaLog.log.id) },
                    onAddIdea = { onAddIdea(ideaLog.log.id) },
                )
            }
        }
    }
}

/**
 * One Idea Log card: its name, then the same entry summary line a form card
 * shows. The "+" on the right adds a new active idea straight into that log.
 */
@Composable
private fun IdeaLogRow(
    ideaLog: HomeIdeaLog,
    onOpen: () -> Unit,
    onAddIdea: () -> Unit,
) {
    HomeCard(
        title = ideaLog.log.name,
        onClick = onOpen,
        subtitle = summaryLine(ideaLog.entryCount, ideaLog.lastEntryAt),
        trailing = {
            IconButton(onClick = onAddIdea) {
                Icon(Icons.Filled.Add, contentDescription = "Add idea to ${ideaLog.log.name}")
            }
        },
    )
}

@Composable
private fun LogRow(
    log: HomeLog,
    onOpen: () -> Unit,
    onAddEntry: () -> Unit,
) {
    HomeCard(
        title = log.template.name,
        onClick = onOpen,
        subtitle = entrySummaryLine(log),
        leadingIcon = if (log.template.locked) {
            {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
            }
        } else {
            null
        },
        // Per-row add-entry button stays on the far right so the everyday
        // action is consistent and far from any destructive control.
        trailing = {
            IconButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry to ${log.template.name}")
            }
        },
    )
}

@Composable
private fun ChecklistRow(
    checklist: Checklist,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = checklist.name.ifBlank { "Untitled list" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The Daily List mode's body: the list of date cards plus the favorite filter
 * star (equivalent to the Forms marked-only star) in the top bar's trailing
 * cluster, handled here so the star sits beside the mode toggles like Forms'.
 */
@Composable
private fun DailyListHomeBody(
    dailyListViewModel: DailyListViewModel,
    onOpenCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards by dailyListViewModel.cards.collectAsStateWithLifecycle()
    val cardItems by dailyListViewModel.cardItems.collectAsStateWithLifecycle()
    val celebrationEnabled by dailyListViewModel.celebrationEnabled.collectAsStateWithLifecycle()
    val celebrationIcon by dailyListViewModel.celebrationIcon.collectAsStateWithLifecycle()
    val newestFirst by dailyListViewModel.newestFirst.collectAsStateWithLifecycle()
    val showFavoritesOnly by dailyListViewModel.showFavoritesOnly.collectAsStateWithLifecycle()

    // Daily List maintenance runs only when the user enters Daily List mode —
    // at most once per local day (guarded in the repository).
    LaunchedEffect(Unit) { dailyListViewModel.runMaintenanceIfDue() }

    val visibleCards = if (showFavoritesOnly) cards.filter { it.favorited } else cards
    val anyFavorited = cards.any { it.favorited }

    Column(modifier = modifier) {
        if (anyFavorited) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = {
                    dailyListViewModel.setShowFavoritesOnly(!showFavoritesOnly)
                }) {
                    Icon(
                        imageVector = if (showFavoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (showFavoritesOnly) {
                            "Showing favorited daily tasks only — tap to show all"
                        } else {
                            "Show favorited daily tasks only"
                        },
                    )
                }
            }
        }
        DailyListBody(
            cards = visibleCards,
            cardItems = cardItems,
            celebrationEnabled = celebrationEnabled,
            celebrationIcon = celebrationIcon,
            celebrationVisible = { card, items ->
                dailyListViewModel.celebrationForCard(card, items)
            },
            newestFirst = newestFirst,
            onNewestFirst = dailyListViewModel::setNewestFirst,
            onOpenCard = onOpenCard,
            onToggleFavorite = dailyListViewModel::toggleFavorite,
            onDeleteCard = dailyListViewModel::deleteCard,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "No Entries Yet" / "1 Entry" / "14 Entries · Last Entry Today" (docs/UI_SPEC.md §2). */
private fun entrySummaryLine(log: HomeLog): String =
    summaryLine(log.entryCount, log.lastEntryAt)

/** The shared count-and-last-entry line, used by form and Idea Log cards alike. */
private fun summaryLine(entryCount: Int, lastEntryAt: String?): String {
    if (entryCount == 0) return "No Entries Yet"
    val count = if (entryCount == 1) "1 Entry" else "$entryCount Entries"
    val last = EntryValues.displayLastEntry(lastEntryAt)
    return if (last != null) "$count · Last Entry $last" else count
}

@Composable
private fun ClickerBody(
    logs: List<HomeClickerLog>,
    modifier: Modifier = Modifier,
    onOpenClicker: (Long) -> Unit,
) {
    if (logs.isEmpty()) {
        EmptyMessage(
            title = "No clicker data lists yet.",
            body = "Click the plus in the top right to begin",
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(logs, key = { it.log.id }) { log ->
                ClickerLogRow(log = log, onOpen = { onOpenClicker(log.log.id) })
            }
        }
    }
}

/**
 * One Clicker grouping's Home row: its title, tappable to open, with the same
 * count-and-last line a Form's row shows — but "Last Saved" instead of "Last
 * Entry", since a clicker card is used over time rather than filed once.
 */
@Composable
private fun ClickerLogRow(log: HomeClickerLog, onOpen: () -> Unit) {
    HomeCard(
        title = log.log.title,
        onClick = onOpen,
        subtitle = clickerSummaryLine(log),
    )
}

/** "No Entries Yet" / "1 Entry" / "14 Entries · Last Saved Today" for a Clicker grouping. */
private fun clickerSummaryLine(log: HomeClickerLog): String {
    if (log.entryCount == 0) return "No Entries Yet"
    val count = if (log.entryCount == 1) "1 Entry" else "${log.entryCount} Entries"
    val last = EntryValues.displayLastSaved(log.log.lastModifiedAt)
    return if (last != null) "$count · Last Saved $last" else count
}

@Composable
private fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
