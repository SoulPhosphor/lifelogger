package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CalendarColorRow
import com.datadragon.app.data.CalendarConfig
import com.datadragon.app.data.CalendarCalcRules
import com.datadragon.app.data.CalendarConditions
import com.datadragon.app.data.CalendarType
import com.datadragon.app.data.ColorPresetCodec
import com.datadragon.app.data.ColorPresets
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.heatMapApplicable
import com.datadragon.app.ui.CalendarConfigViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDropdownRow
import com.datadragon.app.ui.components.ColorPickerDialog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The single, vertically scrollable Edit Calendar screen (owner direction: one
 * screen, never a wizard). It opens for a new calendar from the Form Editor's
 * "Edit Calendar" button, or for an existing one from the calendars list at the
 * bottom of Edit Form, with that calendar's saved values loaded.
 *
 * Built so far, top to bottom: Choose Calendar Type, Calendar Label, Description;
 * for a Heat Map, the data source (Map Heat Map to) and the Calculation Rule
 * (Count Matching reveals a numeric condition + value, or a target-option picker
 * for a choice field); for a Yes/No calendar, Item Tracked (a Yes/No field) and
 * the single Count Yes / No / Unknown rule; then — for every type — the color
 * configuration (how many colors, the Color Preset, and the Color / Min Value /
 * Max Value rows) with the swatch color picker and Save Colors as Preset. Save
 * Calendar and Add Another Calendar close the screen. Still to come on this same
 * screen: the Min/Max Value calendar type's own source control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarConfigScreen(
    logId: String?,
    calendarId: String?,
    onBack: () -> Unit,
    viewModel: CalendarConfigViewModel = viewModel(),
) {
    val templateId = logId?.toLongOrNull()
    val existingCalendarId = calendarId?.toLongOrNull()
    LaunchedEffect(templateId, existingCalendarId) {
        templateId?.let { viewModel.load(it, existingCalendarId) }
    }

    val initial by viewModel.initial.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val formFields by viewModel.formFields.collectAsStateWithLifecycle()

    // Screen-owned editable state, seeded once from the loaded values. Type is
    // held as its token string so it survives process death in the saved state.
    var seeded by rememberSaveable { mutableStateOf(false) }
    var typeToken by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var savedTypeToken by rememberSaveable { mutableStateOf<String?>(null) }
    var savedLabel by rememberSaveable { mutableStateOf("") }
    var savedDescription by rememberSaveable { mutableStateOf("") }

    // Color configuration (range types). Color count is null until the user picks
    // 3/5/10; the rows are seeded from the chosen preset.
    var colorCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var colorPreset by rememberSaveable { mutableStateOf(ColorPresets.GRADIATED) }
    val colorRows = rememberSaveable(saver = colorRowsSaver) { mutableStateListOf<ColorRowState>() }
    var savedColorCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var savedColorPreset by rememberSaveable { mutableStateOf(ColorPresets.GRADIATED) }
    var savedColorRowsJson by rememberSaveable { mutableStateOf(encodeRows(emptyList())) }

    // Heat Map data source + Calculation Rule.
    var sourceLogFrequency by rememberSaveable { mutableStateOf(false) }
    var sourceField by rememberSaveable { mutableStateOf<String?>(null) }
    var calculationRule by rememberSaveable { mutableStateOf<String?>(null) }
    var matchCondition by rememberSaveable { mutableStateOf<String?>(null) }
    var matchValue by rememberSaveable { mutableStateOf("") }
    var matchOption by rememberSaveable { mutableStateOf<String?>(null) }
    var savedSourceLogFrequency by rememberSaveable { mutableStateOf(false) }
    var savedSourceField by rememberSaveable { mutableStateOf<String?>(null) }
    var savedCalculationRule by rememberSaveable { mutableStateOf<String?>(null) }
    var savedMatchCondition by rememberSaveable { mutableStateOf<String?>(null) }
    var savedMatchValue by rememberSaveable { mutableStateOf("") }
    var savedMatchOption by rememberSaveable { mutableStateOf<String?>(null) }

    // Which swatch's color picker is open, and the Save Colors as Preset dialog.
    var pickerRow by remember { mutableStateOf<ColorRowState?>(null) }
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var presetNameInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(initial) {
        val i = initial
        if (!seeded && i != null) {
            typeToken = i.type?.token
            label = i.label
            description = i.description
            sourceLogFrequency = i.config.sourceLogFrequency
            sourceField = i.config.sourceField
            calculationRule = i.config.calculationRule
            matchCondition = i.config.matchCondition
            matchValue = i.config.matchValue
            matchOption = i.config.matchOption
            colorCount = i.config.colorCount
            colorPreset = i.config.colorPreset
            colorRows.clear()
            colorRows.addAll(i.config.colorRows.map { ColorRowState(it.colorHex, it.minValue, it.maxValue) })
            savedTypeToken = typeToken
            savedLabel = label
            savedDescription = description
            savedSourceLogFrequency = sourceLogFrequency
            savedSourceField = sourceField
            savedCalculationRule = calculationRule
            savedMatchCondition = matchCondition
            savedMatchValue = matchValue
            savedMatchOption = matchOption
            savedColorCount = colorCount
            savedColorPreset = colorPreset
            savedColorRowsJson = encodeRows(colorRows.map { it.toRow() })
            seeded = true
        }
    }

    val type = typeToken?.let { CalendarType.fromToken(it) }
    // All three types map their daily result to the same range-based colors.
    val isRangeType = type == CalendarType.HEAT_MAP || type == CalendarType.MIN_MAX ||
        type == CalendarType.YES_NO
    val canSave = type != null && label.isNotBlank()
    val colorRowsJson = encodeRows(colorRows.map { it.toRow() })
    val dirty = seeded && (
        typeToken != savedTypeToken || label != savedLabel || description != savedDescription ||
            sourceLogFrequency != savedSourceLogFrequency || sourceField != savedSourceField ||
            calculationRule != savedCalculationRule || matchCondition != savedMatchCondition ||
            matchValue != savedMatchValue || matchOption != savedMatchOption ||
            colorCount != savedColorCount || colorPreset != savedColorPreset ||
            colorRowsJson != savedColorRowsJson
        )

    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    fun currentConfig(): CalendarConfig = CalendarConfig(
        sourceLogFrequency = sourceLogFrequency,
        sourceField = sourceField,
        calculationRule = calculationRule,
        matchCondition = matchCondition,
        matchValue = matchValue,
        matchOption = matchOption,
        colorCount = colorCount,
        colorPreset = colorPreset,
        colorRows = colorRows.map { it.toRow() },
    )

    fun markSaved() {
        savedTypeToken = typeToken
        savedLabel = label
        savedDescription = description
        savedSourceLogFrequency = sourceLogFrequency
        savedSourceField = sourceField
        savedCalculationRule = calculationRule
        savedMatchCondition = matchCondition
        savedMatchValue = matchValue
        savedMatchOption = matchOption
        savedColorCount = colorCount
        savedColorPreset = colorPreset
        savedColorRowsJson = encodeRows(colorRows.map { it.toRow() })
    }

    // Resolve exactly [count] colors for a preset (built-in or a saved custom one).
    fun resolvePresetColors(presetName: String, count: Int): List<String> {
        val base = if (presetName in ColorPresets.builtInNames) {
            ColorPresets.colorsFor(presetName, count)
        } else {
            val custom = customPresets.firstOrNull { it.name == presetName }
            if (custom != null) {
                ColorPresets.pickEvenly(ColorPresetCodec.decode(custom.colorsJson), count)
            } else {
                ColorPresets.colorsFor(ColorPresets.GRADIATED, count)
            }
        }
        // Guarantee one color per row even if a custom preset stored fewer.
        return (0 until count).map { base.getOrElse(it) { base.lastOrNull() ?: "#000000" } }
    }

    // Choosing a count (re)builds the rows from the current preset with blank ranges.
    fun setColorCount(count: Int) {
        colorCount = count
        colorRows.clear()
        colorRows.addAll(resolvePresetColors(colorPreset, count).map { ColorRowState(it) })
    }

    // Changing the preset recolors the existing rows, keeping the typed ranges.
    fun setColorPreset(preset: String) {
        colorPreset = preset
        val count = colorCount ?: return
        val colors = resolvePresetColors(preset, count)
        colorRows.forEachIndexed { index, row ->
            colors.getOrNull(index)?.let { row.colorHex = it }
        }
    }

    // The "Map Heat Map to" options: the form's applicable fields, then Log Frequency.
    val sourceOptions: List<SourceOption> =
        formFields.filter { it.type.heatMapApplicable() }.map { SourceOption.Field(it) } +
            SourceOption.LogFrequency
    val selectedSource: SourceOption? = when {
        sourceLogFrequency -> SourceOption.LogFrequency
        sourceField != null -> formFields.firstOrNull { it.label == sourceField }?.let { SourceOption.Field(it) }
        else -> null
    }
    val availableRules: List<String> = when (val s = selectedSource) {
        null -> emptyList()
        SourceOption.LogFrequency -> CalendarCalcRules.forLogFrequency()
        is SourceOption.Field -> CalendarCalcRules.forField(s.field.type, s.field.allowUnknown)
    }

    fun selectSource(option: SourceOption) {
        when (option) {
            SourceOption.LogFrequency -> { sourceLogFrequency = true; sourceField = null }
            is SourceOption.Field -> { sourceLogFrequency = false; sourceField = option.field.label }
        }
        // Drop a calculation rule that the new source doesn't offer.
        val rules = when (option) {
            SourceOption.LogFrequency -> CalendarCalcRules.forLogFrequency()
            is SourceOption.Field -> CalendarCalcRules.forField(option.field.type, option.field.allowUnknown)
        }
        if (calculationRule !in rules) {
            calculationRule = null
            matchCondition = null
            matchValue = ""
            matchOption = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Calendar") },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CalendarTypeDropdown(
                selected = type,
                onSelected = { typeToken = it.token },
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Calendar Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Optional: Describes what is tracked. Shows at top of calendar.") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )

            if (type == CalendarType.HEAT_MAP) {
                LabeledDropdown(
                    label = "Map Heat Map to",
                    options = sourceOptions,
                    selected = selectedSource,
                    optionLabel = { it.displayName() },
                    onSelected = { selectSource(it) },
                )

                if (selectedSource != null) {
                    LabeledDropdown(
                        label = "Calculation Rule",
                        options = availableRules,
                        selected = calculationRule?.takeIf { it in availableRules },
                        optionLabel = { calcRuleDisplayName(it) },
                        onSelected = { rule ->
                            calculationRule = rule
                            if (!CalendarCalcRules.requiresCondition(rule)) {
                                matchCondition = null
                                matchValue = ""
                                matchOption = null
                            }
                        },
                    )

                    if (CalendarCalcRules.requiresCondition(calculationRule)) {
                        val sourceFieldDef = (selectedSource as? SourceOption.Field)?.field
                        if (sourceFieldDef != null &&
                            (sourceFieldDef.type == FieldType.DROPDOWN || sourceFieldDef.type == FieldType.MULTIPLE)
                        ) {
                            // Choice field: count occurrences of one chosen option.
                            LabeledDropdown(
                                label = "Value",
                                options = sourceFieldDef.options,
                                selected = matchOption?.takeIf { it in sourceFieldDef.options },
                                optionLabel = { it },
                                onSelected = { matchOption = it },
                            )
                        } else {
                            // Number / Scale: a numeric condition and value.
                            LabeledDropdown(
                                label = "Condition",
                                options = CalendarConditions.all,
                                selected = matchCondition,
                                optionLabel = { conditionDisplayName(it) },
                                onSelected = { matchCondition = it },
                            )
                            OutlinedTextField(
                                value = matchValue,
                                onValueChange = { matchValue = it },
                                label = { Text("Value") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (type == CalendarType.YES_NO) {
                // One calendar tracks one Yes/No response. Both Yes and No means two
                // separate calendars, so there is a single condition here.
                val yesNoFields = formFields.filter { it.type == FieldType.YESNO }
                val trackedField = yesNoFields.firstOrNull { it.label == sourceField }
                LabeledDropdown(
                    label = "Item Tracked",
                    options = yesNoFields,
                    selected = trackedField,
                    optionLabel = { it.label },
                    onSelected = { field ->
                        sourceLogFrequency = false
                        sourceField = field.label
                        val rules = CalendarCalcRules.forField(FieldType.YESNO, field.allowUnknown)
                        if (calculationRule !in rules) calculationRule = null
                    },
                )
                if (trackedField != null) {
                    val rules = CalendarCalcRules.forField(FieldType.YESNO, trackedField.allowUnknown)
                    LabeledDropdown(
                        label = "Calculation Rule",
                        options = rules,
                        selected = calculationRule?.takeIf { it in rules },
                        optionLabel = { calcRuleDisplayName(it) },
                        onSelected = { calculationRule = it },
                    )
                }
            }

            if (isRangeType) {
                ColorCountSelector(selected = colorCount, onSelected = { setColorCount(it) })

                if (colorCount != null) {
                    AppDropdownRow(
                        label = "Color Preset",
                        options = ColorPresets.builtInNames + customPresets.map { it.name },
                        selected = colorPreset,
                        onSelected = { setColorPreset(it) },
                        optionLabel = { it },
                    )
                    ColorRowsHeader()
                    colorRows.forEach { row ->
                        ColorRowEditor(row, onSwatchClick = { pickerRow = row })
                    }
                    AppButton(
                        onClick = { presetNameInput = ""; showSavePreset = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Colors as Preset")
                    }
                }
            }

            AppButton(
                onClick = {
                    val chosen = type ?: return@AppButton
                    viewModel.save(chosen, label, description, currentConfig()) {
                        markSaved()
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Calendar")
            }

            AppButton(
                onClick = {
                    val chosen = type ?: return@AppButton
                    viewModel.save(chosen, label, description, currentConfig()) {
                        // Keep the just-saved calendar; start a fresh, blank one on
                        // this same screen. The next Save inserts a new calendar.
                        viewModel.prepareNew()
                        typeToken = null
                        label = ""
                        description = ""
                        sourceLogFrequency = false
                        sourceField = null
                        calculationRule = null
                        matchCondition = null
                        matchValue = ""
                        matchOption = null
                        colorCount = null
                        colorPreset = ColorPresets.GRADIATED
                        colorRows.clear()
                        markSaved()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Another Calendar")
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }

    val editingRow = pickerRow
    if (editingRow != null) {
        ColorPickerDialog(
            initialHex = editingRow.colorHex,
            onConfirm = { hex -> editingRow.colorHex = hex; pickerRow = null },
            onDismiss = { pickerRow = null },
        )
    }

    if (showSavePreset) {
        SavePresetDialog(
            name = presetNameInput,
            onNameChange = { presetNameInput = it },
            onConfirm = {
                val chosen = presetNameInput.trim()
                if (chosen.isNotEmpty()) {
                    // Saves the preset app-wide (it appears in the Color Preset
                    // dropdown). It does not change this calendar's selected preset.
                    viewModel.saveColorsAsPreset(chosen, colorRows.map { it.colorHex })
                    showSavePreset = false
                    presetNameInput = ""
                }
            },
            onDismiss = { showSavePreset = false; presetNameInput = "" },
        )
    }
}

/** The naming dialog for "Save Colors as Preset": a required name, Cancel, Okay. */
@Composable
private fun SavePresetDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Preset Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.trim().isNotEmpty()) { Text("Okay") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The "Choose Calendar Type" dropdown. Its floating label is the prompt; the box
 *  is empty until a type is picked. Option names are the exact owner-facing labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTypeDropdown(
    selected: CalendarType?,
    onSelected: (CalendarType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Choose Calendar Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CalendarType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A labeled dropdown whose floating [label] is the prompt; the box is empty until
 * an option is picked. Used for Map Heat Map to, Calculation Rule, and Condition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A choice in "Map Heat Map to": one of the form's fields, or Log Frequency. */
private sealed interface SourceOption {
    data class Field(val field: FieldDef) : SourceOption
    data object LogFrequency : SourceOption
}

private fun SourceOption.displayName(): String = when (this) {
    is SourceOption.Field -> field.label
    SourceOption.LogFrequency -> "Log Frequency"
}

/** The exact owner-facing name for a Calculation Rule token. */
private fun calcRuleDisplayName(token: String): String = when (token) {
    CalendarCalcRules.COUNT_LOGS -> "Count Logs"
    CalendarCalcRules.HIGHEST_VALUE -> "Highest Value"
    CalendarCalcRules.LOWEST_VALUE -> "Lowest Value"
    CalendarCalcRules.AVERAGE -> "Average"
    CalendarCalcRules.TOTAL -> "Total"
    CalendarCalcRules.COUNT_ENTRIES -> "Count Entries"
    CalendarCalcRules.COUNT_MATCHING -> "Count Matching"
    CalendarCalcRules.COUNT_YES -> "Count Yes"
    CalendarCalcRules.COUNT_NO -> "Count No"
    CalendarCalcRules.COUNT_UNKNOWN -> "Count Unknown"
    else -> token
}

/** The exact owner-facing name for a Count Matching condition token. */
private fun conditionDisplayName(token: String): String = when (token) {
    CalendarConditions.GREATER_THAN -> "Greater Than"
    CalendarConditions.GREATER_OR_EQUAL -> "Greater Than or Equal To"
    CalendarConditions.EQUAL_TO -> "Equal To"
    CalendarConditions.LESS_OR_EQUAL -> "Less Than or Equal To"
    CalendarConditions.LESS_THAN -> "Less Than"
    else -> token
}

/** Picks how many color values to use: 3, 5, or 10. */
@Composable
private fun ColorCountSelector(selected: Int?, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorPresets.counts.forEach { count ->
            Row(
                modifier = Modifier.weight(1f).clickable { onSelected(count) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == count, onClick = { onSelected(count) })
                Text(count.toString())
            }
        }
    }
}

/** The centered Color / Min Value / Max Value headers above the range rows. */
@Composable
private fun ColorRowsHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Color",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SWATCH_CELL_WIDTH),
        )
        Text(
            "Min Value",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Max Value",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One range row: the color swatch (tap to pick its color) and its Min/Max fields. */
@Composable
private fun ColorRowEditor(row: ColorRowState, onSwatchClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(SWATCH_CELL_WIDTH), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(hexToColor(row.colorHex))
                    .clickable(onClick = onSwatchClick),
            )
        }
        OutlinedTextField(
            value = row.minValue,
            onValueChange = { row.minValue = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = row.maxValue,
            onValueChange = { row.maxValue = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

private val SWATCH_CELL_WIDTH = 56.dp

/** The exact owner-facing type names shown in "Choose Calendar Type". */
private fun CalendarType.displayName(): String = when (this) {
    CalendarType.HEAT_MAP -> "Heat Map"
    CalendarType.YES_NO -> "Yes/No"
    CalendarType.MIN_MAX -> "Min/Max Value"
}

/** Compose color from a "#RRGGBB" hex string; a bad value falls back to gray. */
private fun hexToColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

/** Compose-observable editing state for one color range row. */
private class ColorRowState(
    colorHex: String,
    minValue: String = "",
    maxValue: String = "",
) {
    var colorHex by mutableStateOf(colorHex)
    var minValue by mutableStateOf(minValue)
    var maxValue by mutableStateOf(maxValue)

    fun toRow(): CalendarColorRow = CalendarColorRow(colorHex, minValue, maxValue)
}

private fun encodeRows(rows: List<CalendarColorRow>): String = Json.encodeToString(rows)

/** Saves the color rows as JSON so unsaved range edits survive process death. */
private val colorRowsSaver = Saver<SnapshotStateList<ColorRowState>, String>(
    save = { list -> encodeRows(list.map { it.toRow() }) },
    restore = { text ->
        mutableStateListOf<ColorRowState>().apply {
            addAll(
                Json.decodeFromString<List<CalendarColorRow>>(text)
                    .map { ColorRowState(it.colorHex, it.minValue, it.maxValue) },
            )
        }
    },
)
