package com.datadragon.app.ui.screens

import com.datadragon.app.data.DEFAULT_IDEA_LINES
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.IdeaFieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdeaLogEditorTest {

    @Test
    fun aNewIdeaLogStartsWithOneMultilineTextField() {
        val fields = startingIdeaFields()

        assertEquals(1, fields.size)
        assertEquals(IdeaFieldKind.TEXT, fields.single().kind)
        assertEquals(FieldType.MULTILINE, fields.single().toFieldDef().type)
    }

    @Test
    fun useDefaultFieldsAddsTitleAndTagsAroundTheExistingTextField() {
        val fields = startingIdeaFields()
        val originalTextId = fields.single().id

        addMissingDefaultFields(fields)

        assertEquals(
            listOf(IdeaFieldKind.TITLE, IdeaFieldKind.TEXT, IdeaFieldKind.TAGS),
            fields.map { it.kind },
        )
        assertEquals(listOf("Title", "Text", "Tags"), fields.map { it.label })
        // The field that was already there is the default Text field, not a duplicate.
        assertEquals(originalTextId, fields[1].id)
    }

    @Test
    fun useDefaultFieldsAddsNothingTwiceAndKeepsWhatIsAlreadyThere() {
        val fields = startingIdeaFields()
        fields.add(IdeaDraftField(label = "Source", kind = IdeaFieldKind.WEBPAGES))

        addMissingDefaultFields(fields)
        val afterFirst = fields.map { it.id }
        addMissingDefaultFields(fields)

        assertEquals(afterFirst, fields.map { it.id })
        assertEquals(4, fields.size)
        // The user's own field survives untouched.
        assertEquals(1, fields.count { it.kind == IdeaFieldKind.WEBPAGES })
    }

    @Test
    fun aFieldKeepsItsIdentityAcrossARenameAndRetype() {
        val field = IdeaDraftField(label = "Text", kind = IdeaFieldKind.TEXT)
        val id = field.id

        field.label = "Body"
        val def = field.toFieldDef()

        assertEquals(id, def.id)
        assertEquals("Body", def.label)
        // Round-tripping a saved field back into the editor keeps that identity.
        assertEquals(id, def.toDraft(sortByTimestamp = false).id)
    }

    @Test
    fun everyDraftGetsItsOwnIdentity() {
        assertNotEquals(IdeaDraftField().id, IdeaDraftField().id)
    }

    @Test
    fun onlyADateBearingFieldCanBeTheDefaultSortTimestamp() {
        val timeOnly = IdeaDraftField(label = "At", kind = IdeaFieldKind.TIME).apply {
            sortByTimestamp = true
        }
        assertNull(sortTimestampFieldIdOf(listOf(timeOnly)))

        val dated = IdeaDraftField(label = "Due", kind = IdeaFieldKind.DATE).apply {
            sortByTimestamp = true
        }
        assertEquals(dated.id, sortTimestampFieldIdOf(listOf(timeOnly, dated)))
    }

    @Test
    fun aBlankLineCountFallsBackToTwentyRatherThanZero() {
        assertEquals(DEFAULT_IDEA_LINES, clampedLineCount(""))
        assertEquals(DEFAULT_IDEA_LINES, clampedLineCount("   "))
        assertEquals(1, clampedLineCount("0"))
        assertEquals(1, clampedLineCount("1"))
        assertEquals(999, clampedLineCount("999"))
    }

    @Test
    fun categoriesNeedAtLeastOneOptionBeforeTheFieldIsValid() {
        val field = IdeaDraftField(label = "Kind", kind = IdeaFieldKind.CATEGORIES)
        assertEquals("Add at least one option.", field.validationHint())

        field.optionsText = "Home\nWork"
        assertNull(field.validationHint())
        assertEquals(listOf("Home", "Work"), field.toFieldDef().options)
    }

    @Test
    fun theMultilineOnlySettingsAreDroppedWhenTheTypeIsNotMultiline() {
        val field = IdeaDraftField(label = "Title", kind = IdeaFieldKind.TITLE).apply {
            wordCount = true
            characterCount = true
            allowCopying = true
            truncateInEntireCard = true
        }

        val def = field.toFieldDef()

        assertEquals(false, def.wordCount)
        assertEquals(false, def.characterCount)
        assertEquals(false, def.allowCopying)
        assertEquals(false, def.truncateInEntireCard)
    }
}
