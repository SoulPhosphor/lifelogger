package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerComparison
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.ClickerLog
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Backs the per-card edit screen: load one card and its log's fields, and save edits. */
class ClickerCardEditViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).clickerDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCard(cardId: Long): ClickerCard? = dao.getCard(cardId)

    suspend fun loadLog(logId: Long): ClickerLog? = dao.getLog(logId)

    suspend fun loadFields(logId: Long): List<ClickerField> =
        dao.getLog(logId)?.let { decodeFields(it.fieldsJson) } ?: emptyList()

    private fun decodeFields(fieldsJson: String): List<ClickerField> =
        runCatching { json.decodeFromString<List<ClickerField>>(fieldsJson) }.getOrDefault(emptyList())

    /** Add items to a Comparison field of log [logId] (New Option); [onAdded] gets their ids. */
    fun addComparisonOptions(logId: Long, fieldId: String, additions: List<String>, onAdded: (List<String>) -> Unit) {
        viewModelScope.launch {
            val capitalize = SettingsRepository(getApplication()).autoCapitalizeOptions
            onAdded(ClickerComparison.addOptions(dao, logId, fieldId, additions, capitalize))
        }
    }

    /** Restore a retired Comparison item with its original id; [onDone] runs once it is back. */
    fun restoreComparisonOption(logId: Long, fieldId: String, optionId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            ClickerComparison.restoreOption(dao, logId, fieldId, optionId)
            onDone()
        }
    }

    suspend fun newCard(logId: Long): ClickerCard? {
        val log = dao.getLog(logId) ?: return null
        val now = System.currentTimeMillis()
        val local = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        return ClickerCard(
            clickerLogId = logId, uuid = com.datadragon.app.data.StableUuid.createNew(), createdAt = now,
            displayDate = if (log.autoDateStamp) local.toLocalDate().toString() else null,
            displayTime = if (log.autoTimeStamp) local.toLocalTime().format(com.datadragon.app.data.EntryValues.TIME_STORAGE) else null,
            valuesJson = com.datadragon.app.data.ClickerValues.encode(com.datadragon.app.data.Weekday.clickerDefaults(decodeFields(log.fieldsJson), local.toLocalDate())),
        )
    }

    fun save(card: ClickerCard, onSaved: () -> Unit) {
        viewModelScope.launch {
            val fields = loadFields(card.clickerLogId)
            if (com.datadragon.app.data.Weekday.missingRequired(fields, com.datadragon.app.data.ClickerValues.decode(card.valuesJson)).isNotEmpty()) return@launch
            if (card.id == 0L) dao.insertCard(card) else dao.updateCard(card)
            dao.touchModified(card.clickerLogId, System.currentTimeMillis())
            onSaved()
        }
    }
}
