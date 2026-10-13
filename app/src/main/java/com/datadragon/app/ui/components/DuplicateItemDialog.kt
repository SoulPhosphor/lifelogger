package com.datadragon.app.ui.components

import androidx.compose.runtime.Composable
import com.datadragon.app.data.OptionDuplicates

/**
 * The notice shown whenever a Forms or Ideas item (an option, a Category, or a
 * tag) can't be added because the same item already exists. Built from
 * [AppDialog]: the owner's sentence as the centered title and a single Okay.
 */
@Composable
fun DuplicateItemDialog(onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = OptionDuplicates.MESSAGE,
        confirmButton = { DialogActionButton("Okay", onClick = onDismiss) },
    )
}
