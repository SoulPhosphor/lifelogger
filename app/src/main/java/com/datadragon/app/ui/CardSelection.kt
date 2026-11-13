package com.datadragon.app.ui

import androidx.room.withTransaction
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerCardOrder
import com.datadragon.app.data.ClickerDao
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerReport
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import com.datadragon.app.data.ReportBuilder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Multiple Selection on the Forms and Clicker Data Main screens: which cards
 * Select Newest / Oldest X picks, the count box's input rules, the Copy text,
 * and deleting the selected cards.
 *
 * A selection is a set of card row ids. It lives only on the screen; it never
 * touches a card's data, its mark, or the screen's filters.
 */
object CardSelection {

    /** Digits the Select Newest / Oldest box holds: enough to type the card count. */
    fun digitsFor(cardCount: Int): Int = maxOf(1, cardCount.toString().length)

    /** What the count box keeps of typed text: digits only, at most [digits] of them. */
    fun countInput(input: String, digits: Int): String = input.filter(Char::isDigit).take(digits)

    /** The typed count, or null when it is blank, zero, or not a whole number (Okay stays disabled). */
    fun parseCount(text: String): Int? =
        text.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 }

    /** [selected] with [id] flipped in or out. */
    fun toggle(selected: Set<Long>, id: Long): Set<Long> = if (id in selected) selected - id else selected + id

    /**
     * The [count] newest (or oldest) of [entries] by the form's timestamp: its
     * designated sort timestamp field when it has one, otherwise the automatic
     * entry timestamp. An entry with no value in the designated field has no
     * timestamp and is never picked. Equal timestamps fall back to row id.
     */
    fun formPick(
        entries: List<LogEntry>,
        fields: List<FieldDef>,
        template: LogTemplate?,
        count: Int,
        newest: Boolean,
    ): Set<Long> {
        val sortField = defaultSortField(fields, template)
        val ascending = compareBy<Pair<LogEntry, LocalDateTime>>({ it.second }, { it.first.id })
        return entries
            .mapNotNull { entry -> entrySortTime(entry, sortField)?.let { entry to it } }
            .sortedWith(if (newest) ascending.reversed() else ascending)
            .take(count)
            .map { it.first.id }
            .toSet()
    }

    /**
     * The [count] newest (or oldest) Clicker cards by their date/time stamps,
     * shown or not, with the creation time filling any part the card has no
     * stamp for — the same timestamps the Main screen orders cards by.
     */
    fun clickerPick(
        cards: List<ClickerCard>,
        count: Int,
        newest: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<Long> = ClickerCardOrder.sort(cards, newest, zone).take(count).map { it.id }.toSet()

    /** The selected entries as the Forms `.txt` report, Follow-Up Notes included. */
    fun formCopyText(
        template: LogTemplate,
        fields: List<FieldDef>,
        selected: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>>,
    ): String = ReportBuilder.build(
        template, fields, selected, markdown = false,
        entryNotes = notesByEntry, includeFollowUps = true,
    ).text

    /** The selected cards as the Clicker Data `.txt` report, Follow-Up Notes included. */
    fun clickerCopyText(log: ClickerLog, fields: List<ClickerField>, selected: List<ClickerCard>): String =
        ClickerReport.text(ClickerReport.build(log, fields, selected, includeFollowUps = true), markdown = false)

    /** Only the Follow-Up Notes that belong to [ids], so a selected export never carries others. */
    fun notesFor(notesByEntry: Map<Long, List<EntryNote>>, ids: Set<Long>): Map<Long, List<EntryNote>> =
        notesByEntry.filterKeys { it in ids }

    /**
     * Delete the selected entries of one form, each with its Follow-Up Notes, as
     * deleting one entry does. Only entries of [templateId] are touched; the
     * form itself never is.
     */
    suspend fun deleteEntries(db: AppDatabase, templateId: Long, ids: Set<Long>) {
        val entryDao = db.logEntryDao()
        val noteDao = db.entryNoteDao()
        db.withTransaction {
            entryDao.getForTemplateOnce(templateId).filter { it.id in ids }.forEach { entry ->
                noteDao.deleteForEntry(entry.id)
                entryDao.delete(entry)
            }
        }
    }

    /**
     * Delete the selected cards of one Clicker Data grouping, as deleting one
     * card does. Only cards of [logId] are touched; the grouping never is.
     */
    suspend fun deleteCards(dao: ClickerDao, logId: Long, ids: Set<Long>) {
        val doomed = dao.getCardsForLog(logId).filter { it.id in ids }
        doomed.forEach { dao.deleteCard(it) }
        if (doomed.isNotEmpty()) dao.touchModified(logId, System.currentTimeMillis())
    }
}
