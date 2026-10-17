package com.datadragon.app.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards existing backup files against JSON library upgrades.
 *
 * Restore re-encodes a decoded payload and compares its SHA-256 with the
 * checksum stored in the file, so any change in how kotlinx-serialization
 * writes JSON would make every existing backup (manual, automatic and undo)
 * fail with "Backup checksum does not match its payload." The frozen fixture
 * below was written by the library versions the app shipped with; it must keep
 * decoding, and must re-encode byte-for-byte, under every later version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFormatStabilityTest {

    @Test
    fun frozenBackupStillDecodesAndReEncodesByteForByte() {
        val fixture = javaClass.classLoader?.getResource(FIXTURE)?.readText()
        if (fixture == null) {
            val generated = generateFixture()
            val out = File("build/generated-fixtures/$FIXTURE_NAME")
            out.parentFile?.mkdirs()
            out.writeText(generated)
            fail("Missing test resource $FIXTURE; wrote a candidate to ${out.absolutePath}")
            return
        }

        val decoded = BackupCodec.decode(fixture)

        assertEquals(fixture, BackupCodec.encode(decoded))
        assertEquals(BackupCategory.entries, decoded.includedCategories)
        assertTrue(decoded.payload.forms!!.any { it.name == TRICKY_TEXT })
    }

    private fun generateFixture(): String = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            // Text that exercises every JSON escaping rule the encoder applies.
            db.openHelper.writableDatabase.execSQL(
                "UPDATE log_templates SET name = ? WHERE id = 101",
                arrayOf(TRICKY_TEXT),
            )
            val backup = BackupRepository(db, { BackupPortablePreferences.defaults() }, "format-baseline").buildFull()
            BackupCodec.encode(backup)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val FIXTURE_NAME = "backup-v9-format-baseline.json"
        const val FIXTURE = "fixtures/$FIXTURE_NAME"
        const val TRICKY_TEXT = "Odor \"Log\" \\ / tab\t line\n return\r ctrl\u0001 del\u007f " +
            "nbsp  ls  ps  zw​ dragon 🐉 שלום " +
            "éñü <b>&amp;</b> 'single'"
    }
}
