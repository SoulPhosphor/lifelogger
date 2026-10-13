package com.datadragon.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.datadragon.app.data.ChoiceEdits
import com.datadragon.app.data.ChoiceNameCheck
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.TitleCase
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.checkName
import com.datadragon.app.data.retiredChoices
import com.datadragon.app.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableColumn

/**
 * The words an option editor shows. Forms options and Idea Categories share
 * one editor and differ only in these.
 */
data class ChoiceEditorWording(
    /** The heading above the rows. */
    val heading: String,
    /** The explanatory lines directly under the heading. */
    val helpLines: List<String>,
    /** The button that adds a new row. */
    val addLabel: String,
    /** The collapsible section's heading, shown with its count: "Retired Options (2)". */
    val retiredHeading: String,
    /** Title of the dialog shown when a new name matches a retired item. */
    val alreadyExistsTitle: String,
    /** Body of that dialog for [label]. */
    val alreadyExistsBody: (label: String) -> String,
    /** Body of the delete dialog for an active item (it also offers Retire). */
    val deleteActiveBody: String,
    /** Body of the delete dialog for an already retired item. */
    val deleteRetiredBody: String,
)

object ChoiceEditorWordings {
    val formOptions = ChoiceEditorWording(
        heading = "Options",
        helpLines = listOf(
            "Tap to rename. Hold and drag to reorder.",
            "Renaming an option changes its label without removing its saved answers.",
        ),
        addLabel = "Add Option",
        retiredHeading = "Retired Options",
        alreadyExistsTitle = "Option Already Exists",
        alreadyExistsBody = { label ->
            "\"$label\" is in Retired Options. Restore it to keep its previously saved data."
        },
        deleteActiveBody = "Deleting will permanently remove all past data associated with this option. " +
            "Retire to keep past data intact.",
        deleteRetiredBody = "Deleting will permanently remove all past data associated with this option.",
    )

    val ideaCategories = ChoiceEditorWording(
        heading = "Categories",
        helpLines = listOf(
            "Tap to rename. Hold and drag to reorder.",
            "Renaming a category changes its label without removing its saved answers.",
        ),
        addLabel = "Add Category",
        retiredHeading = "Retired Categories",
        alreadyExistsTitle = "Category Already Exists",
        alreadyExistsBody = { label ->
            "\"$label\" is in Retired Categories. Restore it to keep its previously saved data."
        },
        deleteActiveBody = "Deleting will permanently remove all past data associated with this category. " +
            "Retire to keep past data intact.",
        deleteRetiredBody = "Deleting will permanently remove all past data associated with this category.",
    )
}

/** Which pre-selection an option editor offers. */
enum class ChoicePreselection {
    /** No pre-selection (Idea Categories). */
    NONE,
    /** At most one pre-selected option (Single Choice). */
    SINGLE,
    /** Any number of pre-selected options (Multiple Choice). */
    MULTIPLE,
}

/**
 * The row-based editor for a saved field's options (Form Single / Multiple
 * Choice) or an Idea Log's Categories.
 *
 * Every row is one [ChoiceOption] and keeps its permanent id through every
 * edit here: a quick tap renames the row in place, a long press (or the grip)
 * drags it to a new position, and its menu retires or deletes it. Renaming,
 * reordering, retiring and restoring only change [choices] through
 * [onChoicesChange]; they are saved with the rest of the form.
 *
 * Permanent deletion is different: after the delete dialog is confirmed, the
 * row is removed through [onChoicesChange] and [onDeletePermanently] is called
 * so the host can commit the deletion — and the removal of every answer that
 * used it — right away.
 *
 * New names follow the duplicate rule against active and retired rows alike:
 * an active match shows the duplicate notice; a retired match offers restoring
 * that retired row (with its original id) instead of creating another.
 */
@Composable
fun ChoiceOptionsEditor(
    choices: List<ChoiceOption>,
    onChoicesChange: (List<ChoiceOption>) -> Unit,
    wording: ChoiceEditorWording,
    preselection: ChoicePreselection,
    capitalizeNew: Boolean,
    onDeletePermanently: (ChoiceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The row being renamed, the label it had when editing began, and whether it
    // is a just-added row that hasn't been committed yet.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var labelBeforeEdit by rememberSaveable { mutableStateOf("") }
    var editingIsNew by rememberSaveable { mutableStateOf(false) }
    var retiredExpanded by rememberSaveable { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<ChoiceOption?>(null) }
    var pendingDelete by remember { mutableStateOf<ChoiceOption?>(null) }
    val focusManager = LocalFocusManager.current

    /**
     * Finish renaming [id] and return the resulting list: a valid new name is
     * kept; a blank or colliding name puts the row back (and drops a just-added
     * row). Returns [current] unchanged when [id] isn't the row being edited.
     */
    fun committed(id: String, current: List<ChoiceOption>): List<ChoiceOption> {
        if (editingId != id) return current
        editingId = null
        val row = current.firstOrNull { it.id == id } ?: return current
        val typed = row.label.trim()
        val revert = if (editingIsNew) {
            ChoiceEdits.withoutId(current, id)
        } else {
            ChoiceEdits.renamed(current, id, labelBeforeEdit)
        }
        if (typed.isEmpty()) return revert
        return when (val check = current.checkName(typed, exceptId = id)) {
            ChoiceNameCheck.Available -> {
                val label = if (editingIsNew && capitalizeNew) TitleCase.apply(typed) else typed
                ChoiceEdits.renamed(current, id, label)
            }
            is ChoiceNameCheck.ActiveDuplicate -> {
                showDuplicate = true
                revert
            }
            is ChoiceNameCheck.RetiredDuplicate -> {
                // The edited row keeps its own name; restoring is offered
                // separately, so two ids are never merged.
                pendingRestore = check.option
                revert
            }
        }
    }

    /** Commit [id]'s rename (if it is being edited) and publish the result. */
    fun commit(id: String) {
        val result = committed(id, choices)
        if (result != choices) onChoicesChange(result)
    }

    /**
     * Settle any rename in progress and return the list to build the next
     * change on, so a commit and a following change never overwrite each other.
     */
    fun settled(): List<ChoiceOption> = editingId?.let { committed(it, choices) } ?: choices

    fun startEditing(option: ChoiceOption) {
        val base = settled()
        if (base != choices) onChoicesChange(base)
        editingId = option.id
        labelBeforeEdit = option.label
        editingIsNew = false
    }

    val active = choices.activeChoices
    val retired = choices.retiredChoices

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
    ) {
        Text(wording.heading, style = MaterialTheme.typography.labelLarge)
        wording.helpLines.forEach { line ->
            Text(
                line,
                style = AppTheme.textStyles.optionEditorHelp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ReorderableColumn keeps per-item state sized to the list it first saw;
        // keying on the count rebuilds it when rows are added, retired or deleted.
        key(active.size) {
            ReorderableColumn(
                list = active,
                onSettle = { from, to ->
                    active.getOrNull(from)?.let { moved ->
                        onChoicesChange(ChoiceEdits.movedActive(choices, moved.id, to))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
            ) { index, option, _ ->
                val isEditing = editingId == option.id
                ActiveChoiceRow(
                    option = option,
                    isEditing = isEditing,
                    preselection = preselection,
                    gripModifier = Modifier
                        .draggableHandle(enabled = !isEditing)
                        .accessibleReorder(index, active.size) { offset ->
                            val destination = index + offset
                            if (destination in active.indices) {
                                onChoicesChange(ChoiceEdits.movedActive(choices, option.id, destination))
                                true
                            } else {
                                false
                            }
                        },
                    // Dragging is off while the row is being edited, so selecting
                    // text or moving the cursor never reorders it.
                    labelDragModifier = Modifier.longPressDraggableHandle(enabled = !isEditing),
                    onTap = { if (!isEditing) startEditing(option) },
                    onLabelChange = { onChoicesChange(ChoiceEdits.renamed(choices, option.id, it)) },
                    onCommit = { commit(option.id) },
                    onDone = { focusManager.clearFocus() },
                    onRemovePreselection = {
                        onChoicesChange(ChoiceEdits.notPreselected(settled(), option.id))
                    },
                    onMakePreselected = {
                        onChoicesChange(
                            ChoiceEdits.preselected(
                                settled(),
                                option.id,
                                single = preselection == ChoicePreselection.SINGLE,
                            ),
                        )
                    },
                    onRetire = { onChoicesChange(ChoiceEdits.retired(settled(), option.id)) },
                    onDelete = {
                        val base = settled()
                        if (base != choices) onChoicesChange(base)
                        // A just-added row that was never named is simply gone.
                        base.firstOrNull { it.id == option.id }?.let { pendingDelete = it }
                    },
                )
            }
        }

        AppButton(
            onClick = {
                val option = ChoiceOption.createNew(label = "")
                val base = settled()
                editingId = option.id
                labelBeforeEdit = ""
                editingIsNew = true
                onChoicesChange(ChoiceEdits.withNew(base, option))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  ${wording.addLabel}")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { retiredExpanded = !retiredExpanded }
                .semantics { stateDescription = if (retiredExpanded) "Expanded" else "Collapsed" }
                .padding(vertical = AppTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${wording.retiredHeading} (${retired.size})",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (retiredExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (retiredExpanded) {
            retired.forEach { option ->
                RetiredChoiceRow(
                    option = option,
                    onRestore = { onChoicesChange(ChoiceEdits.restored(settled(), option.id)) },
                    onDelete = { pendingDelete = option },
                )
            }
        }
    }

    if (showDuplicate) {
        DuplicateItemDialog(onDismiss = { showDuplicate = false })
    }

    pendingRestore?.let { option ->
        AppDialog(
            onDismissRequest = { pendingRestore = null },
            title = wording.alreadyExistsTitle,
            body = wording.alreadyExistsBody(option.label),
            dismissButton = { DialogDismissButton("Cancel") { pendingRestore = null } },
            confirmButton = {
                DialogActionButton("Restore") {
                    onChoicesChange(ChoiceEdits.restored(choices, option.id))
                    pendingRestore = null
                }
            },
        )
    }

    pendingDelete?.let { option ->
        ChoiceDeleteDialog(
            option = option,
            wording = wording,
            onCancel = { pendingDelete = null },
            onRetire = {
                onChoicesChange(ChoiceEdits.retired(choices, option.id))
                pendingDelete = null
            },
            onDelete = {
                onChoicesChange(ChoiceEdits.withoutId(choices, option.id))
                onDeletePermanently(option)
                pendingDelete = null
            },
        )
    }
}

/**
 * The permanent-delete confirmation. An active item offers Cancel, Retire,
 * Delete; an already retired one offers Cancel, Delete.
 */
@Composable
fun ChoiceDeleteDialog(
    option: ChoiceOption,
    wording: ChoiceEditorWording,
    onCancel: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onCancel,
        title = "Delete ${option.label}?",
        body = if (option.retired) wording.deleteRetiredBody else wording.deleteActiveBody,
        dismissButton = { DialogDismissButton("Cancel", onClick = onCancel) },
        confirmButton = {
            if (!option.retired) DialogActionButton("Retire", onClick = onRetire)
            DialogDestructiveButton("Delete", onClick = onDelete)
        },
    )
}

/** The framed box a row's label sits in, shared by active and retired rows. */
@Composable
private fun ChoiceLabelFrame(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(
                AppTheme.shapes.controlBorder,
                if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                AppTheme.shapes.control,
            )
            .padding(horizontal = AppTheme.spacing.rowInset, vertical = AppTheme.spacing.fieldVerticalInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun ActiveChoiceRow(
    option: ChoiceOption,
    isEditing: Boolean,
    preselection: ChoicePreselection,
    gripModifier: Modifier,
    labelDragModifier: Modifier,
    onTap: () -> Unit,
    onLabelChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDone: () -> Unit,
    onRemovePreselection: () -> Unit,
    onMakePreselected: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = gripModifier.padding(horizontal = AppTheme.spacing.related),
        )
        ChoiceLabelFrame(
            highlighted = isEditing,
            modifier = Modifier
                .weight(1f)
                .then(labelDragModifier)
                .clickable(enabled = !isEditing, onClickLabel = "Rename", onClick = onTap),
        ) {
            if (isEditing) {
                EditingLabel(
                    text = option.label,
                    onTextChange = onLabelChange,
                    onCommit = onCommit,
                    onDone = onDone,
                )
            } else {
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (preselection != ChoicePreselection.NONE && option.preselected) {
            IconButton(onClick = onRemovePreselection) {
                Icon(Icons.Filled.TaskAlt, contentDescription = "Remove Pre-Selection")
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Option Menu")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (preselection != ChoicePreselection.NONE && !option.preselected) {
                    DropdownMenuItem(
                        text = { Text("Make Pre-Selected") },
                        onClick = { menuOpen = false; onMakePreselected() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Retire") },
                    onClick = { menuOpen = false; onRetire() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** The in-place text box a row shows while it is being renamed. */
@Composable
private fun EditingLabel(
    text: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDone: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            // Named only while empty; a description would replace typed text for TalkBack.
            .semantics { if (text.isEmpty()) contentDescription = "Option Name" }
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onCommit()
                }
            },
    )
}

@Composable
private fun RetiredChoiceRow(
    option: ChoiceOption,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Lines up with the active rows' grip; retired rows can't be dragged.
        Spacer(Modifier.width(AppTheme.sizes.icon + AppTheme.spacing.related * 2))
        ChoiceLabelFrame(highlighted = false, modifier = Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Filled.Add, contentDescription = "Restore ${option.label}")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${option.label}")
        }
    }
}
