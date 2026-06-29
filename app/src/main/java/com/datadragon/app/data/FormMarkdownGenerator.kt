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
                }
                FieldType.DROPDOWN, FieldType.MULTIPLE -> {
                    sb.append("options:\n")
                    field.options.forEach { sb.append("- ").append(it).append('\n') }
                }
                FieldType.DATETIME -> if (field.defaultNow) sb.append("default: now\n")
                else -> Unit
            }
            if (field.required) sb.append("required\n")
        }
        return sb.toString().trimEnd() + "\n"
    }
}
