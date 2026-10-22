package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A List Item Quantities field's option ids, settings and the rows saved
 * against them survive a full backup and a Replace restore unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListItemQuantitiesBackupTest {

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
    fun backupAndRestoreKeepListItemQuantitiesIdsSettingsAndRows() = runBlocking {
        val field = FieldDef(
            id = "field-groceries",
            label = "Groceries",
            type = FieldType.LIST_ITEM_QUANTITIES,
            digits = 3,
            itemLabel = "Food",
            firstLabel = "Count",
            totalLabel = "All Food",
            addQuantities = true,
            choices = listOf(
                ChoiceOption("option-soda", "Soda"),
                ChoiceOption("option-juice", "Juice", retired = true),
            ),
        )
        val schemaJson = FormMarkdownParser.encodeFields(listOf(field))
        val templateId = db.logTemplateDao().insert(
            LogTemplate(uuid = "form-groceries", name = "Groceries", createdAt = 1, schemaJson = schemaJson),
        )
        val rows = listOf(ComparisonRow("option-juice", "10"), ComparisonRow("option-soda", "5"))
        val valuesJson = EntryValues.encode(mapOf(field.id to ComparisonValues.encode(rows)!!))
        db.logEntryDao().insert(LogEntry(templateId = templateId, createdAt = "2026-10-22T08:00:00Z", valuesJson = valuesJson))

        val repository = BackupRepository(db)
        val decoded = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        repository.restore(decoded, RestoreMode.REPLACE)

        val template = db.logTemplateDao().getAllOnce().single()
        assertEquals("form-groceries", template.uuid)
        val restored = json.decodeFromString(ListSerializer(FieldDef.serializer()), template.schemaJson).single()
        assertEquals(field, restored)
        val entry = db.logEntryDao().getAllOnce().single()
        assertEquals(rows, ComparisonValues.rows(EntryValues.decode(entry.valuesJson), restored))
    }
}
