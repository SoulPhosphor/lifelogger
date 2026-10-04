package com.datadragon.app.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import java.io.ByteArrayOutputStream

/**
 * Renders the readable report (docs/FORMATTING_SPEC.md §2) to a one-column PDF
 * using the framework's [PdfDocument] — no third-party PDF library. The layout
 * mirrors the .md/.txt report: title, date range, total entries, then each entry
 * field-by-field with a Notes block. Long lines wrap and content paginates.
 */
object PdfReport {


    fun writeToBytes(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        entryNotes: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): ByteArray {
        val titlePaint = paint(PrintStyle.titleSize, bold = true)
        val metaPaint = paint(PrintStyle.metadataSize).apply { color = PrintStyle.metadataColor }
        val headingPaint = paint(PrintStyle.headingSize, bold = true)
        val labelPaint = paint(PrintStyle.bodySize, bold = true)
        val bodyPaint = paint(PrintStyle.bodySize)

        val doc = PdfDocument()
        val writer = PageWriter(doc)
        val ordered = entries.sortedBy { it.createdAt }

        writer.text(titlePaint, "${template.name} Report")
        if (ordered.isNotEmpty()) {
            val first = EntryValues.displayEntryDate(ordered.first().createdAt)
            val last = EntryValues.displayEntryDate(ordered.last().createdAt)
            writer.text(metaPaint, "Date range: " + if (first == last) first else "$first – $last")
        }
        writer.text(metaPaint, "Total entries: ${ordered.size}")

        ordered.forEach { entry ->
            writer.gap(PrintStyle.sectionGap)
            writer.rule()
            writer.gap(PrintStyle.sectionGap)
            writer.text(headingPaint, EntryValues.displayEntryDateTime(entry.createdAt))
            writer.gap(PrintStyle.relatedGap)

            val values = EntryValues.decode(entry.valuesJson)
            fields.forEach { field ->
                val value = EntryValues.displayValue(field, values) ?: return@forEach
                if (field.type == FieldType.MULTILINE) {
                    writer.text(labelPaint, "${field.label}:")
                    writer.text(bodyPaint, value)
                } else {
                    writer.text(bodyPaint, "${field.label}: $value")
                }
            }
            EntryValues.notes(values)?.let { notes ->
                writer.gap(PrintStyle.relatedGap)
                writer.text(labelPaint, "Notes:")
                writer.text(bodyPaint, notes)
            }

            val followUps = if (includeFollowUps) entryNotes[entry.id].orEmpty() else emptyList()
            if (followUps.isNotEmpty()) {
                writer.gap(PrintStyle.relatedGap)
                writer.text(labelPaint, "Follow-Up Notes:")
                followUps.forEach { note ->
                    writer.text(
                        bodyPaint,
                        "• ${EntryValues.displayEntryDateTime(note.createdAt)}: ${note.text}",
                    )
                }
            }
        }

        writer.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    internal fun paint(size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = PrintStyle.bodyColor
        if (bold) typeface = PrintStyle.boldTypeface
    }

    /** Cursor that lays text out top-to-bottom, paginating when a page fills. */
    internal class PageWriter(private val doc: PdfDocument) {
        private val maxWidth = PrintStyle.pageWidth - 2 * PrintStyle.margin
        private val bottom = PrintStyle.pageHeight - PrintStyle.margin
        private var pageNumber = 1
        private var page = startPage()
        private var canvas = page.canvas
        private var y = PrintStyle.margin

        private fun startPage(): PdfDocument.Page =
            doc.startPage(PdfDocument.PageInfo.Builder(PrintStyle.pageWidth, PrintStyle.pageHeight, pageNumber).create())

        private fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = startPage()
            canvas = page.canvas
            y = PrintStyle.margin
        }

        fun gap(amount: Float) {
            y += amount
        }

        fun rule() {
            if (y + PrintStyle.ruleWidth > bottom) newPage()
            val linePaint = Paint().apply {
                strokeWidth = PrintStyle.ruleWidth
                color = PrintStyle.ruleColor
            }
            canvas.drawLine(PrintStyle.margin, y, PrintStyle.pageWidth - PrintStyle.margin, y, linePaint)
            y += PrintStyle.ruleWidth
        }

        fun text(paint: Paint, content: String) {
            val fm = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent + fm.leading
            for (line in wrap(content, paint)) {
                if (y + lineHeight > bottom) newPage()
                canvas.drawText(line, PrintStyle.margin, y - fm.ascent, paint)
                y += lineHeight
            }
        }

        fun finish() {
            doc.finishPage(page)
        }

        /** Word-wrap to [maxWidth], honoring existing newlines and hard-breaking
         *  any single word that is itself too wide. */
        private fun wrap(text: String, paint: Paint): List<String> {
            val lines = mutableListOf<String>()
            for (paragraph in text.split("\n")) {
                if (paragraph.isEmpty()) {
                    lines.add("")
                    continue
                }
                var line = StringBuilder()
                for (word in paragraph.split(" ")) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    when {
                        paint.measureText(candidate) <= maxWidth -> line = StringBuilder(candidate)
                        paint.measureText(word) > maxWidth -> {
                            if (line.isNotEmpty()) {
                                lines.add(line.toString())
                                line = StringBuilder()
                            }
                            var chunk = StringBuilder()
                            for (ch in word) {
                                if (chunk.isNotEmpty() && paint.measureText("$chunk$ch") > maxWidth) {
                                    lines.add(chunk.toString())
                                    chunk = StringBuilder()
                                }
                                chunk.append(ch)
                            }
                            line = chunk
                        }
                        else -> {
                            if (line.isNotEmpty()) lines.add(line.toString())
                            line = StringBuilder(word)
                        }
                    }
                }
                lines.add(line.toString())
            }
            return lines
        }
    }
}
