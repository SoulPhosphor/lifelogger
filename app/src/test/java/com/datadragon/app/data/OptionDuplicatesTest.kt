package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionDuplicatesTest {

    @Test
    fun matchingIgnoresCapitalizationAndSurroundingSpaces() {
        assertTrue(OptionDuplicates.contains(listOf("Red", "Blue"), "  red "))
        assertTrue(OptionDuplicates.contains(listOf("android"), "Android"))
        assertFalse(OptionDuplicates.contains(listOf("Red"), "Reds"))
    }

    @Test
    fun preselectMarkerIsIgnoredOnlyWhenAsked() {
        assertTrue(OptionDuplicates.contains(listOf("*Red"), "red", stripPreselectMarker = true))
        assertFalse(OptionDuplicates.contains(listOf("*Red"), "red", stripPreselectMarker = false))
    }

    @Test
    fun aNewRepeatIsRefused() {
        assertTrue(OptionDuplicates.introducesDuplicate(emptyList(), listOf("Red", "RED")))
        assertTrue(OptionDuplicates.introducesDuplicate(listOf("Red"), listOf("Red", "red")))
        assertFalse(OptionDuplicates.introducesDuplicate(emptyList(), listOf("Red", "Blue")))
    }

    @Test
    fun aHistoricalDuplicateStaysSaveable() {
        val saved = listOf("Red", "red", "Blue")
        // Unchanged, reordered, or with an unrelated addition: still allowed.
        assertFalse(OptionDuplicates.introducesDuplicate(saved, saved))
        assertFalse(OptionDuplicates.introducesDuplicate(saved, listOf("Blue", "red", "Red", "Green")))
        // A third copy is a new duplicate.
        assertTrue(OptionDuplicates.introducesDuplicate(saved, saved + "RED"))
    }

    @Test
    fun anOptionListWithARepeatedNameIsDetected() {
        val choices = listOf(ChoiceOption("a", "Red"), ChoiceOption("b", " red", retired = true))
        assertTrue(choices.hasDuplicateLabels())
        assertFalse(choices.take(1).hasDuplicateLabels())
    }

    @Test
    fun theWarningIsTheOwnersExactWording() {
        assertEquals("Sorry, that item can't be added as it already exists.", OptionDuplicates.MESSAGE)
    }
}
