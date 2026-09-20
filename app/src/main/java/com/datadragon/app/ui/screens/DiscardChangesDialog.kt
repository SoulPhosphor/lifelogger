package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogDestructiveButton
import com.datadragon.app.ui.components.DialogDismissButton

/**
 * Confirmation shown when leaving a screen that has unsaved input. "Discard"
 * throws the unsaved changes away and leaves; "Cancel" stays on the screen.
 * Shared by the new-list editor and the form screens so the warning reads the
 * same everywhere.
 */
@Composable
fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "Discard Changes?",
        body = "You've made changes that haven't been saved. Discard them?",
        dismissButton = { DialogDismissButton("Cancel", onClick = onDismiss) },
        confirmButton = { DialogDestructiveButton("Discard", onClick = onConfirm) },
    )
}
