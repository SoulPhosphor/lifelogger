package com.datadragon.app.data

/**
 * Export file naming (docs/FORMATTING_SPEC.md §5): lowercase the name, spaces to
 * underscores, strip punctuation other than underscores. Every export format
 * uses the bare base plus its extension (e.g. `my_log.pdf`, `my_log.json`).
 */
object ExportNaming {

    fun base(name: String): String {
        val cleaned = name.trim().lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .trim('_')
        return cleaned.ifEmpty { "log" }
    }

    /** The suggested file name for an export: the base plus the format's extension. */
    fun fileName(name: String, extension: String): String = "${base(name)}.$extension"

    /**
     * The "Human Readable" `.json` export's name: the base plus `_clean`, so it
     * never collides with the restorable `.json` (e.g. `my_log_clean.json`).
     */
    fun cleanJsonFileName(name: String): String = "${base(name)}_clean.json"
}
