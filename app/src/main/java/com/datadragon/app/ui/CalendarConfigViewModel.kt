package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.Calendar
import com.datadragon.app.data.CalendarConfig
import com.datadragon.app.data.CalendarConfigCodec
import com.datadragon.app.data.CalendarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The values the Edit Calendar screen seeds its editable fields from: the chosen
 * type (null when configuring a brand-new calendar and nothing is picked yet),
 * the label, and the description.
 */
data class CalendarConfigInitial(
    val type: CalendarType?,
    val label: String,
    val description: String,
    val config: CalendarConfig,
)

/**
 * Backs the single Edit Calendar screen. Loads one calendar for editing (or
 * starts a new one for a form) and writes it back.
 *
 * The type-specific configuration (colors now; data source and calculation rule
 * in later phases) travels as a [CalendarConfig] the screen owns end to end:
 * [load] decodes it from the stored [Calendar.configJson], and [save] re-encodes
 * the whole config the screen hands back, so nothing is dropped on a round-trip.
 */
class CalendarConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val calendarDao = AppDatabase.getInstance(app).calendarDao()

    private var templateId: Long = -1

    /** Null while configuring a new calendar; set once one has been saved/loaded. */
    private var calendarId: Long? = null
    private var loadedPosition: Int = 0

    private val _initial = MutableStateFlow<CalendarConfigInitial?>(null)
    val initial: StateFlow<CalendarConfigInitial?> = _initial

    fun load(templateId: Long, calendarId: Long?) {
        this.templateId = templateId
        this.calendarId = calendarId
        viewModelScope.launch {
            val existing = calendarId?.let { calendarDao.getById(it) }
            if (existing != null) {
                loadedPosition = existing.position
                _initial.value = CalendarConfigInitial(
                    type = CalendarType.fromToken(existing.type),
                    label = existing.label,
                    description = existing.description,
                    config = CalendarConfigCodec.decode(existing.configJson),
                )
            } else {
                _initial.value = CalendarConfigInitial(
                    type = null,
                    label = "",
                    description = "",
                    config = CalendarConfig(),
                )
            }
        }
    }

    /**
     * Persist the calendar. Inserts a new row (appended after the form's existing
     * calendars) the first time, then updates that same row on later saves.
     * [onSaved] receives the saved calendar's id.
     */
    fun save(
        type: CalendarType,
        label: String,
        description: String,
        config: CalendarConfig,
        onSaved: (Long) -> Unit,
    ) {
        if (templateId < 0) return
        val configJson = CalendarConfigCodec.encode(config)
        viewModelScope.launch {
            val id = calendarId
            if (id == null) {
                val position = (calendarDao.getForTemplateOnce(templateId)
                    .maxOfOrNull { it.position } ?: -1) + 1
                val newId = calendarDao.insert(
                    Calendar(
                        templateId = templateId,
                        position = position,
                        type = type.token,
                        label = label.trim(),
                        description = description,
                        configJson = configJson,
                    ),
                )
                calendarId = newId
                loadedPosition = position
                onSaved(newId)
            } else {
                calendarDao.update(
                    Calendar(
                        id = id,
                        templateId = templateId,
                        position = loadedPosition,
                        type = type.token,
                        label = label.trim(),
                        description = description,
                        configJson = configJson,
                    ),
                )
                onSaved(id)
            }
        }
    }

    /**
     * Reset for "Add Another Calendar": the next Save inserts a brand-new
     * calendar instead of updating the one just saved.
     */
    fun prepareNew() {
        calendarId = null
        loadedPosition = 0
        _initial.value = CalendarConfigInitial(
            type = null,
            label = "",
            description = "",
            config = CalendarConfig(),
        )
    }
}
