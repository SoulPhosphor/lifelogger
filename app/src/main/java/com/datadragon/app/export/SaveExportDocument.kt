package com.datadragon.app.export

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system "Save to…" sheet, with the MIME type taken from the suggested
 * file name's extension instead of a fixed type.
 *
 * With the generic wildcard type the document provider can't tie ".json" to the
 * requested type, so it treats the whole name as the base and appends its
 * duplicate counter after the extension ("log.json (8)"). Matching the type to
 * the extension makes it insert the counter before it ("log (8).json").
 */
class SaveExportDocument : ActivityResultContracts.CreateDocument("*/*") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).setType(mimeTypeFor(input))

    private fun mimeTypeFor(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
