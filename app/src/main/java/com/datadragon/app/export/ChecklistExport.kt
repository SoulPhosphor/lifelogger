package com.datadragon.app.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.ExportNaming
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * Builds the downloadable representations of one list. Each function only
 * produces the file bytes; the caller saves them wherever the user picks.
 *
 * Blank rows are dropped from every export. Completion is shown as a checkbox
 * (`[x]`/`[ ]` in text, a drawn box in the PDF, a boolean in JSON); sub-items
 * (indent 1) are indented under the item above them.
 */
object ChecklistExport {

    fun text(title: String, items: List<ChecklistItem>): ExportContent {
        val sb = StringBuilder()
        sb.append(title.ifBlank { UNTITLED }).append("\n\n")
        visible(items).forEach { item ->
            val indent = if (item.indent == 1) "    " else ""
            val box = if (item.completed) "[x]" else "[ ]"
            sb.append(indent).append(box).append(' ').append(item.text).append('\n')
        }
        return ExportContent("${baseName(title)}.txt", sb.toString().toByteArray())
    }

    fun json(title: String, items: List<ChecklistItem>): ExportContent {
        val dto = ChecklistDto(
            title = title.trim(),
            items = visible(items).map { ChecklistItemDto(it.text, it.completed, it.indent) },
        )
        val json = PRETTY.encodeToString(dto)
        return ExportContent("${baseName(title)}.json", json.toByteArray())
    }

    fun pdf(title: String, items: List<ChecklistItem>): ExportContent {
        val doc = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { textSize = 13f; isAntiAlias = true }
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            isAntiAlias = true
        }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var pageNo = 1
        var y = MARGIN + 20f

        canvas.drawText(title.ifBlank { UNTITLED }, MARGIN, y, titlePaint)
        y += 30f

        visible(items).forEach { item ->
            if (y > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN + 20f
            }
            val left = MARGIN + if (item.indent == 1) 24f else 0f
            // A small drawn checkbox (drawn, not a glyph, so it never renders as
            // tofu on devices whose PDF font lacks the box characters).
            val top = y - BOX
            canvas.drawRect(left, top, left + BOX, top + BOX, boxPaint)
            if (item.completed) {
                canvas.drawLine(left + 2f, top + BOX * 0.55f, left + BOX * 0.42f, top + BOX - 2f, boxPaint)
                canvas.drawLine(left + BOX * 0.42f, top + BOX - 2f, left + BOX - 1f, top + 2f, boxPaint)
            }
            canvas.drawText(item.text, left + BOX + 8f, y, bodyPaint)
            y += 22f
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return ExportContent("${baseName(title)}_report.pdf", out.toByteArray())
    }

    private fun visible(items: List<ChecklistItem>) = items.filter { it.text.isNotBlank() }

    /** `untitled_list` for a title-less list, otherwise the cleaned title. */
    private fun baseName(title: String): String =
        if (title.isBlank()) "untitled_list" else ExportNaming.base(title)

    private const val UNTITLED = "Untitled list"
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BOX = 11f
    private val PRETTY = Json { prettyPrint = true }
}

@Serializable
private data class ChecklistDto(val title: String, val items: List<ChecklistItemDto>)

@Serializable
private data class ChecklistItemDto(val text: String, val completed: Boolean, val indent: Int)
