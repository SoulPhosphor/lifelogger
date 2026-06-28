package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository

/**
 * Backs the Backup and Restore actions. Backup builds the JSON for the whole
 * database; restore parses a backup and replaces all current data with it.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BackupRepository(AppDatabase.getInstance(app))

    /** The full-database backup as pretty-printed JSON. */
    suspend fun buildBackupJson(): String = BackupCodec.encode(repository.buildFull())

    /** Parse [text] and replace all data with it. */
    suspend fun restore(text: String): RestoreResult =
        try {
            val backup = BackupCodec.decode(text)
            RestoreResult.Success(repository.restore(backup))
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "This file isn't a valid backup.")
        }
}

sealed interface RestoreResult {
    data class Success(val logs: Int) : RestoreResult
    data class Failure(val message: String) : RestoreResult
}
