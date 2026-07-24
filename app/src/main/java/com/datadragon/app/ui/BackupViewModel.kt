package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.RestoreMode
import com.datadragon.app.data.UndoSnapshot
import com.datadragon.app.data.UndoSnapshotStore

/**
 * Backs the Backup and Restore actions. Backup builds the JSON for the whole
 * database; restore parses a backup and applies it using the chosen [RestoreMode].
 * Every restore also captures a pre-import snapshot for Undo Last Import.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BackupRepository(AppDatabase.getInstance(app))
    private val undoStore = UndoSnapshotStore(app)

    /** The full-database backup as pretty-printed JSON. */
    suspend fun buildBackupJson(): String = BackupCodec.encode(repository.buildFull())

    /**
     * Parse [text] and apply it with [mode]. The state right before the import
     * is captured for Undo Last Import — but only once the import itself
     * succeeds, so a failed restore leaves the existing undo snapshot in place.
     */
    suspend fun restore(text: String, mode: RestoreMode): RestoreResult =
        try {
            val backup = BackupCodec.decode(text)
            val preImage = repository.buildFull()
            val counts = repository.restore(backup, mode)
            undoStore.save(UndoSnapshot(capturedAt = BackupRepository.now(), data = preImage))
            RestoreResult.Success(logs = counts.logs, lists = counts.lists)
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
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
                    val counts = repository.restore(backup, RestoreMode.MERGE)
                    RestoreResult.Success(logs = counts.logs, lists = counts.lists)
                }
            }
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
        }

    /** True once an import has happened, so there's a snapshot to restore from. */
    suspend fun hasUndoSnapshot(): Boolean = undoStore.load() != null

    /** Puts lists back to right before the last import; forms are untouched. */
    suspend fun undoListImport(): String {
        val snapshot = undoStore.load() ?: return "Nothing to restore."
        repository.restoreListsFromSnapshot(snapshot.data)
        return "List state restored."
    }

    /** Puts forms back to right before the last import; lists are untouched. */
    suspend fun undoFormImport(): String {
        val snapshot = undoStore.load() ?: return "Nothing to restore."
        repository.restoreFormsFromSnapshot(snapshot.data)
        return "Form state restored."
    }
}

sealed interface RestoreResult {
    data class Success(val logs: Int, val lists: Int) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}
