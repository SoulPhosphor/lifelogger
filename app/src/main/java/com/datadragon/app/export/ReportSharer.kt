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
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, report.fileName)
    file.writeText(report.text)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = report.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, report.fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(sendIntent, "Share report").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}
