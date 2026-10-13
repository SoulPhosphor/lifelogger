package com.datadragon.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editing a saved option list: every edit keeps each option's permanent id,
 * pre-selection follows the Single / Multiple Choice rules, and names follow
 * the duplicate rule against active and retired options alike.
 */
class ChoiceEditsTest {

    private val cow = ChoiceOption(id = "cow", label = "Cow")
    private val cat = ChoiceOption(id = "cat", label = "Cat")
    private val dog = ChoiceOption(id = "dog", label = "Dog")
    private val ferret = ChoiceOption(id = "ferret", label = "Ferret", retired = true)
    private val list = listOf(cow, cat, dog, ferret)

    @Test
    fun renamingKeepsTheIdAndPositionAndTheAnswersFollow() {
        val renamed = ChoiceEdits.renamed(list, "cat", "Kitten")
        assertEquals(listOf("cow", "cat", "dog", "ferret"), renamed.map { it.id })
        assertEquals("Kitten", renamed[1].label)

        val field = FieldDef(id = "pet", label = "Pet", type = FieldType.DROPDOWN, choices = renamed)
        val values = kotlinx.serialization.json.JsonObject(mapOf("pet" to JsonPrimitive("cat")))
        assertEquals("Kitten", EntryValues.displayValue(field, values))
    }

    @Test
    fun reorderingOnlyMovesActiveRowsAndKeepsEveryId() {
        val moved = ChoiceEdits.movedActive(list, "dog", 0)
        assertEquals(listOf("dog", "cow", "cat", "ferret"), moved.map { it.id })
        assertEquals(list.toSet(), moved.toSet())
        // Out of range or retired: nothing changes.
        assertEquals(list, ChoiceEdits.movedActive(list, "dog", 7))
        assertEquals(list, ChoiceEdits.movedActive(list, "ferret", 0))
    }

    @Test
    fun retiringKeepsTheIdAndDropsPreselection() {
        val withDefault = ChoiceEdits.preselected(list, "cat", single = true)
        val retired = ChoiceEdits.retired(withDefault, "cat")
        val cat = retired.single { it.id == "cat" }
        assertTrue(cat.retired)
        assertFalse(cat.preselected)
        assertEquals(listOf("cow", "dog"), retired.activeChoices.map { it.id })
        assertEquals(listOf("ferret", "cat"), retired.retiredChoices.map { it.id })
        val field = FieldDef(id = "pet", label = "Pet", type = FieldType.DROPDOWN, choices = retired)
        assertNull(field.preselectedSingleChoice())
    }

    @Test
    fun restoringBringsBackTheOriginalIdWithoutReplacingTheDefault() {
        val withDefault = ChoiceEdits.preselected(list, "cow", single = true)
        val restored = ChoiceEdits.restored(withDefault, "ferret")
        val ferret = restored.single { it.id == "ferret" }
        assertFalse(ferret.retired)
        assertFalse(ferret.preselected)
        assertEquals(listOf("cow", "cat", "dog", "ferret"), restored.activeChoices.map { it.id })
        val field = FieldDef(id = "pet", label = "Pet", type = FieldType.DROPDOWN, choices = restored)
        assertEquals("cow", field.preselectedSingleChoice())
    }

    @Test
    fun singleChoiceKeepsOnePreselectedOption() {
        val first = ChoiceEdits.preselected(list, "cow", single = true)
        val moved = ChoiceEdits.preselected(first, "dog", single = true)
        assertEquals(listOf("dog"), moved.filter { it.preselected }.map { it.id })
        // A retired option can never be made the default.
        assertEquals(moved, ChoiceEdits.preselected(moved, "ferret", single = true))
    }

    @Test
    fun multipleChoiceCanPreselectEveryOptionAndRemoveThemOneAtATime() {
        var choices = list
        listOf("cow", "cat", "dog").forEach { choices = ChoiceEdits.preselected(choices, it, single = false) }
        val field = FieldDef(id = "pet", label = "Pet", type = FieldType.MULTIPLE, choices = choices)
        assertEquals(setOf("cow", "cat", "dog"), field.preselectedMultipleChoices())

        val oneRemoved = field.copy(choices = ChoiceEdits.notPreselected(choices, "cat"))
        assertEquals(setOf("cow", "dog"), oneRemoved.preselectedMultipleChoices())
    }

    @Test
    fun changingDefaultsNeverTouchesSavedAnswers() {
        val stored = """{"pet":["cat"]}"""
        val before = FieldDef(id = "pet", label = "Pet", type = FieldType.MULTIPLE, choices = list)
        val after = before.copy(choices = ChoiceEdits.preselected(list, "dog", single = false))
        val values = EntryValues.decode(stored)
        assertEquals(EntryValues.displayValue(before, values), EntryValues.displayValue(after, values))
        assertEquals(JsonArray(listOf(JsonPrimitive("cat"))), values["pet"])
    }

    @Test
    fun namesAreCheckedAgainstActiveAndRetiredOptions() {
        assertEquals(ChoiceNameCheck.ActiveDuplicate(cat), list.checkName(" cat "))
        assertEquals(ChoiceNameCheck.RetiredDuplicate(ferret), list.checkName("FERRET"))
        assertEquals(ChoiceNameCheck.Available, list.checkName("Hamster"))
        // Renaming a row to its own name (another capitalization) is fine.
        assertEquals(ChoiceNameCheck.Available, list.checkName("CAT", exceptId = "cat"))
    }

    @Test
    fun aNewOptionNamedLikeADeletedOneGetsANewIdAndNoOldAnswers() {
        val deleted = ChoiceEdits.withoutId(list, "cat")
        val storedBefore = EntryValues.withoutOption("""{"pet":"cat"}""", "pet", "cat")
        assertEquals(ChoiceNameCheck.Available, deleted.checkName("Cat"))
        val fresh = ChoiceOption.createNew("Cat")
        assertNotEquals("cat", fresh.id)
        val field = FieldDef(id = "pet", label = "Pet", type = FieldType.DROPDOWN, choices = ChoiceEdits.withNew(deleted, fresh))
        assertNull(EntryValues.displayValue(field, EntryValues.decode(storedBefore)))
    }

    @Test
    fun aNewOptionGoesAfterTheActiveOnes() {
        val added = ChoiceEdits.withNew(list, ChoiceOption(id = "pig", label = "Pig"))
        assertEquals(listOf("cow", "cat", "dog", "pig", "ferret"), added.map { it.id })
    }

    @Test
    fun duplicateLabelsAreDetectedIgnoringCase() {
        assertFalse(list.hasDuplicateLabels())
        assertTrue((list + ChoiceOption(id = "x", label = " dog")).hasDuplicateLabels())
    }
}
