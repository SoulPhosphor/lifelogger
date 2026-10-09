package com.datadragon.app.data

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class LuckySelectionSessionTest {
    private val items = listOf(
        ChecklistItem(id = 1, checklistId = 1, text = "Same", position = 0),
        ChecklistItem(id = 2, checklistId = 1, text = "Same", position = 1),
        ChecklistItem(id = 3, checklistId = 1, text = "  ", position = 2),
    )
    private fun session() = LuckySelectionSession(object : Random() { override fun nextBits(bitCount: Int) = 0 })

    @Test fun defaultAllowsRepeatingTheWinner() {
        val session = session()
        assertEquals(1L, session.select(items, false)!!.id)
        assertEquals(1L, session.select(items, false)!!.id)
    }
    @Test fun exclusionUsesEachRowOnceEvenWithDuplicateTextAndIgnoresBlankRows() {
        val session = session()
        assertEquals(1L, session.select(items, true)!!.id)
        assertEquals(2L, session.select(items, true)!!.id)
        assertNull(session.select(items, true))
    }
    @Test fun startFreshAndClosingTheDialogResetTheSession() {
        val session = session()
        session.select(items, true); session.select(items, true)
        session.reset()
        assertEquals(1L, session.select(items, true)!!.id)
        session.reset()
        assertEquals(1L, session.select(items, true)!!.id)
    }
    @Test fun oneOptionExhaustsWithExclusionAndCanRepeatWithoutIt() {
        val session = session()
        val one = items.take(1)
        assertNotNull(session.select(one, true))
        assertNull(session.select(one, true))
        session.reset()
        assertNotNull(session.select(one, false))
        assertNotNull(session.select(one, false))
    }
    @Test fun emptyAndWhitespaceOnlyListsHaveNoWinner() {
        assertNull(session().select(emptyList(), false))
        assertNull(session().select(items.takeLast(1), false))
    }
}
