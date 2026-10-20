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
 * A Comparison field's option ids and the rows saved against them survive a
 * full backup and a Replace restore unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComparisonBackupTest {

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
    fun backupAndRestoreKeepComparisonItemIdsAndRows() = runBlocking {
        val field = FieldDef(
            id = "field-meals",
            label = "Meals",
            type = FieldType.COMPARISON,
            digits = 3,
            wholeNumbersOnly = false,
            firstLabel = "Planned",
            choices = listOf(
                ChoiceOption("option-turkey", "Turkey Dinner"),
                ChoiceOption("option-ham", "Ham Dinner", retired = true),
            ),
        )
        val schemaJson = FormMarkdownParser.encodeFields(listOf(field))
        val templateId = db.logTemplateDao().insert(
            LogTemplate(uuid = "form-meals", name = "Meals", createdAt = 1, schemaJson = schemaJson),
        )
        val valuesJson = EntryValues.encode(
            mapOf(
                field.id to ComparisonValues.encode(
                    listOf(ComparisonRow("option-ham", "2", ""), ComparisonRow("option-turkey", "34.4", "-1")),
                )!!,
            ),
        )
        db.logEntryDao().insert(LogEntry(templateId = templateId, createdAt = "2026-10-20T08:00:00Z", valuesJson = valuesJson))

        val repository = BackupRepository(db)
        val decoded = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        repository.restore(decoded, RestoreMode.REPLACE)

        val template = db.logTemplateDao().getAllOnce().single()
        val restored = json.decodeFromString(ListSerializer(FieldDef.serializer()), template.schemaJson).single()
        assertEquals(field, restored)
        val entry = db.logEntryDao().getAllOnce().single()
        assertEquals(
            listOf(ComparisonRow("option-ham", "2", ""), ComparisonRow("option-turkey", "34.4", "-1")),
            ComparisonValues.rows(EntryValues.decode(entry.valuesJson), restored),
        )
    }
}
