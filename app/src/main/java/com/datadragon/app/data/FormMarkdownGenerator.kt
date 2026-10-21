package com.datadragon.app.data

/**
 * Renders a list of [FieldDef]s back into Form Markdown — the inverse of
 * [FormMarkdownParser]. The visual field builder uses this so a template built
 * with taps still stores readable Form Markdown, and so switching from the Build
 * tab to the Paste tab shows the equivalent text.
 */
object FormMarkdownGenerator {

    fun generate(name: String, fields: List<FieldDef>): String {
        val sb = StringBuilder()
        val trimmedName = name.trim()
        if (trimmedName.isNotEmpty()) sb.append("# ").append(trimmedName).append("\n\n")

        fields.forEachIndexed { index, field ->
            if (index > 0) sb.append('\n')
            sb.append("## ").append(field.label).append('\n')
            sb.append("type: ").append(field.type.token).append('\n')
            when (field.type) {
                FieldType.MULTILINE -> field.lines?.let { sb.append("lines: ").append(it).append('\n') }
                FieldType.NUMBER -> field.digits?.let { sb.append("digits: ").append(it).append('\n') }
                FieldType.SCALE -> {
                    field.from?.let { sb.append("from: ").append(it).append('\n') }
                    field.to?.let { sb.append("to: ").append(it).append('\n') }
                    if (field.makeDropdown) sb.append("make_dropdown: true\n")
                }
                FieldType.DROPDOWN, FieldType.MULTIPLE -> {
                    // Only active options are written; retired ones stay in the
                    // saved form but aren't part of its Markdown.
                    sb.append("options:\n")
                    field.activeChoices.forEach { choice ->
                        sb.append("- ")
                        if (field.type == FieldType.MULTIPLE && choice.preselected) sb.append('*')
                        sb.append(choice.label).append('\n')
                    }
                    if (field.type == FieldType.DROPDOWN) {
                        if (field.choiceStyle == ChoiceStyle.DISPLAYED) sb.append("choice_style: displayed\n")
                        field.activeChoices.firstOrNull { it.preselected }?.let {
                            sb.append("preselected_option: ").append(it.label).append('\n')
                        }
                    }
                }
                FieldType.COMPARISON -> {
                    sb.append("options:\n")
                    field.activeChoices.forEach { choice -> sb.append("- ").append(choice.label).append('\n') }
                    field.digits?.let { sb.append("digits: ").append(it).append('\n') }
                    if (!field.wholeNumbersOnly) sb.append("whole_numbers: false\n")
                    if (field.itemLabel.isNotBlank()) sb.append("item_label: ").append(field.itemLabel.trim()).append('\n')
                    if (field.firstLabel.isNotBlank()) sb.append("first_label: ").append(field.firstLabel.trim()).append('\n')
                    if (field.secondLabel.isNotBlank()) sb.append("second_label: ").append(field.secondLabel.trim()).append('\n')
                    if (field.totalLabel.isNotBlank()) sb.append("total_label: ").append(field.totalLabel.trim()).append('\n')
                    if (field.clickerCounting) sb.append("clicker_counting: true\n")
                    if (field.clickerButtonLabel.isNotBlank()) {
                        sb.append("button_label: ").append(field.clickerButtonLabel.trim()).append('\n')
                    }
                    if (field.clickerStartingNumber != 0) sb.append("starting_number: ").append(field.clickerStartingNumber).append('\n')
                    if (field.clickerIncrement != 1) sb.append("increment: ").append(field.clickerIncrement).append('\n')
                    if (field.clickerDirection == ClickerIncrementDirection.SUBTRACT) sb.append("direction: subtract\n")
                }
                FieldType.RATIO -> {
                    field.leftDigits?.let { sb.append("left_digits: ").append(it).append('\n') }
                    field.rightDigits?.let { sb.append("right_digits: ").append(it).append('\n') }
                    if (field.unit.isNotBlank()) sb.append("unit: ").append(field.unit.trim()).append('\n')
                }
                FieldType.DATETIME -> if (field.defaultNow) sb.append("default: now\n")
                FieldType.YESNO -> if (field.allowUnknown) sb.append("allow_unknown: true\n")
                else -> Unit
            }
            // Auto Fill names its source by label (or `timestamp`).
            when (val source = field.autoFillSource) {
                null -> Unit
                AutoFill.TIMESTAMP -> sb.append("auto_fill: timestamp\n")
                else -> fields.firstOrNull { it.id == source }?.let { sb.append("auto_fill: ").append(it.label).append('\n') }
            }
            if (field.required) sb.append("required\n")
        }
        return sb.toString().trimEnd() + "\n"
    }
}
