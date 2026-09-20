package com.datadragon.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CelebrationIcon
import com.datadragon.app.data.DailyList
import com.datadragon.app.data.DailyListItem
import com.datadragon.app.ui.DAILY_LIST_DATE_FORMAT
import com.datadragon.app.ui.DailyListViewModel
import com.datadragon.app.ui.celebrationIconVector
import com.datadragon.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * The Daily List main view: one contained card per saved date, like opening a
 * Form and seeing all of that Form's cards. Cards are display-only — tapping a
 * card opens its editor.
 *
 * The two creation controls mean "today" (Event Note) and "picked date"
 * (Calendar Add On, immediately to the left of Event Note); they live on the
 * Home bar with the other mode actions, so this screen just renders cards.
 */
@Composable
fun DailyListBody(
    cards: List<DailyList>,
    cardItems: Map<Long, List<DailyListItem>>,
    celebrationEnabled: Boolean,
    celebrationIcon: CelebrationIcon,
    celebrationVisible: (DailyList, List<DailyListItem>) -> Boolean,
    newestFirst: Boolean,
    onNewestFirst: (Boolean) -> Unit,
    onOpenCard: (Long) -> Unit,
    onToggleFavorite: (DailyList) -> Unit,
    onDeleteCard: (DailyList) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<DailyList?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    if (cards.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No daily lists yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "dailyListSortBar") {
                Box {
                    SortControl(
                        label = "Sort: " + if (newestFirst) "Newest" else "Oldest",
                        onClick = { sortMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest") },
                            onClick = { sortMenuOpen = false; onNewestFirst(true) },
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest") },
                            onClick = { sortMenuOpen = false; onNewestFirst(false) },
                        )
                    }
                }
            }
            items(cards, key = { it.id }) { card ->
                DailyListCardRow(
                    card = card,
                    items = cardItems[card.id].orEmpty(),
                    celebrationEnabled = celebrationEnabled,
                    celebrationIcon = celebrationIcon,
                    celebrationVisible = celebrationVisible(card, cardItems[card.id].orEmpty()),
                    onOpen = { onOpenCard(card.id) },
                    onToggleFavorite = { onToggleFavorite(card) },
                    onDelete = { deleteCandidate = card },
                )
            }
        }
    }

    // Manual whole-card deletion — the established list deletion dialog, with
    // its exact wording and fixed Okay-then-Cancel button order.
    deleteCandidate?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete list?") },
            // Cancel on the left, Okay on the right. Material renders the dismiss
            // slot before the confirm slot, so Cancel goes in dismiss and Okay in
            // confirm to keep that order on screen.
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    onDeleteCard(card)
                }) { Text("Okay") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A Daily List main-view card: its date always, its per-day title beneath the
 * date when the day has one, and the icon cluster at the right — Favorite
 * star, then completion icon, then three dots, with no blank spaces reserved
 * for missing icons.
 */
@Composable
private fun DailyListCardRow(
    card: DailyList,
    items: List<DailyListItem>,
    celebrationEnabled: Boolean,
    celebrationIcon: CelebrationIcon,
    celebrationVisible: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.date.format(DAILY_LIST_DATE_FORMAT),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A saved title is always displayed on its card, whatever the
                // display toggles do. They are display-only and never delete it.
                if (card.title.isNotBlank()) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = if (item.indent == 1) 20.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.completed) Icons.Filled.Check else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (item.completed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = item.text,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.completed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (card.favorited) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorited — tap to unfavorite",
                    )
                }
            }
            if (celebrationEnabled && celebrationVisible) {
                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = celebrationIconVector(celebrationIcon),
                        contentDescription = "All tasks completed",
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Daily List options")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (card.favorited) "Unfavorite" else "Favorite") },
                        onClick = {
                            menuOpen = false
                            onToggleFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The sort control, framed exactly like AppButton and the drop-down boxes —
 * the shared control corner and outline (docs/STYLE.md §3/§5). Never a pill.
 */
@Composable
private fun SortControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.control)
            .clickable(onClick = onClick)
            .border(
                AppTheme.shapes.controlBorder,
                MaterialTheme.colorScheme.outline,
                AppTheme.shapes.control,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppTheme.textStyles.controlLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
