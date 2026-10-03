package com.datadragon.app.export

import android.graphics.pdf.PdfDocument
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerReport
import com.datadragon.app.data.ExportNaming
import java.io.ByteArrayOutputStream

/**
 * Builds each downloadable representation of one Clicker Data grouping, the
 * same set Forms offer. These only produce the file bytes; the caller saves
 * them to a location the user picks via the system "Save to…" document sheet.
 *
 * [includeFollowUps] (chosen in the export dialog) decides whether the cards'
 * Follow-Up Notes appear in every format.
 */
object ClickerExport {

    fun text(log: ClickerLog, fields: List<ClickerField>, cards: List<ClickerCard>, includeFollowUps: Boolean): ExportContent =
        ExportContent(
            ExportNaming.fileName(log.title, "txt"),
            ClickerReport.text(ClickerReport.build(log, fields, cards, includeFollowUps), markdown = false).toByteArray(),
        )

    fun markdown(log: ClickerLog, fields: List<ClickerField>, cards: List<ClickerCard>, includeFollowUps: Boolean): ExportContent =
        ExportContent(
            ExportNaming.fileName(log.title, "md"),
            ClickerReport.text(ClickerReport.build(log, fields, cards, includeFollowUps), markdown = true).toByteArray(),
        )

    fun csv(log: ClickerLog, fields: List<ClickerField>, cards: List<ClickerCard>, includeFollowUps: Boolean): ExportContent =
        ExportContent(
            ExportNaming.fileName(log.title, "csv"),
            ClickerReport.csv(log, fields, cards, includeFollowUps).toByteArray(),
        )

    fun json(log: ClickerLog, cards: List<ClickerCard>, includeFollowUps: Boolean): ExportContent =
        ExportContent(
            ExportNaming.fileName(log.title, "json"),
            BackupCodec.encodeSingleClicker(log, cards, BackupRepository.now(), includeFollowUps).toByteArray(),
        )

    fun pdf(log: ClickerLog, fields: List<ClickerField>, cards: List<ClickerCard>, includeFollowUps: Boolean): ExportContent =
        ExportContent(
            ExportNaming.fileName(log.title, "pdf"),
            pdfBytes(ClickerReport.build(log, fields, cards, includeFollowUps)),
        )

    /** Renders the report with the Forms PDF's page layout, paints, and spacing. */
    private fun pdfBytes(report: ClickerReport.Report): ByteArray {
        val titlePaint = PdfReport.paint(PrintStyle.titleSize, bold = true)
        val metaPaint = PdfReport.paint(PrintStyle.metadataSize).apply { color = PrintStyle.metadataColor }
        val headingPaint = PdfReport.paint(PrintStyle.headingSize, bold = true)
        val labelPaint = PdfReport.paint(PrintStyle.bodySize, bold = true)
        val bodyPaint = PdfReport.paint(PrintStyle.bodySize)

        val doc = PdfDocument()
        val writer = PdfReport.PageWriter(doc)

        writer.text(titlePaint, report.title)
        report.dateRange?.let { writer.text(metaPaint, "Date range: $it") }
        writer.text(metaPaint, "Total cards: ${report.totalCards}")

        report.cards.forEach { card ->
            writer.gap(PrintStyle.sectionGap)
            writer.rule()
            writer.gap(PrintStyle.sectionGap)
            writer.text(headingPaint, card.heading)
            writer.gap(PrintStyle.relatedGap)
            card.lines.forEach { line ->
                if (line.block) {
                    writer.text(labelPaint, "${line.label}:")
                    writer.text(bodyPaint, line.value)
                } else {
                    writer.text(bodyPaint, "${line.label}: ${line.value}")
                }
            }
            card.followUp?.let { note ->
                writer.gap(PrintStyle.relatedGap)
                writer.text(labelPaint, "Follow-Up Notes:")
                writer.text(bodyPaint, note)
            }
        }

        writer.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
