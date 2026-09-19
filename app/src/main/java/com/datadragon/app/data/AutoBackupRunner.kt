package com.datadragon.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Writes the once-a-day automatic backup into the folder the user picked.
 *
 * The file it writes is the same `.json` the manual "Back Up All Data" produces
 * — same [BackupRepository.buildFull] snapshot, same [BackupCodec] encoding — so
 * Restore reads an automatic backup with no special handling. Only the filename
 * differs, so rotation can tell its own files apart from the user's.
 */
class AutoBackupRunner(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsRepository(appContext)

    /** What one [runIfDue] attempt did. */
    enum class Outcome {
        /** Off, no folder chosen, or backed up less than a day ago. */
        NOT_DUE,

        /** Nothing to save yet, so no empty file was written and no time recorded. */
        NO_DATA,

        /** The chosen folder is gone or read-only; the toggle has been turned off. */
        DESTINATION_UNAVAILABLE,

        /** The write failed. Nothing was recorded, so the next launch tries again. */
        FAILED,

        BACKED_UP,
    }

    /**
     * Backs up if one is due, and does nothing at all otherwise.
     *
     * The due check is three `SharedPreferences` reads and a subtraction, so the
     * overwhelmingly common "opened the app again" case costs a launch nothing
     * and never leaves the calling thread. Only a backup that is actually due
     * moves to [Dispatchers.IO].
     */
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): Outcome {
        val destination = settings.autoBackupFolderUri
        val due = AutoBackup.isDue(
            enabled = settings.autoBackupEnabled,
            hasDestination = destination != null,
            lastRunAt = settings.lastAutoBackupAt,
            now = now,
        )
        if (!due || destination == null) return Outcome.NOT_DUE
        return withContext(Dispatchers.IO) { backUp(Uri.parse(destination), now) }
    }

    private suspend fun backUp(treeUri: Uri, now: Long): Outcome {
        val folder = runCatching { DocumentFile.fromTreeUri(appContext, treeUri) }.getOrNull()
        if (folder == null || !folder.exists() || !folder.canWrite()) return destinationLost()

        // The same snapshot the manual backup takes, so the two formats can
        // never drift apart.
        val backup = BackupRepository(AppDatabase.getInstance(appContext)).buildFull()
        if (backup.logs.isEmpty() && backup.checklists.isEmpty()) return Outcome.NO_DATA

        val json = BackupCodec.encode(backup)
        val name = AutoBackup.fileName(LocalDate.now())

        // A file already carrying today's name can only be a previous attempt
        // that failed before recording its time — a successful one would have
        // held off another backup for a day. Replace it rather than letting the
        // provider add a "(1)" copy that then eats a rotation slot.
        runCatching { folder.findFile(name)?.takeIf { it.isFile }?.delete() }

        val file = runCatching { folder.createFile(MIME_TYPE, name) }.getOrNull()
            ?: return Outcome.FAILED

        val written = runCatching {
            appContext.contentResolver.openOutputStream(file.uri)?.use {
                it.write(json.toByteArray())
            } ?: error("The backup file could not be opened for writing.")
        }
        if (written.isFailure) {
            // Don't leave a half-written backup looking like a good one.
            runCatching { file.delete() }
            return Outcome.FAILED
        }

        rotate(folder)
        // Recorded only now, so a failure anywhere above means the next launch
        // tries again rather than waiting another day.
        settings.lastAutoBackupAt = now
        settings.autoBackupDestinationLost = false
        return Outcome.BACKED_UP
    }

    /** Deletes the automatic backups beyond the newest [AutoBackup.KEEP]. */
    private fun rotate(folder: DocumentFile) {
        val files = runCatching { folder.listFiles().filter { it.isFile } }.getOrNull().orEmpty()
        val stale = AutoBackup.staleBackups(files.mapNotNull { it.name }).toSet()
        if (stale.isEmpty()) return
        files.forEach { file ->
            val name = file.name ?: return@forEach
            if (name in stale) runCatching { file.delete() }
        }
    }

    /**
     * The folder is gone, or the permission to write it was revoked. Turn the
     * toggle off and leave a flag, so Settings says the location needs choosing
     * again instead of the feature quietly doing nothing forever.
     */
    private fun destinationLost(): Outcome {
        settings.autoBackupEnabled = false
        settings.autoBackupDestinationLost = true
        return Outcome.DESTINATION_UNAVAILABLE
    }

    private companion object {
        const val MIME_TYPE = "application/json"
    }
}
