package com.datadragon.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPhase2Test {

    private val preferences = BackupPortablePreferences.defaults().copy(
        autoCapitalizeLabels = false,
        lastHomeView = HomeView.CLICKER.key,
        navStyle = NavStyle.DROPDOWN.key,
        modeEnabledIdeas = false,
        listCrossOutCompleted = true,
        dailyListHeading = "Today",
        dailyListAutoRenew = true,
        dailyListRetention = "14",
    )

    @Test
    fun populatedDatabaseBuildsCompleteCurrentSnapshotAndRoundTrips() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val snapshot = BackupRepository(db, { preferences }, "phase-2-test").buildFull()
            val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))

            assertEquals(BackupFile.VERSION, decoded.version)
            assertEquals(BackupCategory.entries, decoded.includedCategories)
            assertEquals(2, decoded.counts.forms)
            assertEquals(2, decoded.counts.formEntries)
            assertEquals(2, decoded.counts.followUpNotes)
            assertEquals(2, decoded.counts.formCalendars)
            assertEquals(2, decoded.counts.lists)
            assertEquals(4, decoded.counts.listItems)
            assertEquals(1, decoded.counts.listDrafts)
            assertEquals(1, decoded.counts.ideaLogs)
            assertEquals(2, decoded.counts.ideaEntries)
            assertEquals(2, decoded.counts.dailyTasks)
            assertEquals(2, decoded.counts.dailyTaskItems)
            assertEquals(1, decoded.counts.clickerLogs)
            assertEquals(2, decoded.counts.clickerCards)
            assertEquals(2, decoded.counts.savedColorPresets)
            assertEquals(29, decoded.counts.portablePreferences)

            assertTrue(decoded.checklists.single { it.name == "Travel Draft" }.draft)
            assertEquals("55555555-5555-4555-8555-555555555555", decoded.payload.ideaLogs!!.single().uuid)
            assertEquals("cccccccc-cccc-4ccc-8ccc-cccccccccccc", decoded.payload.dailyTasks!!
                .single { it.date == "2026-09-18" }.items.first().uuid)
            assertEquals("12121212-1212-4121-8121-121212121212", decoded.payload.clickerData!!
                .single().cards.first().uuid)
            assertTrue(decoded.payload.savedColorPresets!!.all { it.uuid.isNotBlank() })
            assertEquals(preferences, decoded.payload.portablePreferences)
            assertNotNull(decoded.checksum)
        } finally {
            db.close()
        }
    }

    @Test
    fun currentFilesDoNotSerializeLocalRoomRowIds() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val encoded = BackupCodec.encode(BackupRepository(db, { preferences }, "test").buildFull())
            assertFalse(Regex("(?m)^\\s*\\\"id\\\"\\s*:").containsMatchIn(encoded))
        } finally {
            db.close()
        }
    }

    @Test
    fun canonicalPayloadAndEncodingAreDeterministic() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val original = BackupRepository(db, { preferences }, "test").buildFull()
            val reversedPayload = original.payload.copy(
                forms = original.payload.forms!!.reversed().map { it.copy(entries = it.entries.reversed()) },
                lists = original.payload.lists!!.reversed().map { it.copy(items = it.items.reversed()) },
                dailyTasks = original.payload.dailyTasks!!.reversed().map { it.copy(items = it.items.reversed()) },
                savedColorPresets = original.payload.savedColorPresets!!.reversed(),
            )
            val fixedA = original.copy(backupId = "fixed-backup-id", exportedAt = "2026-09-22T00:00:00Z")
            val fixedB = fixedA.copy(payload = reversedPayload)

            assertEquals(BackupCodec.encode(fixedA), BackupCodec.encode(fixedB))
        } finally {
            db.close()
        }
    }

    @Test
    fun checksumAndCountsDetectEditedContent() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val encoded = BackupCodec.encode(BackupRepository(db, { preferences }, "test").buildFull())
            val edited = encoded.replace("A useful note", "A corrupted note")
            val wrongCounts = encoded.replaceFirst("\"forms\": 2", "\"forms\": 99")

            assertTrue(runCatching { BackupCodec.decode(edited) }.exceptionOrNull()!!.message!!.contains("checksum"))
            assertTrue(runCatching { BackupCodec.decode(wrongCounts) }.exceptionOrNull()!!.message!!.contains("counts"))
        } finally {
            db.close()
        }
    }

    @Test
    fun manifestDistinguishesAbsentFromIntentionallyEmpty() {
        val emptyLists = BackupFile(
            backupId = "empty-list-backup",
            exportedAt = "2026-09-22T00:00:00Z",
            sourceAppVersion = "test",
            roomSchemaVersion = AppDatabase.SCHEMA_VERSION,
            includedCategories = listOf(BackupCategory.LISTS),
            payload = BackupPayload(lists = emptyList()),
        )
        val decodedEmpty = BackupCodec.decode(BackupCodec.encode(emptyLists))
        assertNotNull(decodedEmpty.payload.lists)
        assertTrue(decodedEmpty.payload.lists!!.isEmpty())

        val legacyV1 = javaClass.classLoader!!.getResource("fixtures/backup-v1.json")!!.readText()
        val decodedLegacy = BackupCodec.decode(legacyV1)
        assertNull(decodedLegacy.payload.lists)
        assertFalse(BackupCategory.LISTS in decodedLegacy.includedCategories)

        val mismatched = emptyLists.copy(includedCategories = listOf(BackupCategory.FORMS))
        assertTrue(runCatching { BackupCodec.encode(mismatched) }.isFailure)
    }

    @Test
    fun legacyVersionsConvertToCurrentPayloadWithoutInventingCategories() {
        val loader = javaClass.classLoader!!
        val v1 = BackupCodec.decode(loader.getResource("fixtures/backup-v1.json")!!.readText())
        val v2 = BackupCodec.decode(loader.getResource("fixtures/backup-v2.json")!!.readText())

        assertEquals(1, v1.version)
        assertEquals(listOf(BackupCategory.FORMS), v1.includedCategories)
        assertNull(v1.payload.lists)
        assertEquals("", v1.logs.single().uuid)

        assertEquals(2, v2.version)
        assertEquals(listOf(BackupCategory.FORMS, BackupCategory.LISTS), v2.includedCategories)
        assertEquals("4d8f7e2a-2f7f-4c31-8f0f-8a1d7c2e6001", v2.logs.single().uuid)
        assertEquals(1, v2.checklists.size)
    }

    @Test
    fun wrongFormatFutureVersionAndTruncatedJsonAreRejected() {
        val wrongFormat = """{"format":"another-app","version":3}"""
        val future = """{"format":"datadragon-backup","version":5}"""
        val truncated = """{"format":"datadragon-backup","version":3,"payload":"""

        assertTrue(runCatching { BackupCodec.decode(wrongFormat) }.exceptionOrNull()!!.message!!.contains("not a Data Dragon"))
        assertTrue(runCatching { BackupCodec.decode(future) }.exceptionOrNull()!!.message!!.contains("newer unsupported"))
        assertTrue(runCatching { BackupCodec.decode(truncated) }.isFailure)
    }
}
