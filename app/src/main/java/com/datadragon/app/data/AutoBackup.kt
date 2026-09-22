package com.datadragon.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** How often automatic backup is due. Portable preference. */
enum class AutoBackupCadence(val key: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    CUSTOM("custom");

    companion object {
        val DEFAULT = DAILY

        fun fromKey(key: String?): AutoBackupCadence = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Owner-approved automatic-backup ranges and defaults. */
object AutoBackupPolicy {
    val CUSTOM_DAYS_RANGE: IntRange = 1..365
    const val DEFAULT_CUSTOM_DAYS = 3
    val RETENTION_CHOICES: List<Int> = listOf(3, 5, 7)
    const val DEFAULT_RETENTION = 3

    /** How long to wait before retrying after a failed attempt. */
    const val RETRY_DELAY_MILLIS: Long = 15L * 60L * 1000L

    private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun intervalDays(cadence: AutoBackupCadence, customDays: Int): Int = when (cadence) {
        AutoBackupCadence.DAILY -> 1
        AutoBackupCadence.WEEKLY -> 7
        AutoBackupCadence.CUSTOM -> customDays.coerceIn(CUSTOM_DAYS_RANGE)
    }

    /** Elapsed time, not calendar days: Daily is 24 hours after the last success. */
    fun intervalMillis(cadence: AutoBackupCadence, customDays: Int): Long =
        intervalDays(cadence, customDays) * DAY_MILLIS

    fun parseCustomDays(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in CUSTOM_DAYS_RANGE }

    fun normalizeRetention(value: Int): Int =
        if (value in RETENTION_CHOICES) value else DEFAULT_RETENTION
}

/**
 * Everything the due/dirty decision depends on, read from device-local state,
 * the database revision, and the portable preference revision.
 */
data class AutoBackupSnapshotState(
    val enabled: Boolean,
    val folderUri: String?,
    val lastSuccessAt: Long?,
    val lastSuccessDestination: String?,
    val protectedDataRevision: Long,
    val protectedPreferencesRevision: Long,
    val currentDataRevision: Long,
    val currentPreferencesRevision: Long,
    val intervalMillis: Long,
)

/** Pure due and dirty rules, kept free of Android so they can be tested directly. */
object AutoBackupSchedule {

    /** A destination with no verified backup of its own needs its first backup. */
    fun destinationNeedsInitialBackup(state: AutoBackupSnapshotState): Boolean =
        state.folderUri != null &&
            (state.lastSuccessAt == null || state.lastSuccessDestination != state.folderUri)

    /** Protected data or portable preferences changed since the last verified backup. */
    fun isDirty(state: AutoBackupSnapshotState): Boolean =
        destinationNeedsInitialBackup(state) ||
            state.currentDataRevision > state.protectedDataRevision ||
            state.currentPreferencesRevision > state.protectedPreferencesRevision

    /**
     * When the next backup may run. A new destination is due immediately. A last
     * success in the future means the clock moved backwards, which is due now
     * rather than parking backups until the clock catches up.
     */
    fun dueAt(state: AutoBackupSnapshotState, now: Long): Long {
        val last = state.lastSuccessAt
        return when {
            destinationNeedsInitialBackup(state) -> now
            last == null -> now
            last > now -> now
            else -> last + state.intervalMillis
        }
    }

    fun isDue(state: AutoBackupSnapshotState, now: Long): Boolean = dueAt(state, now) <= now
}

/**
 * Automatic backup file names. Only names matching one of these exact shapes
 * are ever counted or deleted by rotation:
 *
 * - `datadragon_autobackup_YYYY-MM-DD.json`
 * - `datadragon_autobackup_YYYY-MM-DD_HH-mm.json` when the date name is taken
 * - `datadragon_autobackup_YYYY-MM-DD_HH-mm-ss.json` when that is taken too
 *
 * Manual backups are named `datadragon_backup_…` and never match.
 */
object AutoBackupFiles {
    const val PREFIX = "datadragon_autobackup_"
    const val SUFFIX = ".json"
    const val MIME_TYPE = "application/json"

    private val PATTERN = Regex(
        "^datadragon_autobackup_(\\d{4}-\\d{2}-\\d{2})(?:_(\\d{2})-(\\d{2})(?:-(\\d{2}))?)?\\.json$",
    )

    /** The moment a name records, or null when the name is not an automatic backup. */
    fun parse(name: String): LocalDateTime? {
        val match = PATTERN.matchEntire(name) ?: return null
        val (dateText, hourText, minuteText, secondText) = match.destructured
        return try {
            val date = LocalDate.parse(dateText)
            if (hourText.isEmpty()) {
                date.atStartOfDay()
            } else {
                val time = LocalTime.of(
                    hourText.toInt(),
                    minuteText.toInt(),
                    secondText.ifEmpty { "0" }.toInt(),
                )
                date.atTime(time)
            }
        } catch (_: DateTimeParseException) {
            null
        } catch (_: java.time.DateTimeException) {
            null
        }
    }

    fun isAutomaticBackup(name: String): Boolean = parse(name) != null

    /**
     * The name for a backup made at [at]. The plain date is used when no
     * automatic backup already exists for that date; otherwise the time is
     * added, and seconds only when needed. This keeps names ordered correctly.
     * Returns null when every candidate name is already taken.
     */
    fun nameFor(at: LocalDateTime, existingNames: Collection<String>): String? {
        val existing = existingNames.toSet()
        val date = at.toLocalDate()
        val dateName = "$PREFIX$date$SUFFIX"
        val anyForDate = existing.any { name -> parse(name)?.toLocalDate() == date }
        val minuteName = "$PREFIX${date}_${two(at.hour)}-${two(at.minute)}$SUFFIX"
        val secondName = "$PREFIX${date}_${two(at.hour)}-${two(at.minute)}-${two(at.second)}$SUFFIX"
        return when {
            !anyForDate && dateName !in existing -> dateName
            minuteName !in existing -> minuteName
            secondName !in existing -> secondName
            else -> null
        }
    }

    /**
     * The automatic backups rotation deletes so only the newest [keep] remain.
     * Names that are not automatic backups are dropped first and can never be
     * returned, and [newlyCreated] is never returned.
     */
    fun staleBackups(names: Collection<String>, keep: Int, newlyCreated: String): List<String> {
        val automatic = names.toSet()
            .mapNotNull { name -> parse(name)?.let { name to it } }
            .sortedWith(compareByDescending<Pair<String, LocalDateTime>> { it.first == newlyCreated }
                .thenByDescending { it.second }
                .thenByDescending { it.first })
        return automatic.drop(keep.coerceAtLeast(1)).map { it.first }.filter { it != newlyCreated }
    }

    private fun two(value: Int): String = value.toString().padStart(2, '0')
}
