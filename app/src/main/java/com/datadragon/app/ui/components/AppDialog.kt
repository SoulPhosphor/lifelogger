package com.datadragon.app.ui.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.datadragon.app.ui.theme.AppTheme
import com.datadragon.app.ui.theme.PopupButtonRole

/**
 * The one simple dialog every confirm/notice box in the app is built from — a
 * centered title, left-aligned body, and a centered row of buttons.
 *
 * Everything visual here comes from the theme (color scheme, type scale,
 * shapes, spacing) so the look changes in one place, never on a screen. To
 * recolor or restyle dialogs, edit this file and the theme — not the callers.
 *
 * Buttons keep a fixed order in the row: the dismiss (Cancel) button on the
 * left, the action (or destructive) button on the right. The row itself is
 * centered as a group.
 *
 * Pickers, export dialogs, and option-list dialogs are their own patterns and
 * do not use this.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    body: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            shape = AppTheme.shapes.control,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = AppTheme.sizes.dialogElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppTheme.spacing.dialogInset),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
            ) {
                // Title: centered, the question the dialog is asking.
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Body / subtext: left-aligned, one line explaining what happens.
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Buttons: the row is centered as a group; Cancel (left) then the
                // action (right) keep that order.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppTheme.spacing.related),
                    horizontalArrangement = Arrangement.spacedBy(
                        AppTheme.spacing.related,
                        Alignment.CenterHorizontally,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * The affirmative button in a dialog — the one that does the thing ("Okay",
 * "Unlock", "Open Card"). Its color comes from the theme.
 */
@Composable
fun DialogActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    PopupButton(
        onClick = onClick,
        enabled = enabled,
    ) { Text(text) }
}

/**
 * The destructive button in a dialog — deletes or discards ("Delete Log",
 * "Discard").
 *
 * It is a separate composable from [DialogActionButton] on purpose: a future
 * theme can set destructive actions apart here without touching any screen.
 * Today it renders exactly like the action button — one shared color, no red.
 */
@Composable
fun DialogDestructiveButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    PopupButton(
        onClick = onClick,
        enabled = enabled,
        role = PopupButtonRole.DESTRUCTIVE,
    ) { Text(text) }
}

/** The dismiss button in a dialog — backs out without acting ("Cancel"). */
@Composable
fun DialogDismissButton(
    text: String,
    onClick: () -> Unit,
) {
    PopupButton(
        onClick = onClick,
    ) { Text(text) }
}
