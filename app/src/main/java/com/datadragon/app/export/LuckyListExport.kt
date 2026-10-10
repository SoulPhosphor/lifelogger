package com.datadragon.app.export

import com.datadragon.app.data.*

object LuckyListExport {
    fun build(list: LuckyList, items: List<LuckyListItem>, format: ChecklistExportFormat): ExportContent {
        val ordered = items.sortedBy { it.position }
        val title = list.name.ifBlank { "Lucky List" }
        val base = ExportNaming.base(list.name)
        return when (format) {
            ChecklistExportFormat.JSON -> ExportContent("$base.json", BackupCodec.encodeSingleLuckyList(list, ordered, BackupRepository.now()).toByteArray())
            ChecklistExportFormat.TEXT -> ExportContent("$base.txt", (title + "\n\n" + ordered.filter { it.text.isNotBlank() }.joinToString("\n", postfix = "\n") { it.text }).toByteArray())
            ChecklistExportFormat.MARKDOWN -> ExportContent("$base.md", ("# $title\n\n" + ordered.filter { it.text.isNotBlank() }.joinToString("\n", postfix = "\n") { "- ${it.text}" }).toByteArray())
            ChecklistExportFormat.PDF -> ExportContent("$base.pdf", ListPdf.writeToBytes(title, ordered.map { ListPdfRow(it.text, false, 0) }, showCompletion = false))
        }
    }
}
