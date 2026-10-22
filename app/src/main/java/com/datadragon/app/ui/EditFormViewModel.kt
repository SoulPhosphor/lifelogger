package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.Calendar
import com.datadragon.app.data.ChoiceDeletion
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.savedComparisonDigits
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.data.AutoFill
import com.datadragon.app.data.hasItemRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Backs "Edit Form": loads a form's current title and fields and writes edits
 * back while preserving stored entry values. Entries store answers by field and
 * option id, so renaming or reordering never needs to touch them.
 */
class EditFormViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val dao = AppDatabase.getInstance(app).logTemplateDao()
    private val entryDao = AppDatabase.getInstance(app).logEntryDao()
    private val calendarDao = AppDatabase.getInstance(app).calendarDao()
    private val json = Json { ignoreUnknownKeys = true }
    private var templateId: Long = -1
    private var calendarsCollected = false

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val _automaticTimestamping = MutableStateFlow(false)
    val automaticTimestamping: StateFlow<Boolean> = _automaticTimestamping

    /** The permanent id of the default sort field, or null for the entry time. */
    private val _sortTimestampFieldId = MutableStateFlow<String?>(null)
    val sortTimestampFieldId: StateFlow<String?> = _sortTimestampFieldId

    private val _sortNewestFirst = MutableStateFlow(true)
    val sortNewestFirst: StateFlow<Boolean> = _sortNewestFirst

    private val _integrateCalendar = MutableStateFlow(false)
    val integrateCalendar: StateFlow<Boolean> = _integrateCalendar

    private val _advancedSearchEnabled = MutableStateFlow(false)
    val advancedSearchEnabled: StateFlow<Boolean> = _advancedSearchEnabled

    private val _allowAdditionsFromForm = MutableStateFlow(false)
    val allowAdditionsFromForm: StateFlow<Boolean> = _allowAdditionsFromForm

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** Set at creation and never changed: the form is permanently locked. */
    private val _neverAllowUnlocking = MutableStateFlow(false)
    val neverAllowUnlocking: StateFlow<Boolean> = _neverAllowUnlocking

    /** Whether the form has at least one saved entry (gates Fill Existing Cards One Time). */
    private val _hasEntries = MutableStateFlow(false)
    val hasEntries: StateFlow<Boolean> = _hasEntries

    /**
     * Per Comparison field id, the most digits any saved number of that field
     * has — the lowest its Max Digits may be set to.
     */
    private val _savedComparisonDigits = MutableStateFlow<Map<String, Int>>(emptyMap())
    val savedComparisonDigits: StateFlow<Map<String, Int>> = _savedComparisonDigits

    /** This form's configured calendars, listed at the bottom of Edit Form. */
    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())
    val calendars: StateFlow<List<Calendar>> = _calendars

    fun load(id: Long) {
        templateId = id
        // Observe the form's calendars once so saves from the config screen show up.
        if (!calendarsCollected) {
            calendarsCollected = true
            viewModelScope.launch {
                calendarDao.observeForTemplate(id).collect { _calendars.value = it }
            }
        }
        viewModelScope.launch {
            val template = dao.getById(id)
            _automaticTimestamping.value = template?.automaticTimestamping ?: false
            _sortTimestampFieldId.value = template?.sortTimestampFieldId
            _sortNewestFirst.value = template?.sortNewestFirst ?: true
            _integrateCalendar.value = template?.integrateCalendar ?: false
            _allowAdditionsFromForm.value = template?.allowAdditionsFromForm ?: false
            _advancedSearchEnabled.value = template?.advancedSearchEnabled ?: false
            _locked.value = template?.locked ?: false
            _neverAllowUnlocking.value = template?.neverAllowUnlocking ?: false
            _hasEntries.value = entryDao.getForTemplateOnce(id).isNotEmpty()
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
            val comparisons = _fields.value.filter { it.type.hasItemRows }
            if (comparisons.isNotEmpty()) {
                val entryValues = entryDao.getForTemplateOnce(id).map { it.valuesJson }
                _savedComparisonDigits.value = comparisons
                    .mapNotNull { field -> field.savedComparisonDigits(entryValues)?.let { field.id to it } }
                    .toMap()
            }
            // Name is the screen's loaded sentinel, so publish it after the
            // settings and fields it gates have been populated.
            _name.value = template?.name
        }
    }

    /**
     * Write back the edited title, schema and form settings. Each field in
     * [convertFieldIds] is also auto-filled across existing entries wherever it
     * is empty (Fill Existing Cards One Time), in the same transaction.
     */
    fun save(
        name: String,
        fields: List<FieldDef>,
        automaticTimestamping: Boolean,
        sortTimestampFieldId: String?,
        sortNewestFirst: Boolean,
        integrateCalendar: Boolean,
        allowAdditionsFromForm: Boolean,
        advancedSearchEnabled: Boolean = false,
        locked: Boolean,
        convertFieldIds: Set<String> = emptySet(),
        onSaved: () -> Unit,
    ) {
        if (templateId < 0) return
        val id = templateId
        viewModelScope.launch {
            val savedName = name.trim()
            val markdown = FormMarkdownGenerator.generate(savedName, fields)
            db.withTransaction {
                dao.updateForm(
                    id,
                    savedName,
                    FormMarkdownParser.encodeFields(fields),
                    markdown,
                    automaticTimestamping,
                    sortTimestampFieldId,
                    sortNewestFirst,
                    integrateCalendar,
                    allowAdditionsFromForm,
                    advancedSearchEnabled,
                )
                dao.setLocked(id, locked)
                AutoFill.convert(entryDao, id, fields, convertFieldIds)
            }
            _locked.value = dao.getById(id)?.locked ?: locked
            _name.value = savedName
            _fields.value = fields
            _automaticTimestamping.value = automaticTimestamping
            _sortTimestampFieldId.value = sortTimestampFieldId
            _sortNewestFirst.value = sortNewestFirst
            _integrateCalendar.value = integrateCalendar
            _allowAdditionsFromForm.value = allowAdditionsFromForm
            _advancedSearchEnabled.value = advancedSearchEnabled
            onSaved()
        }
    }

    /**
     * Permanently delete option [optionId] of field [fieldId], right away and
     * independently of the screen's Save: it leaves the saved form, every entry
     * loses that answer (other answers and the entries themselves stay), and a
     * calendar counting it stops pointing at it — all in one transaction. Any
     * other unsaved edits on the screen are left for Save or Discard as usual.
     * An option that was never saved has nothing stored, so nothing changes.
     */
    fun deleteOptionPermanently(fieldId: String, optionId: String) {
        val id = templateId
        if (id < 0) return
        viewModelScope.launch {
            // Once confirmed, the deletion always runs to the end: leaving the
            // screen (which clears this ViewModel) must not roll it back.
            withContext(NonCancellable) {
                db.withTransaction {
                    val template = dao.getById(id) ?: return@withTransaction
                    val entries = entryDao.getForTemplateOnce(id)
                    val calendars = calendarDao.getForTemplateOnce(id)
                    val result = ChoiceDeletion.fromForm(
                        schemaJson = template.schemaJson,
                        entryValues = entries.map { it.valuesJson },
                        calendarConfigs = calendars.map { it.configJson },
                        fieldId = fieldId,
                        optionId = optionId,
                    ) ?: return@withTransaction
                    if (result.schemaJson != template.schemaJson) {
                        val storedFields = runCatching {
                            json.decodeFromString<List<FieldDef>>(result.schemaJson)
                        }.getOrNull().orEmpty()
                        dao.updateSchema(
                            id,
                            result.schemaJson,
                            FormMarkdownGenerator.generate(template.name, storedFields),
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        val values = result.entryValues[index]
                        if (values != entry.valuesJson) entryDao.update(entry.copy(valuesJson = values))
                    }
                    calendars.forEachIndexed { index, calendar ->
                        val config = result.calendarConfigs[index]
                        if (config != calendar.configJson) calendarDao.update(calendar.copy(configJson = config))
                    }
                }
            }
        }
    }
}
