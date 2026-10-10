package com.datadragon.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafAutoBackupFoldersTest {
    @Test fun directoryWithBackupFilenameIsNeverOfferedForRotation() {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                val columns = projection!!
                assertEquals(listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE), columns.toList())
                return MatrixCursor(columns).apply {
                    addRow(arrayOf("datadragon_autobackup_2026-01-01.json", "directory", Document.MIME_TYPE_DIR))
                    addRow(arrayOf("datadragon_autobackup_2026-01-02.json", "backup", "application/json"))
                    addRow(arrayOf("notes.txt", "notes", "text/plain"))
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal("backup.test", provider)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = SafAutoBackupFolders(context).open("content://backup.test/tree/root").listFiles()
        assertEquals(listOf(
            AutoBackupFolderFile("datadragon_autobackup_2026-01-02.json", "backup"),
            AutoBackupFolderFile("notes.txt", "notes"),
        ), files)
    }
}
