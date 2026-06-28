package com.datadragon.app.data

/**
 * Builds the spreadsheet-compatible CSV export for one log
 * (docs/FORMATTING_SPEC.md §3). One file per log, UTF-8, with a header row.
 *
 * Rules followed here:
 * - RFC 4180 quoting: cells containing a comma, double-quote, or newline are
 *   wrapped in double quotes, and internal double-quotes are doubled.
 * - `multiple` values join with `; ` (semicolon-space) so they don't collide
 *   with the CSV comma.
 * - `scale` exports the raw number only (`4`), not `4 / 5`.
 * - date / time columns export the ISO-8601 string exactly as stored, so
 *   spreadsheets can sort them. Empty optional fields export as empty cells.
 *
 * Column order: `entry_id, created_at, {field 1}…{field n}, notes`. (The free-text
 * Notes column is appended so notes aren't lost in the CSV; everything else
 * matches §3 exactly.)
 */
object CsvBuilder {

    private const val EOL = "\r\n"

    fun build(fields: List<FieldDef>, entries: List<LogEntry>): String {
        val sb = StringBuilder()

        val header = buildList {
            add("entry_id")
            add("created_at")
            fields.forEach { add(it.label) }
            add("notes")
        }
        sb.append(header.joinToString(",") { escape(it) }).append(EOL)

        entries.sortedBy { it.createdAt }.forEach { entry ->
            val values = EntryValues.decode(entry.valuesJson)
            val row = buildList {
                add(entry.id.toString())
                add(entry.createdAt)
                fields.forEach { add(cell(it, values)) }
                add(EntryValues.notes(values) ?: "")
            }
            sb.append(row.joinToString(",") { escape(it) }).append(EOL)
        }

        return sb.toString()
    }

    private fun cell(field: FieldDef, values: kotlinx.serialization.json.JsonObject): String =
        when (field.type) {
            FieldType.MULTIPLE -> {
                val selected = EntryValues.selectedOptions(values, field.label)
                field.options.filter { it in selected }.joinToString("; ")
            }
            // Everything else exports its raw stored value (scale as the bare
            // number; date/time as the stored ISO-8601 string).
            else -> EntryValues.rawValue(values, field.label) ?: ""
        }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
