package com.datadragon.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerValues
import com.datadragon.app.data.LogTemplate
import com.datadragon.app.export.ClickerExport
import kotlinx.coroutines.runBlocking
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
 * Restore Individual Item reads a Clicker Data grouping's .json export back in,
 * keeping every UUID, and still handles forms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreIndividualClickerTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before
    fun setUp() {
        source = newDatabase()
        target = newDatabase()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()

    private val fieldsJson =
        """[{"id":"f-tracker","type":"click_tracker","label":"Glasses","startingNumber":0}]"""

    private suspend fun seedSource(): ClickerLog {
        val id = source.clickerDao().insertLog(
            ClickerLog(
                uuid = "clicker-uuid",
                title = "Water",
                createdAt = 10,
                lastAccessedAt = 20,
                lastModifiedAt = 15,
                fieldsJson = fieldsJson,
                autoDateStamp = true,
                autoTimeStamp = true,
                allowFollowUp = true,
            ),
        )
        source.clickerDao().insertCard(
            ClickerCard(
                clickerLogId = id,
                uuid = "card-1",
                createdAt = 11,
                displayDate = "2026-09-01",
                displayTime = "08:00",
                valuesJson = ClickerValues.encode(mapOf("f-tracker" to "4", ClickerValues.FOLLOW_UP_KEY to "Note")),
            ),
        )
        source.clickerDao().insertCard(
            ClickerCard(clickerLogId = id, uuid = "card-2", createdAt = 12, valuesJson = ClickerValues.encode(mapOf("f-tracker" to "9"))),
        )
        return source.clickerDao().getLog(id)!!
    }

    private suspend fun exportJson(includeFollowUps: Boolean): String {
        val log = seedSource()
        val cards = source.clickerDao().getCardsForLog(log.id)
        return String(ClickerExport.json(log, cards, includeFollowUps).bytes)
    }

    @Test
    fun clickerExportRestoresWithEveryUuidAndValue() = runBlocking {
        val json = exportJson(includeFollowUps = true)
        val result = restoreIndividualItem(json, BackupRepository(target))
        assertTrue(result is RestoreResult.Success)
        assertEquals(1, (result as RestoreResult.Success).clickerData)

        val restored = target.clickerDao().getLogByUuid("clicker-uuid")!!
        assertEquals("Water", restored.title)
        assertEquals(10L, restored.createdAt)
        assertEquals(20L, restored.lastAccessedAt)
        assertEquals(15L, restored.lastModifiedAt)
        assertEquals(fieldsJson, restored.fieldsJson)
        val cards = target.clickerDao().getCardsForLog(restored.id).sortedBy { it.createdAt }
        assertEquals(listOf("card-1", "card-2"), cards.map { it.uuid })
        assertEquals(listOf(11L, 12L), cards.map { it.createdAt })
        assertEquals("2026-09-01", cards[0].displayDate)
        assertEquals("08:00", cards[0].displayTime)
        assertEquals("Note", ClickerValues.decode(cards[0].valuesJson)[ClickerValues.FOLLOW_UP_KEY])
    }

    @Test
    fun restoringOverAnEditedGroupingReplacesItAndKeepsItsUuids() = runBlocking {
        val json = exportJson(includeFollowUps = false)
        restoreIndividualItem(json, BackupRepository(target))
        // Rename and step the restored copy, then restore the export again.
        val first = target.clickerDao().getLogByUuid("clicker-uuid")!!
        target.clickerDao().updateLog(first.copy(title = "Renamed"))

        val result = restoreIndividualItem(json, BackupRepository(target))
        assertEquals(1, (result as RestoreResult.Success).clickerData)
        assertEquals(1, target.clickerDao().getAllLogsOnce().size)
        val restored = target.clickerDao().getLogByUuid("clicker-uuid")!!
        assertEquals("Water", restored.title)
        val cards = target.clickerDao().getCardsForLog(restored.id)
        assertEquals(setOf("card-1", "card-2"), cards.map { it.uuid }.toSet())
        // Exported without Follow-Up Notes: the file stays valid and carries none.
        cards.forEach { assertFalse(ClickerValues.decode(it.valuesJson).containsKey(ClickerValues.FOLLOW_UP_KEY)) }
    }

    @Test
    fun aFileWithNoItemGetsTheNewMessage() = runBlocking {
        val empty = BackupCodec.encode(
            com.datadragon.app.data.BackupFile.single(
                BackupRepository.now(),
                com.datadragon.app.data.BackupCategory.CLICKER_DATA,
                com.datadragon.app.data.BackupPayload(clickerData = emptyList()),
            ),
        )
        val result = restoreIndividualItem(empty, BackupRepository(target))
        assertEquals("That file doesn't hold a list, a form, or clicker data.", (result as RestoreResult.Failure).message)
    }

    @Test
    fun formExportsStillRestore() = runBlocking {
        val template = LogTemplate(uuid = "form-uuid", name = "Form", createdAt = 1, schemaJson = "[]")
        val json = BackupCodec.encodeSingleLog(template, emptyList(), BackupRepository.now())
        val result = restoreIndividualItem(json, BackupRepository(target))
        assertEquals(1, (result as RestoreResult.Success).logs)
        assertEquals("form-uuid", target.logTemplateDao().getByUuid("form-uuid")!!.uuid)
    }
}
