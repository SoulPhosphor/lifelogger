package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Clicker Follow-Up Notes survive backup and restore: the note text inside a
 * card's values, the grouping's Follow-Up Notes setting, and the per-grouping
 * show/hide choice for the Main screen's top-bar button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickerFollowUpBackupTest {
    private lateinit var context: Context
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = newDatabase()
        target = newDatabase()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()

    @Test
    fun followUpNoteTextAndSettingRoundTripThroughReplaceRestore() = runBlocking {
        val logId = source.clickerDao().insertLog(
            ClickerLog(
                uuid = "clicker-uuid",
                title = "Clicker",
                createdAt = 1,
                lastAccessedAt = 1,
                fieldsJson = "[]",
                allowFollowUp = true,
            ),
        )
        val values = mapOf("field-uuid" to "Field text", ClickerValues.FOLLOW_UP_KEY to "Line one\nLine two")
        source.clickerDao().insertCard(
            ClickerCard(
                clickerLogId = logId,
                uuid = "card-uuid",
                createdAt = 2,
                valuesJson = ClickerValues.encode(values),
            ),
        )

        val file = BackupCodec.decode(BackupCodec.encode(BackupRepository(source).buildFull()))
        BackupRepository(target).restore(file, RestoreMode.REPLACE)

        val restoredLog = target.clickerDao().getLogByUuid("clicker-uuid")!!
        assertTrue(restoredLog.allowFollowUp)
        val restoredCard = target.clickerDao().getCardsForLog(restoredLog.id).single()
        assertEquals("card-uuid", restoredCard.uuid)
        assertEquals(values, ClickerValues.decode(restoredCard.valuesJson))
    }

    @Test
    fun followUpShownChoicesRoundTripAndOlderBackupsLeaveThemAlone() {
        val settings = SettingsRepository(context)
        settings.setClickerFollowUpShown("grouping-a", true)
        settings.setClickerFollowUpShown("grouping-b", false)
        val snapshot = BackupCodec.decode(
            BackupCodec.encode(
                BackupFile(
                    backupId = "follow-up-test",
                    exportedAt = "2026-09-26T00:00:00Z",
                    sourceAppVersion = "test",
                    roomSchemaVersion = AppDatabase.SCHEMA_VERSION,
                    includedCategories = listOf(BackupCategory.PORTABLE_PREFERENCES),
                    payload = BackupPayload(portablePreferences = settings.portableBackupSnapshot()),
                ),
            ),
        ).payload.portablePreferences!!
        assertEquals(mapOf("grouping-a" to true, "grouping-b" to false), snapshot.clickerFollowUpShown)

        // A backup carrying the choices replaces the device's choices.
        settings.setClickerFollowUpShown("grouping-a", false)
        settings.setClickerFollowUpShown("grouping-c", true)
        settings.applyPortableBackup(snapshot)
        assertTrue(settings.isClickerFollowUpShown("grouping-a"))
        assertFalse(settings.isClickerFollowUpShown("grouping-b"))
        assertFalse(settings.isClickerFollowUpShown("grouping-c"))

        // An older backup without them leaves the device's choices as they are.
        settings.setClickerFollowUpShown("grouping-c", true)
        settings.applyPortableBackup(snapshot.copy(clickerFollowUpShown = null))
        assertTrue(settings.isClickerFollowUpShown("grouping-a"))
        assertTrue(settings.isClickerFollowUpShown("grouping-c"))
    }
}
