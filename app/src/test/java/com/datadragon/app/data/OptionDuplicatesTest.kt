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
    fun fieldCheckAppliesOnlyToOptionFields() {
        val dropdown = FieldDef(label = "Color", type = FieldType.DROPDOWN, options = listOf("Red", "red"))
        assertTrue(dropdown.introducesOptionDuplicate())
        assertFalse(dropdown.introducesOptionDuplicate(originalOptions = listOf("Red", "red")))

        val multiple = FieldDef(
            label = "Color",
            type = FieldType.MULTIPLE,
            options = listOf("*Red", "Red"),
            allowPreselectedMultipleChoices = true,
        )
        assertTrue(multiple.introducesOptionDuplicate())
        assertFalse(multiple.copy(allowPreselectedMultipleChoices = false).introducesOptionDuplicate())

        assertFalse(FieldDef(label = "Name", type = FieldType.TEXT).introducesOptionDuplicate())
    }

    @Test
    fun theWarningIsTheOwnersExactWording() {
        assertEquals("Sorry, that item can't be added as it already exists.", OptionDuplicates.MESSAGE)
    }
}
