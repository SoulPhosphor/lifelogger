package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.data.CelebrationIcon
import com.datadragon.app.ui.DAILY_LIST_DATE_FORMAT
import com.datadragon.app.ui.DailyListEditorRow
import com.datadragon.app.ui.DailyListViewModel
import com.datadragon.app.ui.theme.AppTheme
import java.time.LocalDate
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The Daily List editor: one date's full editable list. Top bar follows the
 * Data Dragon pattern — Back double-chevron, then the Settings gear, with
 * Cycle (when renewal is manual) and `+` on the right. Cycle renews manually;
 * the top-right `+` adds a new top-level item through the ordinary list
 * insertion behavior.
 *
 * The date is always shown and is immutable once saved.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyListEditorScreen(
    date: String?,
    onBack: () -> Unit,
    onOpenPreferences: () -> Unit = {},
    viewModel: DailyListViewModel = viewModel(),
) {
    // The route argument is the exact ISO date this editor edits — an existing
    // card's date or a fresh unsaved date. Opening never persists anything.
    LaunchedEffect(date) {
        date?.let { iso ->
            runCatching { LocalDate.parse(iso) }.getOrNull()?.let { viewModel.openForEditorDate(it) }
        }
    }
    val scope = rememberCoroutineScope()
    val card by viewModel.editorCard.collectAsStateWithLifecycle()
    val date by viewModel.editorDate.collectAsStateWithLifecycle()
    val title by viewModel.editorTitle.collectAsStateWithLifecycle()
    val rows by viewModel.editorRows.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val autoRenew by viewModel.autoRenew.collectAsStateWithLifecycle()
    val allowTitle by viewModel.allowTitle.collectAsStateWithLifecycle()

    val editorHeading = heading.ifBlank { "Daily Tasks" }
    val editorDate = date

    // Leaving flushes nothing (autosave is immediate), so Back just leaves. A
    // fresh date with no saved card also just leaves — opening never creates.
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = editorDate?.format(DAILY_LIST_DATE_FORMAT) ?: "",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = editorHeading,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        IconButton(onClick = onOpenPreferences) {
                            Icon(
                                Icons.Filled.SettingsApplications,
                                contentDescription = "Daily List Preferences",
                                modifier = Modifier.size(AppTheme.sizes.settingsCog),
                            )
                        }
                    }
                },
                actions = {
                    // Cycle only when automatic renewal is off — the manual
                    // renewal that is repeatable without duplicating carried rows.
                    if (!autoRenew) {
                        IconButton(onClick = { viewModel.runManualRenewal() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cycle),
                                contentDescription = "Renew unfinished items from the previous day",
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.addItem() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Daily List item")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            // The optional per-day title field — shown only when the
            // "Allow creating title for daily lists." toggle is on. Hiding it
            // never deletes a stored title.
            if (allowTitle) {
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::setEditorTitle,
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val ids = rows.map { it.localId }.toMutableList()
                if (from.index in ids.indices && to.index in ids.indices) {
                    ids.add(to.index, ids.removeAt(from.index))
                    viewModel.reorder(ids)
                }
            }
            val density = LocalDensity.current
            val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density) > 0
            val keyboardScrollSpace = with(density) {
                if (imeVisible) lazyListState.layoutInfo.viewportSize.height.toDp() else 0.dp
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = keyboardScrollSpace),
            ) {
                itemsIndexed(rows, key = { _, item -> item.localId }) { _, item ->
                    ReorderableItem(reorderState, key = item.localId) { _ ->
                        DailyListEditorRowView(
                            row = item,
                            dragHandleModifier = Modifier.draggableHandle(),
                            onComplete = { viewModel.setCompleted(item.localId, !item.completed) },
                            onTextChange = { viewModel.updateText(item.localId, it) },
                            onAddSubItem = { viewModel.addSubItem(item.localId) },
                            onDelete = { viewModel.deleteItem(item.localId) },
                        )
                    }
                }
            }

        }
    }

}

/** One editable Daily List item row, matching the ordinary List row model. */
@Composable
private fun DailyListEditorRowView(
    row: DailyListEditorRow,
    dragHandleModifier: Modifier,
    onComplete: () -> Unit,
    onTextChange: (String) -> Unit,
    onAddSubItem: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(row.localId) { mutableStateOf(row.text) }
    LaunchedEffect(row.text) { if (row.text != text && row.text.isNotBlank()) text = row.text }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (row.indent == 1) 32.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandleModifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        IconButton(onClick = onComplete) {
            Icon(
                imageVector = if (row.completed) Icons.Filled.Check else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (row.completed) "Mark not done" else "Mark done",
                tint = if (row.completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it; onTextChange(it) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (row.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAddSubItem) {
            Icon(Icons.Filled.Add, contentDescription = "Add sub-item")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete item")
        }
    }
}

/**
 * Daily List preferences, opened from the gear beside Back. Exact labels and
 * defaults, in the owner's order; the completion-icon dropdown sits directly
 * underneath its toggle; the numeric auto-delete field comes underneath the
 * toggle group. Every change persists immediately.
 */
@Composable
private fun DailyListPreferencesDialog(
    viewModel: DailyListViewModel,
    onDismiss: () -> Unit,
) {
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val autoRenew by viewModel.autoRenew.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompleted.collectAsStateWithLifecycle()
    val showCurrentUnfinished by viewModel.showCurrentUnfinished.collectAsStateWithLifecycle()
    val showPastUnfinished by viewModel.showPastUnfinished.collectAsStateWithLifecycle()
    val autoTrashPast by viewModel.autoTrashPast.collectAsStateWithLifecycle()
    val celebrationEnabled by viewModel.celebrationEnabled.collectAsStateWithLifecycle()
    val celebrationIcon by viewModel.celebrationIcon.collectAsStateWithLifecycle()
    val allowTitle by viewModel.allowTitle.collectAsStateWithLifecycle()
    val protectFavorited by viewModel.protectFavorited.collectAsStateWithLifecycle()
    val autoReopen by viewModel.autoReopen.collectAsStateWithLifecycle()
    val retentionRaw by viewModel.retentionRaw.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily List Preferences") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = heading,
                    onValueChange = viewModel::setHeading,
                    singleLine = true,
                    label = { Text("Name showed at the top of your daily lists") },
                    placeholder = { Text("Daily Tasks") },
                )

                SettingToggle(
                    checked = autoRenew,
                    onCheckedChange = viewModel::setAutoRenew,
                    title = "Automatically renew daily list items that weren't completed.",
                )
                SettingToggle(
                    checked = showCompleted,
                    onCheckedChange = viewModel::setShowCompleted,
                    title = "Show completed list items in main view.",
                )
                SettingToggle(
                    checked = showCurrentUnfinished,
                    onCheckedChange = viewModel::setShowCurrentUnfinished,
                    title = "Show current dates uncompleted list items in main view.",
                )
                SettingToggle(
                    checked = showPastUnfinished,
                    onCheckedChange = viewModel::setShowPastUnfinished,
                    title = "Show past dates uncompleted list items in main view",
                )
                SettingToggle(
                    checked = autoTrashPast,
                    onCheckedChange = viewModel::setAutoTrashPast,
                    title = "Automatically trash uncompleted items from past days",
                )
                SettingToggle(
                    checked = autoReopen,
                    onCheckedChange = viewModel::setAutoReopen,
                    title = "Automatically show current daily list when app is started.",
                )
                SettingToggle(
                    checked = allowTitle,
                    onCheckedChange = viewModel::setAllowTitle,
                    title = "Allow creating title for daily lists.",
                )
                SettingToggle(
                    checked = celebrationEnabled,
                    onCheckedChange = viewModel::setCelebrationEnabled,
                    title = "Mark days all tasks were completed with an icon on the home screen.",
                )
                if (celebrationEnabled) {
                    CelebrationIconDropdown(
                        selected = celebrationIcon,
                        onSelected = viewModel::setCelebrationIcon,
                    )
                }
                SettingToggle(
                    checked = protectFavorited,
                    onCheckedChange = viewModel::setProtectFavorited,
                    title = "Protect favorited days.",
                )

                // The numeric auto-delete write-in: blank disables; 1 through 999;
                // at most three digits.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Auto delete daily lists older then (",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = retentionRaw,
                        onValueChange = viewModel::setRetentionRaw,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp),
                    )
                    Text(") days.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/** The same toggle row style Settings uses — no second switch design. */
@Composable
private fun SettingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The one global celebration-icon choice, in the owner's exact option order. */
@Composable
private fun CelebrationIconDropdown(
    selected: CelebrationIcon,
    onSelected: (CelebrationIcon) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Celebration icon:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // The box shows the same wording as the menu, and its width is fixed.
        Box(
            modifier = Modifier
                .clip(AppTheme.shapes.control)
                .clickable { expanded = true }
                .border(
                    AppTheme.shapes.controlBorder,
                    MaterialTheme.colorScheme.outline,
                    AppTheme.shapes.control,
                )
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                CelebrationIcon.entries.forEach { icon ->
                    DropdownMenuItem(
                        text = { Text(icon.label) },
                        onClick = {
                            onSelected(icon)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
