package com.datadragon.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Holds the one pre-import [UndoSnapshot] Undo Last Import restores from.
 * Written automatically right before any restore runs; overwritten by the next
 * one. There is no expiry — it stays available until that next import replaces
 * it, whether or not it has already been used.
 */
class UndoSnapshotStore internal constructor(
    private val file: File,
    private val beforeAtomicReplace: (File) -> Unit = {},
) {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "pre_import_snapshot.json"),
    )

    /**
     * Secure the next undo slot before restore starts: write and fsync a sibling
     * temporary file, reopen and validate it, then atomically replace the slot.
     */
    suspend fun saveVerified(snapshot: UndoSnapshot) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(BackupCodec.encodeSnapshot(snapshot).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val verified = BackupCodec.decodeSnapshot(temporary.readText())
            BackupRestoreValidator.validate(verified.data)
            require(verified.selectedCategories == snapshot.selectedCategories.distinct().sortedBy { it.ordinal }) {
                "Undo category boundary could not be verified."
            }
            beforeAtomicReplace(temporary)
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    suspend fun load(): UndoSnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { BackupCodec.decodeSnapshot(file.readText()) }.getOrNull()
    }
}
