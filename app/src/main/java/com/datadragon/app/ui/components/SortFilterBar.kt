package com.datadragon.app.ui.components

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
 * The ordering controls that sit at the top of an entry list:
 * `[ Timestamp ▾ ] [ Sort: Newest ▾ ] [ ✕ Clear ]`. The first chip carries the
 * name of the field the list is currently ordered by, and its menu lists every
 * other ordering available. With only the automatic entry timestamp to sort by
 * there is nothing to choose between, so that chip becomes a plain label next to
 * the Sort dropdown. Clear drops both picks and returns the list to its own
 * default ordering. The whole row is centred across the screen.
 *
 * Forms and Ideas share this one bar — there is a single sorting system, not one
 * per feature — so it is addressed purely by category name.
 */
@Composable
fun SortFilterBar(
    categoryLabels: List<String>,
    selectedLabel: String?,
    newestFirst: Boolean,
    onSelectCategory: (String) -> Unit,
    onSelectNewestFirst: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categoryLabels.isEmpty()) return
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (categoryLabels.size > 1) {
            Box {
                AssistChip(
                    onClick = { fieldMenuOpen = true },
                    label = { Text(selectedLabel ?: categoryLabels.first()) },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    },
                    interactionSource = NoPressFeedback,
                )
                DropdownMenu(
                    expanded = fieldMenuOpen,
                    onDismissRequest = { fieldMenuOpen = false },
                ) {
                    categoryLabels.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = {
                                if (label == selectedLabel) {
                                    Icon(Icons.Filled.Check, contentDescription = "Sorting by this")
                                }
                            },
                            onClick = {
                                fieldMenuOpen = false
                                onSelectCategory(label)
                            },
                        )
                    }
                }
            }
        } else {
            // Nothing to pick between — name the one ordering instead.
            Text(
                categoryLabels.single(),
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
