package com.datadragon.app.data

/** One preflight difference that cannot be merged silently. */
data class RestoreConflict(
    val id: String,
    val category: BackupCategory,
    val title: String,
    val currentDescription: String,
    val backupDescription: String,
    val kind: RestoreConflictKind,
)

enum class RestoreConflictKind {
    WHOLE_GROUP,
    DAILY_DATE_CARD,
    SAVED_COLOR_PRESET,
}

enum class RestoreConflictChoice { KEEP_CURRENT, USE_BACKUP }

data class RestorePreflight(
    val conflicts: List<RestoreConflict>,
    val selectedCategories: Set<BackupCategory>,
)

data class RestoreCategoryCounts(
    val added: Int = 0,
    val replaced: Int = 0,
    val skipped: Int = 0,
    val conflicted: Int = 0,
) {
    operator fun plus(other: RestoreCategoryCounts): RestoreCategoryCounts = RestoreCategoryCounts(
        added = added + other.added,
        replaced = replaced + other.replaced,
        skipped = skipped + other.skipped,
        conflicted = conflicted + other.conflicted,
    )
}

/** Complete machine-readable result used by summaries and tests. */
data class RestoreCounts(
    val categories: Map<BackupCategory, RestoreCategoryCounts> = emptyMap(),
) {
    val logs: Int get() = categories[BackupCategory.FORMS]?.let { it.added + it.replaced } ?: 0
    val lists: Int get() = categories[BackupCategory.LISTS]?.let { it.added + it.replaced } ?: 0
    val added: Int get() = categories.values.sumOf { it.added }
    val replaced: Int get() = categories.values.sumOf { it.replaced }
    val skipped: Int get() = categories.values.sumOf { it.skipped }
    val conflicted: Int get() = categories.values.sumOf { it.conflicted }
}
