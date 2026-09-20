package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Backs the per-card edit screen: load one card and its log's fields, and save edits. */
class ClickerCardEditViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).clickerDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCard(cardId: Long): ClickerCard? = dao.getCard(cardId)

    suspend fun loadFields(logId: Long): List<ClickerField> =
        dao.getLog(logId)?.let { decodeFields(it.fieldsJson) } ?: emptyList()

    private fun decodeFields(fieldsJson: String): List<ClickerField> =
        runCatching { json.decodeFromString<List<ClickerField>>(fieldsJson) }.getOrDefault(emptyList())

    fun save(card: ClickerCard, onSaved: () -> Unit) {
        viewModelScope.launch {
            dao.updateCard(card)
            dao.touchLog(card.clickerLogId, System.currentTimeMillis())
            onSaved()
        }
    }
}
