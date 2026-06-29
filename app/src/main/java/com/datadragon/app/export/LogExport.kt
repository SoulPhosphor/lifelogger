package com.datadragon.app.export

import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.CsvBuilder
import com.datadragon.app.data.EntryNote
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
 *
 * [notesByEntry] holds the append-only follow-up notes keyed by entry id;
 * [includeFollowUps] (chosen in the export dialog) decides whether they appear.
 * The per-entry free-text Notes box is always included — it's part of the entry.
 */
object LogExport {

    fun markdown(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): ExportContent {
        val report = ReportBuilder.build(
            template, fields, entries, markdown = true,
            entryNotes = notesByEntry, includeFollowUps = includeFollowUps,
        )
        return ExportContent(report.fileName, report.text.toByteArray())
    }

    fun text(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): ExportContent {
        val report = ReportBuilder.build(
            template, fields, entries, markdown = false,
            entryNotes = notesByEntry, includeFollowUps = includeFollowUps,
        )
        return ExportContent(report.fileName, report.text.toByteArray())
    }

    fun json(
        template: LogTemplate,
        entries: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): ExportContent {
        val notes = if (includeFollowUps) notesByEntry.values.flatten() else emptyList()
        val json = BackupCodec.encodeSingleLog(template, entries, BackupRepository.now(), notes)
        return ExportContent("${ExportNaming.base(template.name)}.json", json.toByteArray())
    }

    fun csv(template: LogTemplate, fields: List<FieldDef>, entries: List<LogEntry>): ExportContent =
        ExportContent(
            "${ExportNaming.base(template.name)}.csv",
            CsvBuilder.build(fields, entries).toByteArray(),
        )

    fun pdf(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): ExportContent =
        ExportContent(
            "${ExportNaming.base(template.name)}_report.pdf",
            PdfReport.writeToBytes(template, fields, entries, notesByEntry, includeFollowUps),
        )
}
