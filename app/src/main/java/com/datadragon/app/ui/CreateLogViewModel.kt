package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.LogTemplate
import kotlinx.coroutines.launch

class CreateLogViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).logTemplateDao()

    /**
     * Persist a new log template, then invoke [onSaved] on the main thread.
     *
     * Stores the parsed [schemaJson] together with the original [formMarkdown] so
     * the source text can be shown again and included in exports. Templates are
     * write-once and cannot be edited afterwards.
     */
    fun save(
        name: String,
        schemaJson: String,
        formMarkdown: String,
        locked: Boolean,
        allowAppendedNotes: Boolean,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            dao.insert(
                LogTemplate(
                    name = name.trim(),
                    createdAt = System.currentTimeMillis(),
                    schemaJson = schemaJson,
                    formMarkdown = formMarkdown,
                    locked = locked,
                    allowAppendedNotes = allowAppendedNotes,
                )
            )
            onSaved()
        }
    }
}
