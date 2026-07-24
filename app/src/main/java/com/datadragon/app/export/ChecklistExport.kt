package com.datadragon.app.export

import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.ExportNaming

/** The formats a single list can be exported as. */
enum class ChecklistExportFormat { MARKDOWN, JSON, TEXT, PDF }

/**
 * Builds each downloadable representation of one list. Like [LogExport], these
 * only produce the file bytes; the caller saves them to a location the user
 * picks. A completed item shows `[x]`, an open one `[ ]`; a sub-item (one level
 * deep) is indented under the item above it.
 */
object ChecklistExport {

    fun json(checklist: Checklist, items: List<ChecklistItem>): ExportContent =
        ExportContent(
            "${ExportNaming.base(checklist.name)}.json",
            BackupCodec.encodeSingleChecklist(checklist, items, BackupRepository.now()).toByteArray(),
        )

    fun markdown(checklist: Checklist, items: List<ChecklistItem>): ExportContent {
        val title = checklist.name.ifBlank { "List" }
        val body = buildString {
            append("# ").append(title).append("\n\n")
            items.filter { it.text.isNotBlank() }.forEach { item ->
                val indent = if (item.indent == 1) "  " else ""
                val box = if (item.completed) "[x]" else "[ ]"
                append(indent).append("- ").append(box).append(' ').append(item.text).append('\n')
            }
        }
        return ExportContent("${ExportNaming.base(checklist.name)}.md", body.toByteArray())
    }

    fun text(checklist: Checklist, items: List<ChecklistItem>): ExportContent {
        val title = checklist.name.ifBlank { "List" }
        val body = buildString {
            append(title).append("\n\n")
            items.filter { it.text.isNotBlank() }.forEach { item ->
                val indent = if (item.indent == 1) "    " else ""
                val box = if (item.completed) "[x]" else "[ ]"
                append(indent).append(box).append(' ').append(item.text).append('\n')
            }
        }
        return ExportContent("${ExportNaming.base(checklist.name)}.txt", body.toByteArray())
    }

    fun pdf(checklist: Checklist, items: List<ChecklistItem>): ExportContent =
        ExportContent(
            "${ExportNaming.base(checklist.name)}.pdf",
            ChecklistPdf.writeToBytes(checklist, items),
        )
}
