package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backups and field identities: a current backup carries every field and
 * option id, retirement, order and pre-selection unchanged through encode and
 * decode; a backup written before identities is converted on restore exactly
 * as the database upgrade converts installed data; and an older format can't
 * claim to carry identities.
 */
class BackupIdentityUpgradeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val pet = FieldDef(
        id = "pet-field",
        label = "Pet",
        type = FieldType.MULTIPLE,
        choices = listOf(
            ChoiceOption("dog", "Dog", preselected = true),
            ChoiceOption("cat", "Cat"),
            ChoiceOption("ferret", "Ferret", retired = true),
        ),
    )
    private val whenField = FieldDef(id = "when-field", label = "When", type = FieldType.DATE)

    private fun file(log: BackupLog): String = BackupCodec.encode(
        BackupFile.single(
            exportedAt = "2026-10-13T12:00:00Z",
            category = BackupCategory.FORMS,
            payload = BackupPayload(forms = listOf(log)),
        ),
    )

    private fun currentLog(): BackupLog = BackupCodec.logOf(
        LogTemplate(
            id = 4,
            uuid = "form-uuid",
            name = "Pets",
            createdAt = 100,
            schemaJson = FormMarkdownParser.encodeFields(listOf(pet, whenField)),
            sortTimestampFieldId = "when-field",
        ),
        listOf(
            LogEntry(
                id = 9,
                templateId = 4,
                createdAt = "2026-10-01T10:00:00-05:00",
                valuesJson = """{"pet-field":["dog","ferret"],"when-field":"2026-10-01"}""",
            ),
        ),
    )

    @Test
    fun aCurrentBackupRoundTripsEveryIdentityUnchanged() {
        val decoded = BackupCodec.decode(file(currentLog()))
        assertEquals(8, decoded.version)
        val log = decoded.logs.single()
        val fields = json.decodeFromString(ListSerializer(FieldDef.serializer()), log.schemaJson)
        assertEquals(listOf(pet, whenField), fields)
        assertEquals("when-field", log.sortTimestampFieldId)
        assertEquals("""{"pet-field":["dog","ferret"],"when-field":"2026-10-01"}""", log.entries.single().valuesJson)
        // Restoring it changes nothing.
        assertSame(log, BackupIdentityUpgrade.form(log))
    }

    @Test
    fun aLegacyBackupIsConvertedOnRestore() {
        val legacy = BackupLog(
            uuid = "form-uuid",
            name = "Pets",
            createdAt = 100,
            schemaJson = """[{"label":"When","type":"DATE"},""" +
                """{"label":"Pet","type":"DROPDOWN","options":["Dog","Cat","Dog"],"topAnswerPreselected":true}]""",
            sortTimestampFieldId = "When",
            entries = listOf(
                BackupEntry(createdAt = "2026-10-01T10:00:00-05:00", valuesJson = """{"When":"2026-10-01","Pet":"Cat"}"""),
                BackupEntry(createdAt = "2026-10-02T10:00:00-05:00", valuesJson = """{"Pet":"Hamster"}"""),
            ),
        )
        val upgraded = BackupIdentityUpgrade.form(legacy)
        val (whenOut, petOut) = json.decodeFromString(ListSerializer(FieldDef.serializer()), upgraded.schemaJson)
        assertEquals(whenOut.id, upgraded.sortTimestampFieldId)
        assertEquals(listOf("Dog", "Cat"), petOut.activeChoices.map { it.label })
        assertEquals(listOf("Hamster"), petOut.retiredChoices.map { it.label })
        assertEquals(petOut.choices[0].id, petOut.preselectedSingleChoice())
        val first = EntryValues.decode(upgraded.entries[0].valuesJson)
        assertEquals("Cat", EntryValues.displayValue(petOut, first))
        assertEquals("2026-10-01", EntryValues.rawValue(first, whenOut))
        assertEquals("Hamster", EntryValues.displayValue(petOut, EntryValues.decode(upgraded.entries[1].valuesJson)))
        // Everything that isn't converted JSON is carried over as-is.
        assertEquals(legacy.uuid, upgraded.uuid)
        assertEquals(legacy.entries.map { it.createdAt }, upgraded.entries.map { it.createdAt })
    }

    @Test
    fun aLegacyIdeaLogsCategoriesAreConvertedOnRestore() {
        val legacy = BackupIdeaLog(
            uuid = "idea-uuid",
            name = "Ideas",
            createdAt = 1,
            fieldsJson = """[{"id":"kind","label":"Kind","type":"DROPDOWN","options":["Home","Work"]}]""",
            automaticTimestamping = false,
            allowArchiving = false,
            showEntireIdeaCard = false,
            previewLines = 20,
            sortTimestampFieldId = null,
            sortNewestFirst = true,
            entries = listOf(BackupIdeaEntry("2026-10-01T10:00:00-05:00", null, """{"kind":"Work"}""", false, false)),
        )
        val upgraded = BackupIdentityUpgrade.ideaLog(legacy)
        val kind = json.decodeFromString(ListSerializer(IdeaFieldDef.serializer()), upgraded.fieldsJson).single()
        assertEquals(listOf("Home", "Work"), kind.choices.map { it.label })
        assertEquals("Work", IdeaValues.displayValue(IdeaValues.decode(upgraded.entries.single().valuesJson), kind))
        assertSame(upgraded, BackupIdentityUpgrade.ideaLog(upgraded))
    }

    @Test
    fun anOlderFormatCannotCarryIdentities() {
        val text = file(currentLog()).replace("\"version\": ${BackupFile.VERSION}", "\"version\": 7")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun aVersion7BackupWithoutIdentitiesStillDecodes() {
        val legacy = BackupLog(
            uuid = "form-uuid",
            name = "Pets",
            createdAt = 100,
            schemaJson = """[{"label":"Pet","type":"DROPDOWN","options":["Dog"],"choiceStyle":"displayed"}]""",
        )
        val text = file(legacy).replace("\"version\": ${BackupFile.VERSION}", "\"version\": 7")
        val decoded = BackupCodec.decode(text)
        assertEquals(7, decoded.version)
        assertTrue(FieldIdentityMigration.formNeedsMigration(decoded.logs.single().schemaJson))
    }

    @Test
    fun duplicateFieldOrOptionIdsInAVersion8BackupAreRejected() {
        val duplicateFields = currentLog().copy(
            schemaJson = FormMarkdownParser.encodeFields(listOf(pet, whenField.copy(id = pet.id))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidator.validate(BackupCodec.decode(file(duplicateFields)))
        }

        val duplicateOptions = currentLog().copy(
            schemaJson = FormMarkdownParser.encodeFields(
                listOf(pet.copy(choices = pet.choices + ChoiceOption("dog", "Second Dog")), whenField),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidator.validate(BackupCodec.decode(file(duplicateOptions)))
        }

        // A field without an id beside fields that have ids can never be matched
        // to its stored answers, so it is rejected too.
        val blankField = currentLog().copy(
            schemaJson = FormMarkdownParser.encodeFields(listOf(pet, whenField.copy(id = ""))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidator.validate(BackupCodec.decode(file(blankField)))
        }

        // Option identities without field ids are also the identity shape, so the
        // blank field ids are rejected rather than treated as legacy data.
        val choicesWithoutFieldIds = currentLog().copy(
            schemaJson = FormMarkdownParser.encodeFields(listOf(pet.copy(id = ""), whenField.copy(id = ""))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidator.validate(BackupCodec.decode(file(choicesWithoutFieldIds)))
        }

        // An empty "choices" list is still the identity shape.
        val emptyChoicesWithoutFieldIds = currentLog().copy(
            schemaJson = """[{"label":"Mood","type":"TEXT","choices":[]}]""",
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidator.validate(BackupCodec.decode(file(emptyChoicesWithoutFieldIds)))
        }

        // The well-formed backup passes the same validation.
        BackupRestoreValidator.validate(BackupCodec.decode(file(currentLog())))
    }

    @Test
    fun aVersion1BackupWithLowercaseTypesRestoresReadableFields() {
        val legacy = BackupLog(
            uuid = "form-uuid",
            name = "Mood",
            createdAt = 1,
            schemaJson = """[{"label":"Mood","type":"scale","from":1,"to":5}]""",
            entries = listOf(BackupEntry(createdAt = "2026-01-01T10:00:00-05:00", valuesJson = """{"Mood":"4"}""")),
        )
        val upgraded = BackupIdentityUpgrade.form(legacy)
        val mood = json.decodeFromString(ListSerializer(FieldDef.serializer()), upgraded.schemaJson).single()
        assertEquals(FieldType.SCALE, mood.type)
        assertEquals("4 / 5", EntryValues.displayValue(mood, EntryValues.decode(upgraded.entries.single().valuesJson)))
    }
}
