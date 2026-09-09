package com.datadragon.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datadragon.app.data.WebAddress
import com.datadragon.app.ui.theme.AppTheme

/**
 * The controls shared by Forms and Ideas for the two field capabilities added
 * for Ideas — Tags and Webpages — plus the copy button a multiline Idea field
 * can carry. They live here so both features draw the same thing.
 */

/**
 * A saved tag shown read-only: a chip with no removal "X". It is drawn rather
 * than borrowed from a Material chip so it never looks tappable or disabled —
 * it is a value on display, not a control.
 */
@Composable
fun TagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(
                AppTheme.shapes.controlBorder,
                MaterialTheme.colorScheme.outline,
                AppTheme.shapes.control,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Every saved tag of a field, read-only, wrapping onto as many rows as it needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipRow(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { TagChip(it) }
    }
}

/**
 * The Tags entry control: a text box with an **Add** button immediately after
 * it, and the saved tags below as removable chips.
 *
 * Add trims the typed text, ignores it when empty, and refuses a tag this field
 * already holds — compared without regard to case, so "android" can't be added
 * alongside "Android". The casing of the first saved tag is what stays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var draft by remember { mutableStateOf("") }

    fun addDraft() {
        val tag = draft.trim()
        if (tag.isEmpty()) return
        if (tags.any { it.equals(tag, ignoreCase = true) }) {
            // Already held under some casing; the first one keeps its spelling.
            draft = ""
            return
        }
        onTagsChange(tags + tag)
        draft = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                isError = isError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft() }),
                modifier = Modifier
                    .weight(1f)
                    .let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
            Spacer(Modifier.width(8.dp))
            AppButton(onClick = { addDraft() }) { Text("Add") }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The Webpages entry control: the person filling the idea or entry in types the
 * address here. What they type is what gets stored, trimmed and not otherwise
 * rewritten. A blank optional field is fine; anything non-blank has to be a real
 * webpage address.
 */
@Composable
fun WebpageEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val invalid = value.isNotBlank() && !WebAddress.isValid(value)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError || invalid,
            placeholder = { Text("example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
        )
        if (invalid) {
            Text(
                "Enter a webpage address, like example.com.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * A saved webpage address with a small button that opens it in the browser.
 * Only the button navigates — the card or row around it keeps its own tap.
 */
@Composable
fun WebpageOpenButton(address: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Nothing to open when the stored text isn't a usable address, so no button.
    val url = WebAddress.openable(address)
    if (url != null) {
        IconButton(
            onClick = {
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) {
                    Toast.makeText(context, "Couldn't open $address", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = "Open $address",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The copy button under a read-only multiline value. It always copies the whole
 * stored text, even when the visible text on a card was cut short.
 */
@Composable
fun CopyValueButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The right-aligned word / character count line under a multiline field. */
@Composable
fun CountsLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}
