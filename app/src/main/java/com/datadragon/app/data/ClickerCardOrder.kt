package com.datadragon.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ordering for a Clicker Data Log's cards on its Main screen. Cards sort by the
 * date/time stamp the card shows, so changing a card's date from its edit menu
 * moves it to its new place in the list.
 */
object ClickerCardOrder {

    /**
     * The instant a card sorts at. A shown date with no shown time sorts at the
     * start of its day (as a Date field does in Forms); a part the grouping
     * doesn't stamp comes from the card's real creation instant.
     */
    fun sortTime(card: ClickerCard, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val created = Instant.ofEpochMilli(card.createdAt).atZone(zone).toLocalDateTime()
        val date = card.displayDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val time = card.displayTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return when {
            date != null -> date.atTime(time ?: LocalTime.MIDNIGHT)
            time != null -> created.toLocalDate().atTime(time)
            else -> created
        }
    }

    /** [cards] newest first or oldest first; ties keep creation order in the same direction. */
    fun sort(
        cards: List<ClickerCard>,
        newestFirst: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ClickerCard> {
        val ascending = compareBy<ClickerCard>({ sortTime(it, zone) }, { it.createdAt }, { it.id })
        return cards.sortedWith(if (newestFirst) ascending.reversed() else ascending)
    }
}
