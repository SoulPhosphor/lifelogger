package com.datadragon.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Parses Form Markdown into a list of [FieldDef]s, following the rules in
 * docs/FORM_MARKDOWN_SPEC.md §4. It never throws on bad input: unrecognized or
 * invalid content is collected into [ParseResult.skipped] / [ParseResult.issues]
 * so the preview can show the user exactly what happened.
 */
object FormMarkdownParser {

    private val json = Json { prettyPrint = false }

    /** Encode a field list to the same schema JSON a parsed result would produce. */
    fun encodeFields(fields: List<FieldDef>): String = json.encodeToString(fields)

    data class ParseResult(
        val name: String?,
        val fields: List<FieldDef>,
        /** Lines that were not recognized and had no effect. */
        val skipped: List<String>,
        /** Recognized-but-problematic content (missing type, duplicate label, …). */
        val issues: List<String>,
    ) {
        fun toSchemaJson(): String = json.encodeToString(fields)
    }

    /** Mutable accumulator for the field currently being assembled. */
    private class Builder(val label: String, val lineNo: Int) {
        var type: FieldType? = null
        var required = false
        var lines: Int? = null
        var digits: Int? = null
        var from: Int? = null
        var to: Int? = null
        var defaultNow = false
        var allowUnknown = false
        var makeDropdown = false
        var choiceStyle = ChoiceStyle.DROPDOWN
        /** Legacy `top_answer_preselected: true`: pre-select the first option. */
        var topAnswerPreselected = false
        /** `preselected_option: <name>`: the Single Choice option pre-selected on new entries. */
        var preselectedOption: String? = null
        var leftDigits: Int? = null
        var rightDigits: Int? = null
        var unit = ""
        var itemLabel = ""
        var firstLabel = ""
        var secondLabel = ""
        var totalLabel = ""
        var wholeNumbersOnly = true
        var clickerCounting = false
        var clickerButtonLabel = ""
        var clickerStartingNumber = 0
        var clickerIncrement = 1
        var clickerDirection = ClickerIncrementDirection.ADD
        val options = mutableListOf<String>()
    }

    /**
     * Parse [input]. Every field and option in the result receives a brand-new
     * permanent id from [newId]; the ids are kept when the form is saved.
     */
    fun parse(input: String, newId: () -> String = StableUuid::createNew): ParseResult {
        val fields = mutableListOf<FieldDef>()
        val skipped = mutableListOf<String>()
        val issues = mutableListOf<String>()
        val seenLabels = mutableSetOf<String>()

        var name: String? = null
        var builder: Builder? = null
        var inOptions = false

        fun flush() {
            val b = builder ?: return
            builder = null
            inOptions = false
            buildField(b, seenLabels, fields, issues, newId)
        }

        input.lines().forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            val line = raw.trim()

            when {
                line.isEmpty() -> {
                    inOptions = false
                }

                // Field header (check ## before #).
                line.startsWith("##") -> {
                    flush()
                    val label = line.removePrefix("##").trim()
                    if (label.isEmpty()) {
                        skipped.add("Line $lineNo: field with no name (\"$line\")")
                    } else {
                        builder = Builder(label, lineNo)
                    }
                }

                // Log name: the first single-# line; later ones are skipped.
                line.startsWith("#") -> {
                    val value = line.removePrefix("#").trim()
                    if (name == null) name = value
                    else skipped.add("Line $lineNo: extra heading ignored (\"$line\")")
                }

                // Option list items.
                line.startsWith("-") -> {
                    val b = builder
                    val option = line.removePrefix("-").trim()
                    if (b != null && inOptions && option.isNotEmpty()) {
                        b.options.add(option)
                    } else {
                        skipped.add("Line $lineNo: list item with no options field (\"$line\")")
                    }
                }

                // Bare `required`.
                line.equals("required", ignoreCase = true) -> {
                    val b = builder
                    if (b != null) b.required = true
                    else skipped.add("Line $lineNo: \"required\" outside a field")
                }

                // key: value lines.
                line.contains(":") -> {
                    val key = line.substringBefore(":").trim().lowercase()
                    val value = line.substringAfter(":").trim()
                    val b = builder
                    if (b == null) {
                        skipped.add("Line $lineNo: \"$line\" before any field")
                        return@forEachIndexed
                    }
                    when (key) {
                        "type" -> {
                            val t = FieldType.fromToken(value)
                            if (t == null) {
                                issues.add("Field \"${b.label}\": unknown type \"$value\".")
                            } else {
                                b.type = t
                            }
                        }
                        "lines" -> b.lines = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": lines \"$value\" is not a number."); null }
                        "digits" -> b.digits = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": digits \"$value\" is not a number."); null }
                        "from" -> b.from = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": from \"$value\" is not a number."); null }
                        "to" -> b.to = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": to \"$value\" is not a number."); null }
                        "required" -> b.required = value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                        "default" -> b.defaultNow = value.equals("now", ignoreCase = true)
                        "allow_unknown" -> b.allowUnknown =
                            value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                        "make_dropdown" -> b.makeDropdown =
                            value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                        "choice_style" -> when (value.lowercase()) {
                            "dropdown" -> b.choiceStyle = ChoiceStyle.DROPDOWN
                            "displayed" -> b.choiceStyle = ChoiceStyle.DISPLAYED
                            else -> issues.add("Field \"${b.label}\": choice_style \"$value\" is not dropdown or displayed.")
                        }
                        // Legacy syntax, still accepted when pasting older Markdown.
                        "top_answer_preselected" -> b.topAnswerPreselected =
                            value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                        "preselected_option" -> b.preselectedOption = value.ifEmpty { null }
                        "left_digits" -> b.leftDigits = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": left_digits \"$value\" is not a number."); null }
                        "right_digits" -> b.rightDigits = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": right_digits \"$value\" is not a number."); null }
                        "unit" -> b.unit = value
                        "item_label" -> b.itemLabel = value
                        "first_label" -> b.firstLabel = value
                        "second_label" -> b.secondLabel = value
                        "total_label" -> b.totalLabel = value
                        "whole_numbers" -> b.wholeNumbersOnly =
                            !(value.equals("false", ignoreCase = true) || value.equals("no", ignoreCase = true))
                        "clicker_counting" -> b.clickerCounting =
                            value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                        "button_label" -> b.clickerButtonLabel = value
                        "starting_number" -> b.clickerStartingNumber = value.toIntOrNull()
                            ?: run { issues.add("Field \"${b.label}\": starting_number \"$value\" is not a number."); 0 }
                        "increment" -> b.clickerIncrement = value.toIntOrNull()?.takeIf { it > 0 }
                            ?: run { issues.add("Field \"${b.label}\": increment \"$value\" is not a number above 0."); 1 }
                        "direction" -> when (value.lowercase()) {
                            "add" -> b.clickerDirection = ClickerIncrementDirection.ADD
                            "subtract" -> b.clickerDirection = ClickerIncrementDirection.SUBTRACT
                            else -> issues.add("Field \"${b.label}\": direction \"$value\" is not add or subtract.")
                        }
                        "options" -> {
                            inOptions = true
                            if (value.isNotEmpty()) {
                                skipped.add("Line $lineNo: text after \"options:\" ignored (\"$value\")")
                            }
                        }
                        else -> skipped.add("Line $lineNo: unrecognized \"$line\"")
                    }
                }

                else -> skipped.add("Line $lineNo: unrecognized \"$line\"")
            }
        }
        flush()

        return ParseResult(
            name = name?.ifEmpty { null },
            fields = fields,
            skipped = skipped,
            issues = issues,
        )
    }

    /** Validate one accumulated field and, if valid, add it to [fields]. */
    private fun buildField(
        b: Builder,
        seenLabels: MutableSet<String>,
        fields: MutableList<FieldDef>,
        issues: MutableList<String>,
        newId: () -> String,
    ) {
        val type = b.type
        if (type == null) {
            issues.add("Field \"${b.label}\" has no type and was skipped.")
            return
        }
        if (!seenLabels.add(b.label.lowercase())) {
            issues.add("Duplicate field name \"${b.label}\" was skipped.")
            return
        }

        // Type-specific requirements.
        when (type) {
            FieldType.SCALE -> {
                if (b.from == null || b.to == null) {
                    issues.add("Field \"${b.label}\" (scale) needs both from and to; skipped.")
                    return
                }
                if (b.to!! < b.from!!) {
                    issues.add("Field \"${b.label}\" (scale): to is less than from; skipped.")
                    return
                }
            }
            FieldType.RATIO -> {
                val digitRange = MIN_RATIO_DIGITS..MAX_RATIO_DIGITS
                if (b.leftDigits?.let { it in digitRange } != true || b.rightDigits?.let { it in digitRange } != true) {
                    issues.add("Field \"${b.label}\" (ratio) needs left_digits and right_digits from 1 to 10; skipped.")
                    return
                }
            }
            FieldType.DROPDOWN, FieldType.MULTIPLE, FieldType.COMPARISON -> {
                if (b.options.isEmpty()) {
                    issues.add("Field \"${b.label}\" (${type.token}) has no options; skipped.")
                    return
                }
            }
            else -> Unit
        }

        fields.add(
            FieldDef(
                id = newId(),
                label = b.label,
                type = type,
                // A Comparison field is never required.
                required = b.required && type != FieldType.COMPARISON,
                lines = b.lines.takeIf { type == FieldType.MULTILINE },
                digits = b.digits.takeIf { type == FieldType.NUMBER || type == FieldType.COMPARISON },
                from = b.from.takeIf { type == FieldType.SCALE },
                to = b.to.takeIf { type == FieldType.SCALE },
                choices = if (type.hasOptionList) buildChoices(b, type, issues, newId) else emptyList(),
                defaultNow = b.defaultNow && type == FieldType.DATETIME,
                allowUnknown = b.allowUnknown && type == FieldType.YESNO,
                makeDropdown = b.makeDropdown && type == FieldType.SCALE,
                choiceStyle = if (type == FieldType.DROPDOWN) b.choiceStyle else ChoiceStyle.DROPDOWN,
                leftDigits = b.leftDigits.takeIf { type == FieldType.RATIO },
                rightDigits = b.rightDigits.takeIf { type == FieldType.RATIO },
                unit = if (type == FieldType.RATIO) b.unit else "",
                itemLabel = if (type == FieldType.COMPARISON) b.itemLabel else "",
                firstLabel = if (type == FieldType.COMPARISON) b.firstLabel else "",
                secondLabel = if (type == FieldType.COMPARISON) b.secondLabel else "",
                totalLabel = if (type == FieldType.COMPARISON) b.totalLabel else "",
                wholeNumbersOnly = type != FieldType.COMPARISON || b.wholeNumbersOnly,
                clickerCounting = type == FieldType.COMPARISON && b.clickerCounting,
                clickerButtonLabel = if (type == FieldType.COMPARISON) b.clickerButtonLabel else "",
                clickerStartingNumber = if (type == FieldType.COMPARISON) b.clickerStartingNumber else 0,
                clickerIncrement = if (type == FieldType.COMPARISON) b.clickerIncrement else 1,
                clickerDirection = if (type == FieldType.COMPARISON) b.clickerDirection else ClickerIncrementDirection.ADD,
            )
        )
    }

    /**
     * Turn the written option lines into [ChoiceOption]s with new ids.
     *
     * - Multiple Choice: a leading `*` marks the option as pre-selected and is
     *   not part of its name (a line that is only `*` stays a literal `*`).
     * - Single Choice: `preselected_option:` names the pre-selected option and
     *   must match one of the options; the legacy `top_answer_preselected: true`
     *   pre-selects the first option when no `preselected_option:` is given.
     * - A repeated option (same name, ignoring capitalization) is skipped.
     */
    private fun buildChoices(
        b: Builder,
        type: FieldType,
        issues: MutableList<String>,
        newId: () -> String,
    ): List<ChoiceOption> {
        val choices = mutableListOf<ChoiceOption>()
        b.options.forEach { written ->
            val marked = type == FieldType.MULTIPLE && written.startsWith("*") &&
                written.removePrefix("*").isNotBlank()
            val label = if (marked) written.removePrefix("*").trim() else written
            val existing = choices.matchingLabel(label)
            if (existing != null) {
                if (marked) choices[choices.indexOf(existing)] = existing.copy(preselected = true)
                issues.add("Field \"${b.label}\": duplicate option \"$label\" was skipped.")
            } else {
                choices += ChoiceOption(id = newId(), label = label, preselected = marked)
            }
        }
        if (type == FieldType.DROPDOWN) {
            val named = b.preselectedOption
            if (named != null) {
                val match = choices.matchingLabel(named)
                if (match == null) {
                    issues.add("Field \"${b.label}\": preselected_option \"$named\" is not one of its options.")
                } else {
                    return choices.map { it.copy(preselected = it.id == match.id) }
                }
            } else if (b.topAnswerPreselected && choices.isNotEmpty()) {
                return choices.mapIndexed { index, choice -> choice.copy(preselected = index == 0) }
            }
        }
        return choices
    }
}
