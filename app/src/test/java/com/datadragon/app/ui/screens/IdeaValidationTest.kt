package com.datadragon.app.ui.screens

import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaValidationTest {

    private fun field(
        id: String,
        type: FieldType,
        required: Boolean = false,
        includeInPreview: Boolean = true,
    ) = IdeaFieldDef(
        id = id,
        label = id,
        type = type,
        required = required,
        includeInPreview = includeInPreview,
    )

    @Test
    fun requiredFieldsBlockSavingInVisibleOrder() {
        val fields = listOf(
            field("optional", FieldType.TEXT),
            field("title", FieldType.TEXT, required = true),
            field("done", FieldType.MULTILINE, required = true),
            field("tags", FieldType.TAGS, required = true),
        )

        val blocking = blockingIdeaFieldIds(
            fields = fields,
            textValues = mapOf("done" to "written"),
            tagValues = emptyMap(),
        )

        assertEquals(listOf("title", "tags"), blocking)
    }

    @Test
    fun whitespaceDoesNotSatisfyARequiredField() {
        val fields = listOf(field("title", FieldType.TEXT, required = true))

        val blocking = blockingIdeaFieldIds(fields, mapOf("title" to "   "), emptyMap())

        assertEquals(listOf("title"), blocking)
    }

    @Test
    fun oneSavedTagSatisfiesARequiredTagsField() {
        val fields = listOf(field("tags", FieldType.TAGS, required = true))

        assertTrue(
            blockingIdeaFieldIds(fields, emptyMap(), mapOf("tags" to listOf("Android"))).isEmpty(),
        )
    }

    @Test
    fun anOptionalWebpageMayBeBlankButNeverInvalid() {
        val fields = listOf(field("link", FieldType.WEBPAGE))

        assertTrue(blockingIdeaFieldIds(fields, emptyMap(), emptyMap()).isEmpty())
        assertTrue(
            blockingIdeaFieldIds(fields, mapOf("link" to "example.com"), emptyMap()).isEmpty(),
        )
        assertEquals(
            listOf("link"),
            blockingIdeaFieldIds(fields, mapOf("link" to "not a webpage"), emptyMap()),
        )
    }

    @Test
    fun aRequiredWebpageMustBeThereAndValid() {
        val fields = listOf(field("link", FieldType.WEBPAGE, required = true))

        assertEquals(listOf("link"), blockingIdeaFieldIds(fields, emptyMap(), emptyMap()))
        assertTrue(
            blockingIdeaFieldIds(fields, mapOf("link" to "https://example.com/x"), emptyMap())
                .isEmpty(),
        )
    }

    @Test
    fun collectedValuesAreKeyedByFieldIdAndDropEmptyFields() {
        val title = field("title", FieldType.TEXT)
        val tags = field("tags", FieldType.TAGS)
        val empty = field("empty", FieldType.MULTILINE)

        val values = collectIdeaValues(
            fields = listOf(title, tags, empty),
            textValues = mapOf("title" to "  An idea  ", "empty" to "   "),
            tagValues = mapOf("tags" to listOf("Android", "Kotlin")),
        )

        assertEquals(setOf("title", "tags"), values.keys)
        val decoded = IdeaValues.decode(EntryValues.encode(values))
        assertEquals("An idea", IdeaValues.rawValue(decoded, title))
        assertEquals(listOf("Android", "Kotlin"), IdeaValues.tags(decoded, tags))
    }

    @Test
    fun previewModeHidesOptedOutFieldsButTheDetailViewShowsEverything() {
        val shown = field("shown", FieldType.TEXT)
        val hidden = field("hidden", FieldType.TEXT, includeInPreview = false)
        val fields = listOf(shown, hidden)
        val values = IdeaValues.decode(
            EntryValues.encode(
                collectIdeaValues(
                    fields = fields,
                    textValues = mapOf("shown" to "on the card", "hidden" to "detail only"),
                    tagValues = emptyMap(),
                )
            )
        )

        assertEquals(
            listOf("shown"),
            visibleIdeaFields(fields, values, IdeaDisplayMode.PREVIEW).map { it.id },
        )
        // Entire Idea Card mode and the full detail view both ignore the opt-out.
        assertEquals(
            listOf("shown", "hidden"),
            visibleIdeaFields(fields, values, IdeaDisplayMode.ENTIRE_CARD).map { it.id },
        )
        assertEquals(
            listOf("shown", "hidden"),
            visibleIdeaFields(fields, values, IdeaDisplayMode.DETAIL).map { it.id },
        )
    }

    @Test
    fun aFieldWithNoValueIsNeverDrawnInAnyMode() {
        val filled = field("filled", FieldType.TEXT)
        val blank = field("blank", FieldType.TEXT)
        val fields = listOf(filled, blank)
        val values = IdeaValues.decode(
            EntryValues.encode(
                collectIdeaValues(fields, mapOf("filled" to "here"), emptyMap()),
            )
        )

        IdeaDisplayMode.entries.forEach { mode ->
            assertEquals(listOf("filled"), visibleIdeaFields(fields, values, mode).map { it.id })
        }
    }
}
