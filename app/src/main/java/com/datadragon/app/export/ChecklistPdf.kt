package com.datadragon.app.export

import com.datadragon.app.data.Checklist
import com.datadragon.app.data.ChecklistItem

/**
 * Renders one list to a one-column PDF via the shared [ListPdf] renderer: the
 * list name at the top, then each item as `[x] text` (done) or `[ ] text` (open),
 * with sub-items indented under the item above them.
 */
object ChecklistPdf {

    fun writeToBytes(checklist: Checklist, items: List<ChecklistItem>): ByteArray =
        ListPdf.writeToBytes(
            title = checklist.name.ifBlank { "List" },
            rows = items.map { ListPdfRow(it.text, it.completed, it.indent) },
        )
}
