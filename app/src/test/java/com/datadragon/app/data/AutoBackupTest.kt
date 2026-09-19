package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The rules behind Automatic Backup: when it runs, and what it may delete. */
class AutoBackupTest {

    private val day = AutoBackup.INTERVAL_MILLIS
    private val now = 1_800_000_000_000L

    private fun auto(date: String) = "${AutoBackup.FILE_PREFIX}$date${AutoBackup.FILE_SUFFIX}"

    // --- when it runs -------------------------------------------------------

    @Test
    fun `off means never due`() {
        assertFalse(AutoBackup.isDue(enabled = false, hasDestination = true, lastRunAt = 0L, now = now))
    }

    @Test
    fun `no destination means never due even when enabled`() {
        assertFalse(AutoBackup.isDue(enabled = true, hasDestination = false, lastRunAt = 0L, now = now))
    }

    @Test
    fun `first run after setup is due`() {
        assertTrue(AutoBackup.isDue(enabled = true, hasDestination = true, lastRunAt = 0L, now = now))
    }

    @Test
    fun `not due again until a full day has passed`() {
        assertFalse(
            AutoBackup.isDue(enabled = true, hasDestination = true, lastRunAt = now - day + 1, now = now)
        )
        assertTrue(
            AutoBackup.isDue(enabled = true, hasDestination = true, lastRunAt = now - day, now = now)
        )
    }

    @Test
    fun `a clock moved backwards does not park backups forever`() {
        assertTrue(
            AutoBackup.isDue(enabled = true, hasDestination = true, lastRunAt = now + day, now = now)
        )
    }

    // --- what it may delete -------------------------------------------------

    @Test
    fun `nothing is stale while four or fewer exist`() {
        val names = listOf(auto("2026-09-16"), auto("2026-09-17"), auto("2026-09-18"), auto("2026-09-19"))
        assertEquals(emptyList<String>(), AutoBackup.staleBackups(names))
    }

    @Test
    fun `the oldest beyond four are stale`() {
        val names = listOf(
            auto("2026-09-15"), auto("2026-09-16"), auto("2026-09-17"),
            auto("2026-09-18"), auto("2026-09-19"), auto("2026-09-14"),
        )
        assertEquals(listOf(auto("2026-09-15"), auto("2026-09-14")), AutoBackup.staleBackups(names))
    }

    @Test
    fun `a manual backup is never stale, however many there are`() {
        val manual = listOf(
            "datadragon_backup_2026-09-10.json",
            "datadragon_backup_2026-09-11.json",
            "datadragon_backup_2026-09-12.json",
            "datadragon_backup_2026-09-13.json",
            "datadragon_backup_2026-09-14.json",
        )
        assertEquals(emptyList<String>(), AutoBackup.staleBackups(manual))
    }

    @Test
    fun `unrelated files in the folder are never stale`() {
        val names = listOf("notes.txt", "my_log.json", "datadragon_backup_2026-09-19.json", "photo.jpg")
        assertEquals(emptyList<String>(), AutoBackup.staleBackups(names))
    }

    @Test
    fun `manual backups sharing the folder do not use up a rotation slot`() {
        val names = listOf(
            auto("2026-09-16"), auto("2026-09-17"), auto("2026-09-18"), auto("2026-09-19"),
            "datadragon_backup_2026-09-19.json", "datadragon_backup_2026-09-18.json",
        )
        assertEquals(emptyList<String>(), AutoBackup.staleBackups(names))
    }

    // --- naming -------------------------------------------------------------

    @Test
    fun `an automatic backup is named for its date and recognized as one`() {
        val name = AutoBackup.fileName(LocalDate.of(2026, 9, 19))
        assertEquals("datadragon_autobackup_2026-09-19.json", name)
        assertTrue(AutoBackup.isAutomaticBackup(name))
    }

    @Test
    fun `the manual backup name is not recognized as an automatic one`() {
        assertFalse(AutoBackup.isAutomaticBackup("datadragon_backup_2026-09-19.json"))
    }
}
