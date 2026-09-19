package com.datadragon.app.data

import java.time.LocalDate

/**
 * The rules behind Automatic Backup, kept free of Android so they can be tested
 * directly. Everything that touches a file lives in [AutoBackupRunner].
 */
object AutoBackup {

    /**
     * Automatic backups carry their own prefix. Settings writes a manual backup
     * as `datadragon_backup_<date>.json`, which deliberately does not match, so
     * rotation can never delete a file the user made by hand.
     */
    const val FILE_PREFIX = "datadragon_autobackup_"

    const val FILE_SUFFIX = ".json"

    /** How many automatic backups are kept; anything older is removed. */
    const val KEEP = 4

    /** A backup becomes due a day after the last successful one. */
    const val INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    fun fileName(date: LocalDate): String = "$FILE_PREFIX$date$FILE_SUFFIX"

    /**
     * True only for a file this feature wrote. Rotation asks this before
     * deleting anything, so a manual backup, an export, or an unrelated file
     * sharing the folder is never a candidate.
     */
    fun isAutomaticBackup(name: String): Boolean =
        name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

    /**
     * Whether a backup should run now.
     *
     * [lastBackupDestination] is the folder the last recorded backup was written
     * to. A folder that isn't the one that backup went to has nothing in it yet,
     * so it is due immediately whatever the clock says — otherwise choosing a new
     * folder would leave it empty for up to a day and look broken. It also makes
     * the recorded time self-correcting: a backup credited to a folder that has
     * since been replaced simply doesn't count towards the new one.
     *
     * A [lastRunAt] of 0 means none has ever succeeded, so the first launch after
     * setup backs up. A [lastRunAt] in the future means the device clock moved
     * backwards; that counts as due rather than parking backups until the clock
     * catches up.
     */
    fun isDue(
        enabled: Boolean,
        destination: String?,
        lastBackupDestination: String?,
        lastRunAt: Long,
        now: Long,
    ): Boolean {
        if (!enabled || destination == null) return false
        if (lastBackupDestination != destination) return true
        if (lastRunAt > now) return true
        return now - lastRunAt >= INTERVAL_MILLIS
    }

    /**
     * Which automatic backups to delete so only the newest [keep] remain. Names
     * are dated `yyyy-MM-dd`, which sorts lexicographically exactly as it sorts
     * chronologically, so the newest sort last. Any name that is not an
     * automatic backup is dropped before sorting and can never be returned.
     */
    fun staleBackups(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter(::isAutomaticBackup)
            .distinct()
            .sortedDescending()
            .drop(keep)
}
