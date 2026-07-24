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
