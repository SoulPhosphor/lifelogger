package com.datadragon.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.datadragon.app.ui.theme.DeleteRed

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard Changes?") },
        text = { Text("You've made changes that haven't been saved. Discard them?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Discard", color = DeleteRed) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
