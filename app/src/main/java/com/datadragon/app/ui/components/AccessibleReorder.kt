package com.datadragon.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** Pointer dragging remains intact; assistive technology can move one place at a time. */
fun Modifier.accessibleReorder(index: Int, count: Int, onMove: (Int) -> Boolean): Modifier = semantics {
    stateDescription = "Item ${index + 1} of $count"
    customActions = buildList {
        if (index > 0) add(CustomAccessibilityAction("Move Up") { onMove(-1) })
        if (index < count - 1) add(CustomAccessibilityAction("Move Down") { onMove(1) })
    }
}

/** Move existing identities only. Reject stale/missing items and moves outside the list. */
fun <T> reorderedItems(items: List<T>, item: T, offset: Int): List<T>? {
    val from = items.indexOf(item)
    val to = from + offset
    if (from < 0 || to !in items.indices || offset !in listOf(-1, 1)) return null
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
