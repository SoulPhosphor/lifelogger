package com.datadragon.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One Clicker grouping's Statistics Designer choices, serialized into
 * [ClickerLog.statisticsJson]. Blank JSON decodes to these defaults, which is
 * what every grouping starts with.
 *
 * Statistics are stored as what is turned **off** ([disabledStats], keyed by
 * [ClickerField.id]), so every tracker — including one added later — starts
 * with every statistic on. A tracker with all of its statistics off is left
 * off the Statistics page.
 *
 * All Time is not stored: it is always offered and is the page's default range.
 */
@Serializable
data class ClickerStatisticsConfig(
    val last7Days: Boolean = true,
    val last30Days: Boolean = true,
    val customRange: Boolean = false,
    /** The typed number of days for the Custom Date Range; blank until filled in. */
    val customDays: String = "",
    /**
     * The [ClickerField.id] of the Date or Date & Time field that places a log
     * in a date range, or null for Log Date (the stamp at the top of the log).
     */
    val dateFieldId: String? = null,
    /** "Don't count current date's statistics when calculating". */
    val excludeToday: Boolean = false,
    val disabledStats: Map<String, List<String>> = emptyMap(),
) {
    fun isEnabled(fieldId: String, stat: String): Boolean =
        disabledStats[fieldId]?.contains(stat) != true

    /** The custom range's day count, or null when it is blank or zero. */
    val customDayCount: Long?
        get() = customDays.toLongOrNull()?.takeIf { it > 0 }
}

/** The statistic tokens (stored in [ClickerStatisticsConfig.disabledStats]), in display order. */
object ClickerStats {
    const val HIGHEST_VALUE = "highest_value"
    const val LOWEST_VALUE = "lowest_value"
    const val AVERAGE = "average"
    const val TOTAL = "total"
    const val DEFAULT_VALUE_CHANGED = "default_value_changed"

    val all: List<String> = listOf(HIGHEST_VALUE, LOWEST_VALUE, AVERAGE, TOTAL, DEFAULT_VALUE_CHANGED)
}

/** The ranges the Statistics page can show. */
sealed interface ClickerStatisticsRange {
    data object AllTime : ClickerStatisticsRange
    data class LastDays(val days: Long) : ClickerStatisticsRange
}

/** Encodes / decodes a [ClickerStatisticsConfig] to and from [ClickerLog.statisticsJson]. */
object ClickerStatisticsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Blank or unparseable JSON decodes to the defaults rather than throwing. */
    fun decode(statisticsJson: String): ClickerStatisticsConfig =
        if (statisticsJson.isBlank()) {
            ClickerStatisticsConfig()
        } else {
            runCatching { json.decodeFromString<ClickerStatisticsConfig>(statisticsJson) }
                .getOrDefault(ClickerStatisticsConfig())
        }

    /**
     * Deterministic encoding (sorted keys and lists) so an unchanged choice
     * always produces the same text for backup comparison. The all-defaults
     * choice encodes to blank, matching a grouping that was never designed.
     */
    fun encode(config: ClickerStatisticsConfig): String {
        val normalized = config.copy(
            disabledStats = config.disabledStats
                .mapValues { (_, stats) -> stats.distinct().sorted() }
                .filterValues { it.isNotEmpty() }
                .toSortedMap(),
        )
        return if (normalized == ClickerStatisticsConfig()) "" else json.encodeToString(normalized)
    }
}

/** One tracker's calculated statistics for the chosen range. */
data class ClickerTrackerStatistics(
    val field: ClickerField,
    /** Null when the range holds no value for this tracker. */
    val highest: Int?,
    val lowest: Int?,
    val average: Double?,
    val total: Long?,
    /** Logs in the range whose value differs from the tracker's default. */
    val defaultChangedCount: Int,
    /** Every log in the range. */
    val logCount: Int,
)

/**
 * Turns a Clicker grouping's logs into per-tracker statistics for one range.
 * Pure, so it can be unit tested without Android.
 *
 * - A log's day comes from the chosen Date / Date & Time field, or its Log Date
 *   (the date stamp, else its creation day) when none is chosen or the chosen
 *   field no longer exists.
 * - "Last N Days" is today and the N − 1 days before it; with
 *   [ClickerStatisticsConfig.excludeToday] on, it is the N days before today,
 *   and All Time also leaves out today's logs. A log with no day is counted
 *   only in All Time.
 * - Every log in the range counts toward Highest / Lowest / Average / Total,
 *   including one still at its default. A Click Tracker with no stored value
 *   counts as its Starting Number; a blank Write-In has no number and is
 *   skipped.
 * - The default is the tracker's current Starting Number (Click Tracker) or
 *   blank (Write-In).
 */
object ClickerStatisticsCalculator {

    /** The number trackers, in setup order — the only fields with statistics. */
    fun numericFields(fields: List<ClickerField>): List<ClickerField> =
        fields.filter { it.type == ClickerFieldType.CLICK_TRACKER || it.type == ClickerFieldType.WRITE_IN_NUMBER }

    /** The Date and Date & Time fields, Date fields first, each in setup order. */
    fun dateFields(fields: List<ClickerField>): List<ClickerField> =
        fields.filter { it.type == ClickerFieldType.DATE } + fields.filter { it.type == ClickerFieldType.DATE_TIME }

    fun calculate(
        config: ClickerStatisticsConfig,
        fields: List<ClickerField>,
        cards: List<ClickerCard>,
        range: ClickerStatisticsRange,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ClickerTrackerStatistics> {
        val dateField = config.dateFieldId?.let { id -> dateFields(fields).firstOrNull { it.id == id } }
        val inRange = cards
            .map { card -> card to ClickerValues.decode(card.valuesJson) }
            .filter { (card, values) -> inRange(dayOf(card, values, dateField, zone), range, config.excludeToday, today) }
        return numericFields(fields).map { field ->
            val numbers = inRange.mapNotNull { (_, values) -> valueOf(field, values) }
            ClickerTrackerStatistics(
                field = field,
                highest = numbers.maxOrNull(),
                lowest = numbers.minOrNull(),
                average = if (numbers.isEmpty()) null else numbers.average(),
                total = if (numbers.isEmpty()) null else numbers.sumOf { it.toLong() },
                defaultChangedCount = inRange.count { (_, values) -> defaultChanged(field, values) },
                logCount = inRange.size,
            )
        }
    }

    private fun valueOf(field: ClickerField, values: Map<String, String>): Int? {
        val stored = ClickerValues.number(values, field.id)
        return if (field.type == ClickerFieldType.CLICK_TRACKER) stored ?: field.startingNumber else stored
    }

    private fun defaultChanged(field: ClickerField, values: Map<String, String>): Boolean {
        val stored = ClickerValues.number(values, field.id)
        return if (field.type == ClickerFieldType.CLICK_TRACKER) {
            stored != null && stored != field.startingNumber
        } else {
            stored != null
        }
    }

    private fun dayOf(
        card: ClickerCard,
        values: Map<String, String>,
        dateField: ClickerField?,
        zone: ZoneId,
    ): LocalDate? {
        if (dateField != null) {
            // Date is stored as yyyy-MM-dd; Date & Time as "yyyy-MM-dd HH:mm".
            val raw = values[dateField.id]?.trim()?.substringBefore(' ') ?: return null
            return runCatching { LocalDate.parse(raw) }.getOrNull()
        }
        card.displayDate?.let { stamp -> runCatching { LocalDate.parse(stamp) }.getOrNull()?.let { return it } }
        return Instant.ofEpochMilli(card.createdAt).atZone(zone).toLocalDate()
    }

    private fun inRange(
        day: LocalDate?,
        range: ClickerStatisticsRange,
        excludeToday: Boolean,
        today: LocalDate,
    ): Boolean = when (range) {
        ClickerStatisticsRange.AllTime -> !(excludeToday && day == today)
        is ClickerStatisticsRange.LastDays -> {
            if (day == null) {
                false
            } else {
                val end = if (excludeToday) today.minusDays(1) else today
                // Older than the earliest representable start means "no lower bound".
                val start = runCatching { end.minusDays(range.days - 1) }.getOrDefault(LocalDate.MIN)
                !day.isBefore(start) && !day.isAfter(end)
            }
        }
    }
}
