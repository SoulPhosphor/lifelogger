package com.datadragon.app.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comparison statistics come from each card's Total Difference, count only
 * cards with a completed row, and count a mismatch from the rows themselves so
 * differences that cancel out to zero are still caught.
 */
class ClickerComparisonStatisticsTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val meals = ClickerField(
        id = "meals",
        type = ClickerFieldType.COMPARISON,
        label = "Meals",
        choices = listOf(ChoiceOption("turkey", "Turkey"), ChoiceOption("ham", "Ham"), ChoiceOption("beef", "Beef")),
    )
    private val reps = ClickerField(id = "reps", type = ClickerFieldType.CLICK_TRACKER, label = "Reps", startingNumber = 5)

    private var nextId = 1L

    private fun card(field: ClickerField, rows: List<ComparisonRow>, vararg other: Pair<String, String>): ClickerCard {
        val values = buildMap {
            ClickerComparison.stored(field.comparisonDef(), rows)?.let { put(field.id, it) }
            putAll(other)
        }
        return ClickerCard(
            id = nextId,
            clickerLogId = 1,
            uuid = "card-${nextId++}",
            createdAt = 0,
            displayDate = today.toString(),
            valuesJson = ClickerValues.encode(values),
        )
    }

    private fun stats(cards: List<ClickerCard>, fields: List<ClickerField> = listOf(meals)) =
        ClickerStatisticsCalculator.calculate(
            ClickerStatisticsConfig(), fields, cards, ClickerStatisticsRange.AllTime, today, ZoneOffset.UTC,
        )

    private fun BigDecimal?.text(): String? = this?.let(ComparisonValues::format)

    @Test
    fun theFirstFourComeFromEachCardsTotalDifference() {
        val cards = listOf(
            // Two rows: −2 and +1, so the card's total is −1.
            card(meals, listOf(ComparisonRow("turkey", "3", "1"), ComparisonRow("ham", "1", "2"))),
            card(meals, listOf(ComparisonRow("turkey", "2", "6"))),
            card(meals, listOf(ComparisonRow("beef", "4", "4"))),
        )
        val result = stats(cards).single().comparison!!
        assertEquals("4", result.greatestTotalDifference.text())
        assertEquals("-1", result.leastTotalDifference.text())
        assertEquals("1", result.averageTotalDifference.text())
        assertEquals("3", result.totalDifference.text())
    }

    @Test
    fun aCardWhoseDifferencesCancelToZeroStillCountsAsDifferent() {
        val cards = listOf(
            card(meals, listOf(ComparisonRow("turkey", "10", "8"), ComparisonRow("ham", "0", "2"))),
            card(meals, listOf(ComparisonRow("turkey", "10", "10"))),
        )
        val result = stats(cards).single().comparison!!
        assertEquals("0", result.totalDifference.text())
        assertEquals("0", result.greatestTotalDifference.text())
        assertEquals(1, result.timesDifferent)
    }

    @Test
    fun aMissingNumberIsNotZeroAndACardWithoutACompletedRowIsLeftOut() {
        val cards = listOf(
            card(meals, listOf(ComparisonRow("turkey", "10", ""))),
            card(meals, listOf(ComparisonRow("turkey", "", "3"), ComparisonRow("ham", "2", "5"))),
            card(meals, emptyList()),
        )
        val result = stats(cards).single().comparison!!
        // Only the second card has a completed row (Ham, +3).
        assertEquals("3", result.greatestTotalDifference.text())
        assertEquals("3", result.leastTotalDifference.text())
        assertEquals("3", result.averageTotalDifference.text())
        assertEquals(1, result.timesDifferent)
    }

    @Test
    fun noCompletedRowsGivesNoValuesAndNoMismatches() {
        val result = stats(listOf(card(meals, listOf(ComparisonRow("turkey", "4", ""))))).single().comparison!!
        assertNull(result.greatestTotalDifference)
        assertNull(result.leastTotalDifference)
        assertNull(result.averageTotalDifference)
        assertNull(result.totalDifference)
        assertEquals(0, result.timesDifferent)
    }

    @Test
    fun averagesAndNegativeTotalsKeepTheirSign() {
        val cards = listOf(
            card(meals, listOf(ComparisonRow("turkey", "5", "0"))),
            card(meals, listOf(ComparisonRow("turkey", "1", "0"))),
        )
        val result = stats(cards).single().comparison!!
        assertEquals("-1", result.greatestTotalDifference.text())
        assertEquals("-5", result.leastTotalDifference.text())
        assertEquals("-3", result.averageTotalDifference.text())
        assertEquals("-6", result.totalDifference.text())
        assertEquals(2, result.timesDifferent)
    }

    @Test
    fun clickerCountingCountsTheStartingNumberOnceARowHasAnItem() {
        val counted = meals.copy(useClickerCounting = true, startingNumber = 10)
        // Stored without a second number: as on the card, it holds the Starting Number.
        val values = ClickerValues.encode(
            mapOf(counted.id to ComparisonValues.encode(listOf(ComparisonRow("turkey", "8", "")))!!.toString()),
        )
        val card = ClickerCard(clickerLogId = 1, uuid = "c", createdAt = 0, displayDate = today.toString(), valuesJson = values)
        val result = stats(listOf(card), listOf(counted)).single().comparison!!
        assertEquals("2", result.totalDifference.text())
        assertEquals(1, result.timesDifferent)
    }

    @Test
    fun numberTrackersKeepTheirStatisticsBesideAComparison() {
        val cards = listOf(
            card(meals, listOf(ComparisonRow("turkey", "3", "1")), "reps" to "9"),
            card(meals, emptyList()),
        )
        val result = stats(cards, listOf(meals, reps))
        assertEquals(listOf("meals", "reps"), result.map { it.field.id })
        val repsStats = result[1]
        assertNull(repsStats.comparison)
        assertEquals(9, repsStats.highest)
        assertEquals(5, repsStats.lowest)
        assertEquals(14L, repsStats.total)
        assertEquals(1, repsStats.defaultChangedCount)
        assertEquals(2, repsStats.logCount)
    }

    @Test
    fun comparisonFieldsOfferTheirOwnStatistics() {
        assertEquals(ClickerStats.comparison, ClickerStats.forField(meals))
        assertEquals(ClickerStats.all, ClickerStats.forField(reps))
    }
}
