package com.datadragon.app.data

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable future execution. Android's implementation is WorkManager. */
interface AutoBackupScheduler {
    /** Ensures exactly one pending automatic-backup run at [atMillis]. */
    fun schedule(atMillis: Long, nowMillis: Long)

    /** Removes any pending automatic-backup run. */
    fun cancel()
}

/** Portable automatic-backup settings and the portable-preference revision. */
interface AutoBackupSettingsSource {
    val intervalMillis: Long
    val retention: Int
    val preferencesRevision: Long
}

/** The one shared complete-snapshot builder, with the revisions it captured. */
interface AutoBackupSnapshotSource {
    suspend fun capture(): CapturedBackup
    suspend fun currentDataRevision(): Long
}

/** What one pass of the runner did. */
enum class AutoBackupRunResult {
    DISABLED,
    CLEAN,
    NOT_DUE,
    BACKED_UP,
    FAILED,
}

/**
 * Every automatic-backup decision and write goes through this coordinator, and
 * every entry point (WorkManager, app foreground, folder change, settings
 * change) runs under the same process-wide [RUN_LOCK], so two attempts can
 * never write or rotate at the same time.
 *
 * Automatic backup uses the shared snapshot builder, the shared codec, and the
 * shared verified [BackupFileWriter]; there is no private payload or format.
 */
class AutoBackupCoordinator(
    private val state: AutoBackupStateStore,
    private val settings: AutoBackupSettingsSource,
    private val snapshots: AutoBackupSnapshotSource,
    private val folders: AutoBackupFolderAccess,
    private val scheduler: AutoBackupScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val writer: BackupFileWriter = BackupFileWriter(),
    private val lock: Mutex = RUN_LOCK,
) {

    /**
     * Verify the destination, back up when dirty and due, and otherwise make
     * sure one future run exists for the next protection point.
     */
    suspend fun runIfNeeded(): AutoBackupRunResult = lock.withLock { runLocked() }

    /** Enable or disable. Enabling requires a verified destination. */
    suspend fun setEnabled(enabled: Boolean): AutoBackupRunResult = lock.withLock {
        if (enabled) {
            check(state.read().folderUri != null) { "Choose a backup folder first." }
            state.setEnabled(true)
            runLocked()
        } else {
            state.setEnabled(false)
            cancelSchedule()
            AutoBackupRunResult.DISABLED
        }
    }

    /**
     * Replace the destination safely: acquire the new permission, verify the
     * folder with a real create/write/read/delete check, save it only then, and
     * release the previous folder's permission only after the new one is active.
     * A failure leaves the working destination untouched.
     */
    suspend fun selectFolder(uri: String): AutoBackupFolderSelection = lock.withLock {
        val previous = state.read().folderUri
        if (!folders.acquirePermission(uri)) {
            if (uri != previous) folders.releasePermission(uri)
            return@withLock AutoBackupFolderSelection.PERMISSION_DENIED
        }
        val usable = runCatching {
            folders.status(uri) == AutoBackupFolderStatus.AVAILABLE &&
                AutoBackupFolderProbe.verify(folders.open(uri))
        }.getOrDefault(false)
        if (!usable) {
            if (uri != previous) folders.releasePermission(uri)
            return@withLock AutoBackupFolderSelection.CANNOT_SAVE
        }
        val label = runCatching { folders.label(uri) }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri
        state.setDestination(uri, label)
        if (previous != null && previous != uri) folders.releasePermission(previous)
        // A new destination has no backup of its own yet, so it is due now.
        if (state.read().enabled) runLocked()
        AutoBackupFolderSelection.SELECTED
    }

    /**
     * Make sure one future run exists for the next protection point without
     * writing anything now. Used when the app leaves the foreground and after
     * cadence or retention changes; a backup that is already due is handed to
     * WorkManager to run immediately.
     */
    suspend fun ensureScheduled() = lock.withLock {
        val local = state.read()
        if (!local.enabled || local.folderUri == null) {
            cancelSchedule()
            return@withLock
        }
        val now = clock()
        val decision = decisionState(local)
        if (AutoBackupSchedule.isDirty(decision)) {
            val due = AutoBackupSchedule.dueAt(decision, now)
            // After a failure, keep the retry time rather than retrying at once.
            val retryAt = local.scheduledAt?.takeIf { local.error != null && it > due }
            scheduleAt(retryAt ?: due, now)
        } else {
            cancelSchedule()
        }
    }

    private suspend fun runLocked(): AutoBackupRunResult {
        val local = state.read()
        val folder = local.folderUri
        if (!local.enabled || folder == null) {
            cancelSchedule()
            return AutoBackupRunResult.DISABLED
        }
        val now = clock()
        val decision = decisionState(local)
        val dirty = AutoBackupSchedule.isDirty(decision)

        when (val status = runCatching { folders.status(folder) }.getOrDefault(AutoBackupFolderStatus.MISSING)) {
            AutoBackupFolderStatus.AVAILABLE -> {
                // Access came back on its own (for example, storage reconnected).
                if (local.error?.needsUserAction == true) state.clearAccessError()
            }
            else -> {
                if (dirty) {
                    state.recordFailure(now, accessError(status))
                    scheduleAt(now + AutoBackupPolicy.RETRY_DELAY_MILLIS, now)
                    return AutoBackupRunResult.FAILED
                }
                // Nothing needs protecting, but Settings must still show the problem.
                // No backup failed, so no dialog is raised; the dialog comes only
                // when a needed backup is blocked by this problem.
                if (local.error != accessError(status)) {
                    state.recordFailure(now, accessError(status), raiseNotice = false)
                }
                cancelSchedule()
                return AutoBackupRunResult.CLEAN
            }
        }

        if (!dirty) {
            cancelSchedule()
            return AutoBackupRunResult.CLEAN
        }
        if (!AutoBackupSchedule.isDue(decision, now)) {
            scheduleAt(AutoBackupSchedule.dueAt(decision, now), now)
            return AutoBackupRunResult.NOT_DUE
        }
        return backUp(folder, now)
    }

    private suspend fun backUp(folderUri: String, now: Long): AutoBackupRunResult {
        val captured = try {
            snapshots.capture()
        } catch (error: Exception) {
            return fail(folderUri, now, AutoBackupError.WRITE_FAILED)
        }
        val encoded = try {
            BackupCodec.encode(captured.backup).also { writer.validateFullBackup(it) }
        } catch (error: Exception) {
            return fail(folderUri, now, AutoBackupError.VERIFY_FAILED)
        }

        val (folder, existing) = try {
            val opened = folders.open(folderUri)
            opened to opened.listFiles()
        } catch (error: Exception) {
            return fail(folderUri, now, AutoBackupError.WRITE_FAILED)
        }

        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone())
        val name = AutoBackupFiles.nameFor(at, existing.map { it.name })
            ?: return fail(folderUri, now, AutoBackupError.WRITE_FAILED)

        var created: AutoBackupFolderFile? = null
        try {
            val file = folder.createFile(name, AutoBackupFiles.MIME_TYPE)
            created = file
            // A provider that renamed the file would put it outside rotation's
            // exact-name rule, so it is not accepted as an automatic backup.
            if (file.name != name) throw BackupWriteException("The backup file name was changed by the folder.")
            writer.writeAndVerify(FolderDestination(folder, file), encoded)
        } catch (error: Exception) {
            withContext(NonCancellable) {
                created?.let { partial -> runCatching { folder.delete(partial) } }
            }
            val kind = if (error is BackupVerificationException) {
                AutoBackupError.VERIFY_FAILED
            } else {
                AutoBackupError.WRITE_FAILED
            }
            return fail(folderUri, now, kind)
        }

        val saved = checkNotNull(created)
        withContext(NonCancellable) {
            // Only the revisions this snapshot captured become protected. A
            // change made while the file was being written stays dirty.
            state.recordSuccess(now, folderUri, captured.dataRevision, captured.preferencesRevision)
            rotate(folder, existing + saved, name)
            scheduleNext(now)
        }
        return AutoBackupRunResult.BACKED_UP
    }

    /** Deletes only positively identified automatic backups beyond the retention count. */
    private fun rotate(folder: AutoBackupFolder, files: List<AutoBackupFolderFile>, newlyCreated: String) {
        val byName = files.associateBy { it.name }
        AutoBackupFiles.staleBackups(byName.keys, settings.retention, newlyCreated).forEach { name ->
            byName[name]?.let { file -> runCatching { folder.delete(file) } }
        }
    }

    private suspend fun scheduleNext(now: Long) {
        val decision = decisionState(state.read())
        if (AutoBackupSchedule.isDirty(decision)) {
            scheduleAt(AutoBackupSchedule.dueAt(decision, now), now)
        } else {
            cancelSchedule()
        }
    }

    private suspend fun fail(folderUri: String, now: Long, error: AutoBackupError): AutoBackupRunResult {
        // Losing access mid-write is reported as the access problem, not a write failure.
        val status = runCatching { folders.status(folderUri) }.getOrDefault(AutoBackupFolderStatus.MISSING)
        val recorded = if (status == AutoBackupFolderStatus.AVAILABLE) error else accessError(status)
        withContext(NonCancellable) {
            state.recordFailure(now, recorded)
            scheduleAt(now + AutoBackupPolicy.RETRY_DELAY_MILLIS, now)
        }
        return AutoBackupRunResult.FAILED
    }

    private suspend fun decisionState(local: AutoBackupLocalState) = AutoBackupSnapshotState(
        enabled = local.enabled,
        folderUri = local.folderUri,
        lastSuccessAt = local.lastSuccessAt,
        lastSuccessDestination = local.lastSuccessDestination,
        protectedDataRevision = local.protectedDataRevision,
        protectedPreferencesRevision = local.protectedPreferencesRevision,
        currentDataRevision = snapshots.currentDataRevision(),
        currentPreferencesRevision = settings.preferencesRevision,
        intervalMillis = settings.intervalMillis,
    )

    private fun scheduleAt(at: Long, now: Long) {
        scheduler.schedule(at, now)
        state.setScheduledAt(at)
    }

    private fun cancelSchedule() {
        if (state.read().scheduledAt != null) state.setScheduledAt(null)
        scheduler.cancel()
    }

    private fun accessError(status: AutoBackupFolderStatus): AutoBackupError = when (status) {
        AutoBackupFolderStatus.PERMISSION_LOST -> AutoBackupError.PERMISSION_LOST
        else -> AutoBackupError.FOLDER_MISSING
    }

    private class FolderDestination(
        private val folder: AutoBackupFolder,
        private val file: AutoBackupFolderFile,
    ) : BackupDestination {
        override fun openOutputStream(): OutputStream? = folder.openOutputStream(file)
        override fun openInputStream(): InputStream? = folder.openInputStream(file)
    }

    companion object {
        /** The one runner lock shared by every automatic-backup entry point in the process. */
        val RUN_LOCK = Mutex()
    }
}
