package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

    fun setAutoCapitalizeLabels(value: Boolean) {
        repo.autoCapitalizeLabels = value
        _autoCapitalizeLabels.value = value
    }

    fun setAutoCapitalizeOptions(value: Boolean) {
        repo.autoCapitalizeOptions = value
        _autoCapitalizeOptions.value = value
    }
}
