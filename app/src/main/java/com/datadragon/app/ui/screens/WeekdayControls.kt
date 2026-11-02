package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.ChoiceStyle
import com.datadragon.app.data.Weekday
import com.datadragon.app.data.WeekdaySettings
import com.datadragon.app.data.activeChoices
import com.datadragon.app.ui.components.AppDialog
import com.datadragon.app.ui.components.ChoiceEditorWordings
import com.datadragon.app.ui.components.ChoiceOptionsEditor
import com.datadragon.app.ui.components.ChoicePreselection
import com.datadragon.app.ui.components.DialogActionButton
import com.datadragon.app.ui.components.DialogDismissButton
import com.datadragon.app.ui.theme.AppTheme

/** Reuses the app's controls and theme; the owner explicitly places the subtext below this toggle. */
@Composable
internal fun WeekdaySettingsControls(
    settings: WeekdaySettings,
    choices: List<ChoiceOption>,
    seeds: List<ChoiceOption>,
    persistedIds: Set<String>,
    onSettingsChange: (WeekdaySettings) -> Unit,
    onChoicesChange: (List<ChoiceOption>) -> Unit,
    required: Boolean,
    onRequiredChange: (Boolean) -> Unit,
    capitalizeNew: Boolean,
    onDeletePermanently: (ChoiceOption) -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    fun changeToSingle() {
        onChoicesChange(choices.map { it.copy(preselected = false) })
        onSettingsChange(settings.copy(onlyOne = true))
        confirmReset = false
    }
    SettingSwitchRow(checked = required, title = "Required", onCheckedChange = onRequiredChange)
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
        SettingSwitchRow(
            checked = settings.defaultEnteredDay,
            title = "Default Entered Day",
            onCheckedChange = { checked ->
                var updated = if (checked) Weekday.restoreWeekdays(choices, seeds) else choices
                if (checked && settings.onlyOne) updated = updated.map { it.copy(preselected = false) }
                onChoicesChange(updated)
                onSettingsChange(settings.copy(defaultEnteredDay = checked))
            },
        )
        Text(
            "When enabled all 7 week days must be displayed.",
            style = AppTheme.textStyles.settingDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingSwitchRow(
        checked = settings.onlyOne,
        title = "Only Allow Choosing One Day",
        onCheckedChange = { checked ->
            if (checked && choices.activeChoices.count { it.preselected } > 1) {
                confirmReset = true
            } else if (checked && settings.defaultEnteredDay) {
                changeToSingle()
            } else {
                onSettingsChange(settings.copy(onlyOne = checked))
            }
        },
    )
    ChoiceOptionsEditor(
        choices = choices,
        onChoicesChange = onChoicesChange,
        wording = ChoiceEditorWordings.formOptions,
        preselection = when {
            settings.onlyOne && settings.defaultEnteredDay -> ChoicePreselection.NONE
            settings.onlyOne -> ChoicePreselection.SINGLE
            else -> ChoicePreselection.MULTIPLE
        },
        capitalizeNew = capitalizeNew,
        onDeletePermanently = onDeletePermanently,
        persistedIds = persistedIds,
        protectedIds = if (settings.defaultEnteredDay) choices.filter { it.weekday != null }.map { it.id }.toSet() else emptySet(),
    )
    if (settings.onlyOne) ChoiceStyleRadios(
        style = settings.choiceStyle,
        onStyleChange = { onSettingsChange(settings.copy(choiceStyle = it)) },
    )
    if (confirmReset) AppDialog(
        onDismissRequest = { confirmReset = false },
        title = "Default Options Will Be Reset",
        body = "You had multiple items selected as default. These will be reset and you will have to select what you would like your new default option to be if any.",
        dismissButton = { DialogDismissButton("Cancel") { confirmReset = false } },
        confirmButton = { DialogActionButton("Okay") { changeToSingle() } },
    )
}

@Composable
internal fun WeekdayEntryControl(
    label: String,
    choices: List<ChoiceOption>,
    settings: WeekdaySettings,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    val offered = choices.filter { !it.retired || it.id in selected }
    if (!settings.onlyOne) {
        MultipleField(
            label = label, options = offered, selectedIds = selected,
            onToggle = { option -> onSelectedChange(if (option.id in selected) selected - option.id else selected + option.id) },
            onAdd = onAdd,
        )
    } else if (settings.choiceStyle == ChoiceStyle.DISPLAYED) {
        SingleChoiceChipsField(
            label = label, options = offered, selectedId = selected.firstOrNull().orEmpty(),
            onTap = { option -> onSelectedChange(if (option.id in selected) emptySet() else setOf(option.id)) },
            onAdd = onAdd,
        )
    } else {
        DropdownField(
            label = label, options = offered,
            selectedLabel = choices.firstOrNull { it.id == selected.firstOrNull() }?.label ?: selected.firstOrNull().orEmpty(),
            onSelected = { onSelectedChange(setOf(it.id)) },
            onAdd = onAdd,
            emptyLabel = "Select",
        )
    }
}
