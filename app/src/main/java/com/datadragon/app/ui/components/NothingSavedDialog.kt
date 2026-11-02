package com.datadragon.app.ui.components

import androidx.compose.runtime.Composable

/** STYLE.md §9: shared dialog, centered title, left body, outlined action. */
@Composable
fun NothingSavedDialog(onDismiss: () -> Unit) {
    AppDialog(
        title = "Nothing Saved",
        body = "No Data Was Entered",
        onDismissRequest = onDismiss,
        confirmButton = { DialogActionButton("Okay", onClick = onDismiss) },
    )
}
