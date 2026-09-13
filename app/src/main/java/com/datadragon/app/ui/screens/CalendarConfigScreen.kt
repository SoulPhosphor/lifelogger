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
import com.datadragon.app.data.CalendarType
import com.datadragon.app.data.ColorPresets
import com.datadragon.app.ui.CalendarConfigViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.components.AppDropdownRow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The single, vertically scrollable Edit Calendar screen (owner direction: one
 * screen, never a wizard). It opens for a new calendar from the Form Editor's
 * "Edit Calendar" button, or for an existing one from the calendars list at the
 * bottom of Edit Form, with that calendar's saved values loaded.
 *
 * Built so far, top to bottom: Choose Calendar Type, Calendar Label, Description,
 * then — for the range types (Heat Map, Min/Max Value) — the color configuration
 * (how many colors, the Color Preset, and the Color / Min Value / Max Value rows).
 * Save Calendar and Add Another Calendar close the screen. The data-source and
 * calculation-rule controls, the Yes/No option table, the color picker, and
 * Save Colors as Preset are added by later phases, on this same screen.
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

    LaunchedEffect(initial) {
        val i = initial
        if (!seeded && i != null) {
            typeToken = i.type?.token
            label = i.label
            description = i.description
            colorCount = i.config.colorCount
            colorPreset = i.config.colorPreset
            colorRows.clear()
            colorRows.addAll(i.config.colorRows.map { ColorRowState(it.colorHex, it.minValue, it.maxValue) })
            savedTypeToken = typeToken
            savedLabel = label
            savedDescription = description
            savedColorCount = colorCount
            savedColorPreset = colorPreset
            savedColorRowsJson = encodeRows(colorRows.map { it.toRow() })
            seeded = true
        }
    }

    val type = typeToken?.let { CalendarType.fromToken(it) }
    val isRangeType = type == CalendarType.HEAT_MAP || type == CalendarType.MIN_MAX
    val canSave = type != null && label.isNotBlank()
    val colorRowsJson = encodeRows(colorRows.map { it.toRow() })
    val dirty = seeded && (
        typeToken != savedTypeToken || label != savedLabel || description != savedDescription ||
            colorCount != savedColorCount || colorPreset != savedColorPreset ||
            colorRowsJson != savedColorRowsJson
        )

    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun attemptBack() { if (dirty) showDiscard = true else onBack() }
    BackHandler { attemptBack() }

    fun currentConfig(): CalendarConfig = CalendarConfig(
        colorCount = colorCount,
        colorPreset = colorPreset,
        colorRows = colorRows.map { it.toRow() },
    )

    fun markSaved() {
        savedTypeToken = typeToken
        savedLabel = label
        savedDescription = description
        savedColorCount = colorCount
        savedColorPreset = colorPreset
        savedColorRowsJson = encodeRows(colorRows.map { it.toRow() })
    }

    // Choosing a count (re)builds the rows from the current preset with blank ranges.
    fun setColorCount(count: Int) {
        colorCount = count
        colorRows.clear()
        colorRows.addAll(ColorPresets.colorsFor(colorPreset, count).map { ColorRowState(it) })
    }

    // Changing the preset recolors the existing rows, keeping the typed ranges.
    fun setColorPreset(preset: String) {
        colorPreset = preset
        val count = colorCount ?: return
        val colors = ColorPresets.colorsFor(preset, count)
        colorRows.forEachIndexed { index, row ->
            colors.getOrNull(index)?.let { row.colorHex = it }
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

            if (isRangeType) {
                ColorCountSelector(selected = colorCount, onSelected = { setColorCount(it) })

                if (colorCount != null) {
                    AppDropdownRow(
                        label = "Color Preset",
                        options = ColorPresets.builtInNames,
                        selected = colorPreset,
                        onSelected = { setColorPreset(it) },
                        optionLabel = { it },
                    )
                    ColorRowsHeader()
                    colorRows.forEach { row -> ColorRowEditor(row) }
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

/** One range row: the color swatch and its Min/Max Value fields. */
@Composable
private fun ColorRowEditor(row: ColorRowState) {
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
                    .background(hexToColor(row.colorHex)),
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
