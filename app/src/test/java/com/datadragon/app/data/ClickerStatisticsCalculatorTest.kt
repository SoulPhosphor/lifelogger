package com.datadragon.app.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickerStatisticsCalculatorTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val reps = ClickerField(id = "reps", type = ClickerFieldType.CLICK_TRACKER, label = "Reps", startingNumber = 5)
    private val weight = ClickerField(id = "weight", type = ClickerFieldType.WRITE_IN_NUMBER, label = "Weight")
    private val done = ClickerField(id = "done", type = ClickerFieldType.DATE, label = "Done On")
    private val note = ClickerField(id = "note", type = ClickerFieldType.TEXT, label = "Note")
    private val fields = listOf(note, reps, done, weight)

    private var nextId = 1L

    private fun card(date: LocalDate?, vararg values: Pair<String, String>) = ClickerCard(
        id = nextId,
        clickerLogId = 1,
        uuid = "card-${nextId++}",
        createdAt = LocalDate.of(2020, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        displayDate = date?.toString(),
        valuesJson = ClickerValues.encode(mapOf(*values)),
    )

    private fun calc(
        cards: List<ClickerCard>,
        range: ClickerStatisticsRange,
        config: ClickerStatisticsConfig = ClickerStatisticsConfig(),
    ) = ClickerStatisticsCalculator.calculate(config, fields, cards, range, today, ZoneOffset.UTC)

    @Test
    fun onlyNumberTrackersGetStatisticsInSetupOrder() {
        val result = calc(emptyList(), ClickerStatisticsRange.AllTime)
        assertEquals(listOf("reps", "weight"), result.map { it.field.id })
    }

    @Test
    fun defaultValuesCountTowardTheNumbersButNotTowardDefaultChanged() {
        val cards = listOf(
            card(today, "reps" to "5"),
            card(today, "reps" to "9", "weight" to "180"),
            card(today),
        )
        val (repsStats, weightStats) = calc(cards, ClickerStatisticsRange.AllTime)

        // A Click Tracker with no stored value counts as its Starting Number.
        assertEquals(9, repsStats.highest)
        assertEquals(5, repsStats.lowest)
        assertEquals(19L / 3.0, repsStats.average!!, 0.0001)
        assertEquals(19L, repsStats.total)
        assertEquals(1, repsStats.defaultChangedCount)
        assertEquals(3, repsStats.logCount)

        // A blank Write-In is skipped; one with a value has changed its default.
        assertEquals(180, weightStats.highest)
        assertEquals(180.0, weightStats.average!!, 0.0)
        assertEquals(1, weightStats.defaultChangedCount)
    }

    @Test
    fun lastSevenDaysIncludesTodayAndTheSixBefore() {
        val cards = listOf(card(today), card(today.minusDays(6)), card(today.minusDays(7)))
        assertEquals(2, calc(cards, ClickerStatisticsRange.LastDays(7)).first().logCount)
    }

    @Test
    fun excludingTodayUsesTheSevenDaysBeforeTodayAndDropsTodayFromAllTime() {
        val config = ClickerStatisticsConfig(excludeToday = true)
        val cards = listOf(card(today), card(today.minusDays(1)), card(today.minusDays(7)), card(today.minusDays(8)))
        assertEquals(2, calc(cards, ClickerStatisticsRange.LastDays(7), config).first().logCount)
        assertEquals(3, calc(cards, ClickerStatisticsRange.AllTime, config).first().logCount)
    }

    @Test
    fun aChosenDateFieldPlacesLogsAndLogsWithoutItOnlyCountInAllTime() {
        val config = ClickerStatisticsConfig(dateFieldId = "done")
        val cards = listOf(
            card(today.minusDays(100), "done" to today.toString()),
            card(today, "done" to today.minusDays(100).toString()),
            card(today),
        )
        assertEquals(1, calc(cards, ClickerStatisticsRange.LastDays(30), config).first().logCount)
        assertEquals(3, calc(cards, ClickerStatisticsRange.AllTime, config).first().logCount)
    }

    @Test
    fun aDateAndTimeFieldUsesItsDate() {
        val stamp = ClickerField(id = "stamp", type = ClickerFieldType.DATE_TIME, label = "Stamp")
        val config = ClickerStatisticsConfig(dateFieldId = "stamp")
        val cards = listOf(card(null, "stamp" to "${today.minusDays(2)} 14:05"))
        val result = ClickerStatisticsCalculator.calculate(
            config, listOf(reps, stamp), cards, ClickerStatisticsRange.LastDays(7), today, ZoneOffset.UTC,
        )
        assertEquals(1, result.single().logCount)
    }

    @Test
    fun aDeletedDateFieldFallsBackToLogDate() {
        val config = ClickerStatisticsConfig(dateFieldId = "gone")
        assertEquals(1, calc(listOf(card(today)), ClickerStatisticsRange.LastDays(7), config).first().logCount)
    }

    @Test
    fun logDateFallsBackToCreationDayWithoutADateStamp() {
        val old = card(null)
        assertEquals(0, calc(listOf(old), ClickerStatisticsRange.LastDays(7)).first().logCount)
        assertEquals(1, calc(listOf(old), ClickerStatisticsRange.LastDays(3_000)).first().logCount)
    }

    @Test
    fun anEnormousCustomRangeHasNoLowerBound() {
        val result = calc(listOf(card(LocalDate.of(1900, 1, 1))), ClickerStatisticsRange.LastDays(999_999_999_999))
        assertEquals(1, result.first().logCount)
    }

    @Test
    fun anEmptyRangeHasNoNumbers() {
        val stats = calc(emptyList(), ClickerStatisticsRange.LastDays(7)).first()
        assertNull(stats.highest)
        assertNull(stats.average)
        assertNull(stats.total)
        assertEquals(0, stats.logCount)
    }

    @Test
    fun dateFieldsListDatesBeforeDateAndTimes() {
        val stamp = ClickerField(id = "stamp", type = ClickerFieldType.DATE_TIME, label = "Stamp")
        val second = ClickerField(id = "second", type = ClickerFieldType.DATE, label = "Second")
        assertEquals(
            listOf("done", "second", "stamp"),
            ClickerStatisticsCalculator.dateFields(listOf(stamp, done, second)).map { it.id },
        )
    }
}
