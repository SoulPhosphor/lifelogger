package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.R
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerValues
import com.datadragon.app.data.editOnlyFromCardMenu
import com.datadragon.app.ui.ClickerLogViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.DialogDestructiveButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val STAMP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val STAMP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private const val WRITE_IN_DEFAULT_DIGITS = 6

/** The stamp line at the top of a card, or null when the log stamps neither part. */
private fun stampText(date: String?, time: String?): String? {
    val d = date?.let { runCatching { LocalDate.parse(it).format(STAMP_DATE_FORMAT) }.getOrNull() }
    val t = time?.let { runCatching { LocalTime.parse(it).format(STAMP_TIME_FORMAT) }.getOrNull() }
    return when {
        d != null && t != null -> "$d at $t"
        d != null -> d
        t != null -> t
        else -> null
    }
}

/** Format a display-only field's stored value for the card face, per its type. */
private fun displayFieldValue(type: ClickerFieldType, raw: String): String = when (type) {
    ClickerFieldType.DATE ->
        runCatching { LocalDate.parse(raw).format(STAMP_DATE_FORMAT) }.getOrDefault(raw)
    ClickerFieldType.TIME ->
        runCatching { LocalTime.parse(raw).format(STAMP_TIME_FORMAT) }.getOrDefault(raw)
    ClickerFieldType.DATE_TIME -> {
        val parts = raw.split(" ")
        val d = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it).format(STAMP_DATE_FORMAT) }.getOrNull() }
        val t = parts.getOrNull(1)?.let { runCatching { LocalTime.parse(it).format(STAMP_TIME_FORMAT) }.getOrNull() }
        if (d != null && t != null) "$d at $t" else raw
    }
    else -> raw
}

/** Keep an optional leading minus and digits, capped at [maxDigits]. */
private fun numberInput(input: String, maxDigits: Int): String {
    val negative = input.startsWith("-")
    val digits = input.filter { it.isDigit() }.take(maxDigits)
    return (if (negative) "-" else "") + digits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickerLogScreen(
    logId: Long,
    onBack: () -> Unit,
    onEditLog: (Long) -> Unit,
    onEditCard: (Long) -> Unit,
    onOpenStatistics: (Long) -> Unit,
    viewModel: ClickerLogViewModel = viewModel(),
) {
    LaunchedEffect(logId) { viewModel.start(logId) }
    val log by viewModel.log.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val followUpShown by viewModel.followUpShown.collectAsStateWithLifecycle()

    var cogMenuOpen by remember { mutableStateOf(false) }
    var confirmDeleteLog by remember { mutableStateOf(false) }

    // Cards are listed newest first, so a card added with the top-bar "+" lands
    // above whatever is on screen. When a tall card fills the screen the list
    // would otherwise stay put and the new card would be out of sight, so each
    // add scrolls back up until the new card's top is at the top of the screen.
    val listState = rememberLazyListState()
    // Counts taps whose card hasn't appeared yet, so several quick taps each scroll.
    var pendingAddScrolls by remember { mutableStateOf(0) }
    val topCardId = cards.firstOrNull()?.id
    LaunchedEffect(topCardId) {
        if (pendingAddScrolls > 0 && topCardId != null) {
            pendingAddScrolls -= 1
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(log?.title ?: "Clicker Data") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        Box {
                            IconButton(onClick = { cogMenuOpen = true }) {
                                Icon(
                                    Icons.Filled.SettingsApplications,
                                    contentDescription = "Clicker Data Options",
                                    modifier = Modifier.size(AppTheme.sizes.settingsCog),
                                )
                            }
                            DropdownMenu(expanded = cogMenuOpen, onDismissRequest = { cogMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { cogMenuOpen = false; onEditLog(logId) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { cogMenuOpen = false; confirmDeleteLog = true },
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Opens the Statistics page. Directly left of the Follow-Up
                    // Notes button when that shows, otherwise directly left of "+".
                    IconButton(onClick = { onOpenStatistics(logId) }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Statistics")
                    }
                    // Directly left of the "+": shows (Edit Note) or hides (Article
                    // Shortcut) the Follow-Up Notes box on every card. Hiding never
                    // touches the notes' saved text.
                    if (log?.allowFollowUp == true) {
                        IconButton(onClick = { viewModel.toggleFollowUpShown() }) {
                            Icon(
                                painterResource(if (followUpShown) R.drawable.ic_article_shortcut else R.drawable.ic_edit_note),
                                contentDescription = if (followUpShown) "Hide Follow-Up Notes" else "Show Follow-Up Notes",
                            )
                        }
                    }
                    IconButton(onClick = { pendingAddScrolls += 1; viewModel.addCard() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New card")
                    }
                },
            )
        },
    ) { padding ->
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No cards yet.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Click the plus in the top right to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    ClickerCardView(
                        card = card,
                        fields = fields,
                        displayOnlyClickerDateTime = log?.displayOnlyClickerDateTime ?: false,
                        showFollowUp = followUpShown,
                        onSetFollowUp = { raw -> viewModel.setValue(card, ClickerValues.FOLLOW_UP_KEY, raw) },
                        onStep = { field -> viewModel.step(card, field) },
                        onSetValue = { field, raw -> viewModel.setValue(card, field.id, raw) },
                        onEditCard = { onEditCard(card.id) },
                        onDeleteCard = { viewModel.deleteCard(card) },
                    )
                }
            }
        }
    }

    if (confirmDeleteLog) {
        AppDialog(
            onDismissRequest = { confirmDeleteLog = false },
            title = "Delete this clicker data?",
            body = "This permanently deletes this clicker data and all of its cards. This can't be undone.",
            dismissButton = { DialogDismissButton("Cancel") { confirmDeleteLog = false } },
            confirmButton = {
                DialogDestructiveButton("Delete") {
                    confirmDeleteLog = false
                    viewModel.deleteLog(onDeleted = onBack)
                }
            },
        )
    }
}

/** One card's face: its stamp, then each field rendered for its type. */
@Composable
private fun ClickerCardView(
    card: ClickerCard,
    fields: List<ClickerField>,
    displayOnlyClickerDateTime: Boolean,
    showFollowUp: Boolean,
    onSetFollowUp: (String) -> Unit,
    onStep: (ClickerField) -> Unit,
    onSetValue: (ClickerField, String) -> Unit,
    onEditCard: () -> Unit,
    onDeleteCard: () -> Unit,
) {
    val values = remember(card.valuesJson) { ClickerValues.decode(card.valuesJson) }
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stampText(card.displayDate, card.displayTime) ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Card options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuOpen = false; onEditCard() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDeleteCard() },
                        )
                    }
                }
            }

            fields.forEach { field ->
                val hiddenOnFace = displayOnlyClickerDateTime &&
                    (field.type == ClickerFieldType.TEXT || field.type == ClickerFieldType.MULTITEXT)
                if (!hiddenOnFace) {
                    ClickerFieldFace(
                        field = field,
                        values = values,
                        onStep = { onStep(field) },
                        onSetValue = { raw -> onSetValue(field, raw) },
                    )
                }
            }

            // Follow-Up Notes at the bottom of the card, only while the top-bar
            // button has them showing. Saved as typed, like the text fields.
            if (showFollowUp) {
                var followUp by remember { mutableStateOf(ClickerValues.text(values, ClickerValues.FOLLOW_UP_KEY)) }
                Labeled("Follow-Up Notes") {
                    OutlinedTextField(
                        value = followUp,
                        onValueChange = { followUp = it; onSetFollowUp(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = CLICKER_TEXT_BOX_MIN_HEIGHT),
                    )
                }
            }
        }
    }
}

/** One field on a card face; number trackers and text fields are editable, date/time read-only. */
@Composable
private fun ClickerFieldFace(
    field: ClickerField,
    values: Map<String, String>,
    onStep: () -> Unit,
    onSetValue: (String) -> Unit,
) {
    when (field.type) {
        ClickerFieldType.CLICK_TRACKER -> {
            val value = ClickerValues.number(values, field.id) ?: field.startingNumber
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${field.label}: $value", modifier = Modifier.weight(1f))
                AppButton(onClick = onStep) {
                    Text(field.buttonLabel.ifBlank { "Add" })
                }
            }
        }
        ClickerFieldType.WRITE_IN_NUMBER -> {
            val maxDigits = field.maxDigits ?: WRITE_IN_DEFAULT_DIGITS
            var text by remember(field.id) { mutableStateOf(ClickerValues.text(values, field.id)) }
            // The label sits above the box, never beside or inside it (docs/STYLE.md §1 rule 7).
            Labeled(field.label) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = numberInput(it, maxDigits); onSetValue(text) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    if (field.autoIncrement) {
                        Spacer(Modifier.width(8.dp))
                        AppButton(onClick = {
                            val current = text.toIntOrNull() ?: 0
                            val delta = if (field.incrementDirection == com.datadragon.app.data.ClickerIncrementDirection.ADD) {
                                field.incrementAmount
                            } else {
                                -field.incrementAmount
                            }
                            text = (current + delta).toString()
                            onSetValue(text)
                        }) {
                            Text(field.buttonLabel.ifBlank { "Okay" })
                        }
                    }
                }
            }
        }
        ClickerFieldType.TEXT, ClickerFieldType.MULTITEXT -> {
            // Edited right on the card face and saved as typed; the label sits
            // above the box, never inside it (docs/STYLE.md §1 rule 7). A
            // Multi-Line Text box grows to show its whole text.
            val multiLine = field.type == ClickerFieldType.MULTITEXT
            var text by remember(field.id) { mutableStateOf(ClickerValues.text(values, field.id)) }
            Labeled(field.label) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; onSetValue(it) },
                    singleLine = !multiLine,
                    modifier = if (multiLine) {
                        Modifier.fillMaxWidth().heightIn(min = CLICKER_TEXT_BOX_MIN_HEIGHT)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
            }
        }
        else -> {
            // Display-only types: show the stored value formatted, read-only on the face.
            val shown = displayFieldValue(field.type, ClickerValues.text(values, field.id))
            if (field.type.editOnlyFromCardMenu) {
                Text(
                    text = if (shown.isNotBlank()) "${field.label}: $shown" else field.label,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
