package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.OptionDuplicates
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.matchingLabel
import com.datadragon.app.data.retiredChoices
import com.datadragon.app.ui.components.ChoiceEditorWordings
import androidx.compose.runtime.remember
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.components.DuplicateItemDialog
import com.datadragon.app.ui.components.TagsEditor

/**
 * "Add Option to (field title)": a single line with an Add button, the added
 * words listed beneath as chips with an X to remove each, then Cancel / Okay.
 * The line, Add button and chips are the shared [TagsEditor], so an item that
 * repeats one of [field]'s active options or an already-added chip is refused
 * with the duplicate notice. An item that matches a retired option offers
 * restoring that option (with its original id and saved answers) through
 * [onRestore] instead of creating a second one. Okay also takes any text still
 * on the line, subject to the same checks, and hands every new word to
 * [onConfirm].
 */
@Composable
internal fun AddOptionDialog(
    field: FieldDef,
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onRestore: (ChoiceOption) -> Unit,
) {
    var added by rememberSaveable(field.id) { mutableStateOf(listOf<String>()) }
    var draft by rememberSaveable(field.id) { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    var retiredMatch by remember { mutableStateOf<ChoiceOption?>(null) }
    val activeLabels = field.activeChoices.map { it.label }

    /** True (and the restore offer shown) when [label] names a retired option. */
    fun offerRestore(label: String): Boolean {
        val match = field.retiredChoices.matchingLabel(label) ?: return false
        retiredMatch = match
        return true
    }

    AppDialog(
        onDismissRequest = onCancel,
        title = "Add Option to ${field.label}",
        bodyContent = {
            TagsEditor(
                tags = added,
                onTagsChange = { added = it },
                existingItems = activeLabels,
                draft = draft,
                onDraftChange = { draft = it },
                interceptAdd = { offerRestore(it) },
            )
        },
        dismissButton = { DialogDismissButton("Cancel", onClick = onCancel) },
        confirmButton = {
            DialogActionButton("Okay") {
                val typed = draft.trim()
                when {
                    typed.isEmpty() -> onConfirm(added)
                    offerRestore(typed) -> Unit
                    OptionDuplicates.contains(added + activeLabels, typed) -> showDuplicate = true
                    else -> onConfirm(added + typed)
                }
            }
        },
    )

    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
    }

    retiredMatch?.let { option ->
        AppDialog(
            onDismissRequest = { retiredMatch = null },
            title = ChoiceEditorWordings.formOptions.alreadyExistsTitle,
            body = ChoiceEditorWordings.formOptions.alreadyExistsBody(option.label),
            dismissButton = { DialogDismissButton("Cancel") { retiredMatch = null } },
            confirmButton = {
                DialogActionButton("Restore") {
                    onRestore(option)
                    retiredMatch = null
                    draft = ""
                }
            },
        )
    }
}
