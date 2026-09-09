package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
import com.datadragon.app.data.IdeaValues
import com.datadragon.app.ui.components.CopyValueButton
import com.datadragon.app.ui.components.CountsLine
import com.datadragon.app.ui.components.TagChipRow
import com.datadragon.app.ui.components.WebpageOpenButton
import kotlinx.serialization.json.JsonObject

/**
 * How much of an idea a surface shows. Cards obey the log's display settings;
 * the full Idea Detail view ignores all of them and shows everything.
 */
enum class IdeaDisplayMode { PREVIEW, ENTIRE_CARD, DETAIL }

/** The display mode a card is in, from the log's "Show Entire Idea Card" switch. */
fun cardDisplayMode(log: IdeaLog?): IdeaDisplayMode =
    if (log?.showEntireIdeaCard == true) IdeaDisplayMode.ENTIRE_CARD else IdeaDisplayMode.PREVIEW

/**
 * The fields this surface draws, in the log's field order and only where the
 * idea actually has a value.
 *
 * Preview Mode is the only mode that hides anything: a field left out of Preview
 * Mode simply isn't drawn on the card. Its stored value is untouched and always
 * appears in the full Idea Detail view.
 */
fun visibleIdeaFields(
    fields: List<IdeaFieldDef>,
    values: JsonObject,
    mode: IdeaDisplayMode,
): List<IdeaFieldDef> = fields.filter { field ->
    IdeaValues.hasValue(values, field) &&
        (mode != IdeaDisplayMode.PREVIEW || field.includeInPreview)
}

/**
 * How many rendered lines a multiline field is allowed here, or null for all of
 * them.
 *
 * - Preview Mode: the log's "Number of Lines Shown in Preview Mode".
 * - Entire Idea Card Mode: everything, unless this field opted into its own
 *   truncation rule.
 * - The full detail view: everything, always.
 *
 * This is a display limit only. The stored text is never shortened.
 */
private fun multilineLimit(field: IdeaFieldDef, log: IdeaLog?, mode: IdeaDisplayMode): Int? =
    when (mode) {
        IdeaDisplayMode.PREVIEW -> log?.previewLines ?: com.datadragon.app.data.DEFAULT_IDEA_LINES
        IdeaDisplayMode.ENTIRE_CARD -> field.entireCardLines.takeIf { field.truncateInEntireCard }
        IdeaDisplayMode.DETAIL -> null
    }

/**
 * One field of an idea, rendered read-only.
 *
 * Most types read as `label: value` on one line. A Tags field shows its label
 * with the tags as chips beneath. A Webpages field gets an open-in-browser
 * button beside its address — only that button navigates. A multiline field can
 * carry its own word/character counts and a copy button, and is the only type
 * whose display is ever cut short.
 */
@Composable
fun IdeaFieldReadout(
    field: IdeaFieldDef,
    values: JsonObject,
    log: IdeaLog?,
    mode: IdeaDisplayMode,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
) {
    when (field.type) {
        FieldType.TAGS -> {
            val tags = IdeaValues.tags(values, field)
            if (tags.isNotEmpty()) {
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdeaFieldLabel(field.label)
                    TagChipRow(tags)
                }
            }
        }

        FieldType.MULTILINE -> {
            val text = IdeaValues.rawValue(values, field)
            if (text != null) {
                val limit = multilineLimit(field, log, mode)
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    IdeaFieldLabel(field.label)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = limit ?: Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IdeaValues.countsLine(field, text)?.let { CountsLine(it) }
                    // Copy always takes the whole stored text, even where the
                    // visible text above was cut short.
                    if (field.allowCopying) CopyValueButton(text)
                }
            }
        }

        FieldType.WEBPAGE -> {
            val address = IdeaValues.rawValue(values, field)
            if (address != null) {
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    InlineLabelledValue(
                        label = field.label,
                        value = address,
                        modifier = Modifier.weight(1f),
                    )
                    WebpageOpenButton(address)
                }
            }
        }

        else -> {
            val value = IdeaValues.displayValue(values, field)
            if (value != null) {
                InlineLabelledValue(label = field.label, value = value, modifier = modifier)
            }
        }
    }
}

@Composable
private fun IdeaFieldLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** `label: value` on one line — the muted label, then the value at normal weight. */
@Composable
private fun InlineLabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}
