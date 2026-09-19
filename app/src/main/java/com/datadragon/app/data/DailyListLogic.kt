package com.datadragon.app.data

import java.time.LocalDate

/** How one [DailyListItem] row is nested for sequence purposes. */
const val DAILY_LIST_TOP_LEVEL_INDENT = 0
const val DAILY_LIST_SUB_ITEM_INDENT = 1

/**
 * The pure Daily List rules, kept Android-free so they can be unit tested
 * directly. The repository applies them to real rows.
 */
object DailyListLogic {

    /**
     * True when every task on [items] is completed. A day with zero tasks does
     * not qualify — "all completed" must mean there was something to complete.
     */
    fun isAllCompleted(items: List<DailyListItem>): Boolean =
        items.isNotEmpty() && items.all { it.completed }

    // --- Sequences ----------------------------------------------------------

    /**
     * A sequence is one top-level item plus every sub-item directly following
     * it until the next top-level item. Returns the items grouped in order.
     */
    fun sequences(items: List<DailyListItem>): List<List<DailyListItem>> {
        val groups = mutableListOf<MutableList<DailyListItem>>()
        for (item in items) {
            if (item.indent == DAILY_LIST_TOP_LEVEL_INDENT || groups.isEmpty()) {
                groups.add(mutableListOf(item))
            } else {
                groups.last().add(item)
            }
        }
        return groups
    }

    /**
     * The Daily List completion order: every still-active sequence stays in its
     * original relative order, and any fully-completed sequence moves below all
     * active ones — as a whole, never just the row that was most recently
     * checked. Unchecking any member returns the whole sequence to the bottom
     * of the active section, which this pure re-sort produces from current
     * state alone (completed-ness is the only input).
     *
     * Ordinary Lists never use this — their per-row movement setting is theirs
     * alone and is untouched here.
     */
    fun orderedBySequenceCompletion(items: List<DailyListItem>): List<DailyListItem> {
        val (active, completed) = sequences(items).partition { group -> group.any { !it.completed } }
        return (active + completed).flatten()
    }

    // --- Renewal ------------------------------------------------------------

    /**
     * What automatic/manual renewal copies from a source card: only its
     * unfinished items, in their source order. Completed items are never
     * recreated. A surviving sub-item whose parent was completed (and so was
     * not copied) is promoted to a top-level item; sub-items under a surviving
     * parent stay nested under it.
     */
    fun renewedItems(source: List<DailyListItem>): List<RenewedItem> {
        val rebuilt = mutableListOf<RenewedItem>()
        var haveParent = false
        for (item in source) {
            if (item.completed) {
                // Completed rows are not carried. A sub-item under a completed
                // parent loses its parent; the next surviving sub-item (if its
                // own parent also didn't survive) becomes top-level.
                if (item.indent == DAILY_LIST_SUB_ITEM_INDENT) haveParent = false
                continue
            }
            if (item.indent == DAILY_LIST_TOP_LEVEL_INDENT) {
                rebuilt.add(RenewedItem(text = item.text, indent = DAILY_LIST_TOP_LEVEL_INDENT, sourceItem = item))
                haveParent = true
            } else if (haveParent) {
                rebuilt.add(RenewedItem(text = item.text, indent = DAILY_LIST_SUB_ITEM_INDENT, sourceItem = item))
            } else {
                // Orphaned unfinished sub-item: promote to top level.
                rebuilt.add(RenewedItem(text = item.text, indent = DAILY_LIST_TOP_LEVEL_INDENT, sourceItem = item))
                haveParent = true
            }
        }
        return rebuilt
    }

    /** One renewal-carried row before it is persisted, with its source row. */
    data class RenewedItem(val text: String, val indent: Int, val sourceItem: DailyListItem)

    /**
     * Filters [sourceItems] down to the rows a new renewal pass may still
     * carry: a row is skipped when this card already has a row renewed from
     * the same source row (matched by stable source identity — source card
     * uuid + source item uuid — never by visible text). Rows the user edited
     * on this day keep their identity, so Cycle never duplicates or rewrites
     * them; it only discovers what was not eligible last time.
     */
    fun filterAlreadyCarried(
        sourceItems: List<DailyListItem>,
        sourceCardUuid: String,
        currentItems: List<DailyListItem>,
    ): List<DailyListItem> {
        val carriedKeys = currentItems.mapNotNull { it.sourceUuid }.toSet()
        return sourceItems.filterNot { source ->
            "$sourceCardUuid:${source.uuid}" in carriedKeys
        }
    }

    /** The stored identity string marking a row as renewed from [sourceItem]. */
    fun sourceIdentityOf(sourceCardUuid: String, sourceItem: DailyListItem): String =
        "$sourceCardUuid:${sourceItem.uuid}"

    // --- Maintenance ----------------------------------------------------------

    /**
     * Which whole cards automatic retention deletes: when the user keeps
     * [keepCount] cards, everything strictly older than the newest [keepCount]
     * cards (by card date, never by creation time) is a candidate. Favorited
     * cards are skipped while protection is on. Gaps between dates are
     * irrelevant — this counts cards, not calendar days.
     */
    fun retentionDeletionCandidates(
        cards: List<DailyList>,
        keepCount: Int,
        protectFavorited: Boolean,
    ): List<DailyList> {
        if (keepCount <= 0) return emptyList()
        val byDateDesc = cards.sortedByDescending { it.date }
        return byDateDesc.drop(keepCount).filter { card ->
            !(protectFavorited && card.favorited)
        }
    }

    /**
     * Which unfinished items destructive cleanup permanently deletes from a
     * card dated [cardDate]: unfinished rows on **past** cards only — never
     * today's card, never a future-planned card. Completed rows always
     * survive, as do completed sub-items whose parent is deleted (they are
     * promoted up top by [flattenOrphansAfterCleanup]).
     */
    fun deletableUnfinishedItems(
        cardDate: LocalDate,
        today: LocalDate,
        items: List<DailyListItem>,
    ): List<DailyListItem> {
        if (!cardDate.isBefore(today)) return emptyList()
        return items.filter { !it.completed }
    }

    /**
     * After destructive cleanup removed some rows, rebuild the survivors:
     * relative order preserved, and any surviving sub-item whose parent was
     * deleted becomes a normal top-level item (never deleted merely for having
     * been nested).
     */
    fun flattenOrphansAfterCleanup(
        survivors: List<DailyListItem>,
    ): List<DailyListItem> {
        val rebuilt = mutableListOf<DailyListItem>()
        var haveParent = false
        for (item in survivors) {
            if (item.indent == DAILY_LIST_TOP_LEVEL_INDENT) {
                rebuilt.add(item)
                haveParent = true
            } else if (haveParent) {
                rebuilt.add(item)
            } else {
                rebuilt.add(item.copy(indent = DAILY_LIST_TOP_LEVEL_INDENT))
                // An orphan promoted to top level becomes the parent for the
                // sub-items that follow it.
                haveParent = true
            }
        }
        return rebuilt
    }

    /**
     * Renewal's once-only eligibility: a card's one-time automatic renewal may
     * run only when the local date it belongs to has arrived, and only once —
     * never before its date (future cards are never pre-populated), never again
     * after it ran (reopening does not re-sync), and never on a different day.
     * The marker is [renewalRunOn], its own column — the maintenance pass's
     * marker must never suppress it.
     */
    fun automaticRenewalDue(
        card: DailyList,
        today: LocalDate,
        enabled: Boolean,
    ): Boolean = enabled && card.renewalRunOn == null && card.date == today

    /**
     * Maintenance's once-per-local-day gate: the pass runs when the user
     * enters Daily List mode on [today] and has not already run it that day.
     * The marker lives on the current day's card; when today has no card yet
     * (nothing saved), there is nothing to maintain for today and the marker
     * is simply absent until the first saved card of that day.
     */
    fun maintenanceDue(currentCard: DailyList?, today: LocalDate): Boolean =
        currentCard != null && currentCard.maintenanceRunOn != today
}
