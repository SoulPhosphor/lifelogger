package com.datadragon.app.data

/** Device-local policy chosen before a Merge restore begins. */
enum class RestoreConflictPolicy(val key: String) {
    KEEP_CURRENT("keep_current"),
    USE_BACKUP("use_backup"),
    ASK("ask");

    companion object {
        fun fromKey(key: String?): RestoreConflictPolicy =
            entries.firstOrNull { it.key == key } ?: ASK
    }
}
