package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AutoBackupCadence
import com.datadragon.app.data.AutoBackupFolderSelection
import com.datadragon.app.data.AutoBackupLocalState
import com.datadragon.app.data.AutoBackupPolicy
import com.datadragon.app.data.AutoBackupService
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.HomeView
import com.datadragon.app.data.NavStyle
import com.datadragon.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    // Navigation menu preferences.
    private val _navStyle = MutableStateFlow(repo.navStyle)
    val navStyle: StateFlow<NavStyle> = _navStyle

    private val _useModeLabelInDropdown = MutableStateFlow(repo.useModeLabelInDropdown)
    val useModeLabelInDropdown: StateFlow<Boolean> = _useModeLabelInDropdown

    private val _enabledModes = MutableStateFlow(repo.enabledModes.toSet())
    val enabledModes: StateFlow<Set<HomeView>> = _enabledModes

    // Automatic backup. Cadence and retention are portable preferences; the
    // folder, enabled state, history, and errors are device-local.
    private val autoBackup = AutoBackupService.get(app)
    val autoBackupState: StateFlow<AutoBackupLocalState> = autoBackup.state

    private val _autoBackupCadence = MutableStateFlow(repo.automaticBackupCadence)
    val autoBackupCadence: StateFlow<AutoBackupCadence> = _autoBackupCadence

    private val _autoBackupCustomDaysText = MutableStateFlow(repo.automaticBackupCustomDays.toString())
    val autoBackupCustomDaysText: StateFlow<String> = _autoBackupCustomDaysText

    private val _autoBackupRetention = MutableStateFlow(repo.automaticBackupRetention)
    val autoBackupRetention: StateFlow<Int> = _autoBackupRetention

    /** The result of the most recent folder choice, while it failed. */
    private val _folderSelectionError = MutableStateFlow<AutoBackupFolderSelection?>(null)
    val folderSelectionError: StateFlow<AutoBackupFolderSelection?> = _folderSelectionError

    fun refreshAutoBackupState() = autoBackup.refresh()

    fun selectBackupFolder(uri: String) {
        viewModelScope.launch {
            val result = autoBackup.selectFolder(uri)
            _folderSelectionError.value = result.takeIf { it != AutoBackupFolderSelection.SELECTED }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { autoBackup.setEnabled(enabled) }
    }

    fun setAutoBackupCadence(value: AutoBackupCadence) {
        repo.automaticBackupCadence = value
        _autoBackupCadence.value = value
        autoBackup.onSettingsChanged()
    }

    /** Keeps the typed text; only a number from 1 to 365 is saved. */
    fun setAutoBackupCustomDaysText(text: String) {
        val digits = text.filter { it.isDigit() }.take(3)
        _autoBackupCustomDaysText.value = digits
        AutoBackupPolicy.parseCustomDays(digits)?.let { days ->
            repo.automaticBackupCustomDays = days
            autoBackup.onSettingsChanged()
        }
    }

    fun setAutoBackupRetention(value: Int) {
        repo.automaticBackupRetention = value
        _autoBackupRetention.value = value
        autoBackup.onSettingsChanged()
    }

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

    fun setNavStyle(value: NavStyle) {
        repo.navStyle = value
        _navStyle.value = value
    }

    fun setUseModeLabelInDropdown(value: Boolean) {
        repo.useModeLabelInDropdown = value
        _useModeLabelInDropdown.value = value
    }

    fun setModeEnabled(view: HomeView, enabled: Boolean) {
        repo.setModeEnabled(view, enabled)
        _enabledModes.value = repo.enabledModes.toSet()
    }
}
