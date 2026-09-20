package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerIncrementDirection
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerValues
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Stored clicker time stamps are `HH:mm` (24h); display formatting happens in the UI. */
private val CLICKER_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Backs one Clicker Data Log's screen: the log, its fields, and its cards, plus
 * the card-face actions (add a card, step a tracker, set a value) and log-level
 * actions (delete the log).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClickerLogViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).clickerDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val logId = MutableStateFlow<Long?>(null)

    // Serializes card value writes so concurrent taps/edits never lose an update.
    private val writeMutex = Mutex()

    val log: StateFlow<ClickerLog?> =
        logId.flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeLog(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fields: StateFlow<List<ClickerField>> =
        log.map { it?.let { l -> decodeFields(l.fieldsJson) } ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cards: StateFlow<List<ClickerCard>> =
        logId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeCards(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Point the screen at a log and mark it used (so it rises in the home order). */
    fun start(id: Long) {
        if (logId.value != id) {
            logId.value = id
            viewModelScope.launch { dao.touchLog(id, System.currentTimeMillis()) }
        }
    }

    private fun decodeFields(fieldsJson: String): List<ClickerField> =
        runCatching { json.decodeFromString<List<ClickerField>>(fieldsJson) }.getOrDefault(emptyList())

    /** Add a new card, stamping it per the log's settings and seeding tracker starts. */
    fun addCard() {
        val id = logId.value ?: return
        viewModelScope.launch {
            val current = dao.getLog(id) ?: return@launch
            val defs = decodeFields(current.fieldsJson)
            val now = System.currentTimeMillis()
            val values = defs
                .filter { it.type == ClickerFieldType.CLICK_TRACKER }
                .associate { it.id to it.startingNumber.toString() }
            dao.insertCard(
                ClickerCard(
                    clickerLogId = id,
                    createdAt = now,
                    displayDate = if (current.autoDateStamp) LocalDate.now().toString() else null,
                    displayTime = if (current.autoTimeStamp) LocalTime.now().format(CLICKER_TIME_FORMAT) else null,
                    valuesJson = ClickerValues.encode(values),
                ),
            )
            dao.touchModified(id, now)
        }
    }

    /** Press a tracker's button: apply its signed increment to the latest stored value. */
    fun step(card: ClickerCard, field: ClickerField) {
        mutate(card.id) { values ->
            val start = if (field.type == ClickerFieldType.CLICK_TRACKER) field.startingNumber else 0
            val currentValue = ClickerValues.number(values, field.id) ?: start
            val delta =
                if (field.incrementDirection == ClickerIncrementDirection.ADD) field.incrementAmount
                else -field.incrementAmount
            values[field.id] = (currentValue + delta).toString()
        }
    }

    /** Set a field's raw value directly (inline number entry / manual edit). */
    fun setValue(card: ClickerCard, fieldId: String, raw: String) {
        mutate(card.id) { values ->
            if (raw.isEmpty()) values.remove(fieldId) else values[fieldId] = raw
        }
    }

    /**
     * Serialize a read-modify-write against the card's latest stored values, so
     * rapid taps and concurrent field edits never overwrite one another.
     */
    private fun mutate(cardId: Long, change: (MutableMap<String, String>) -> Unit) {
        viewModelScope.launch {
            writeMutex.withLock {
                val fresh = dao.getCard(cardId) ?: return@withLock
                val values = ClickerValues.decode(fresh.valuesJson).toMutableMap()
                change(values)
                dao.updateCard(fresh.copy(valuesJson = ClickerValues.encode(values)))
                dao.touchModified(fresh.clickerLogId, System.currentTimeMillis())
            }
        }
    }

    fun deleteCard(card: ClickerCard) {
        viewModelScope.launch {
            dao.deleteCard(card)
            dao.touchModified(card.clickerLogId, System.currentTimeMillis())
        }
    }

    fun deleteLog(onDeleted: () -> Unit) {
        val id = logId.value ?: return onDeleted()
        viewModelScope.launch {
            dao.getLog(id)?.let { dao.deleteLogWithCards(it) }
            onDeleted()
        }
    }
}
