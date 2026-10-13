package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.OptionDuplicates
import com.datadragon.app.data.stripsPreselectMarker
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.components.DuplicateItemDialog
import com.datadragon.app.ui.components.TagsEditor

/**
 * "Add Option to (field title)": a single line with an Add button, the added
 * words listed beneath as chips with an X to remove each, then Cancel / Okay.
 * The line, Add button and chips are the shared [TagsEditor], so an item that
 * repeats one of [field]'s options or an already-added chip is refused with the
 * duplicate notice. Okay also takes any text still on the line, subject to the
 * same check, and hands every new word to [onConfirm].
 */
@Composable
internal fun AddOptionDialog(
    field: FieldDef,
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var added by rememberSaveable(field.label) { mutableStateOf(listOf<String>()) }
    var draft by rememberSaveable(field.label) { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = onCancel,
        title = "Add Option to ${field.label}",
        bodyContent = {
            TagsEditor(
                tags = added,
                onTagsChange = { added = it },
                existingItems = field.options,
                stripPreselectMarker = field.stripsPreselectMarker,
                draft = draft,
                onDraftChange = { draft = it },
            )
        },
        dismissButton = { DialogDismissButton("Cancel", onClick = onCancel) },
        confirmButton = {
            DialogActionButton("Okay") {
                val typed = draft.trim()
                if (typed.isEmpty()) {
                    onConfirm(added)
                } else if (
                    OptionDuplicates.contains(added + field.options, typed, field.stripsPreselectMarker)
                ) {
                    showDuplicate = true
                } else {
                    onConfirm(added + typed)
                }
            }
        },
    )

    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
    }
}
