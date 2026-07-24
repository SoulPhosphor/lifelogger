package com.datadragon.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the one pre-import [UndoSnapshot] Undo Last Import restores from.
 * Written automatically right before any restore runs; overwritten by the next
 * one. There is no expiry — it stays available until that next import replaces
 * it, whether or not it has already been used.
 */
class UndoSnapshotStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "pre_import_snapshot.json")

    suspend fun save(snapshot: UndoSnapshot) = withContext(Dispatchers.IO) {
        file.writeText(BackupCodec.encodeSnapshot(snapshot))
    }

    suspend fun load(): UndoSnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { BackupCodec.decodeSnapshot(file.readText()) }.getOrNull()
    }
}
