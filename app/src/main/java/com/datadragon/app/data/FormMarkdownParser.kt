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
        val options = mutableListOf<String>()
    }

    fun parse(input: String): ParseResult {
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
            buildField(b, seenLabels, fields, issues)
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
            FieldType.DROPDOWN, FieldType.MULTIPLE -> {
                if (b.options.isEmpty()) {
                    issues.add("Field \"${b.label}\" (${type.token}) has no options; skipped.")
                    return
                }
            }
            else -> Unit
        }

        fields.add(
            FieldDef(
                label = b.label,
                type = type,
                required = b.required,
                lines = b.lines.takeIf { type == FieldType.MULTILINE },
                digits = b.digits.takeIf { type == FieldType.NUMBER },
                from = b.from.takeIf { type == FieldType.SCALE },
                to = b.to.takeIf { type == FieldType.SCALE },
                options = if (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE) b.options.toList() else emptyList(),
                defaultNow = b.defaultNow && type == FieldType.DATETIME,
            )
        )
    }
}
