package com.datadragon.app.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 5: pure cadence, due/dirty, naming, and rotation rules. */
class AutoBackupScheduleTest {

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun dailyWeeklyAndCustomIntervalsAreElapsedDays() {
        assertEquals(day, AutoBackupPolicy.intervalMillis(AutoBackupCadence.DAILY, 3))
        assertEquals(7 * day, AutoBackupPolicy.intervalMillis(AutoBackupCadence.WEEKLY, 3))
        assertEquals(3 * day, AutoBackupPolicy.intervalMillis(AutoBackupCadence.CUSTOM, 3))
        assertEquals(365 * day, AutoBackupPolicy.intervalMillis(AutoBackupCadence.CUSTOM, 365))
        assertEquals(day, AutoBackupPolicy.intervalMillis(AutoBackupCadence.CUSTOM, 1))
    }

    @Test
    fun customDaysAcceptOnlyOneThrough365() {
        assertEquals(1, AutoBackupPolicy.parseCustomDays("1"))
        assertEquals(365, AutoBackupPolicy.parseCustomDays("365"))
        assertNull(AutoBackupPolicy.parseCustomDays("0"))
        assertNull(AutoBackupPolicy.parseCustomDays("366"))
        assertNull(AutoBackupPolicy.parseCustomDays(""))
        assertNull(AutoBackupPolicy.parseCustomDays("abc"))
    }

    @Test
    fun approvedDefaultsAndRetentionChoices() {
        assertEquals(AutoBackupCadence.DAILY, AutoBackupCadence.DEFAULT)
        assertEquals(3, AutoBackupPolicy.DEFAULT_CUSTOM_DAYS)
        assertEquals(listOf(3, 5, 7), AutoBackupPolicy.RETENTION_CHOICES)
        assertEquals(3, AutoBackupPolicy.DEFAULT_RETENTION)
        assertEquals(3, AutoBackupPolicy.normalizeRetention(4))
    }

    @Test
    fun dailyWeeklyAndCustomDueTimesFollowTheLastSuccess() {
        val last = 1_000_000L
        listOf(
            AutoBackupPolicy.intervalMillis(AutoBackupCadence.DAILY, 3),
            AutoBackupPolicy.intervalMillis(AutoBackupCadence.WEEKLY, 3),
            AutoBackupPolicy.intervalMillis(AutoBackupCadence.CUSTOM, 5),
        ).forEach { interval ->
            val state = state(lastSuccessAt = last, intervalMillis = interval, currentData = 2)
            assertEquals(last + interval, AutoBackupSchedule.dueAt(state, last + 1))
            assertFalse(AutoBackupSchedule.isDue(state, last + interval - 1))
            assertTrue(AutoBackupSchedule.isDue(state, last + interval))
        }
    }

    @Test
    fun cleanStateIsNeverDirtyEvenWhenDue() {
        val state = state(lastSuccessAt = 0, intervalMillis = day)
        assertFalse(AutoBackupSchedule.isDirty(state))
        assertTrue(AutoBackupSchedule.isDue(state, 10 * day))
    }

    @Test
    fun changedDataOrPreferencesMakeTheStateDirty() {
        assertTrue(AutoBackupSchedule.isDirty(state(lastSuccessAt = 0, intervalMillis = day, currentData = 2)))
        assertTrue(AutoBackupSchedule.isDirty(state(lastSuccessAt = 0, intervalMillis = day, currentPreferences = 2)))
    }

    @Test
    fun aNewDestinationOrNoSuccessIsDirtyAndDueImmediately() {
        val changed = state(lastSuccessAt = 5 * day, intervalMillis = 7 * day, lastDestination = "old")
        assertTrue(AutoBackupSchedule.isDirty(changed))
        assertTrue(AutoBackupSchedule.isDue(changed, 5 * day + 1))

        val never = state(lastSuccessAt = null, intervalMillis = 7 * day)
        assertTrue(AutoBackupSchedule.isDirty(never))
        assertTrue(AutoBackupSchedule.isDue(never, 0))
    }

    @Test
    fun clockMovedBackwardsIsDueNow() {
        val state = state(lastSuccessAt = 10 * day, intervalMillis = day, currentData = 2)
        assertTrue(AutoBackupSchedule.isDue(state, 2 * day))
    }

    @Test
    fun namesUseTheDateAndAddTimeOnlyWhenNeeded() {
        val at = LocalDateTime.of(2026, 9, 22, 14, 32, 5)
        assertEquals("datadragon_autobackup_2026-09-22.json", AutoBackupFiles.nameFor(at, emptyList()))
        assertEquals(
            "datadragon_autobackup_2026-09-22_14-32.json",
            AutoBackupFiles.nameFor(at, listOf("datadragon_autobackup_2026-09-22.json")),
        )
        assertEquals(
            "datadragon_autobackup_2026-09-22_14-32-05.json",
            AutoBackupFiles.nameFor(
                at,
                listOf("datadragon_autobackup_2026-09-22.json", "datadragon_autobackup_2026-09-22_14-32.json"),
            ),
        )
        assertNull(
            AutoBackupFiles.nameFor(
                at,
                listOf(
                    "datadragon_autobackup_2026-09-22.json",
                    "datadragon_autobackup_2026-09-22_14-32.json",
                    "datadragon_autobackup_2026-09-22_14-32-05.json",
                ),
            ),
        )
    }

    @Test
    fun onlyExactAutomaticNamesAreRecognized() {
        assertTrue(AutoBackupFiles.isAutomaticBackup("datadragon_autobackup_2026-09-22.json"))
        assertTrue(AutoBackupFiles.isAutomaticBackup("datadragon_autobackup_2026-09-22_14-32.json"))
        assertTrue(AutoBackupFiles.isAutomaticBackup("datadragon_autobackup_2026-09-22_14-32-05.json"))
        listOf(
            "datadragon_backup_2026-09-22.json",
            "datadragon_autobackup_2026-09-22 (1).json",
            "datadragon_autobackup_2026-13-40.json",
            "datadragon_autobackup_2026-09-22.json.bak",
            "datadragon_autobackup_notes.json",
            "my_datadragon_autobackup_2026-09-22.json",
            "notes.json",
        ).forEach { assertFalse(it, AutoBackupFiles.isAutomaticBackup(it)) }
    }

    @Test
    fun rotationKeepsTheNewestAutomaticBackupsAndIgnoresEverythingElse() {
        val names = listOf(
            "datadragon_autobackup_2026-09-18.json",
            "datadragon_autobackup_2026-09-19.json",
            "datadragon_autobackup_2026-09-20.json",
            "datadragon_autobackup_2026-09-20_08-00.json",
            "datadragon_backup_2026-01-01.json",
            "notes.json",
            "datadragon_autobackup_notes.json",
        )
        val stale = AutoBackupFiles.staleBackups(
            names + "datadragon_autobackup_2026-09-21.json",
            keep = 3,
            newlyCreated = "datadragon_autobackup_2026-09-21.json",
        )
        assertEquals(
            setOf("datadragon_autobackup_2026-09-18.json", "datadragon_autobackup_2026-09-19.json"),
            stale.toSet(),
        )
    }

    @Test
    fun rotationNeverDeletesTheNewlyCreatedFileEvenAfterAClockChange() {
        val stale = AutoBackupFiles.staleBackups(
            listOf(
                "datadragon_autobackup_2027-01-01.json",
                "datadragon_autobackup_2027-01-02.json",
                "datadragon_autobackup_2027-01-03.json",
                "datadragon_autobackup_2026-01-01.json",
            ),
            keep = 3,
            newlyCreated = "datadragon_autobackup_2026-01-01.json",
        )
        assertEquals(listOf("datadragon_autobackup_2027-01-01.json"), stale)
    }

    private fun state(
        lastSuccessAt: Long?,
        intervalMillis: Long,
        lastDestination: String = "folder",
        currentData: Long = 1,
        currentPreferences: Long = 1,
    ) = AutoBackupSnapshotState(
        enabled = true,
        folderUri = "folder",
        lastSuccessAt = lastSuccessAt,
        lastSuccessDestination = if (lastSuccessAt == null) null else lastDestination,
        protectedDataRevision = 1,
        protectedPreferencesRevision = 1,
        currentDataRevision = currentData,
        currentPreferencesRevision = currentPreferences,
        intervalMillis = intervalMillis,
    )
}
