package com.datadragon.app.data

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** A destination that can be written and then reopened for verification. */
interface BackupDestination {
    fun openOutputStream(): OutputStream?
    fun openInputStream(): InputStream?
}

/**
 * Writes a complete current backup and reports success only after reopening the
 * actual destination and validating its contents with the shared codec.
 */
class BackupFileWriter {
    fun validateFullBackup(encoded: String): BackupFile = validatedFullBackup(encoded)

    fun writeAndVerify(destination: BackupDestination, encoded: String): BackupFile {
        val prepared = validatedFullBackup(encoded)

        destination.openOutputStream()?.use { output ->
            output.write(encoded.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } ?: error("No output stream")

        val saved = destination.openInputStream()?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        } ?: error("No input stream")
        val reopened = validatedFullBackup(saved)
        check(BackupCodec.encode(reopened) == BackupCodec.encode(prepared)) {
            "Saved backup does not match the prepared backup."
        }
        return reopened
    }

    private fun validatedFullBackup(encoded: String): BackupFile {
        val backup = BackupCodec.decode(encoded)
        require(backup.version == BackupFile.VERSION) { "Manual backup is not a current backup." }
        require(backup.includedCategories == BackupCategory.entries) {
            "Manual backup does not contain the complete portable payload."
        }
        return backup
    }
}
