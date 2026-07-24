package com.datadragon.app.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.ChecklistItem
import java.io.ByteArrayOutputStream

/**
 * Renders one list to a one-column PDF using the framework's [PdfDocument] — no
 * third-party library, mirroring [PdfReport]. The title sits at the top, then
 * each item on its own line as `[x] text` (done) or `[ ] text` (open); a
 * sub-item (indent 1) is indented under the item above it. Long lines wrap and
 * content paginates.
 */
object ChecklistPdf {

    // A4 at 72dpi, in points.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val SUB_INDENT = 24f

    fun writeToBytes(checklist: Checklist, items: List<ChecklistItem>): ByteArray {
        val titlePaint = paint(20f, bold = true)
        val bodyPaint = paint(12f)

        val doc = PdfDocument()
        val writer = PageWriter(doc)

        writer.text(titlePaint, checklist.name.ifBlank { "List" }, indent = 0f)
        writer.gap(8f)

        items.filter { it.text.isNotBlank() }.forEach { item ->
            val box = if (item.completed) "[x]" else "[ ]"
            val indent = if (item.indent == 1) SUB_INDENT else 0f
            writer.text(bodyPaint, "$box ${item.text}", indent = indent)
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
        color = 0xFF000000.toInt()
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** Cursor that lays text out top-to-bottom, paginating when a page fills. */
    private class PageWriter(private val doc: PdfDocument) {
        private val bottom = PAGE_HEIGHT - MARGIN
        private var pageNumber = 1
        private var page = startPage()
        private var canvas = page.canvas
        private var y = MARGIN

        private fun startPage(): PdfDocument.Page =
            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())

        private fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = startPage()
            canvas = page.canvas
            y = MARGIN
        }

        fun gap(amount: Float) {
            y += amount
        }

        /** Draw [content], wrapped, starting [indent] points in from the margin. */
        fun text(paint: Paint, content: String, indent: Float) {
            val left = MARGIN + indent
            val maxWidth = PAGE_WIDTH - MARGIN - left
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
