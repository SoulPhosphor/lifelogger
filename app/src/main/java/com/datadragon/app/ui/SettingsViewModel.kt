package com.datadragon.app.ui

import android.app.Application
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
}
