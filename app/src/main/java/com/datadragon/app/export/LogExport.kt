package com.datadragon.app.export

import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.CsvBuilder
import com.datadragon.app.data.ExportNaming
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import com.datadragon.app.data.ReportBuilder

/** A file ready to be written to a user-chosen location: its suggested name and bytes. */
class ExportContent(val suggestedName: String, val bytes: ByteArray)

/**
 * Builds each downloadable representation of one log (docs/FORMATTING_SPEC.md §5).
 * These only produce the file bytes; the caller saves them to a location the user
 * picks via the system "Save to…" document sheet.
 */
object LogExport {

    fun markdown(template: LogTemplate, fields: List<FieldDef>, entries: List<LogEntry>): ExportContent {
        val report = ReportBuilder.build(template, fields, entries, markdown = true)
        return ExportContent(report.fileName, report.text.toByteArray())
    }

    fun text(template: LogTemplate, fields: List<FieldDef>, entries: List<LogEntry>): ExportContent {
        val report = ReportBuilder.build(template, fields, entries, markdown = false)
        return ExportContent(report.fileName, report.text.toByteArray())
    }

    fun json(template: LogTemplate, entries: List<LogEntry>): ExportContent {
        val json = BackupCodec.encodeSingleLog(template, entries, BackupRepository.now())
        return ExportContent("${ExportNaming.base(template.name)}.json", json.toByteArray())
    }

    fun csv(template: LogTemplate, fields: List<FieldDef>, entries: List<LogEntry>): ExportContent =
        ExportContent(
            "${ExportNaming.base(template.name)}.csv",
            CsvBuilder.build(fields, entries).toByteArray(),
        )

    fun pdf(template: LogTemplate, fields: List<FieldDef>, entries: List<LogEntry>): ExportContent =
        ExportContent(
            "${ExportNaming.base(template.name)}_report.pdf",
            PdfReport.writeToBytes(template, fields, entries),
        )
}
