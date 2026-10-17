package com.datadragon.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.datadragon.app.data.AdvancedSearch
import com.datadragon.app.data.AdvancedSearchCriteria
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.ui.theme.AppTheme

/** Kept outside expandable content so collapsing never loses criteria or draft input. */
@Stable
class AdvancedViewState {
    var expanded by mutableStateOf(false)
    var draft by mutableStateOf(AdvancedSearchCriteria())
    var applied by mutableStateOf(AdvancedSearchCriteria())
    fun search() { applied = draft.copy(query = draft.query.trim()) }
    fun clear() { draft = AdvancedSearchCriteria(); applied = AdvancedSearchCriteria() }
}

@Composable
fun AdvancedView(
    state: AdvancedViewState,
    resultCount: Int,
    modifier: Modifier = Modifier,
    fields: List<FieldDef>? = null,
    sorting: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(AppTheme.shapes.control)
                .clickable(role = Role.Button) { state.expanded = !state.expanded }
                .semantics { stateDescription = if (state.expanded) "Expanded" else "Collapsed" }
                .minimumInteractiveComponentSize()
                .border(AppTheme.shapes.controlBorder, MaterialTheme.colorScheme.outline, AppTheme.shapes.control)
                .padding(AppTheme.spacing.rowInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("Advanced View", style = AppTheme.textStyles.settingTitle)
                AdvancedSearch.summary(state.applied, resultCount, fields.orEmpty())?.let {
                    Text(it, style = AppTheme.textStyles.settingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                if (state.expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = null,
            )
        }
        if (state.expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
                Text("Search", style = AppTheme.textStyles.settingTitle)
                AccessibleOutlinedTextField(
                    value = state.draft.query,
                    onValueChange = { state.draft = state.draft.copy(query = it) },
                    accessibleLabel = "Search",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { state.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup()) {
                    listOf(false to "Partial Match", true to "Whole Words").forEach { (whole, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = state.draft.wholeWords == whole,
                                role = Role.RadioButton,
                                onClick = { state.draft = state.draft.copy(wholeWords = whole) },
                            ).minimumInteractiveComponentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.draft.wholeWords == whole, onClick = null)
                            Text(label, modifier = Modifier.padding(start = AppTheme.spacing.related))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = state.draft.matchCase, role = Role.Checkbox,
                        onValueChange = { state.draft = state.draft.copy(matchCase = it) },
                    ).minimumInteractiveComponentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = state.draft.matchCase, onCheckedChange = null)
                    Text("Match Case", modifier = Modifier.padding(start = AppTheme.spacing.related))
                }
                if (fields != null) {
                    val textFields = AdvancedSearch.textFields(fields)
                    val scopes = listOf<String?>(null) + textFields.map { it.id } + EntryValues.NOTES_KEY
                    AdvancedDropdown(
                        "Search In", scopes, state.draft.textFieldId,
                        { state.draft = state.draft.copy(textFieldId = it) },
                        { id -> when (id) {
                            null -> "All Text Fields and Notes"
                            EntryValues.NOTES_KEY -> "Notes"
                            else -> textFields.firstOrNull { it.id == id }?.label.orEmpty()
                        } },
                    )
                    HorizontalDivider()
                    Text("Filter by Saved Answer", style = AppTheme.textStyles.subsectionHeader)
                    val answerFields = AdvancedSearch.answerFields(fields)
                    AdvancedDropdown(
                        "Field", listOf<String?>(null) + answerFields.map { it.id }, state.draft.answerFieldId,
                        { state.draft = state.draft.copy(answerFieldId = it, answer = null) },
                        { id -> answerFields.firstOrNull { it.id == id }?.label ?: "All Fields" },
                    )
                    answerFields.firstOrNull { it.id == state.draft.answerFieldId }?.let { field ->
                        val answers = AdvancedSearch.answers(field)
                        AdvancedDropdown(
                            "Answer", listOf<String?>(null) + answers.map { it.value }, state.draft.answer,
                            { state.draft = state.draft.copy(answer = it) },
                            { value -> answers.firstOrNull { it.value == value }?.label ?: "All Answers" },
                        )
                    }
                }
                AppButton(onClick = state::search, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Search") }
                AppButton(onClick = state::clear, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Clear Search") }
                HorizontalDivider()
                sorting()
            }
        }
    }
}

/** Full-width outlined dropdown: stable width, wrapping labels, and a stroke chevron. */
@Composable
fun <T> AdvancedDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
        Text(label, style = AppTheme.textStyles.settingTitle)
        Box {
            AppButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    .semantics { stateDescription = "$label, ${optionLabel(selected)}" },
            ) {
                Text(optionLabel(selected), Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); open = false })
                }
            }
        }
    }
}

@Composable
fun AdvancedSorting(
    categoryLabels: List<String> = emptyList(),
    selectedLabel: String? = null,
    onSelectCategory: (String) -> Unit = {},
    newestFirst: Boolean,
    onSelectNewestFirst: (Boolean) -> Unit,
) {
    if (categoryLabels.isNotEmpty()) {
        AdvancedDropdown("Sort By", categoryLabels, selectedLabel ?: categoryLabels.first(), onSelectCategory, { it })
    }
    AdvancedDropdown("Sort", listOf(true, false), newestFirst, onSelectNewestFirst, { if (it) "Newest" else "Oldest" })
}
