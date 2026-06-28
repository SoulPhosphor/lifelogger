package com.datadragon.app.data

/**
 * Export file naming (docs/FORMATTING_SPEC.md §5): lowercase the name, spaces to
 * underscores, strip punctuation other than underscores. Reports append
 * `_report`; the single-log JSON export uses the bare base (e.g. `my_log.json`).
 */
object ExportNaming {

    fun base(name: String): String {
        val cleaned = name.trim().lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .trim('_')
        return cleaned.ifEmpty { "log" }
    }
}
