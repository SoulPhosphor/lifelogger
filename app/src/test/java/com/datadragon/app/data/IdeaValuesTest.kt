package com.datadragon.app.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class IdeaValuesTest {

    private fun multiline(
        id: String = "f1",
        wordCount: Boolean = false,
        characterCount: Boolean = false,
    ) = IdeaFieldDef(
        id = id,
        label = "Text",
        type = FieldType.MULTILINE,
        wordCount = wordCount,
        characterCount = characterCount,
    )

    private fun tagsField(id: String = "t1") =
        IdeaFieldDef(id = id, label = "Tags", type = FieldType.TAGS)

    @Test
    fun wordCountIgnoresRepeatedWhitespace() {
        assertEquals(0, IdeaValues.wordCount(""))
        assertEquals(0, IdeaValues.wordCount("   \n  "))
        assertEquals(1, IdeaValues.wordCount("  hello  "))
        assertEquals(3, IdeaValues.wordCount("one   two\n\nthree"))
    }

    @Test
    fun characterCountIncludesSpacesAndLineBreaks() {
        assertEquals(0, IdeaValues.characterCount(""))
        assertEquals(3, IdeaValues.characterCount("a b"))
        assertEquals(3, IdeaValues.characterCount("a\nb"))
    }

    @Test
    fun countsLineIsAbsentUntilACountIsSwitchedOn() {
        assertNull(IdeaValues.countsLine(multiline(), "some words here"))
    }

    @Test
    fun countsUseSingularAndPluralCorrectly() {
        assertEquals("1 Word", IdeaValues.countsLine(multiline(wordCount = true), "hello"))
        assertEquals("2 Words", IdeaValues.countsLine(multiline(wordCount = true), "hello there"))
        assertEquals(
            "1 Character",
            IdeaValues.countsLine(multiline(characterCount = true), "x"),
        )
        assertEquals(
            "2 Characters",
            IdeaValues.countsLine(multiline(characterCount = true), "xy"),
        )
    }

    @Test
    fun bothCountsShareOneLineSeparatedByACentredDot() {
        val field = multiline(wordCount = true, characterCount = true)
        val text = "one two three"
        val expected = NumberFormat.getIntegerInstance(Locale.getDefault()).let { format ->
            "${format.format(3)} Words · ${format.format(text.length)} Characters"
        }
        assertEquals(expected, IdeaValues.countsLine(field, text))
    }

    @Test
    fun valuesAreKeyedByFieldIdSoARenameKeepsThemAttached() {
        val field = multiline(id = "stable-id")
        val stored = IdeaValues.encode(mapOf(field.id to EntryValues.string("an idea")))
        val values = IdeaValues.decode(stored)

        assertEquals("an idea", IdeaValues.rawValue(values, field))
        // The same field under a new label still reads its stored value back.
        assertEquals("an idea", IdeaValues.rawValue(values, field.copy(label = "Renamed")))
    }

    @Test
    fun tagsKeepTheOrderTheyWereAddedIn() {
        val field = tagsField()
        val stored = IdeaValues.encode(
            mapOf(field.id to EntryValues.stringArray(listOf("Android", "Kotlin", "Compose"))),
        )
        val values = IdeaValues.decode(stored)

        assertEquals(listOf("Android", "Kotlin", "Compose"), IdeaValues.tags(values, field))
        assertEquals("Android, Kotlin, Compose", IdeaValues.displayValue(values, field))
        assertTrue(IdeaValues.hasValue(values, field))
    }

    @Test
    fun aFieldWithNothingStoredHasNoValue() {
        val empty = JsonObject(emptyMap())
        assertFalse(IdeaValues.hasValue(empty, multiline()))
        assertFalse(IdeaValues.hasValue(empty, tagsField()))
        assertNull(IdeaValues.displayValue(empty, multiline()))
    }

    @Test
    fun searchableTextCoversEveryFieldIncludingTags() {
        val text = multiline(id = "a")
        val tags = tagsField(id = "b")
        val values = IdeaValues.decode(
            IdeaValues.encode(
                mapOf(
                    text.id to EntryValues.string("a dragon idea"),
                    tags.id to EntryValues.stringArray(listOf("Android")),
                ),
            )
        )

        assertEquals(
            listOf("a dragon idea", "Android"),
            IdeaValues.searchableText(values, listOf(text, tags)),
        )
    }
}
