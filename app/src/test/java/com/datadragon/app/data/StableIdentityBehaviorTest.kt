package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StableIdentityBehaviorTest {

    private lateinit var db: AppDatabase
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cleanInstallCreatesEveryUuidIndexAndImmutabilityTrigger() {
        val sqlite = db.openHelper.readableDatabase
        val tables = listOf(
            "log_templates",
            "checklists",
            "idea_logs",
            "daily_lists",
            "daily_list_items",
            "clicker_logs",
            "clicker_cards",
            "color_presets",
        )
        tables.forEach { table ->
            sqlite.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_${table}_uuid'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("missing UUID index for $table", 1, cursor.getInt(0))
            }
            sqlite.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' " +
                    "AND name = 'prevent_${table}_uuid_update'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("missing UUID trigger for $table", 1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun renameEditReorderAndMoveDatePreserveEveryEstablishedIdentity() = runBlocking {
        val formDao = db.logTemplateDao()
        val formId = formDao.insert(
            LogTemplate(
                uuid = "form-uuid",
                name = "Original Form",
                createdAt = 1,
                schemaJson = "[]",
            ),
        )
        formDao.rename(formId, "Renamed Form", "# Renamed Form")
        formDao.updateSchema(formId, "[]", "# Edited Form")
        assertEquals("form-uuid", formDao.getById(formId)!!.uuid)

        val listDao = db.checklistDao()
        val listId = listDao.insertChecklist(
            Checklist(uuid = "list-uuid", name = "Original List", createdAt = 2),
        )
        listDao.renameChecklist(listId, "Renamed List")
        assertEquals("list-uuid", listDao.getChecklist(listId)!!.uuid)

        val ideaFields = listOf(
            IdeaFieldDef(id = "idea-field-a", label = "A", type = FieldType.TEXT),
            IdeaFieldDef(id = "idea-field-b", label = "B", type = FieldType.MULTILINE),
        )
        val ideaId = db.ideaLogDao().insert(
            IdeaLog(
                uuid = "idea-log-uuid",
                name = "Ideas",
                createdAt = 3,
                fieldsJson = json.encodeToString(ideaFields),
            ),
        )
        db.ideaLogDao().updateLog(
            id = ideaId,
            name = "Renamed Ideas",
            fieldsJson = json.encodeToString(ideaFields.reversed().map { it.copy(label = "Edited ${it.label}") }),
            automaticTimestamping = true,
            allowArchiving = true,
            showEntireIdeaCard = true,
            previewLines = 10,
            sortTimestampFieldId = null,
            sortNewestFirst = false,
        )
        val editedIdea = db.ideaLogDao().getById(ideaId)!!
        assertEquals("idea-log-uuid", editedIdea.uuid)
        assertEquals(
            listOf("idea-field-b", "idea-field-a"),
            json.decodeFromString<List<IdeaFieldDef>>(editedIdea.fieldsJson).map { it.id },
        )

        val dailyDao = db.dailyListDao()
        val dailyId = dailyDao.insertDailyList(
            DailyList(uuid = "daily-card-uuid", date = LocalDate.of(2026, 9, 20), createdAt = 4),
        )
        val firstItemId = dailyDao.insertItem(
            DailyListItem(dailyListId = dailyId, uuid = "daily-item-a", text = "A", position = 0),
        )
        val secondItemId = dailyDao.insertItem(
            DailyListItem(dailyListId = dailyId, uuid = "daily-item-b", text = "B", position = 1),
        )
        dailyDao.setTitle(dailyId, "Moved Day")
        dailyDao.setDate(dailyId, "2026-09-21")
        dailyDao.updateItemText(firstItemId, "Edited A")
        dailyDao.applyOrder(listOf(secondItemId, firstItemId))
        assertEquals("daily-card-uuid", dailyDao.getDailyList(dailyId)!!.uuid)
        assertEquals(listOf("daily-item-b", "daily-item-a"), dailyDao.getItemsOnce(dailyId).map { it.uuid })

        val clickerFields = listOf(
            ClickerField(id = "clicker-field-a", type = ClickerFieldType.CLICK_TRACKER, label = "A"),
            ClickerField(id = "clicker-field-b", type = ClickerFieldType.TEXT, label = "B"),
        )
        val clickerDao = db.clickerDao()
        val clickerId = clickerDao.insertLog(
            ClickerLog(
                uuid = "clicker-log-uuid",
                title = "Clicker",
                createdAt = 5,
                lastAccessedAt = 5,
                fieldsJson = json.encodeToString(clickerFields),
            ),
        )
        val clickerCardId = clickerDao.insertCard(
            ClickerCard(
                clickerLogId = clickerId,
                uuid = "clicker-card-uuid",
                createdAt = 6,
                valuesJson = "{}",
            ),
        )
        val clicker = clickerDao.getLog(clickerId)!!
        clickerDao.updateLog(
            clicker.copy(
                title = "Renamed Clicker",
                fieldsJson = json.encodeToString(clickerFields.reversed().map { it.copy(label = "Edited ${it.label}") }),
            ),
        )
        clickerDao.updateCard(clickerDao.getCard(clickerCardId)!!.copy(valuesJson = "{\"clicker-field-a\":\"2\"}"))
        val editedClicker = clickerDao.getLog(clickerId)!!
        assertEquals("clicker-log-uuid", editedClicker.uuid)
        assertEquals("clicker-card-uuid", clickerDao.getCard(clickerCardId)!!.uuid)
        assertEquals(
            listOf("clicker-field-b", "clicker-field-a"),
            json.decodeFromString<List<ClickerField>>(editedClicker.fieldsJson).map { it.id },
        )

        db.colorPresetDao().insert(
            ColorPreset(uuid = "preset-uuid", name = "Ocean", colorsJson = "[\"#0000FF\"]"),
        )
        assertEquals("preset-uuid", db.colorPresetDao().observeAll().first().single().uuid)
    }

    @Test
    fun exportRestoreAndBackupRoundTripsPreservePortableUuidsByteForByte() = runBlocking {
        val formId = db.logTemplateDao().insert(
            LogTemplate(
                uuid = "Form_UUID-MixedCase",
                name = "Form",
                createdAt = 1,
                schemaJson = "[]",
            ),
        )
        val listId = db.checklistDao().insertChecklist(
            Checklist(uuid = "List_UUID-MixedCase", name = "List", createdAt = 2),
        )
        db.checklistDao().insertItem(ChecklistItem(checklistId = listId, text = "Item", position = 0))

        val repository = BackupRepository(db)
        val encoded = BackupCodec.encode(repository.buildFull())
        val decoded = BackupCodec.decode(encoded)
        assertEquals("Form_UUID-MixedCase", decoded.logs.single().uuid)
        assertEquals("List_UUID-MixedCase", decoded.checklists.single().uuid)

        val singleForm = BackupCodec.decode(
            BackupCodec.encodeSingleLog(
                db.logTemplateDao().getById(formId)!!,
                emptyList(),
                "2026-09-22T00:00:00Z",
            ),
        )
        val singleList = BackupCodec.decode(
            BackupCodec.encodeSingleChecklist(
                db.checklistDao().getChecklist(listId)!!,
                db.checklistDao().getItemsOnce(listId),
                "2026-09-22T00:00:00Z",
            ),
        )
        assertEquals("Form_UUID-MixedCase", BackupCodec.templateOf(singleForm.logs.single()).uuid)
        assertEquals("List_UUID-MixedCase", BackupCodec.checklistEntityOf(singleList.checklists.single()).uuid)

        repository.restore(decoded, RestoreMode.REPLACE)
        assertEquals("Form_UUID-MixedCase", db.logTemplateDao().getAllOnce().single().uuid)
        assertEquals("List_UUID-MixedCase", db.checklistDao().getAllChecklistsOnce().single().uuid)

        val secondRoundTrip = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        assertEquals("Form_UUID-MixedCase", secondRoundTrip.logs.single().uuid)
        assertEquals("List_UUID-MixedCase", secondRoundTrip.checklists.single().uuid)
    }

    @Test
    fun explicitCreationAndRestorePathsCannotSilentlyReplaceIdentity() {
        assertNotEquals(StableUuid.createNew(), StableUuid.createNew())
        assertEquals("Keep-Exact_Case", StableUuid.restoreExisting("Keep-Exact_Case"))
        assertTrue(runCatching { StableUuid.restoreExisting("") }.isFailure)

        val legacy = BackupLog(id = 1, name = "Legacy", createdAt = 1, schemaJson = "[]")
        val firstImport = BackupCodec.templateOf(legacy)
        val persistedExport = BackupCodec.logOf(firstImport, emptyList())
        val restoredAgain = BackupCodec.templateOf(persistedExport)
        assertTrue(firstImport.uuid.isNotBlank())
        assertEquals(firstImport.uuid, restoredAgain.uuid)
    }
}
