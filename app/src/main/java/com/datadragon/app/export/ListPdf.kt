package com.datadragon.app.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** One printed line of a list PDF: its text, done state, and nesting (0 or 1). */
data class ListPdfRow(val text: String, val completed: Boolean, val indent: Int)

/**
 * Renders a title plus a checkbox list to a one-column PDF using the framework's
 * [PdfDocument] — no third-party library, mirroring [PdfReport]. Shared by every
 * list-style export (ordinary Lists and Daily Tasks): the title sits at the top,
 * then each row as `[x] text` (done) or `[ ] text` (open); a sub-item (indent 1)
 * is indented under the row above it. Long lines wrap and content paginates.
 */
object ListPdf {


    fun writeToBytes(title: String, rows: List<ListPdfRow>, showCompletion: Boolean = true): ByteArray {
        val titlePaint = paint(PrintStyle.titleSize, bold = true)
        val bodyPaint = paint(PrintStyle.bodySize)

        val doc = PdfDocument()
        val writer = PageWriter(doc)

        writer.text(titlePaint, title, indent = 0f)
        writer.gap(PrintStyle.sectionGap)

        rows.filter { it.text.isNotBlank() }.forEach { row ->
            val box = if (row.completed) "[x]" else "[ ]"
            val indent = if (row.indent == 1) PrintStyle.subIndent else 0f
            writer.text(bodyPaint, if (showCompletion) "$box ${row.text}" else row.text, indent = indent)
        }

        writer.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun paint(size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = PrintStyle.bodyColor
        if (bold) typeface = PrintStyle.boldTypeface
    }

    /** Cursor that lays text out top-to-bottom, paginating when a page fills. */
    private class PageWriter(private val doc: PdfDocument) {
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

        /** Draw [content], wrapped, starting [indent] points in from the margin. */
        fun text(paint: Paint, content: String, indent: Float) {
            val left = PrintStyle.margin + indent
            val maxWidth = PrintStyle.pageWidth - PrintStyle.margin - left
            val fm = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent + fm.leading
            for (line in wrap(content, paint, maxWidth)) {
                if (y + lineHeight > bottom) newPage()
                canvas.drawText(line, left, y - fm.ascent, paint)
                y += lineHeight
            }
        }

        fun finish() {
            doc.finishPage(page)
        }

        /** Word-wrap to [maxWidth], honoring newlines and hard-breaking any single
         *  word that is itself too wide. */
        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
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
