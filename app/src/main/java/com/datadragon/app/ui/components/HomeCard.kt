package com.datadragon.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one home-screen list card, shared by every data mode's home list (Forms,
 * Ideas, Clicker, …) so they look identical and are restyled in a single place.
 *
 * A card is a title with an optional second summary line under it, tappable to
 * open. It sizes to its content. Two optional slots keep the shape shared while
 * letting a mode add its own pieces without a second card style:
 * - [leadingIcon] sits just before the title (e.g. a Form's lock icon).
 * - [trailing] sits at the far right (e.g. a per-row add button).
 *
 * The style — card shape, padding, and the title/summary text roles — lives only
 * here. Do not hand-roll another card for a home list; add a slot instead.
 */
@Composable
fun HomeCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A trailing button carries its own touch padding, so the card's
                // own end padding tightens when one is present.
                .padding(
                    start = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = if (trailing != null) 4.dp else 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.invoke()
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
