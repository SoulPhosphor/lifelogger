package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.CelebrationIcon
import com.datadragon.app.data.DAILY_LIST_SUB_ITEM_INDENT
import com.datadragon.app.data.DailyList
import com.datadragon.app.data.DailyListItem
import com.datadragon.app.data.DailyListLogic
import com.datadragon.app.data.DailyListRepository
import com.datadragon.app.data.SettingsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The full month spelled out: "September 18, 2026". */
val DAILY_LIST_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

/**
 * One row in the editor, carrying a stable local id across persistence and a
 * stable [uuid] — assigned when the row is created, so the autosave can
 * recognize its own row in the database and never insert it twice.
 */
data class DailyListEditorRow(
    val localId: Long,
    val dbId: Long?,
    val text: String,
    val completed: Boolean,
    val indent: Int,
    val sourceUuid: String?,
    val uuid: String = java.util.UUID.randomUUID().toString(),
)

/**
 * Backs the Daily List main view and the Daily List editor.
 *
 * The main view shows every saved date card (read-only display; open the card
 * to edit). The editor edits one date's card with the ordinary List
 * interaction model: autosave on every change, a fresh date becoming a saved
 * card only when its first real non-blank item exists (or when automatic
 * renewal carries items in — those carried items are real items for the day
 * and create the card without waiting for the user to type).
 */
class DailyListViewModel(
    app: Application,
    private val today: () -> LocalDate = { LocalDate.now() },
) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = DailyListRepository(db)
    private val settings = SettingsRepository(app)

    // --- Main view ------------------------------------------------------------

    private val _cards = MutableStateFlow<List<DailyList>>(emptyList())
    val cards: StateFlow<List<DailyList>> = _cards

    private val _newestFirst = MutableStateFlow(true)
    val newestFirst: StateFlow<Boolean> = _newestFirst

    /** Star filter: show only favorited cards. Ephemeral, like the Form star filter. */
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly

    /** The display-only per-card item lists after the visibility toggles. */
    private val _cardItems = MutableStateFlow<Map<Long, List<DailyListItem>>>(emptyMap())
    val cardItems: StateFlow<Map<Long, List<DailyListItem>>> = _cardItems

    // --- Editor ----------------------------------------------------------------

    private val _editorCard = MutableStateFlow<DailyList?>(null)
    val editorCard: StateFlow<DailyList?> = _editorCard

    /** The date being edited, even while the card is still unsaved (a fresh date). */
    private val _editorDate = MutableStateFlow<LocalDate?>(null)
    val editorDate: StateFlow<LocalDate?> = _editorDate

    private val _editorTitle = MutableStateFlow("")
    val editorTitle: StateFlow<String> = _editorTitle

    private val _editorRows = MutableStateFlow<List<DailyListEditorRow>>(emptyList())
    val editorRows: StateFlow<List<DailyListEditorRow>> = _editorRows

    /** True when the editor shows an existing saved card (not a fresh date). */
    private val _editorIsSaved = MutableStateFlow(false)
    val editorIsSaved: StateFlow<Boolean> = _editorIsSaved

    private var nextTempId: Long = -1

    private val _celebrationEnabled = MutableStateFlow(settings.dailyListCelebrationEnabled)
    val celebrationEnabled: StateFlow<Boolean> = _celebrationEnabled

    private val _celebrationIcon = MutableStateFlow(settings.dailyListCelebrationIcon)
    val celebrationIcon: StateFlow<CelebrationIcon> = _celebrationIcon

    private val _allowTitle = MutableStateFlow(settings.dailyListAllowTitle)
    val allowTitle: StateFlow<Boolean> = _allowTitle

    private val _showCompleted = MutableStateFlow(settings.dailyListShowCompleted)
    val showCompleted: StateFlow<Boolean> = _showCompleted

    private val _showCurrentUnfinished = MutableStateFlow(settings.dailyListShowCurrentUnfinished)
    val showCurrentUnfinished: StateFlow<Boolean> = _showCurrentUnfinished

    private val _showPastUnfinished = MutableStateFlow(settings.dailyListShowPastUnfinished)
    val showPastUnfinished: StateFlow<Boolean> = _showPastUnfinished

    private val _heading = MutableStateFlow(settings.dailyListHeading)
    val heading: StateFlow<String> = _heading

    private val _autoRenew = MutableStateFlow(settings.dailyListAutoRenew)
    val autoRenew: StateFlow<Boolean> = _autoRenew

    private val _protectFavorited = MutableStateFlow(settings.dailyListProtectFavorited)
    val protectFavorited: StateFlow<Boolean> = _protectFavorited

    private val _autoReopen = MutableStateFlow(settings.dailyListAutoReopen)
    val autoReopen: StateFlow<Boolean> = _autoReopen

    /** One-shot signal asking the Home bar to show the date picker. */
    private val _datePickerRequested = MutableStateFlow(0)
    val datePickerRequested: StateFlow<Int> = _datePickerRequested

    fun requestDatePicker() {
        _datePickerRequested.value += 1
    }

    private val _retentionRaw = MutableStateFlow(settings.dailyListRetentionRaw)
    val retentionRaw: StateFlow<String> = _retentionRaw

    fun setHeading(value: String) {
        settings.dailyListHeading = value
        _heading.value = value
    }

    fun setAutoRenew(value: Boolean) {
        settings.dailyListAutoRenew = value
        _autoRenew.value = value
    }

    fun setShowCompleted(value: Boolean) {
        settings.dailyListShowCompleted = value
        _showCompleted.value = value
        refresh()
    }

    fun setShowCurrentUnfinished(value: Boolean) {
        settings.dailyListShowCurrentUnfinished = value
        _showCurrentUnfinished.value = value
        refresh()
    }

    fun setShowPastUnfinished(value: Boolean) {
        settings.dailyListShowPastUnfinished = value
        _showPastUnfinished.value = value
        refresh()
    }

    fun setCelebrationEnabled(value: Boolean) {
        settings.dailyListCelebrationEnabled = value
        _celebrationEnabled.value = value
    }

    fun setCelebrationIcon(value: CelebrationIcon) {
        settings.dailyListCelebrationIcon = value
        _celebrationIcon.value = value
    }

    fun setProtectFavorited(value: Boolean) {
        settings.dailyListProtectFavorited = value
        _protectFavorited.value = value
    }

    fun setAutoReopen(value: Boolean) {
        settings.dailyListAutoReopen = value
        _autoReopen.value = value
    }

    fun setAllowTitle(value: Boolean) {
        settings.dailyListAllowTitle = value
        _allowTitle.value = value
    }

    /** Digits only, at most three; blank stays blank (disabled). */
    fun setRetentionRaw(value: String) {
        val filtered = value.filter { it.isDigit() }.take(3)
        settings.dailyListRetentionRaw = filtered
        _retentionRaw.value = filtered
    }

    /** Reload every card and its display-filtered items from the database. */
    fun refresh() {
        viewModelScope.launch {
            val all = db.dailyListDao().getAllDailyListsOnce()
            _cards.value = if (_newestFirst.value) {
                all.sortedByDescending { it.date }
            } else {
                all.sortedBy { it.date }
            }
            loadCardItems(all)
        }
    }

    private suspend fun loadCardItems(all: List<DailyList>) {
        val todayValue = today()
        val result = mutableMapOf<Long, List<DailyListItem>>()
        for (card in all) {
            val items = db.dailyListDao().getItemsOnce(card.id)
            val showUnfinished = when {
                card.date.isAfter(todayValue) -> true // future cards untouched by past toggles
                card.date == todayValue -> _showCurrentUnfinished.value
                else -> _showPastUnfinished.value
            }
            result[card.id] = items.filter { item ->
                if (item.completed) _showCompleted.value else showUnfinished
            }
        }
        _cardItems.value = result
    }

    fun setNewestFirst(newestFirst: Boolean) {
        _newestFirst.value = newestFirst
        _cards.value = if (newestFirst) {
            _cards.value.sortedByDescending { it.date }
        } else {
            _cards.value.sortedBy { it.date }
        }
    }

    fun setShowFavoritesOnly(show: Boolean) {
        _showFavoritesOnly.value = show
    }

    /** The cards currently on screen, honoring the favorite filter. */
    val visibleCards: List<DailyList>
        get() = if (_showFavoritesOnly.value) _cards.value.filter { it.favorited } else _cards.value

    /** The items currently visible on a card, after the display-only toggles. */
    fun visibleCardItems(cardId: Long): List<DailyListItem> = _cardItems.value[cardId].orEmpty()

    /**
     * Whether [card] qualifies for the celebration icon right now: the
     * celebration toggle is on, the day genuinely earned completion, and every
     * current item is still completed. A day with zero tasks never qualifies.
     */
    fun celebrationForCard(card: DailyList, items: List<DailyListItem>): Boolean {
        if (!_celebrationEnabled.value) return false
        return card.genuinelyCompleted && items.isNotEmpty() && items.all { it.completed }
    }

    // --- Opening / creating ----------------------------------------------------

    /**
     * Event Note: open today. An existing card opens directly; a fresh date
     * opens an unsaved editor — nothing is persisted merely by opening.
     */
    fun openToday() {
        val todayValue = today()
        viewModelScope.launch {
            val existing = repo.getByDate(todayValue)
            if (existing != null) {
                openEditor(existing.id, todayValue)
            } else {
                openFresh(todayValue)
            }
        }
    }

    /** Open the editor for a saved card. */
    fun openCard(cardId: Long) {
        viewModelScope.launch {
            val card = repo.getDailyList(cardId) ?: return@launch
            openEditor(card.id, card.date)
        }
    }

    /** Whether a saved card already exists for [date] — the picker's check. */
    suspend fun hasCardForDate(date: LocalDate): Boolean = repo.getByDate(date) != null

    /**
     * The editor route's entry: open the saved card for [date], or start a
     * fresh unsaved editor for it. The chosen date becomes the card's actual
     * date; it is never a secondary field. Any past, present, or future date
     * is allowed.
     */
    fun openForEditorDate(date: LocalDate) {
        viewModelScope.launch {
            val existing = repo.getByDate(date)
            if (existing != null) {
                openEditor(existing.id, existing.date)
            } else {
                openFresh(date)
            }
        }
    }

    /** Open the existing card for [date] (after the duplicate-date dialog). */
    fun openExistingForDate(date: LocalDate) {
        viewModelScope.launch {
            val existing = repo.getByDate(date) ?: return@launch
            openEditor(existing.id, existing.date)
        }
    }

    private suspend fun openEditor(cardId: Long, date: LocalDate) {
        val card = repo.getDailyList(cardId) ?: return
        _editorCard.value = card
        _editorDate.value = date
        _editorIsSaved.value = true
        _editorTitle.value = card.title
        val items = db.dailyListDao().getItemsOnce(cardId)
        _editorRows.value = items.map {
            DailyListEditorRow(it.id, it.id, it.text, it.completed, it.indent, it.sourceUuid, it.uuid)
        }
        // One-time automatic renewal for the current day's card runs when the
        // card is opened on its own date — never before, never twice.
        maybeRunAutomaticRenewal(card)
    }

    private suspend fun openFresh(date: LocalDate) {
        _editorCard.value = null
        _editorDate.value = date
        _editorIsSaved.value = false
        _editorTitle.value = ""
        _editorRows.value = emptyList()
        maybeRunAutomaticRenewalForFresh(date)
    }

    /**
     * Automatic renewal on a fresh current-day editor: the carried items are
     * real items for the day and create the card without waiting for the user
     * to type. Never runs for a past date, or a future date (not due yet). A
     * day whose source had nothing to carry stays a fresh editor — the day
     * still becomes a saved card with its first typed item.
     */
    private suspend fun maybeRunAutomaticRenewalForFresh(date: LocalDate) {
        if (!_autoRenew.value || date != today()) return
        val source = db.dailyListDao().getPreviousBefore(date) ?: return
        val sourceItems = db.dailyListDao().getItemsOnce(source.id)
            .filter { it.text.isNotBlank() && !it.completed }
        if (sourceItems.isEmpty()) return

        val card = repo.createForDate(date, System.currentTimeMillis()) ?: return
        carryInto(card, source, sourceItems)
        // The once-only marker: this pass was the fresh day's one-time renewal.
        repo.markRenewalRun(card.id, date)
        // Reopen as a saved editor showing the renewed rows.
        openEditor(card.id, date)
    }

    private suspend fun maybeRunAutomaticRenewal(card: DailyList) {
        if (!DailyListLogic.automaticRenewalDue(card, today(), _autoRenew.value)) return
        // The one-time marker is set before the source is consulted, so the
        // source is never rechecked after this pass, whatever it held.
        repo.markRenewalRun(card.id, card.date)
        val source = db.dailyListDao().getPreviousBefore(card.date)
        if (source != null) {
            val sourceItems = db.dailyListDao().getItemsOnce(source.id)
                .filter { it.text.isNotBlank() && !it.completed }
            if (sourceItems.isNotEmpty()) {
                carryInto(card, source, sourceItems)
                // The editor is already open on this card (openEditor loaded it
                // just before this ran): show the carried rows immediately.
                _editorRows.value = db.dailyListDao().getItemsOnce(card.id).map {
                    DailyListEditorRow(it.id, it.id, it.text, it.completed, it.indent, it.sourceUuid, it.uuid)
                }
            }
        }
        if (_editorCard.value?.id != card.id) openEditor(card.id, card.date)
    }

    /** Append [sourceItems]' unfinished rows onto [card], honoring prior carries. */
    private suspend fun carryInto(
        card: DailyList,
        source: DailyList,
        sourceItems: List<DailyListItem>,
    ) {
        val current = db.dailyListDao().getItemsOnce(card.id)
        val eligible = DailyListLogic.filterAlreadyCarried(sourceItems, source.uuid, current)
        if (eligible.isEmpty()) return
        var position = current.size
        for (renewed in DailyListLogic.renewedItems(eligible)) {
            db.dailyListDao().insertItem(
                DailyListItem(
                    dailyListId = card.id,
                    text = renewed.text,
                    indent = renewed.indent,
                    position = position++,
                    sourceUuid = DailyListLogic.sourceIdentityOf(source.uuid, renewed.sourceItem),
                ),
            )
        }
    }

    /** Manual Cycle: repeatable; never duplicates already-carried rows. */
    fun runManualRenewal() {
        val date = _editorDate.value ?: return
        viewModelScope.launch {
            val card = _editorCard.value ?: repo.getByDate(date) ?: return@launch
            val source = db.dailyListDao().getPreviousBefore(card.date) ?: return@launch
            val sourceItems = db.dailyListDao().getItemsOnce(source.id)
                .filter { it.text.isNotBlank() && !it.completed }
            if (sourceItems.isEmpty()) return@launch
            carryInto(card, source, sourceItems)
            openEditor(card.id, card.date)
        }
    }

    // --- Editing ----------------------------------------------------------------

    /** The editor's heading: the custom value, or the "Daily Tasks" fallback. */
    val editorHeading: String
        get() = _heading.value.ifBlank { "Daily Tasks" }

    fun setEditorTitle(text: String) {
        _editorTitle.value = text
        val card = _editorCard.value ?: return
        viewModelScope.launch { repo.setTitle(card.id, text) }
    }

    /** Adds a new top-level item row (locally; typing it creates the card). */
    fun addItem(onAdded: (Long) -> Unit = {}) {
        insertRowLocally(indent = 0, afterLocalId = null, onAdded = onAdded)
    }

    /** The per-row sub-item `+` — one nesting level, as on ordinary Lists. */
    fun addSubItem(afterLocalId: Long, onAdded: (Long) -> Unit = {}) {
        insertRowLocally(
            indent = DAILY_LIST_SUB_ITEM_INDENT,
            afterLocalId = afterLocalId,
            onAdded = onAdded,
        )
    }

    /** A real item: any row the user has typed something into. */
    private fun hasRealItem(rows: List<DailyListEditorRow>): Boolean =
        rows.any { it.text.isNotBlank() }

    /** Appends/inserts a blank row in the editor only — nothing is persisted yet. */
    private fun insertRowLocally(indent: Int, afterLocalId: Long?, onAdded: (Long) -> Unit) {
        val localId = nextTempId--
        val row = DailyListEditorRow(
            localId = localId, dbId = null, text = "", completed = false,
            indent = indent, sourceUuid = null,
        )
        val rows = _editorRows.value.toMutableList()
        val insertAt = if (afterLocalId == null) {
            rows.size
        } else {
            (rows.indexOfFirst { it.localId == afterLocalId } + 1).coerceIn(0, rows.size)
        }
        rows.add(insertAt, row)
        _editorRows.value = rows
        onAdded(localId)
    }

    fun updateText(localId: Long, text: String) {
        updateRow(localId) { it.copy(text = text) }
    }

    fun setCompleted(localId: Long, completed: Boolean) {
        updateRow(localId) { it.copy(completed = completed) }
    }

    private fun updateRow(localId: Long, transform: (DailyListEditorRow) -> DailyListEditorRow) {
        _editorRows.value = _editorRows.value.map { if (it.localId == localId) transform(it) else it }
        viewModelScope.launch { saveAfterEdit() }
    }

    /**
     * Autosave after any row change. On a fresh date, the first typed (real,
     * non-blank) item creates the saved card — a blank row alone never does.
     * When the card already exists, rows persist and trailing blank rows are
     * dropped. All card creation, item inserts and updates run inside the
     * repository's transaction, so a rapid text change can never insert the
     * same row twice.
     */
    private suspend fun saveAfterEdit() {
        val date = _editorDate.value ?: return
        val rows = _editorRows.value
        if (_editorCard.value == null) {
            if (!hasRealItem(rows)) return // still just a fresh date
            val firstReal = rows.first { it.text.isNotBlank() }
            val created = repo.createWithFirstItem(
                date = date,
                now = System.currentTimeMillis(),
                typed = DailyListItem(
                    dailyListId = 0, // replaced inside the repository's transaction
                    uuid = firstReal.uuid,
                    text = firstReal.text,
                    completed = firstReal.completed,
                    indent = firstReal.indent,
                    position = 0,
                    sourceUuid = firstReal.sourceUuid,
                ),
            ) ?: return // lost the creation race; the next change retries
            _editorCard.value = created
            _editorIsSaved.value = true
            persistStructure()
            updateCompletionState(created.id)
            return
        }

        val card = _editorCard.value!!
        // The card vanished under us (whole-card deletion elsewhere): drop back
        // to a fresh editor for the same date instead of resurrecting it.
        if (repo.getDailyList(card.id) == null) {
            resetEditorToFresh(date)
            return
        }
        persistStructure()
        // A row the user left completely blank never stays stored data. The
        // card itself stays saved — deleting a saved Daily List is the explicit,
        // confirmed Delete action, never a side effect of editing. The editor
        // row the user is looking at stays on screen; it is only detached from
        // its deleted database row, so typing into it stores it anew.
        if (rows.any { it.dbId != null && it.text.isBlank() }) {
            db.dailyListDao().deleteBlankItems(card.id)
            val surviving = db.dailyListDao().getItemsOnce(card.id).map { it.id }.toSet()
            _editorRows.value = _editorRows.value.map { row ->
                if (row.dbId != null && row.dbId !in surviving) row.copy(dbId = null) else row
            }
        }
        updateCompletionState(card.id)
    }

    /** Turn the editor back into a fresh unsaved editor for [date]. */
    private fun resetEditorToFresh(date: LocalDate) {
        _editorCard.value = null
        _editorIsSaved.value = false
        _editorDate.value = date
        _editorRows.value = emptyList()
    }

    fun deleteItem(localId: Long) {
        val removed = _editorRows.value.firstOrNull { it.localId == localId }
        _editorRows.value = _editorRows.value.filterNot { it.localId == localId }
        viewModelScope.launch {
            val card = _editorCard.value ?: return@launch
            removed?.dbId?.let { db.dailyListDao().deleteItem(it) }
            persistStructure()
            // Emptying the card keeps the saved day (zero tasks never shows the
            // celebration icon); only the confirmed Delete removes a card.
            updateCompletionState(card.id)
        }
    }

    fun reorder(orderedLocalIds: List<Long>) {
        val byId = _editorRows.value.associateBy { it.localId }
        _editorRows.value = orderedLocalIds.mapNotNull { byId[it] }
        viewModelScope.launch { persistStructure() }
    }

    /** Persist the current rows' text/completion/indent/position (create-or-update). */
    private suspend fun persistStructure() {
        val card = _editorCard.value ?: return
        _editorRows.value.forEachIndexed { index, row ->
            if (row.dbId == null) {
                // A blank row is never stored data — it exists only in the
                // editor until the user types into it.
                if (row.text.isBlank()) return@forEachIndexed
                // Insert only if a concurrent save did not create this row
                // already — a rapid second change must not duplicate it.
                val existing = db.dailyListDao().getItemsOnce(card.id)
                    .firstOrNull { it.uuid == row.uuid }
                if (existing != null) {
                    _editorRows.value = _editorRows.value.map {
                        if (it.localId == row.localId) it.copy(dbId = existing.id) else it
                    }
                } else {
                    val newId = db.dailyListDao().insertItem(
                        DailyListItem(
                            uuid = row.uuid,
                            dailyListId = card.id,
                            text = row.text,
                            completed = row.completed,
                            indent = row.indent,
                            position = index,
                            sourceUuid = row.sourceUuid,
                        ),
                    )
                    _editorRows.value = _editorRows.value.map {
                        if (it.localId == row.localId) it.copy(dbId = newId) else it
                    }
                }
            } else {
                db.dailyListDao().updateItem(
                    DailyListItem(
                        id = row.dbId,
                        dailyListId = card.id,
                        text = row.text,
                        completed = row.completed,
                        indent = row.indent,
                        position = index,
                        sourceUuid = row.sourceUuid,
                    ),
                )
            }
        }
    }

    /**
     * Completion-achievement history: when every task on the card is completed
     * (and there is at least one), the day has genuinely achieved
     * all-completed status. Un-completing clears the flag (an already-earned
     * day can re-earn it later by completing everything again); a
     * cleanup-blocked card's flag is never set — destructive cleanup has
     * removed the evidence, so it can no longer newly earn the celebration.
     */
    private suspend fun updateCompletionState(cardId: Long) {
        val items = db.dailyListDao().getItemsOnce(cardId)
        val card = repo.getDailyList(cardId) ?: return
        val allCompleted = DailyListLogic.isAllCompleted(items)
        if (allCompleted && !card.genuinelyCompleted && !card.completionBlockedByCleanup) {
            repo.setGenuinelyCompleted(cardId, earned = true)
        } else if (!allCompleted && card.genuinelyCompleted) {
            repo.setGenuinelyCompleted(cardId, earned = false)
        }
    }

    // --- Card actions ----------------------------------------------------------

    fun toggleFavorite(card: DailyList) {
        viewModelScope.launch {
            repo.setFavorited(card.id, !card.favorited)
            refresh()
        }
    }

    /** Manual whole-card deletion: permanent, works on favorited cards too. */
    fun deleteCard(card: DailyList, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            db.dailyListDao().deleteDailyListWithItems(card.id)
            refresh()
            onDeleted()
        }
    }

    /**
     * Maintenance: runs once per local calendar day when the user enters Daily
     * List mode — never on a timer, never at midnight. Room/coroutine
     * background execution inside the repository keeps the UI free.
     */
    fun runMaintenanceIfDue() {
        viewModelScope.launch {
            repo.runMaintenance(
                today = today(),
                autoTrashEnabled = settings.dailyListAutoTrashPast,
                retentionKeepCount = settings.dailyListRetentionDays,
                protectFavorited = settings.dailyListProtectFavorited,
            )
            refresh()
        }
    }

}
