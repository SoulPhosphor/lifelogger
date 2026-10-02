package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Clicker grouping's Statistics Designer choices survive backup and restore
 * with the grouping's UUID intact, and a grouping still on the defaults backs up
 * exactly as it did before the choices existed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickerStatisticsBackupTest {
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
    fun statisticsChoicesRoundTripThroughReplaceRestore() = runBlocking {
        val logId = source.clickerDao().insertLog(
            ClickerLog(uuid = "clicker-uuid", title = "Clicker", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]"),
        )
        val config = ClickerStatisticsConfig(
            last7Days = false,
            customRange = true,
            customDays = "90",
            dateFieldId = "date-field",
            excludeToday = true,
            disabledStats = mapOf("reps" to listOf(ClickerStats.TOTAL, ClickerStats.AVERAGE)),
        )
        source.clickerDao().setStatistics(logId, ClickerStatisticsCodec.encode(config))

        val file = BackupCodec.decode(BackupCodec.encode(BackupRepository(source).buildFull()))
        BackupRepository(target).restore(file, RestoreMode.REPLACE)

        val restored = target.clickerDao().getLogByUuid("clicker-uuid")!!
        assertEquals(config.copy(disabledStats = mapOf("reps" to listOf(ClickerStats.AVERAGE, ClickerStats.TOTAL))),
            ClickerStatisticsCodec.decode(restored.statisticsJson))
    }

    @Test
    fun defaultChoicesAreNotWrittenAndRestoreAsDefaults() = runBlocking {
        source.clickerDao().insertLog(
            ClickerLog(uuid = "clicker-uuid", title = "Clicker", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]"),
        )
        val backup = BackupRepository(source).buildFull()
        assertNull(backup.payload.clickerData!!.single().statisticsJson)
        val encoded = BackupCodec.encode(backup)
        assertFalse(encoded.contains("statisticsJson"))

        BackupRepository(target).restore(BackupCodec.decode(encoded), RestoreMode.REPLACE)
        val restored = target.clickerDao().getLogByUuid("clicker-uuid")!!
        assertEquals("", restored.statisticsJson)
        assertEquals(ClickerStatisticsConfig(), ClickerStatisticsCodec.decode(restored.statisticsJson))
    }

    @Test
    fun choosingEveryDefaultAgainEncodesBlank() {
        val changedBack = ClickerStatisticsConfig(disabledStats = mapOf("reps" to emptyList()))
        assertEquals("", ClickerStatisticsCodec.encode(changedBack))
    }
}
