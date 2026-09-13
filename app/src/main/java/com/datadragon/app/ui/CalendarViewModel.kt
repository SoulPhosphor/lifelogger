package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the calendar viewing screen: the form's name (for the "[Form Name]
 * Calendar" header) and its configured calendars (for the View Calendar dropdown
 * and the calendar being displayed).
 *
 * The day-coloring calculation and the day's logs are added by later phases.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val templateDao = db.logTemplateDao()
    private val calendarDao = db.calendarDao()

    private var loaded = false

    private val _formName = MutableStateFlow<String?>(null)
    val formName: StateFlow<String?> = _formName

    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())
    val calendars: StateFlow<List<Calendar>> = _calendars

    fun load(templateId: Long) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _formName.value = templateDao.getById(templateId)?.name
        }
        viewModelScope.launch {
            calendarDao.observeForTemplate(templateId).collect { _calendars.value = it }
        }
    }
}
