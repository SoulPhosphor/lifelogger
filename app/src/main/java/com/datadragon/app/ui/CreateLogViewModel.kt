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
     * The schema is stored empty for now; the Form Markdown parser that fills it
     * is Phase 3. Templates are write-once and cannot be edited afterwards.
     */
    fun save(name: String, description: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            dao.insert(
                LogTemplate(
                    name = name.trim(),
                    description = description.trim(),
                    createdAt = System.currentTimeMillis(),
                    schemaJson = "[]",
                )
            )
            onSaved()
        }
    }
}
