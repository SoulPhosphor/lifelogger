package com.datadragon.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

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

        // MainActivity checks on every onCreate and a rotation recreates it, so a
        // second check can arrive while the first backup is still writing — and
        // it has not recorded its time yet, so it still looks due. One at a time.
        if (!running.compareAndSet(false, true)) return Outcome.NOT_DUE
        return try {
            // NonCancellable: the activity that started this is destroyed by a
            // rotation, and a backup abandoned part way through would leave a
            // truncated file wearing a good backup's name.
            withContext(Dispatchers.IO + NonCancellable) { backUp(Uri.parse(destination), now) }
        } finally {
            running.set(false)
        }
    }

    private suspend fun backUp(treeUri: Uri, now: Long): Outcome {
        val folder = runCatching { DocumentFile.fromTreeUri(appContext, treeUri) }.getOrNull()
        if (folder == null || !folder.exists() || !folder.canWrite()) return destinationLost(treeUri)

        // The same snapshot the manual backup takes, so the two formats can
        // never drift apart.
        val backup = BackupRepository(AppDatabase.getInstance(appContext)).buildFull()
        if (backup.logs.isEmpty() && backup.checklists.isEmpty()) return Outcome.NO_DATA

        val json = BackupCodec.encode(backup)
        val name = AutoBackup.fileName(LocalDate.now())

        // A file may already carry today's name: a previous attempt that failed
        // before recording its time, or — when the device clock has moved
        // backwards within the same day — a perfectly good backup. Either way it
        // survives until the replacement is safely written, so a failure here can
        // never leave the day with no backup at all. The provider gives the new
        // document a unique name while both exist.
        val existing = runCatching { folder.findFile(name)?.takeIf { it.isFile } }.getOrNull()

        val file = runCatching { folder.createFile(MIME_TYPE, name) }.getOrNull()
            ?: return Outcome.FAILED

        val written = runCatching {
            appContext.contentResolver.openOutputStream(file.uri)?.use {
                it.write(json.toByteArray())
            } ?: error("The backup file could not be opened for writing.")
        }
        if (written.isFailure) {
            // Drop the half-written file, never the one that was already there.
            runCatching { file.delete() }
            return Outcome.FAILED
        }

        // Only now that the new backup is complete on disk does the old one go,
        // and the finished file take its name. A rename that fails leaves a
        // valid backup under the provider's chosen name rather than none.
        if (existing != null && existing.uri != file.uri) {
            runCatching { existing.delete() }
            if (file.name != name) runCatching { file.renameTo(name) }
        }

        rotate(folder)

        // Settings can change the folder while this is running. If it did, this
        // backup went to the old one, so the newly chosen folder is still owed
        // today's and the time deliberately goes unrecorded — the next launch
        // writes there. Otherwise record it now, so a failure anywhere above
        // means the next launch retries rather than waiting another day.
        if (isStillChosen(treeUri)) {
            settings.lastAutoBackupAt = now
            settings.autoBackupDestinationLost = false
        }
        return Outcome.BACKED_UP
    }

    /** Whether the folder this run was given is still the one Settings holds. */
    private fun isStillChosen(treeUri: Uri): Boolean =
        settings.autoBackupFolderUri == treeUri.toString()

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
    private fun destinationLost(treeUri: Uri): Outcome {
        // Only disown the folder this run was actually given. The user may have
        // picked a different, perfectly good one while this was running — and
        // releasing the old permission is what made this run fail in the first
        // place — so turning the feature off would punish the new choice.
        if (isStillChosen(treeUri)) {
            settings.autoBackupEnabled = false
            settings.autoBackupDestinationLost = true
        }
        return Outcome.DESTINATION_UNAVAILABLE
    }

    private companion object {
        const val MIME_TYPE = "application/json"

        /** Guards against two launches backing up at once. Process-wide. */
        val running = AtomicBoolean(false)
    }
}
