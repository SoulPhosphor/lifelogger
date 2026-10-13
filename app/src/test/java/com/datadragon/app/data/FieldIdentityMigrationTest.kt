package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time conversion from label-keyed Forms data to permanent field and
 * option identities (and Idea Category identities). Ids are drawn from a
 * deterministic counter so every assertion can name them.
 */
class FieldIdentityMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun ids(): () -> String {
        var next = 0
        return { "id-${++next}" }
    }

    private fun fields(schemaJson: String): List<FieldDef> =
        json.decodeFromString(ListSerializer(FieldDef.serializer()), schemaJson)

    private fun ideaFields(fieldsJson: String): List<IdeaFieldDef> =
        json.decodeFromString(ListSerializer(IdeaFieldDef.serializer()), fieldsJson)

    private fun form(
        schema: String,
        entries: List<String> = emptyList(),
        calendars: List<String> = emptyList(),
        sort: String? = null,
    ) = FieldIdentityMigration.FormData(schema, entries, calendars, sort)

    @Test
    fun everyFieldGetsAnIdAndItsValuesMoveFromTheLabelToThatId() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Mood","type":"TEXT"},{"label":"Hours","type":"NUMBER"}]""",
                entries = listOf("""{"Mood":"calm","Hours":"7","__notes__":"slept well"}"""),
            ),
            ids(),
        )
        val schema = fields(migrated.schemaJson)
        assertEquals(listOf("id-1", "id-2"), schema.map { it.id })
        assertEquals(listOf("Mood", "Hours"), schema.map { it.label })
        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals("calm", values["id-1"]!!.jsonPrimitive.content)
        assertEquals("7", values["id-2"]!!.jsonPrimitive.content)
        assertEquals("slept well", EntryValues.notes(values))
        assertFalse("Mood" in values)
        assertFalse("Hours" in values)
    }

    @Test
    fun optionsBecomeIdsAndSavedAnswersPointAtThem() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"DROPDOWN","options":["Cow","Cat","Dog"]},""" +
                    """{"label":"Seen","type":"MULTIPLE","options":["Cow","Cat","Dog"]}]""",
                entries = listOf("""{"Pet":"Cat","Seen":["Cow","Dog"]}"""),
            ),
            ids(),
        )
        val (pet, seen) = fields(migrated.schemaJson)
        assertEquals(listOf("Cow", "Cat", "Dog"), pet.choices.map { it.label })
        assertEquals(listOf("Cow", "Cat", "Dog"), seen.choices.map { it.label })
        // Different fields never share an option id, even with the same name.
        assertTrue(pet.choices.map { it.id }.intersect(seen.choices.map { it.id }.toSet()).isEmpty())
        assertTrue(pet.legacyOptions.isEmpty() && seen.legacyOptions.isEmpty())

        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals(pet.choices[1].id, EntryValues.rawValue(values, pet))
        assertEquals(listOf(seen.choices[0].id, seen.choices[2].id), EntryValues.stringList(values, seen))
        assertEquals("Cat", EntryValues.displayValue(pet, values))
        assertEquals("Cow, Dog", EntryValues.displayValue(seen, values))
    }

    @Test
    fun duplicateOptionsMergeIntoTheFirstOccurrence() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"MULTIPLE","options":["Cow","Cat","Dog","cat "]},""" +
                    """{"label":"Best","type":"DROPDOWN","options":["Cow","Cat","Dog","Cat"]}]""",
                entries = listOf(
                    """{"Pet":["Cat","cat ","Dog"],"Best":"Cat"}""",
                    """{"Pet":["cat "],"Best":"Cat"}""",
                ),
            ),
            ids(),
        )
        val (pet, best) = fields(migrated.schemaJson)
        assertEquals(listOf("Cow", "Cat", "Dog"), pet.choices.map { it.label })
        assertEquals(listOf("Cow", "Cat", "Dog"), best.choices.map { it.label })
        val petCat = pet.choices[1].id
        val bestCat = best.choices[1].id

        val first = EntryValues.decode(migrated.entryValues[0])
        // The merged option appears once; every other selection is kept.
        assertEquals(listOf(petCat, pet.choices[2].id), EntryValues.stringList(first, pet))
        assertEquals(bestCat, EntryValues.rawValue(first, best))
        val second = EntryValues.decode(migrated.entryValues[1])
        assertEquals(listOf(petCat), EntryValues.stringList(second, pet))
        assertEquals(bestCat, EntryValues.rawValue(second, best))
    }

    @Test
    fun mergingKeepsPreselectionWhenEitherDuplicateHadIt() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"MULTIPLE","allowPreselectedMultipleChoices":true,""" +
                    """"options":["Cow","Cat","*Cat","Dog"]}]""",
            ),
            ids(),
        )
        val pet = fields(migrated.schemaJson).single()
        assertEquals(listOf("Cow", "Cat", "Dog"), pet.choices.map { it.label })
        assertEquals(listOf(false, true, false), pet.choices.map { it.preselected })
    }

    @Test
    fun answersThatNoLongerMatchAnOptionBecomeRetiredOptions() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"MULTIPLE","options":["Cow","Dog"]},""" +
                    """{"label":"Best","type":"DROPDOWN","options":["Cow","Dog"]}]""",
                entries = listOf(
                    """{"Pet":["Cow","Hamster"],"Best":"Ferret"}""",
                    """{"Pet":["hamster"],"Best":"Ferret"}""",
                ),
            ),
            ids(),
        )
        val (pet, best) = fields(migrated.schemaJson)
        assertEquals(listOf("Cow", "Dog"), pet.activeChoices.map { it.label })
        assertEquals(listOf("Hamster"), pet.retiredChoices.map { it.label })
        assertEquals(listOf("Ferret"), best.retiredChoices.map { it.label })
        val hamster = pet.retiredChoices.single().id
        val ferret = best.retiredChoices.single().id

        val first = EntryValues.decode(migrated.entryValues[0])
        assertEquals(listOf(pet.choices[0].id, hamster), EntryValues.stringList(first, pet))
        assertEquals(ferret, EntryValues.rawValue(first, best))
        // The same historical value, however it was capitalized, joins the same retired option.
        assertEquals(listOf(hamster), EntryValues.stringList(EntryValues.decode(migrated.entryValues[1]), pet))
        assertEquals("Cow, Hamster", EntryValues.displayValue(pet, first))
    }

    @Test
    fun multipleChoiceAsterisksBecomePreselectionOnlyWhereThatSwitchWasOn() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"On","type":"MULTIPLE","allowPreselectedMultipleChoices":true,""" +
                    """"options":["*Red","Blue"]},""" +
                    """{"label":"Off","type":"MULTIPLE","options":["*Red","Blue"]}]""",
                entries = listOf("""{"On":["Red"],"Off":["*Red"]}"""),
            ),
            ids(),
        )
        val (on, off) = fields(migrated.schemaJson)
        assertEquals(listOf("Red", "Blue"), on.choices.map { it.label })
        assertEquals(listOf(true, false), on.choices.map { it.preselected })
        // With the switch off, the asterisk was part of the option's real name.
        assertEquals(listOf("*Red", "Blue"), off.choices.map { it.label })
        assertEquals(listOf(false, false), off.choices.map { it.preselected })
        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals(listOf(on.choices[0].id), EntryValues.stringList(values, on))
        assertEquals(listOf(off.choices[0].id), EntryValues.stringList(values, off))
        assertFalse(migrated.schemaJson.contains("allowPreselectedMultipleChoices"))
    }

    @Test
    fun singleChoiceTopAnswerBecomesThatOptionsPreselection() {
        val migrated = FieldIdentityMigration.migrateForm(
            form("""[{"label":"Pet","type":"DROPDOWN","options":["Cow","Cat"],"topAnswerPreselected":true}]"""),
            ids(),
        )
        val pet = fields(migrated.schemaJson).single()
        assertEquals(pet.choices[0].id, pet.preselectedSingleChoice())
        assertFalse(migrated.schemaJson.contains("topAnswerPreselected"))
        // Reordering afterwards keeps the default on Cow, not on whatever is first.
        val reordered = pet.copy(choices = ChoiceEdits.movedActive(pet.choices, pet.choices[0].id, 1))
        assertEquals(pet.choices[0].id, reordered.preselectedSingleChoice())
    }

    @Test
    fun fieldsSharingALabelAreNeverMergedAndBothKeepTheValue() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Note","type":"TEXT"},{"label":"Note","type":"TEXT"}]""",
                entries = listOf("""{"Note":"hello"}"""),
            ),
            ids(),
        )
        val schema = fields(migrated.schemaJson)
        assertEquals(2, schema.size)
        assertTrue(schema[0].id != schema[1].id)
        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals("hello", EntryValues.rawValue(values, schema[0]))
        assertEquals("hello", EntryValues.rawValue(values, schema[1]))
    }

    @Test
    fun valuesBelongingToNoFieldAreKeptUntouched() {
        val original = """{"Mood":"calm","Removed field":"old data"}"""
        val migrated = FieldIdentityMigration.migrateForm(
            form("""[{"label":"Mood","type":"TEXT"}]""", entries = listOf(original)),
            ids(),
        )
        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals("old data", values["Removed field"]!!.jsonPrimitive.content)
    }

    @Test
    fun sortAndCalendarReferencesMoveFromLabelsToIds() {
        val calendar = CalendarConfigCodec.encode(
            CalendarConfig(
                sourceField = "Pet",
                calculationRule = CalendarCalcRules.COUNT_MATCHING,
                matchOption = "Cat",
                dayTimestampField = "When",
            ),
        )
        val untouchedCalendar = CalendarConfigCodec.encode(CalendarConfig(sourceLogFrequency = true))
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"When","type":"DATE"},{"label":"Pet","type":"DROPDOWN","options":["Cow","Cat"]}]""",
                calendars = listOf(calendar, untouchedCalendar, ""),
                sort = "When",
            ),
            ids(),
        )
        val (whenField, pet) = fields(migrated.schemaJson)
        assertEquals(whenField.id, migrated.sortTimestampField)
        val config = CalendarConfigCodec.decode(migrated.calendarConfigs[0])
        assertEquals(pet.id, config.sourceField)
        assertEquals(whenField.id, config.dayTimestampField)
        assertEquals(pet.choices[1].id, config.matchOption)
        assertEquals(untouchedCalendar, migrated.calendarConfigs[1])
        assertEquals("", migrated.calendarConfigs[2])
    }

    @Test
    fun aCountMatchingTargetThatNoLongerExistsIsKeptAsARetiredOption() {
        val calendar = CalendarConfigCodec.encode(CalendarConfig(sourceField = "Pet", matchOption = "Parrot"))
        val migrated = FieldIdentityMigration.migrateForm(
            form("""[{"label":"Pet","type":"DROPDOWN","options":["Cow"]}]""", calendars = listOf(calendar)),
            ids(),
        )
        val pet = fields(migrated.schemaJson).single()
        val parrot = pet.retiredChoices.single()
        assertEquals("Parrot", parrot.label)
        assertEquals(parrot.id, CalendarConfigCodec.decode(migrated.calendarConfigs.single()).matchOption)
    }

    @Test
    fun runningTheMigrationAgainNeverChangesAnIdentity() {
        val first = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"DROPDOWN","options":["Cow","Cat"]}]""",
                entries = listOf("""{"Pet":"Cat"}"""),
                sort = null,
            ),
            ids(),
        )
        assertFalse(FieldIdentityMigration.formNeedsMigration(first.schemaJson))
        val second = FieldIdentityMigration.migrateForm(first, ids())
        assertSame(first, second)
    }

    @Test
    fun aPartlyMigratedFormOnlyConvertsTheFieldsWithoutIds() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"id":"kept","label":"Mood","type":"TEXT"},{"label":"Hours","type":"NUMBER"}]""",
                entries = listOf("""{"kept":"calm","Hours":"7"}"""),
            ),
            ids(),
        )
        val schema = fields(migrated.schemaJson)
        assertEquals("kept", schema[0].id)
        assertEquals("id-1", schema[1].id)
        val values = EntryValues.decode(migrated.entryValues.single())
        assertEquals("calm", values["kept"]!!.jsonPrimitive.content)
        assertEquals("7", values["id-1"]!!.jsonPrimitive.content)
    }

    @Test
    fun lowercaseTypeTokensFromOldBackupsAreUnderstoodAndKept() {
        val migrated = FieldIdentityMigration.migrateForm(
            form(
                schema = """[{"label":"Pet","type":"dropdown","options":["Cow"]}]""",
                entries = listOf("""{"Pet":"Cow"}"""),
            ),
            ids(),
        )
        val field = (Json.parseToJsonElement(migrated.schemaJson) as JsonArray).single() as JsonObject
        assertEquals("dropdown", field["type"]!!.jsonPrimitive.content)
        assertNotNull(field["choices"])
        assertEquals(JsonPrimitive("id-1"), field["id"])
    }

    @Test
    fun anUnreadableSchemaIsLeftExactlyAsItWas() {
        val input = form("not json", entries = listOf("""{"a":"b"}"""))
        assertSame(input, FieldIdentityMigration.migrateForm(input, ids()))
    }

    @Test
    fun ideaCategoriesBecomeIdsWithDuplicatesMergedAndMissingOnesRetired() {
        val migrated = FieldIdentityMigration.migrateIdeaLog(
            FieldIdentityMigration.IdeaData(
                fieldsJson = """[{"id":"kind","label":"Kind","type":"DROPDOWN","options":["Home","Work","home"]},""" +
                    """{"id":"text","label":"Text","type":"MULTILINE"}]""",
                entryValues = listOf(
                    """{"kind":"home","text":"one"}""",
                    """{"kind":"Garden","text":"two"}""",
                ),
            ),
            ids(),
        )
        val kind = ideaFields(migrated.fieldsJson).first()
        assertEquals("kind", kind.id)
        assertEquals(listOf("Home", "Work"), kind.choices.activeChoices.map { it.label })
        assertEquals(listOf("Garden"), kind.choices.retiredChoices.map { it.label })
        assertTrue(kind.legacyOptions.isEmpty())
        val first = IdeaValues.decode(migrated.entryValues[0])
        assertEquals(kind.choices[0].id, IdeaValues.rawValue(first, kind))
        assertEquals("Home", IdeaValues.displayValue(first, kind))
        assertEquals("one", first["text"]!!.jsonPrimitive.content)
        val second = IdeaValues.decode(migrated.entryValues[1])
        assertEquals("Garden", IdeaValues.displayValue(second, kind))

        // A second run changes nothing.
        assertFalse(FieldIdentityMigration.ideaNeedsMigration(migrated.fieldsJson))
        assertSame(migrated, FieldIdentityMigration.migrateIdeaLog(migrated, ids()))
    }

    @Test
    fun anEntryWithNothingToConvertKeepsItsStoredTextByteForByte() {
        val untouched = """{ "__notes__" : "spacing kept" }"""
        val migrated = FieldIdentityMigration.migrateForm(
            form("""[{"label":"Mood","type":"TEXT"}]""", entries = listOf(untouched)),
            ids(),
        )
        assertEquals(untouched, migrated.entryValues.single())
        assertNull(migrated.sortTimestampField)
    }
}
