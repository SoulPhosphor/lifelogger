package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.datadragon.app.data.ClickerComparison
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ComparisonRow
import com.datadragon.app.data.ComparisonValues
import com.datadragon.app.data.comparisonDef

/** What a Clicker card's Comparison field can do beyond editing its own rows. */
internal class ClickerComparisonActions(
    /** The log's "Allow Additions from Form": shows New Option. */
    val allowAdditions: Boolean,
    /** Save the field's rows as they change. */
    val onRowsChange: (ClickerField, List<ComparisonRow>) -> Unit,
    /** Add items to field `fieldId`; the callback gets the new items' ids. */
    val onAddOptions: (fieldId: String, additions: List<String>, onAdded: (List<String>) -> Unit) -> Unit,
    /** Restore a retired item of field `fieldId`; the callback runs once it is back. */
    val onRestoreOption: (fieldId: String, optionId: String, onDone: () -> Unit) -> Unit,
    /** Show a short message, such as a refused decimal point. */
    val onNotice: (String) -> Unit,
)

/**
 * A Comparison field on a Clicker card: the same table, buttons and New Option
 * pop-up as on a form entry ([ComparisonField], [AddOptionDialog]). The rows
 * start from [stored] and every change goes to [onRowsChange]. A row with a
 * number but no item is marked until an item is chosen; it isn't stored.
 */
@Composable
internal fun ClickerComparisonEditor(
    field: ClickerField,
    stored: Map<String, String>,
    actions: ClickerComparisonActions,
    onRowsChange: (List<ComparisonRow>) -> Unit,
) {
    val def = remember(field) { field.comparisonDef() }
    var rows by remember(field.id) {
        mutableStateOf(ClickerComparison.rows(stored, field.id).ifEmpty { listOf(ComparisonRow()) })
    }
    var adding by remember(field.id) { mutableStateOf(false) }

    fun change(updated: List<ComparisonRow>) {
        rows = updated
        onRowsChange(updated)
    }

    ComparisonField(
        field = def,
        rows = rows,
        onRowsChange = { change(it) },
        showError = rows.any(ComparisonValues::needsItem),
        onNewOption = if (actions.allowAdditions) {
            { adding = true }
        } else {
            null
        },
        onNotice = actions.onNotice,
    )

    if (adding) {
        AddOptionDialog(
            field = def,
            onCancel = { adding = false },
            onConfirm = { additions ->
                actions.onAddOptions(field.id, additions) { added -> change(ComparisonValues.placeItems(rows, added)) }
                adding = false
            },
            onRestore = { option ->
                actions.onRestoreOption(field.id, option.id) {
                    change(ComparisonValues.placeItems(rows, listOf(option.id)))
                }
            },
        )
    }
}
