package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerLog
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Backs the Clicker Data Log setup screen — creating a new log, or editing an
 * existing series' title, display settings, and tracker/field definitions.
 *
 * Field definitions live in the log's `fieldsJson`; each card keeps its own
 * values for those fields, so editing the setup changes the whole series.
 */
class ClickerSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).clickerDao()
    private val json = Json { ignoreUnknownKeys = true }

    /** The existing log to edit, or null when creating a new one. */
    suspend fun load(id: Long): ClickerLog? = dao.getLog(id)

    /** Parse a log's stored field definitions. */
    fun decodeFields(fieldsJson: String): List<ClickerField> =
        runCatching { json.decodeFromString<List<ClickerField>>(fieldsJson) }.getOrDefault(emptyList())

    /**
     * Save the setup: insert a new log, or update the existing one named by
     * [existingId]. Either way the log is bumped to the top of the recently-used
     * order. [onSaved] runs on the main thread once the write completes.
     */
    fun save(
        existingId: Long?,
        title: String,
        displayOnlyClickerDateTime: Boolean,
        autoDateStamp: Boolean,
        autoTimeStamp: Boolean,
        allowFollowUp: Boolean,
        fields: List<ClickerField>,
        onSaved: () -> Unit,
    ) {
        val encoded = json.encodeToString(fields)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existingId == null) {
                dao.insertLog(
                    ClickerLog(
                        title = title,
                        createdAt = now,
                        lastAccessedAt = now,
                        fieldsJson = encoded,
                        displayOnlyClickerDateTime = displayOnlyClickerDateTime,
                        autoDateStamp = autoDateStamp,
                        autoTimeStamp = autoTimeStamp,
                        allowFollowUp = allowFollowUp,
                    ),
                )
            } else {
                val existing = dao.getLog(existingId) ?: return@launch
                dao.updateLog(
                    existing.copy(
                        title = title,
                        fieldsJson = encoded,
                        displayOnlyClickerDateTime = displayOnlyClickerDateTime,
                        autoDateStamp = autoDateStamp,
                        autoTimeStamp = autoTimeStamp,
                        allowFollowUp = allowFollowUp,
                        lastAccessedAt = now,
                    ),
                )
            }
            onSaved()
        }
    }
}
