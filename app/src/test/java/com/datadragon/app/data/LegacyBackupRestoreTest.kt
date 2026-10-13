package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restoring version-7 backups (written before field and option identities)
 * into a version-8 database, and version-8 backups back again. Merge keeps its
 * established Keep Current / Use Backup behavior; a legacy group is converted
 * to identities only when it is actually inserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyBackupRestoreTest {

    private lateinit var db: AppDatabase
    private val json = Json { ignoreUnknownKeys = true }

    private val currentPet = FieldDef(
        id = "pet-field",
        label = "Pet",
        type = FieldType.DROPDOWN,
        choices = listOf(ChoiceOption("cow-id", "Cow"), ChoiceOption("cat-id", "Cat", preselected = true)),
    )
    private val currentSchema = FormMarkdownParser.encodeFields(listOf(currentPet))
    private val currentValues = """{"pet-field":"cat-id"}"""

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()
    }

    @After
    fun tearDown() = db.close()

    /** A version-7 file: forms keyed by label, options as text, Idea Categories as text. */
    private fun version7Backup(): BackupFile {
        val payload = BackupPayload(
            forms = listOf(
                BackupLog(
                    uuid = "form-uuid",
                    name = "Pets (backup)",
                    createdAt = 1,
                    schemaJson = """[{"label":"Pet","type":"DROPDOWN","options":["Cow","Cat","Cat"],"topAnswerPreselected":true}]""",
                    sortTimestampFieldId = null,
                    entries = listOf(
                        BackupEntry(createdAt = "2026-09-01T10:00:00-05:00", valuesJson = """{"Pet":"Cat"}"""),
                        BackupEntry(createdAt = "2026-09-02T10:00:00-05:00", valuesJson = """{"Pet":"Hamster"}"""),
                    ),
                ),
                BackupLog(
                    uuid = "new-form-uuid",
                    name = "Only in the backup",
                    createdAt = 2,
                    schemaJson = """[{"label":"Mood","type":"TEXT"}]""",
                    entries = listOf(BackupEntry(createdAt = "2026-09-03T10:00:00-05:00", valuesJson = """{"Mood":"calm"}""")),
                ),
            ),
            ideaLogs = listOf(
                BackupIdeaLog(
                    "idea-uuid", "Ideas", 1,
                    """[{"id":"kind","label":"Kind","type":"DROPDOWN","options":["Home","Work"]}]""",
                    false, false, false, 20, null, true,
                    listOf(BackupIdeaEntry("2026-09-01T10:00:00-05:00", null, """{"kind":"Work"}""", false, false)),
                ),
            ),
        )
        val encoded = BackupCodec.encode(
            BackupFile(
                backupId = "legacy-backup",
                exportedAt = "2026-09-30T00:00:00Z",
                sourceAppVersion = "v7-app",
                roomSchemaVersion = 23,
                includedCategories = listOf(BackupCategory.FORMS, BackupCategory.IDEA_LOGS),
                payload = payload,
            ),
        )
        val asVersion7 = encoded.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 7")
        return BackupCodec.decode(asVersion7).also { assertEquals(7, it.version) }
    }

    private suspend fun seedCurrentData(): Long {
        val templateId = db.logTemplateDao().insert(
            LogTemplate(uuid = "form-uuid", name = "Pets", createdAt = 1, schemaJson = currentSchema),
        )
        db.logEntryDao().insert(
            LogEntry(templateId = templateId, createdAt = "2026-10-01T10:00:00-05:00", valuesJson = currentValues),
        )
        db.ideaLogDao().insert(
            IdeaLog(
                uuid = "idea-uuid",
                name = "Current Ideas",
                createdAt = 1,
                fieldsJson = """[{"id":"kind","label":"Kind","type":"DROPDOWN","choices":[{"id":"home-id","label":"Home"}]}]""",
            ),
        )
        // Unrelated data that no restore here may touch.
        db.logTemplateDao().insert(LogTemplate(uuid = "other-form", name = "Other", createdAt = 3, schemaJson = "[]"))
        db.checklistDao().insertChecklist(Checklist(uuid = "list-uuid", name = "Groceries", createdAt = 1))
        return templateId
    }

    private suspend fun storedForm(uuid: String): Pair<List<FieldDef>, List<String>> {
        val template = db.logTemplateDao().getByUuid(uuid)!!
        val fields = json.decodeFromString(ListSerializer(FieldDef.serializer()), template.schemaJson)
        return fields to db.logEntryDao().getForTemplateOnce(template.id).sortedBy { it.createdAt }.map { it.valuesJson }
    }

    @Test
    fun mergeReportsTheLegacyGroupsAsConflictsAndKeepCurrentChangesNothing() = runBlocking {
        seedCurrentData()
        val repository = BackupRepository(db)
        val backup = version7Backup()

        val conflicts = repository.preflight(backup, RestoreMode.MERGE).conflicts
        assertEquals(
            setOf("FORMS:form-uuid", "IDEA_LOGS:idea-uuid"),
            conflicts.map { it.id }.toSet(),
        )

        repository.restore(
            backup,
            RestoreMode.MERGE,
            conflictChoices = conflicts.associate { it.id to RestoreConflictChoice.KEEP_CURRENT },
        )

        // The current form, its UUIDs and its answers are exactly as they were.
        val template = db.logTemplateDao().getByUuid("form-uuid")!!
        assertEquals("Pets", template.name)
        assertEquals(currentSchema, template.schemaJson)
        assertEquals(listOf(currentValues), storedForm("form-uuid").second)
        assertTrue(db.ideaLogDao().getByUuid("idea-uuid")!!.fieldsJson.contains("home-id"))

        // A form only in the backup is added, converted to identities.
        val (added, addedValues) = storedForm("new-form-uuid")
        assertTrue(added.single().id.isNotBlank())
        assertEquals("calm", EntryValues.rawValue(EntryValues.decode(addedValues.single()), added.single()))

        // Unrelated data is untouched.
        assertEquals("Other", db.logTemplateDao().getByUuid("other-form")!!.name)
        assertEquals("Groceries", db.checklistDao().getChecklistByUuid("list-uuid")!!.name)
    }

    @Test
    fun mergeUseBackupRestoresTheBackupVersionUpgradedToIdentities() = runBlocking {
        seedCurrentData()
        val repository = BackupRepository(db)
        val backup = version7Backup()
        val conflicts = repository.preflight(backup, RestoreMode.MERGE).conflicts

        repository.restore(
            backup,
            RestoreMode.MERGE,
            conflictChoices = conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP },
        )

        val template = db.logTemplateDao().getByUuid("form-uuid")!!
        assertEquals("Pets (backup)", template.name)
        val (fields, values) = storedForm("form-uuid")
        val pet = fields.single()
        assertTrue(pet.id.isNotBlank())
        assertFalse(FieldIdentityMigration.formNeedsMigration(template.schemaJson))
        // Duplicate "Cat" merged, the top-answer default kept, the unmatched answer retired.
        assertEquals(listOf("Cow", "Cat"), pet.activeChoices.map { it.label })
        assertEquals(listOf("Hamster"), pet.retiredChoices.map { it.label })
        assertEquals(pet.choices[0].id, pet.preselectedSingleChoice())
        assertEquals(
            listOf("Cat", "Hamster"),
            values.map { EntryValues.displayValue(pet, EntryValues.decode(it)) },
        )

        val idea = db.ideaLogDao().getByUuid("idea-uuid")!!
        val kind = json.decodeFromString(ListSerializer(IdeaFieldDef.serializer()), idea.fieldsJson).single()
        assertEquals(listOf("Home", "Work"), kind.choices.map { it.label })
        val ideaValues = db.ideaEntryDao().getForLogOnce(idea.id).single().valuesJson
        assertEquals("Work", IdeaValues.displayValue(IdeaValues.decode(ideaValues), kind))

        assertEquals("Other", db.logTemplateDao().getByUuid("other-form")!!.name)
        assertEquals("Groceries", db.checklistDao().getChecklistByUuid("list-uuid")!!.name)
    }

    @Test
    fun replaceFromAVersion7BackupUpgradesEveryFormAndLeavesOtherCategoriesAlone() = runBlocking {
        seedCurrentData()
        val repository = BackupRepository(db)

        repository.restore(version7Backup(), RestoreMode.REPLACE)

        assertEquals(null, db.logTemplateDao().getByUuid("other-form"))
        val (fields, values) = storedForm("form-uuid")
        assertEquals(
            listOf("Cat", "Hamster"),
            values.map { EntryValues.displayValue(fields.single(), EntryValues.decode(it)) },
        )
        assertTrue(storedForm("new-form-uuid").first.single().id.isNotBlank())
        // Lists were not part of the backup, so they are untouched.
        assertEquals("Groceries", db.checklistDao().getChecklistByUuid("list-uuid")!!.name)
    }

    @Test
    fun aVersion8BackupRoundTripsEveryIdentityThroughReplace() = runBlocking {
        seedCurrentData()
        val repository = BackupRepository(db)
        val backup = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        assertEquals(BackupFile.VERSION, backup.version)

        repository.restore(backup, RestoreMode.REPLACE)

        val template = db.logTemplateDao().getByUuid("form-uuid")!!
        assertEquals(currentSchema, template.schemaJson)
        assertEquals(listOf(currentValues), storedForm("form-uuid").second)
        assertTrue(db.ideaLogDao().getByUuid("idea-uuid")!!.fieldsJson.contains("home-id"))
        // Merging the same backup again finds nothing to change.
        assertTrue(repository.preflight(backup, RestoreMode.MERGE).conflicts.isEmpty())
    }
}
