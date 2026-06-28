package com.datadragon.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.datadragon.app.data.ReportBuilder
import java.io.File

/**
 * Writes a built [ReportBuilder.Report] to the cache and launches the Android
 * share/save sheet so the user picks where it goes (docs/FORMATTING_SPEC.md §5).
 */
fun shareReport(context: Context, report: ReportBuilder.Report) {
    shareTextFile(context, report.fileName, report.mimeType, report.text)
}

/**
 * Writes [text] to a cache file named [fileName] and shares it via a
 * FileProvider content URI. Used for report (.txt/.md) and single-log .json
 * exports.
 */
fun shareTextFile(context: Context, fileName: String, mimeType: String, text: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(text)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(sendIntent, "Share").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}
