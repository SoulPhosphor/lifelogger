package com.datadragon.app.data

import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Applies the pure [DailyListLogic] rules to real rows inside Room
 * transactions. Owns every Daily List write path: creation from the first
 * non-blank item (or from renewal-carried items), immutable dates, and the
 * destructive maintenance passes. (Renewal itself lives in the ViewModel:
 * it drives the editor the user is looking at.)
 */
class DailyListRepository(private val db: AppDatabase) {

    private val dao = db.dailyListDao()

    /** The one card for [date], or null when that date has not been saved. */
    suspend fun getByDate(date: LocalDate): DailyList? = dao.getByDate(date.toString())

    suspend fun getDailyList(id: Long): DailyList? = dao.getDailyList(id)

    fun observeDailyList(id: Long) = dao.observeDailyList(id)

    fun observeItems(dailyListId: Long) = dao.observeItems(dailyListId)

    fun observeAll() = dao.observeDailyLists()

    /**
     * Persist a new card for [date]. The date-unique index aborts the insert
     * when a card already exists for that date (persistence-level uniqueness,
     * not a UI check). Returns the new card, or the existing one unchanged —
     * no duplicate is created and nothing is merged.
     */
    suspend fun createForDate(date: LocalDate, now: Long): DailyList? {
        val existing = dao.getByDate(date.toString())
        if (existing != null) return existing
        val row = DailyList(date = date, createdAt = now)
        val inserted = dao.insertDailyList(row)
        return if (inserted == -1L) dao.getByDate(date.toString()) else row.copy(id = inserted)
    }

    /**
     * Creation from the first real item: a fresh date becomes a saved card
     * only when its first non-blank item exists — a blank row alone never
     * creates one. The [typed] item (with its own stable uuid) is inserted
     * together with the card in one transaction, so an entry typed on a fresh
     * date is never lost or duplicated, and the returned card already holds it.
     *
     * @return the saved card now holding [typed], or null when creation was
     *   not possible this call (the caller keeps the row local and retries on
     *   the next change).
     */
    suspend fun createWithFirstItem(
        date: LocalDate,
        now: Long,
        typed: DailyListItem,
    ): DailyList? = db.withTransaction {
        val existing = dao.getByDate(date.toString())
        if (existing != null) {
            dao.insertItem(typed.copy(dailyListId = existing.id))
            existing
        } else {
            val inserted = dao.insertDailyList(DailyList(date = date, createdAt = now))
            if (inserted == -1L) return@withTransaction null // lost the race; retry next change
            val card = dao.getByDate(date.toString()) ?: return@withTransaction null
            dao.insertItem(typed.copy(dailyListId = card.id))
            card
        }
    }

    /** Set the per-day title. The date itself is never written after creation. */
    suspend fun setTitle(id: Long, title: String) = dao.setTitle(id, title)

    suspend fun setFavorited(id: Long, favorited: Boolean) =
        dao.setFavorited(id, favorited)

    /**
     * Mark today's card as having had its once-per-day Daily List maintenance
     * pass. Stored on the card row, so it survives restarts. (The one-time
     * automatic renewal has its own marker — [DailyList.renewalRunOn] via
     * [markRenewalRun] — so maintenance never suppresses renewal.)
     */
    suspend fun markMaintenanceRun(cardId: Long, today: LocalDate) =
        dao.setMaintenanceRunOn(cardId, today.toString())

    /** Record that [cardId]'s one-time automatic renewal already ran on [runOn]. */
    suspend fun markRenewalRun(cardId: Long, runOn: LocalDate) =
        dao.markRenewalRun(cardId, runOn.toString())

    /**
     * Record a genuine all-tasks-completed achievement on [cardId]. Never
     * cleared by cleanup — see [DailyList.genuinelyCompleted].
     */
    suspend fun setGenuinelyCompleted(cardId: Long, earned: Boolean) =
        dao.setGenuinelyCompleted(cardId, earned)

    /**
     * The automatic destructive maintenance pass, run at most once per local
     * day and only when the user enters Daily List mode. In one transaction:
     *
     * 1. whole-card retention (auto delete older-than-N cards), skipping
     *    favorited cards while protection is on;
     * 2. unfinished-item trashing on past cards, preserving completed rows,
     *    promoting orphaned completed sub-items, and recording the
     *    completion-history consequence for cards that never earned it.
     *
     * The pass never touches today's or future cards. It has its own marker
     * ([DailyList.maintenanceRunOn]); it never sets the one-time renewal's
     * marker.
     */
    suspend fun runMaintenance(
        today: LocalDate,
        autoTrashEnabled: Boolean,
        retentionKeepCount: Int?,
        protectFavorited: Boolean,
        autoTrashKeepPastCount: Int = 7,
    ) {
        val currentCard = dao.getByDate(today.toString())
        val allCardsBeforeMaintenance = dao.getAllDailyListsOnce()
        // Maintenance is triggered by entering Daily List, even when today has
        // not been started yet. Use the per-card marker as an app-local
        // once-per-day record and avoid rerunning if any surviving card already
        // records today's pass.
        if (allCardsBeforeMaintenance.isEmpty() || allCardsBeforeMaintenance.any { it.maintenanceRunOn == today }) {
            return
        }

        db.withTransaction {
            // 1. Whole-card retention. Card-count based on date order — gaps
            // between dates never count as retained days.
            if (retentionKeepCount != null) {
                val all = dao.getAllDailyListsOnce()
                val toDelete = DailyListLogic.retentionDeletionCandidates(
                    cards = all,
                    keepCount = retentionKeepCount,
                    protectFavorited = protectFavorited,
                )
                toDelete.forEach { dao.deleteDailyListWithItems(it.id) }
            }

            // 2. Unfinished-item trash on eligible past cards only. The most
            // recent [autoTrashKeepPastCount] past cards are protected; only
            // cards older than those may be cleaned.
            if (autoTrashEnabled) {
                val pastCards = DailyListLogic.cleanupEligiblePastCards(
                    cards = dao.getAllDailyListsOnce(),
                    today = today,
                    keepPastCount = autoTrashKeepPastCount,
                )
                for (card in pastCards) {
                    val items = dao.getItemsOnce(card.id)
                    val deletable = DailyListLogic.deletableUnfinishedItems(card.date, today, items)
                    if (deletable.isEmpty()) continue
                    val survivors = items.filterNot { item -> deletable.any { it.id == item.id } }
                    val rebuilt = DailyListLogic.flattenOrphansAfterCleanup(survivors)

                    deletable.forEach { dao.deleteItem(it.id) }
                    // Re-position and re-nest the survivors compactly: orphaned
                    // sub-items are promoted, never deleted for being nested.
                    rebuilt.forEachIndexed { index, item ->
                        if (item.position != index) dao.setItemPosition(item.id, index)
                        val storedIndent = survivors.firstOrNull { it.id == item.id }?.indent
                        if (storedIndent != null && item.indent != storedIndent) {
                            dao.setItemIndent(item.id, item.indent)
                        }
                    }

                    // Completion history: a card that never genuinely earned
                    // completion can no longer newly earn it once its unfinished
                    // evidence was destructively removed. (An already-earned card
                    // keeps its history.)
                    if (!card.genuinelyCompleted) {
                        dao.setGenuinelyCompleted(card.id, earned = false)
                        dao.setNeverEarnsCompletion(card.id)
                    }
                }
            }

            val markerCard = currentCard?.let { dao.getDailyList(it.id) }
                ?: dao.getAllDailyListsOnce().firstOrNull()
            markerCard?.let { dao.setMaintenanceRunOn(it.id, today.toString()) }
        }
    }
}
