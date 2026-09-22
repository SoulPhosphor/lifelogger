package com.datadragon.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupDestination
import com.datadragon.app.data.BackupFileWriter
import com.datadragon.app.data.BackupCategory
import com.datadragon.app.data.BackupFile
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.RestoreConflict
import com.datadragon.app.data.RestoreConflictChoice
import com.datadragon.app.data.RestoreConflictPolicy
import com.datadragon.app.data.RestoreCounts
import com.datadragon.app.data.RestoreMode
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.UndoSnapshot
import com.datadragon.app.data.UndoSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backs the Backup and Restore actions. Backup builds the JSON for the whole
 * database; restore parses a backup and applies it using the chosen [RestoreMode].
 * Every restore also captures a pre-import snapshot for Undo Last Import.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val repository = BackupRepository(
        db = AppDatabase.getInstance(app),
        portablePreferences = settings::portableBackupSnapshot,
        applyPortablePreferences = settings::applyPortableBackup,
        sourceAppVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown",
    )
    private val undoStore = UndoSnapshotStore(app)
    private var pendingRestore: PendingRestore? = null

    val restoreConflictPolicy: RestoreConflictPolicy get() = settings.restoreConflictPolicy

    fun setRestoreConflictPolicy(policy: RestoreConflictPolicy) {
        settings.restoreConflictPolicy = policy
    }

    /** The full-database backup as validated, pretty-printed JSON. */
    suspend fun buildBackupJson(): String {
        val encoded = BackupCodec.encode(repository.buildFull())
        BackupFileWriter().validateFullBackup(encoded)
        return encoded
    }

    /** Build and validate the complete payload before the destination picker opens. */
    suspend fun prepareManualBackupJson(): String = buildBackupJson()

    /** Write, close, reopen, read, and validate the selected destination off the UI thread. */
    suspend fun saveManualBackup(uri: Uri, encoded: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destination = object : BackupDestination {
                override fun openOutputStream() =
                    getApplication<Application>().contentResolver.openOutputStream(uri)

                override fun openInputStream() =
                    getApplication<Application>().contentResolver.openInputStream(uri)
            }
            BackupFileWriter().writeAndVerify(destination, encoded)
        }.map { Unit }
    }

    /**
     * Parse [text] and apply it with [mode]. The state right before the import
     * is secured for Undo Last Import before the database transaction starts.
     * If restore fails, that valid pre-import snapshot remains available.
     */
    suspend fun restore(
        text: String,
        mode: RestoreMode,
        forms: Boolean = true,
        lists: Boolean = true,
        conflictPolicy: RestoreConflictPolicy = settings.restoreConflictPolicy,
    ): RestoreResult =
        try {
            val backup = BackupCodec.decode(text)
            val selected = selectedCategories(forms, lists)
            val preflight = repository.preflight(backup, mode, selected)
            if (mode == RestoreMode.MERGE && conflictPolicy == RestoreConflictPolicy.ASK && preflight.conflicts.isNotEmpty()) {
                pendingRestore = PendingRestore(backup, mode, preflight.selectedCategories, preflight.conflicts)
                RestoreResult.NeedsConflictResolution(preflight.conflicts)
            } else {
                val choice = when (conflictPolicy) {
                    RestoreConflictPolicy.KEEP_CURRENT, RestoreConflictPolicy.ASK -> RestoreConflictChoice.KEEP_CURRENT
                    RestoreConflictPolicy.USE_BACKUP -> RestoreConflictChoice.USE_BACKUP
                }
                executeRestore(
                    backup,
                    mode,
                    preflight.selectedCategories,
                    preflight.conflicts.associate { it.id to choice },
                )
            }
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
        }

    suspend fun continueRestore(choices: Map<String, RestoreConflictChoice>): RestoreResult {
        val pending = pendingRestore ?: return RestoreResult.Failure("There is no pending restore to continue.")
        if (pending.conflicts.any { it.id !in choices }) {
            return RestoreResult.Failure("Choose how to handle every conflict before continuing.")
        }
        pendingRestore = null
        return executeRestore(pending.backup, pending.mode, pending.selectedCategories, choices)
    }

    fun cancelPendingRestore() {
        pendingRestore = null
    }

    private suspend fun executeRestore(
        backup: BackupFile,
        mode: RestoreMode,
        selected: Set<BackupCategory>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreResult {
        val preImage = repository.buildFull()
        try {
            undoStore.saveVerified(
                UndoSnapshot(
                    capturedAt = BackupRepository.now(),
                    data = preImage,
                    selectedCategories = selected.sortedBy { it.ordinal },
                ),
            )
        } catch (error: Exception) {
            return RestoreResult.Failure(
                "Restore did not start because a verified Undo Last Import snapshot could not be saved: " +
                    (error.message ?: "unknown file error"),
            )
        }
        return try {
            RestoreResult.Success(repository.restore(backup, mode, selected, choices))
        } catch (error: Exception) {
            RestoreResult.Failure(
                "Restore failed. Database changes were rolled back and Undo Last Import remains available: " +
                    (error.message ?: "unknown database error"),
            )
        }
    }

    /**
     * Restore one exported list or form.
     *
     * The file is a single-item export — the .json a list or a form writes from
     * its own Export — which is a backup file holding exactly one item. The type
     * is therefore **read from the file**, not chosen by the user, and the item
     * goes back to wherever its kind belongs.
     *
     * It always merges (it adds or updates that one item and leaves everything
     * else alone) and takes no undo snapshot: undo is reserved for the
     * whole-database restores above it.
     */
    suspend fun restoreSingleItem(text: String): RestoreResult =
        try {
            val backup = BackupCodec.decode(text)
            val items = backup.logs.size + backup.checklists.size
            when {
                items == 0 -> RestoreResult.Failure("That file doesn't hold a list or a form.")
                items > 1 -> RestoreResult.Failure(
                    "That file holds more than one item. Use Restore from Database for a full backup.",
                )
                else -> {
                    val category = if (backup.logs.size == 1) BackupCategory.FORMS else BackupCategory.LISTS
                    if (backup.includedCategories.toSet() != setOf(category)) {
                        RestoreResult.Failure(
                            "That file contains database backup categories. Use Restore from Database.",
                        )
                    } else {
                        val selected = setOf(category)
                        val preflight = repository.preflight(backup, RestoreMode.MERGE, selected)
                        // Individual exports retain their established update behavior:
                        // the imported form/list replaces the grouping with its UUID.
                        val choices = preflight.conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP }
                        RestoreResult.Success(repository.restore(backup, RestoreMode.MERGE, selected, choices))
                    }
                }
            }
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
        }

    /** True once an import has happened, so there's a snapshot to restore from. */
    suspend fun hasUndoSnapshot(): Boolean = undoStore.load() != null

    /**
     * Puts the chosen data types back to right before the last import. The types
     * not asked for are left exactly as they are, so undoing lists never touches
     * forms and the other way round.
     */
    suspend fun undoImport(): String {
        val snapshot = undoStore.load() ?: return "Nothing to restore."
        return try {
            repository.undo(snapshot)
            "Previous state restored."
        } catch (error: Exception) {
            "Undo failed. Database changes were rolled back: " +
                (error.message ?: "unknown database error")
        }
    }

    private fun selectedCategories(forms: Boolean, lists: Boolean): Set<BackupCategory> = when {
        forms && lists -> BackupCategory.entries.toSet()
        forms -> setOf(BackupCategory.FORMS)
        lists -> setOf(BackupCategory.LISTS)
        else -> emptySet()
    }

    private data class PendingRestore(
        val backup: BackupFile,
        val mode: RestoreMode,
        val selectedCategories: Set<BackupCategory>,
        val conflicts: List<RestoreConflict>,
    )
}

sealed interface RestoreResult {
    data class Success(val counts: RestoreCounts) : RestoreResult {
        val logs: Int get() = counts.logs
        val lists: Int get() = counts.lists
    }
    data class NeedsConflictResolution(val conflicts: List<RestoreConflict>) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}
