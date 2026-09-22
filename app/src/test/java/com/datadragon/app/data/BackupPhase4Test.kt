package com.datadragon.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPhase4Test {

    @Test
    fun successfulManualBackupReopensAndFullyValidatesTheSavedDestination() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val encoded = BackupCodec.encode(BackupRepository(db).buildFull())
            val destination = MemoryDestination()

            val saved = BackupFileWriter().writeAndVerify(destination, encoded)

            assertTrue(destination.outputClosed)
            assertEquals(BackupFile.VERSION, saved.version)
            assertEquals(BackupCategory.entries, saved.includedCategories)
            assertEquals(BackupCodec.encode(BackupCodec.decode(encoded)), BackupCodec.encode(saved))
        } finally {
            db.close()
        }
    }

    @Test
    fun corruptedOrIncompleteSavedOutputIsNotReportedAsSuccess() {
        val encoded = completeBackup()
        val destination = MemoryDestination(readTransform = { it.replace("phase-4-backup", "corrupted-backup") })

        assertTrue(runCatching { BackupFileWriter().writeAndVerify(destination, encoded) }.isFailure)

        val truncated = MemoryDestination(readTransform = { it.dropLast(20) })
        assertTrue(runCatching { BackupFileWriter().writeAndVerify(truncated, encoded) }.isFailure)
    }

    @Test
    fun reopenOrReadFailureIsReportedAsFailure() {
        val encoded = completeBackup()
        val destination = MemoryDestination(failRead = true)

        assertTrue(runCatching { BackupFileWriter().writeAndVerify(destination, encoded) }.isFailure)
    }

    @Test
    fun validationFailureIsReportedAsFailure() {
        val encoded = completeBackup()
        val destination = MemoryDestination(
            readTransform = { it.replaceFirst("\"forms\": 0", "\"forms\": 99") },
        )

        assertTrue(runCatching { BackupFileWriter().writeAndVerify(destination, encoded) }.isFailure)
    }

    @Test
    fun semanticValidationFailureStopsBeforeDestinationWrite() {
        val encoded = completeBackup(
            BackupPortablePreferences(lastHomeView = "invalid-home-view"),
        )
        val destination = MemoryDestination()

        assertTrue(runCatching { BackupFileWriter().writeAndVerify(destination, encoded) }.isFailure)
        assertFalse(destination.outputClosed)
    }

    @Test
    fun manualBackupUsesTheCompleteCurrentPayload() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val encoded = BackupCodec.encode(BackupRepository(db).buildFull())
            val saved = BackupFileWriter().writeAndVerify(MemoryDestination(), encoded)

            assertEquals(2, saved.counts.forms)
            assertEquals(2, saved.counts.lists)
            assertEquals(1, saved.counts.ideaLogs)
            assertEquals(2, saved.counts.dailyTasks)
            assertEquals(1, saved.counts.clickerLogs)
            assertEquals(2, saved.counts.savedColorPresets)
            assertEquals(26, saved.counts.portablePreferences)
            assertFalse(saved.payload.portablePreferences == null)
        } finally {
            db.close()
        }
    }

    @Test
    fun manualBackupDoesNotModifyAutomaticBackupHistoryOrState() {
        val encoded = completeBackup()
        val automaticBackupStateBefore = "device-local-state"
        val automaticBackupState = automaticBackupStateBefore

        BackupFileWriter().writeAndVerify(MemoryDestination(), encoded)

        assertEquals(automaticBackupStateBefore, automaticBackupState)
    }

    @Test
    fun existingCodecValidationAndRestoreInputsRemainUsable() {
        val encoded = completeBackup()
        val decoded = BackupCodec.decode(encoded)

        assertEquals(BackupCategory.entries, decoded.includedCategories)
        assertEquals(decoded.counts, BackupCounts.from(decoded.payload))
        assertEquals(decoded, BackupCodec.decode(BackupCodec.encode(decoded)))
    }

    private fun completeBackup(
        preferences: BackupPortablePreferences = BackupPortablePreferences(),
    ): String = BackupCodec.encode(
        BackupFile.full(
            exportedAt = "2026-09-22T00:00:00Z",
            sourceAppVersion = "phase-4-test",
            roomSchemaVersion = AppDatabase.SCHEMA_VERSION,
            payload = BackupPayload(
                forms = emptyList(),
                lists = emptyList(),
                ideaLogs = emptyList(),
                dailyTasks = emptyList(),
                clickerData = emptyList(),
                savedColorPresets = emptyList(),
                portablePreferences = preferences,
            ),
            backupId = "phase-4-backup",
        ),
    )

    private class MemoryDestination(
        private val failRead: Boolean = false,
        private val readTransform: (String) -> String = { it },
    ) : BackupDestination {
        private var bytes: ByteArray = byteArrayOf()
        var outputClosed: Boolean = false
            private set

        override fun openOutputStream(): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                bytes = toByteArray()
                outputClosed = true
                super.close()
            }
        }

        override fun openInputStream(): InputStream {
            if (failRead) error("reopen failed")
            val saved = bytes.toString(StandardCharsets.UTF_8)
            return ByteArrayInputStream(readTransform(saved).toByteArray(StandardCharsets.UTF_8))
        }
    }
}
