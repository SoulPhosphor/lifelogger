package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.time.LocalDate

/**
 * One Daily List date card — the whole feature's stored row.
 *
 * Daily List deliberately has no user-created containers: there is exactly one
 * Daily List system, and it holds one card per saved date. [date] is the card's
 * date-only identity (no time component — it is the local calendar day the card
 * belongs to, stored ISO `yyyy-MM-dd` so it sorts lexicographically like
 * chronologically). A UNIQUE index enforces "never two cards for one date" in
 * persistence, not just in the UI.
 *
 * A date is immutable once saved: nothing updates [date], so a card can never
 * be edited into another date. Renaming or reordering items is unaffected.
 *
 * [uuid] is the card's permanent identity, so renewal's source-identity records
 * survive a card's row id changing and remain distinct from visible task text
 * (renewal never uses task text as identity).
 *
 * [title] is the optional per-day title. It is stored even while the
 * title-creating setting is off, so turning that toggle off (and back on) never
 * erases a title the user already saved.
 *
 * [favorited] is the Daily List star (the state is called Favorite, never
 * Mark). It is also the auto-deletion protection flag while
 * "Protect favorited days." is on.
 *
 * [maintenanceRunOn] is the local calendar day this card's Daily List
 * maintenance pass last ran — the once-per-local-day guard's stored state. It
 * stays null until the user has actually entered Daily List on that day, so
 * nothing runs overnight or on a timer.
 *
 * [renewalRunOn] is the local calendar day this card's one-time automatic
 * renewal last ran — a separate marker, so the maintenance pass marking the
 * card (which happens on entering Daily List mode, before any card is opened)
 * never suppresses the renewal that must run when the current day's card is
 * opened, whether that card already existed or was a fresh draft. Even a day
 * whose source had nothing to carry gets marked, so the source is never
 * rechecked afterward.
 *
 * [genuinelyCompleted] records that the user really completed every task on
 * this day at least once. It is permanent history, not current status: once
 * earned it is never unset — not by cleanup and not by un-completing a task —
 * so a day that genuinely earned completion can have its celebration icon
 * reappear whenever the card is fully completed again, including after
 * destructive cleanup removes its unfinished items. The visible icon still
 * follows current status. Earning is only ever set by a genuine all-completed
 * state, so destructive unfinished-item cleanup on a day that never earned it
 * cannot make the card newly earn the icon merely because the unfinished
 * evidence is gone.
 *
 * [completionBlockedByCleanup] is that guard's persistent state: once
 * destructive unfinished-item removal has run on a card that never earned
 * completion, no future completion can earn the celebration on that card —
 * the unfinished evidence that would prove it no longer exists.
 */
@Entity(
    tableName = "daily_lists",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["uuid"], unique = true),
    ],
)
@TypeConverters(DailyListConverters::class)
data class DailyList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val date: LocalDate,
    val title: String = "",
    val favorited: Boolean = false,
    val genuinelyCompleted: Boolean = false,
    val completionBlockedByCleanup: Boolean = false,
    val maintenanceRunOn: LocalDate? = null,
    val renewalRunOn: LocalDate? = null,
    val createdAt: Long,
)
