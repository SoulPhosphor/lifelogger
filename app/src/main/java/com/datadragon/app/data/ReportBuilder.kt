package com.datadragon.app.data

/**
 * Builds the human-readable report for one log (docs/FORMATTING_SPEC.md §2).
 *
 * The same vertical, field-by-field layout backs both formats: the Markdown
 * form uses `#`, `##`, and `**bold**`; the plain-text form drops those markers
 * but keeps the spacing. It is not a spreadsheet — CSV is a separate export.
 */
object ReportBuilder {

    /** A built report ready to share: file name plus contents. */
    data class Report(val fileName: String, val mimeType: String, val text: String)

    fun build(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        markdown: Boolean,
    ): Report {
        // The report reads chronologically (oldest first); the date range still
        // runs first entry → last entry.
        val ordered = entries.sortedBy { it.createdAt }
        val sb = StringBuilder()

        val title = "${template.name} Report"
        if (markdown) sb.append("# ").append(title).append('\n') else sb.append(title).append('\n')

        if (ordered.isNotEmpty()) {
            val first = EntryValues.displayEntryDate(ordered.first().createdAt)
            val last = EntryValues.displayEntryDate(ordered.last().createdAt)
            val range = if (first == last) first else "$first – $last"
            sb.append("Date range: ").append(range).append('\n')
        }
        sb.append("Total entries: ").append(ordered.size).append('\n')

        ordered.forEach { entry ->
            appendSeparator(sb, markdown)
            appendEntry(sb, entry, fields, markdown)
        }

        val ext = if (markdown) "md" else "txt"
        val mime = if (markdown) "text/markdown" else "text/plain"
        return Report(
            fileName = "${sanitize(template.name)}_report.$ext",
            mimeType = mime,
            text = sb.toString(),
        )
    }

    private fun appendSeparator(sb: StringBuilder, markdown: Boolean) {
        sb.append('\n')
        sb.append(if (markdown) "---" else "----------")
        sb.append('\n')
    }

    private fun appendEntry(
        sb: StringBuilder,
        entry: LogEntry,
        fields: List<FieldDef>,
        markdown: Boolean,
    ) {
        val values = EntryValues.decode(entry.valuesJson)
        val heading = "Entry — ${EntryValues.displayEntryDateTime(entry.createdAt)}"
        sb.append('\n')
        if (markdown) sb.append("## ").append(heading).append('\n') else sb.append(heading).append('\n')
        sb.append('\n')

        fields.forEach { field ->
            val value = EntryValues.displayValue(field, values) ?: return@forEach
            val label = if (markdown) "**${field.label}:**" else "${field.label}:"
            if (field.type == FieldType.MULTILINE) {
                // Multiline values render as a block on their own lines.
                sb.append(label).append('\n').append(value).append('\n')
            } else {
                sb.append(label).append(' ').append(value).append('\n')
            }
        }

        EntryValues.notes(values)?.let { notes ->
            sb.append('\n')
            sb.append(if (markdown) "**Notes:**" else "Notes:").append('\n')
            sb.append(notes).append('\n')
        }
    }

    /**
     * Export file naming (docs/FORMATTING_SPEC.md §5): lowercase, spaces to
     * underscores, strip punctuation other than underscores.
     */
    private fun sanitize(name: String): String {
        val cleaned = name.trim().lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .trim('_')
        return cleaned.ifEmpty { "log" }
    }
}
