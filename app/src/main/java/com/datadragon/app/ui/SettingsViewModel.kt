package com.datadragon.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Backs the toggles on the Settings screen. Reads the stored flags once on
 * creation and writes each change straight through to [SettingsRepository].
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    private val _autoCapitalizeLabels = MutableStateFlow(repo.autoCapitalizeLabels)
    val autoCapitalizeLabels: StateFlow<Boolean> = _autoCapitalizeLabels

    private val _autoCapitalizeOptions = MutableStateFlow(repo.autoCapitalizeOptions)
    val autoCapitalizeOptions: StateFlow<Boolean> = _autoCapitalizeOptions

    // Global list behavior (applies to every list).
    private val _completeIcon = MutableStateFlow(repo.completeIcon)
    val completeIcon: StateFlow<CompleteIcon> = _completeIcon

    private val _crossOutWhenCompleted = MutableStateFlow(repo.crossOutWhenCompleted)
    val crossOutWhenCompleted: StateFlow<Boolean> = _crossOutWhenCompleted

    private val _moveCompletedToBottom = MutableStateFlow(repo.moveCompletedToBottom)
    val moveCompletedToBottom: StateFlow<Boolean> = _moveCompletedToBottom

    // --- Automatic Backup ---------------------------------------------------

    private val _backupFolderUri = MutableStateFlow(repo.autoBackupFolderUri)
    val backupFolderUri: StateFlow<String?> = _backupFolderUri

    private val _autoBackupEnabled = MutableStateFlow(repo.autoBackupEnabled)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled

    private val _lastAutoBackupAt = MutableStateFlow(repo.lastAutoBackupAt)
    val lastAutoBackupAt: StateFlow<Long> = _lastAutoBackupAt

    private val _backupDestinationLost = MutableStateFlow(repo.autoBackupDestinationLost)
    val backupDestinationLost: StateFlow<Boolean> = _backupDestinationLost

    fun setAutoCapitalizeLabels(value: Boolean) {
        repo.autoCapitalizeLabels = value
        _autoCapitalizeLabels.value = value
    }

    fun setAutoCapitalizeOptions(value: Boolean) {
        repo.autoCapitalizeOptions = value
        _autoCapitalizeOptions.value = value
    }

    fun setCompleteIcon(value: CompleteIcon) {
        repo.completeIcon = value
        _completeIcon.value = value
    }

    fun setCrossOutWhenCompleted(value: Boolean) {
        repo.crossOutWhenCompleted = value
        _crossOutWhenCompleted.value = value
    }

    fun setMoveCompletedToBottom(value: Boolean) {
        repo.moveCompletedToBottom = value
        _moveCompletedToBottom.value = value
    }

    /**
     * Remember the folder the user picked and hold on to the right to write it
     * after a reboot. The previous folder's permission is released so the app
     * keeps only the access it is actually using.
     */
    fun setBackupFolder(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        repo.autoBackupFolderUri
            ?.takeIf { it != uri.toString() }
            ?.let { previous -> runCatching { resolver.releasePersistableUriPermission(Uri.parse(previous), flags) } }
        repo.autoBackupFolderUri = uri.toString()
        _backupFolderUri.value = uri.toString()
        // Choosing a folder answers the "no longer available" warning.
        repo.autoBackupDestinationLost = false
        _backupDestinationLost.value = false
    }

    fun setAutoBackupEnabled(value: Boolean) {
        repo.autoBackupEnabled = value
        _autoBackupEnabled.value = value
    }

    /** Re-read the flags the background backup may have changed since last look. */
    fun refreshAutoBackupState() {
        _autoBackupEnabled.value = repo.autoBackupEnabled
        _lastAutoBackupAt.value = repo.lastAutoBackupAt
        _backupDestinationLost.value = repo.autoBackupDestinationLost
        _backupFolderUri.value = repo.autoBackupFolderUri
    }
}
