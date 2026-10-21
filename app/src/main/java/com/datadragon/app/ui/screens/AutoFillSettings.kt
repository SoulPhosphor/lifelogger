package com.datadragon.app.ui.screens

import androidx.compose.runtime.Composable
import com.datadragon.app.data.AutoFill
import com.datadragon.app.data.FieldDef
import com.datadragon.app.ui.components.AppDropdown

/**
 * A Date, Time or Date & Time field's Auto Fill setting, shared by both form
 * builders: the checkbox and, while it is checked, the drop-down choosing the
 * source — the entry's timestamp first, then every field in [sourceFields].
 * A [source] that no longer qualifies shows (and saves) as the timestamp.
 */
@Composable
internal fun AutoFillSettings(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    source: String,
    onSourceChange: (String) -> Unit,
    sourceFields: List<FieldDef>,
) {
    CheckboxSettingRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = "Auto Fill",
        subtext = "Future fields will continue to auto-populate from the selected field automatically while checked.",
    )
    if (checked) {
        val options = listOf(AutoFill.TIMESTAMP) + sourceFields.map { it.id }
        AppDropdown(
            options = options,
            selected = validAutoFillSource(source, sourceFields),
            onSelected = onSourceChange,
            optionLabel = { option ->
                if (option == AutoFill.TIMESTAMP) {
                    "Auto Fill from Timestamp"
                } else {
                    "Auto Fill from ${sourceFields.firstOrNull { it.id == option }?.label.orEmpty()}"
                }
            },
            accessibleLabel = "Auto Fill",
        )
    }
}

/** [source] when it is still offered, otherwise the timestamp. */
internal fun validAutoFillSource(source: String, sourceFields: List<FieldDef>): String =
    if (source == AutoFill.TIMESTAMP || sourceFields.any { it.id == source }) source else AutoFill.TIMESTAMP

/** Each Auto Fill field's source made valid for this field list, as the drop-down shows it. */
internal fun List<FieldDef>.withValidAutoFill(): List<FieldDef> = map { field ->
    val source = field.autoFillSource ?: return@map field
    field.copy(autoFillSource = validAutoFillSource(source, AutoFill.sourceFields(field, this)))
}
