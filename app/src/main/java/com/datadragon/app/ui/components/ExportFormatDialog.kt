package com.datadragon.app.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.datadragon.app.ui.theme.AppTheme

/**
 * One format offered by an export dialog. [title] and [subtitle] are fixed
 * per file type in docs/STYLE.md §7 — a format reads the same wherever it is
 * offered. A format that a thing can't be exported as is simply left out.
 */
data class ExportFormatOption(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * The app's export dialog — the same one for every exportable thing.
 *
 * [thing] is the word for what is being exported ("List", "Form"); the title
 * reads "Export <thing>". Options are soft Material surfaces that ripple on
 * touch, never pills or cards, and the subtitles are the whole explanation —
 * there is no paragraph underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportFormatDialog(
    thing: String,
    options: List<ExportFormatOption>,
    onDismiss: () -> Unit,
    header: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $thing") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
                Text(
                    "Choose an export format",
                    style = AppTheme.textStyles.dialogOptionSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                header?.invoke()
                options.forEach { option ->
                    Surface(
                        onClick = option.onClick,
                        shape = AppTheme.shapes.control,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = AppTheme.spacing.screenInset, vertical = AppTheme.spacing.optionVerticalInset)) {
                            Text(
                                option.title,
                                style = AppTheme.textStyles.dialogOptionTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                option.subtitle,
                                style = AppTheme.textStyles.dialogOptionSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            PopupButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
