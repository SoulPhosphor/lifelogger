package com.datadragon.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Builds the human-readable report and the CSV for one Clicker Data grouping,
 * following the Forms report (docs/FORMATTING_SPEC.md §2–§3).
 *
 * Cards read oldest first. Every field exports, including text and multitext
 * that the card face may hide. The automatic card date/time stamps are the
 * exception: they appear only when the grouping is set to show them, so a
 * hidden stamp (or the internal creation time) is never revealed. A card with
 * no visible stamp gets a numbered "Card n" heading instead.
 *
 * [includeFollowUps] (the export dialog's checkbox) decides whether each card's
 * Follow-Up Notes appear, in the report and in the CSV alike.
 */
object ClickerReport {

    /** One field line on a card. [block] values render on their own lines. */
    data class Line(val label: String, val value: String, val block: Boolean)

    data class Card(val heading: String, val lines: List<Line>, val followUp: String?)

    data class Report(
        val title: String,
        val dateRange: String?,
        val totalCards: Int,
        val cards: List<Card>,
    )

    /** Cards in export order: oldest first, ties broken by UUID. */
    fun ordered(cards: List<ClickerCard>): List<ClickerCard> =
        cards.sortedWith(compareBy({ it.createdAt }, { it.uuid }))

    /** The card's automatic date stamp, only when the grouping shows date stamps. */
    fun visibleDate(log: ClickerLog, card: ClickerCard): LocalDate? =
        card.displayDate?.takeIf { log.autoDateStamp }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The card's automatic time stamp, only when the grouping shows time stamps. */
    fun visibleTime(log: ClickerLog, card: ClickerCard): LocalTime? =
        card.displayTime?.takeIf { log.autoTimeStamp }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun build(
        log: ClickerLog,
        fields: List<ClickerField>,
        cards: List<ClickerCard>,
        includeFollowUps: Boolean,
    ): Report {
        val ordered = ordered(cards)
        val dates = if (log.autoDateStamp) ordered.mapNotNull { visibleDate(log, it) } else emptyList()
        val dateRange = if (dates.isEmpty()) {
            null
        } else {
            val first = EntryValues.displayDate(dates.min())
            val last = EntryValues.displayDate(dates.max())
            if (first == last) first else "$first – $last"
        }
        return Report(
            title = "${log.title} Report",
            dateRange = dateRange,
            totalCards = ordered.size,
            cards = ordered.mapIndexed { index, card ->
                val values = ClickerValues.decode(card.valuesJson)
                Card(
                    heading = heading(log, card) ?: "Card ${index + 1}",
                    lines = fields.mapNotNull { field ->
                        displayValue(field, values)?.let {
                            Line(field.label, it, block = field.type == ClickerFieldType.MULTITEXT)
                        }
                    },
                    followUp = if (includeFollowUps) {
                        values[ClickerValues.FOLLOW_UP_KEY]?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    },
                )
            },
        )
    }

    /** The .txt / .md rendering of [report]; the same vertical layout as the Forms report. */
    fun text(report: Report, markdown: Boolean): String {
        val sb = StringBuilder()
        if (markdown) sb.append("# ")
        sb.append(report.title).append('\n')
        report.dateRange?.let { sb.append("Date range: ").append(it).append('\n') }
        sb.append("Total cards: ").append(report.totalCards).append('\n')

        report.cards.forEach { card ->
            sb.append('\n').append(if (markdown) "---" else "----------").append('\n')
            sb.append('\n')
            if (markdown) sb.append("## ")
            sb.append(card.heading).append('\n')
            sb.append('\n')
            card.lines.forEach { line ->
                val label = if (markdown) "**${line.label}:**" else "${line.label}:"
                if (line.block) {
                    sb.append(label).append('\n').append(line.value).append('\n')
                } else {
                    sb.append(label).append(' ').append(line.value).append('\n')
                }
            }
            card.followUp?.let { note ->
                sb.append('\n')
                sb.append(if (markdown) "**Follow-Up Notes:**" else "Follow-Up Notes:").append('\n')
                sb.append(note).append('\n')
            }
        }
        return sb.toString()
    }

    /**
     * The spreadsheet export: one row per card, oldest first. Columns are
     * `card_id` (the card's UUID), then `date` / `time` only when the grouping
     * shows those stamps, each field in setup order, then `follow_up_notes`
     * when [includeFollowUps] is on. Values are the raw stored values; an
     * untouched Click Tracker is its starting number.
     */
    fun csv(
        log: ClickerLog,
        fields: List<ClickerField>,
        cards: List<ClickerCard>,
        includeFollowUps: Boolean,
    ): String {
        val sb = StringBuilder()
        val header = buildList {
            add("card_id")
            if (log.autoDateStamp) add("date")
            if (log.autoTimeStamp) add("time")
            fields.forEach { add(it.label) }
            if (includeFollowUps) add("follow_up_notes")
        }
        sb.append(header.joinToString(",") { CsvBuilder.escape(it) }).append(CsvBuilder.EOL)

        ordered(cards).forEach { card ->
            val values = ClickerValues.decode(card.valuesJson)
            val row = buildList {
                add(card.uuid)
                if (log.autoDateStamp) add(visibleDate(log, card)?.toString() ?: "")
                if (log.autoTimeStamp) add(visibleTime(log, card)?.format(EntryValues.TIME_STORAGE) ?: "")
                fields.forEach { field ->
                    add(
                        if (field.type == ClickerFieldType.CLICK_TRACKER) {
                            (ClickerValues.number(values, field.id) ?: field.startingNumber).toString()
                        } else {
                            values[field.id] ?: ""
                        },
                    )
                }
                if (includeFollowUps) add(values[ClickerValues.FOLLOW_UP_KEY] ?: "")
            }
            sb.append(row.joinToString(",") { CsvBuilder.escape(it) }).append(CsvBuilder.EOL)
        }
        return sb.toString()
    }

    /** The heading from the card's visible stamps, or null when none is visible. */
    private fun heading(log: ClickerLog, card: ClickerCard): String? {
        val date = visibleDate(log, card)
        val time = visibleTime(log, card)
        return when {
            date != null && time != null -> EntryValues.displayDateTime(LocalDateTime.of(date, time))
            date != null -> EntryValues.displayDate(date)
            time != null -> EntryValues.displayTime(time)
            else -> null
        }
    }

    /**
     * A field's value as the report shows it, or null when it is blank and so
     * left out. An untouched Click Tracker shows its starting number, as the
     * card face does.
     */
    internal fun displayValue(field: ClickerField, values: Map<String, String>): String? {
        if (field.type == ClickerFieldType.CLICK_TRACKER) {
            return (ClickerValues.number(values, field.id) ?: field.startingNumber).toString()
        }
        val raw = values[field.id]?.takeIf { it.isNotBlank() } ?: return null
        return when (field.type) {
            ClickerFieldType.DATE ->
                runCatching { EntryValues.displayDate(LocalDate.parse(raw)) }.getOrDefault(raw)
            ClickerFieldType.TIME ->
                runCatching { EntryValues.displayTime(LocalTime.parse(raw)) }.getOrDefault(raw)
            ClickerFieldType.DATE_TIME -> {
                val parts = raw.split(" ")
                runCatching {
                    EntryValues.displayDateTime(LocalDateTime.of(LocalDate.parse(parts[0]), LocalTime.parse(parts[1])))
                }.getOrDefault(raw)
            }
            else -> raw
        }
    }
}
