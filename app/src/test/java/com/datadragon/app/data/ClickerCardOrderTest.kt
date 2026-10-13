package com.datadragon.app.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ClickerCardOrderTest {

    private val zone = ZoneOffset.UTC

    private fun card(id: Long, created: String, date: String? = null, time: String? = null) = ClickerCard(
        id = id,
        clickerLogId = 1,
        uuid = "u$id",
        createdAt = LocalDateTime.parse(created).toInstant(zone).toEpochMilli(),
        displayDate = date,
        displayTime = time,
        valuesJson = "{}",
    )

    @Test
    fun aChangedDateMovesTheCard() {
        val older = card(1, "2026-10-01T09:00", date = "2026-10-01")
        val movedBack = card(2, "2026-10-05T09:00", date = "2026-09-20")
        val newest = card(3, "2026-10-03T09:00", date = "2026-10-03")
        assertEquals(listOf(3L, 1L, 2L), ClickerCardOrder.sort(listOf(older, movedBack, newest), true, zone).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), ClickerCardOrder.sort(listOf(older, movedBack, newest), false, zone).map { it.id })
    }

    @Test
    fun sameDayCardsKeepCreationOrder() {
        val first = card(1, "2026-10-01T09:00", date = "2026-10-01")
        val second = card(2, "2026-10-01T15:00", date = "2026-10-01")
        assertEquals(listOf(2L, 1L), ClickerCardOrder.sort(listOf(first, second), true, zone).map { it.id })
    }

    @Test
    fun shownTimeOrdersCardsWithinADay() {
        val early = card(1, "2026-10-01T20:00", date = "2026-10-01", time = "08:00")
        val late = card(2, "2026-10-01T09:00", date = "2026-10-01", time = "18:00")
        assertEquals(listOf(2L, 1L), ClickerCardOrder.sort(listOf(early, late), true, zone).map { it.id })
    }

    @Test
    fun unstampedCardsUseTheirCreationInstant() {
        val a = card(1, "2026-10-02T09:00")
        val b = card(2, "2026-10-01T09:00")
        assertEquals(listOf(1L, 2L), ClickerCardOrder.sort(listOf(b, a), true, zone).map { it.id })
    }
}
