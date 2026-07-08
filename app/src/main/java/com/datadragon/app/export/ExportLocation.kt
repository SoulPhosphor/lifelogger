package com.datadragon.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import com.datadragon.app.data.SettingsRepository

/**
 * Where exported files and backups get saved.
 *
 * The system "Save to…" sheet always lets the user pick the destination; this
 * just biases the *starting* folder so it feels like a normal download: it opens
 * in the folder used last time, or in Downloads the very first time. Android
 * treats the initial location as a hint, so the picker has the final say.
 */
object ExportLocation {

    // The standard Storage Access Framework URI for the device's Downloads folder.
    private val DOWNLOADS: Uri =
        Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")

    /** The folder the next save sheet should open in. */
    fun initialUri(settings: SettingsRepository): Uri =
        settings.lastExportDir?.let { Uri.parse(it) } ?: DOWNLOADS

    /** Remember where the user just saved, so the next save opens there. */
    fun remember(settings: SettingsRepository, savedUri: Uri) {
        settings.lastExportDir = savedUri.toString()
    }
}

/**
 * A [ActivityResultContracts.CreateDocument] that opens the save sheet in a
 * chosen starting folder. [initialUri] is read at launch time (a lambda, not a
 * captured value) so it always reflects the most recently used location.
 */
class CreateDocumentInFolder(
    mimeType: String,
    private val initialUri: () -> Uri?,
) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        initialUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        return intent
    }
}
