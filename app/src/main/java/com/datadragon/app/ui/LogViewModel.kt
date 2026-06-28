package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.LogTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the single-log (entry list) screen. In Phase 2 it loads the template so
 * the screen can show the real log name. Entries arrive in Phase 4.
 */
class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).logTemplateDao()

    private val _template = MutableStateFlow<LogTemplate?>(null)
    val template: StateFlow<LogTemplate?> = _template

    fun load(id: Long) {
        viewModelScope.launch {
            _template.value = dao.getById(id)
        }
    }
}
