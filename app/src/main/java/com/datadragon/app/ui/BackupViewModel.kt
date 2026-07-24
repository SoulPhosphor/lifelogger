package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.RestoreMode

/**
 * Backs the Backup and Restore actions. Backup builds the JSON for the whole
 * database; restore parses a backup and applies it using the chosen [RestoreMode].
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BackupRepository(AppDatabase.getInstance(app))

    /** The full-database backup as pretty-printed JSON. */
    suspend fun buildBackupJson(): String = BackupCodec.encode(repository.buildFull())

    /** Parse [text] and apply it with [mode]. */
    suspend fun restore(text: String, mode: RestoreMode): RestoreResult =
        try {
            val backup = BackupCodec.decode(text)
            val counts = repository.restore(backup, mode)
            RestoreResult.Success(logs = counts.logs, lists = counts.lists)
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
        }
}

sealed interface RestoreResult {
    data class Success(val logs: Int, val lists: Int) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}
