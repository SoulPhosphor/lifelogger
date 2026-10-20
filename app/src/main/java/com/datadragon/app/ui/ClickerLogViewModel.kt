package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerCardOrder
import com.datadragon.app.data.ClickerComparison
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ComparisonRow
import com.datadragon.app.data.comparisonDef
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerIncrementDirection
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerValues
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.StableUuid
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
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
    private val settings = SettingsRepository(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val logId = MutableStateFlow<Long?>(null)

    val log: StateFlow<ClickerLog?> =
        logId.flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeLog(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fields: StateFlow<List<ClickerField>> =
        log.map { it?.let { l -> decodeFields(l.fieldsJson) } ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The sort bar's direction pick. Null until the user changes it, which means
    // the default (newest first); it dies with this screen, as it does in Forms.
    private val _pickedNewestFirst = MutableStateFlow<Boolean?>(null)

    /** The direction in force: newest first until the user picks. */
    val newestFirst: StateFlow<Boolean> =
        _pickedNewestFirst.map { it ?: true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The log's cards, ordered by their shown date/time in the chosen direction. */
    val cards: StateFlow<List<ClickerCard>> =
        combine(
            logId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeCards(id) },
            _pickedNewestFirst,
        ) { stored, picked -> ClickerCardOrder.sort(stored, newestFirst = picked ?: true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Run the cards newest first, or oldest first. */
    fun selectNewestFirst(value: Boolean) {
        _pickedNewestFirst.value = value
    }

    // Bumped on each toggle so [followUpShown] re-reads the stored state.
    private val followUpToggles = MutableStateFlow(0)

    /**
     * Whether the cards on this Main screen show their Follow-Up Notes box. Only
     * when the grouping allows Follow-Up Notes; otherwise the stored state is
     * kept but ignored. Stays the way it was left, per grouping.
     */
    val followUpShown: StateFlow<Boolean> =
        combine(log, followUpToggles) { l, _ ->
            l != null && l.allowFollowUp && settings.isClickerFollowUpShown(l.uuid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The top-bar Edit Note / Article Shortcut button: show or hide Follow-Up Notes on the cards. */
    fun toggleFollowUpShown() {
        val l = log.value ?: return
        settings.setClickerFollowUpShown(l.uuid, !settings.isClickerFollowUpShown(l.uuid))
        followUpToggles.value += 1
    }

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
                    uuid = StableUuid.createNew(),
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

    /**
     * Save a Comparison field's rows from the card face. A row with no item is
     * not stored; with clicker counting an item's untouched Starting Number is.
     */
    fun setComparison(card: ClickerCard, field: ClickerField, rows: List<ComparisonRow>) {
        val stored = ClickerComparison.stored(field.comparisonDef(), rows)
        mutate(card.id) { values ->
            if (stored == null) values.remove(field.id) else values[field.id] = stored
        }
    }

    /** Add items to a Comparison field from a card (New Option); [onAdded] gets their ids. */
    fun addComparisonOptions(fieldId: String, additions: List<String>, onAdded: (List<String>) -> Unit) {
        val id = logId.value ?: return
        viewModelScope.launch {
            val added = ClickerComparison.addOptions(dao, id, fieldId, additions, settings.autoCapitalizeOptions)
            onAdded(added)
        }
    }

    /** Restore a retired Comparison item with its original id; [onDone] runs once it is back. */
    fun restoreComparisonOption(fieldId: String, optionId: String, onDone: () -> Unit) {
        val id = logId.value ?: return
        viewModelScope.launch {
            ClickerComparison.restoreOption(dao, id, fieldId, optionId)
            onDone()
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
     * rapid taps and concurrent field edits never overwrite one another. Runs in
     * a process-lifetime scope so the last keystrokes typed on a card face are
     * still saved when the user leaves the screen right away.
     */
    private fun mutate(cardId: Long, change: (MutableMap<String, String>) -> Unit) {
        val job = writeScope.launch(start = CoroutineStart.LAZY) {
            writeMutex.withLock {
                val fresh = dao.getCard(cardId) ?: return@withLock
                val values = ClickerValues.decode(fresh.valuesJson).toMutableMap()
                change(values)
                dao.updateCard(fresh.copy(valuesJson = ClickerValues.encode(values)))
                dao.touchModified(fresh.clickerLogId, System.currentTimeMillis())
            }
        }
        synchronized(pendingWrites) { pendingWrites.add(job) }
        job.invokeOnCompletion { synchronized(pendingWrites) { pendingWrites.remove(job) } }
        job.start()
    }

    /**
     * The grouping and its cards as stored once every queued card write has
     * finished, so an export includes the last keystrokes typed on a card face.
     */
    suspend fun exportSnapshot(): Pair<ClickerLog, List<ClickerCard>>? {
        val waiting = synchronized(pendingWrites) { pendingWrites.toList() }
        waiting.joinAll()
        val id = logId.value ?: return null
        val current = dao.getLog(id) ?: return null
        return current to dao.getCardsForLog(id)
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

    companion object {
        // Process-lifetime scope so card writes queued on the way out still finish
        // after the ViewModel (and its viewModelScope) is cleared.
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Shared by every instance so a reopened screen's writes queue behind any
        // still finishing from the last one.
        private val writeMutex = Mutex()

        // Card writes still queued or running, so an export can wait for them.
        private val pendingWrites = mutableSetOf<Job>()
    }
}
