package com.datadragon.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.datadragon.app.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A Home row's body remains clickable; only its optional left handle drags. */
@Composable
fun <T> HomeGroupingList(
    rows: List<T>,
    uuid: (T) -> String,
    reordering: Boolean,
    onOrder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T, (@Composable () -> Unit)?) -> Unit,
) {
    var displayed by remember(rows) { mutableStateOf(rows) }
    val listState = rememberLazyListState()
    fun move(from: Int, to: Int) {
        if (from !in displayed.indices || to !in displayed.indices || from == to) return
        displayed = displayed.toMutableList().apply { add(to, removeAt(from)) }
        onOrder(displayed.map(uuid))
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (reordering) move(from.index, to.index)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(AppTheme.spacing.rowInset),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
    ) {
        itemsIndexed(displayed, key = { _, row -> uuid(row) }) { index, row ->
            ReorderableItem(reorderState, key = uuid(row)) {
                val handle: (@Composable () -> Unit)? = if (reordering) {
                    {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle().accessibleReorder(index, displayed.size) { offset ->
                                val from = displayed.indexOfFirst { uuid(it) == uuid(row) }
                                val to = from + offset
                                if (from >= 0 && to in displayed.indices) {
                                    move(from, to)
                                    true
                                } else false
                            },
                        ) { Icon(Icons.Filled.DragIndicator, contentDescription = "Drag to Reorder") }
                    }
                } else null
                content(row, handle)
            }
        }
    }
}
