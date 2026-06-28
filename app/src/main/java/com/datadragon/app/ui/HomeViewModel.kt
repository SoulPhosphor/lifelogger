package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.LogTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val templateDao = AppDatabase.getInstance(app).logTemplateDao()
    private val entryDao = AppDatabase.getInstance(app).logEntryDao()

    /**
     * Home rows: each template paired with its entry count and most-recent entry
     * time, so the screen can show "N entries · last entry …" (docs/UI_SPEC.md §2).
     */
    val logs: StateFlow<List<HomeLog>> =
        combine(templateDao.observeAll(), entryDao.observeSummaries()) { templates, summaries ->
            val byTemplate = summaries.associateBy { it.templateId }
            templates.map { template ->
                val summary = byTemplate[template.id]
                HomeLog(
                    template = template,
                    entryCount = summary?.count ?: 0,
                    lastEntryAt = summary?.lastCreatedAt,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** A Home list row: a log template plus its entry summary. */
data class HomeLog(
    val template: LogTemplate,
    val entryCount: Int,
    val lastEntryAt: String?,
)
