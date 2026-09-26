package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerStatisticsCodec
import com.datadragon.app.data.ClickerStatisticsConfig
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

/**
 * Backs a Clicker grouping's Statistics page and its Statistics Designer: the
 * grouping, its fields and logs, and the saved designer choices. Every designer
 * change saves at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClickerStatisticsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).clickerDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val logId = MutableStateFlow<Long?>(null)
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

    val config: StateFlow<ClickerStatisticsConfig> =
        log.map { ClickerStatisticsCodec.decode(it?.statisticsJson.orEmpty()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClickerStatisticsConfig())

    fun start(id: Long) {
        logId.value = id
    }

    /** Apply [change] to the latest saved choices and save the result. */
    fun update(change: (ClickerStatisticsConfig) -> ClickerStatisticsConfig) {
        val id = logId.value ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                val current = dao.getLog(id) ?: return@withLock
                val updated = change(ClickerStatisticsCodec.decode(current.statisticsJson))
                dao.setStatistics(id, ClickerStatisticsCodec.encode(updated))
            }
        }
    }

    private fun decodeFields(fieldsJson: String): List<ClickerField> =
        runCatching { json.decodeFromString<List<ClickerField>>(fieldsJson) }.getOrDefault(emptyList())
}
