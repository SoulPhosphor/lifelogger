package com.datadragon.app.data

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** A destination that can be written and then reopened for verification. */
interface BackupDestination {
    fun openOutputStream(): OutputStream?
    fun openInputStream(): InputStream?
}

/** The complete backup could not be written to and closed at the destination. */
class BackupWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The destination was written but did not reopen, decode, and validate as the prepared backup. */
class BackupVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Writes a complete current backup and reports success only after reopening the
 * actual destination and validating its contents with the shared codec and semantic validator.
 * Manual and automatic backup both use this writer.
 */
class BackupFileWriter {
    fun validateFullBackup(encoded: String): BackupFile = validatedFullBackup(encoded)

    fun writeAndVerify(destination: BackupDestination, encoded: String): BackupFile {
        val prepared = validatedFullBackup(encoded)

        try {
            destination.openOutputStream()?.use { output ->
                output.write(encoded.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } ?: throw BackupWriteException("No output stream")
        } catch (error: BackupWriteException) {
            throw error
        } catch (error: Exception) {
            throw BackupWriteException(error.message ?: "The backup could not be written.", error)
        }

        try {
            val saved = destination.openInputStream()?.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            } ?: throw BackupVerificationException("No input stream")
            val reopened = validatedFullBackup(saved)
            if (BackupCodec.encode(reopened) != BackupCodec.encode(prepared)) {
                throw BackupVerificationException("Saved backup does not match the prepared backup.")
            }
            return reopened
        } catch (error: BackupVerificationException) {
            throw error
        } catch (error: Exception) {
            throw BackupVerificationException(error.message ?: "The saved backup could not be verified.", error)
        }
    }

    private fun validatedFullBackup(encoded: String): BackupFile {
        val backup = BackupCodec.decode(encoded)
        require(backup.version == BackupFile.VERSION) { "Manual backup is not a current backup." }
        require(backup.includedCategories == BackupCategory.entries) {
            "Manual backup does not contain the complete portable payload."
        }
        BackupRestoreValidator.validate(backup)
        return backup
    }
}
