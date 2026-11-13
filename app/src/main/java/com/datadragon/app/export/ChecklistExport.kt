package com.datadragon.app.export

import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.ExportNaming
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The formats a single list can be exported as. */
enum class ChecklistExportFormat { MARKDOWN, JSON, CLEAN_JSON, TEXT, PDF }

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

    /**
     * The "Human Readable JSON (.json)" file: the `.txt` export as structured JSON.
     * `title` and `items` in the same order, blank items left out, as in the
     * `.txt`. Each item holds `text` and `completed`; a sub-item sits in the
     * `subItems` of the item above it (one with no item above stays in `items`).
     */
    fun cleanJson(checklist: Checklist, items: List<ChecklistItem>): ExportContent {
        var parentSubItems: MutableList<JsonElement>? = null
        val parents = mutableListOf<Pair<MutableMap<String, JsonElement>, MutableList<JsonElement>>>()
        items.filter { it.text.isNotBlank() }.forEach { item ->
            val node = linkedMapOf<String, JsonElement>(
                "text" to JsonPrimitive(item.text),
                "completed" to JsonPrimitive(item.completed),
            )
            val under = parentSubItems
            if (item.indent == 1 && under != null) {
                under.add(JsonObject(node))
            } else {
                val subs = mutableListOf<JsonElement>()
                parents.add(node to subs)
                parentSubItems = subs
            }
        }
        val rendered = parents.map { (node, subs) ->
            if (subs.isNotEmpty()) node["subItems"] = JsonArray(subs)
            JsonObject(node)
        }
        val root = JsonObject(
            linkedMapOf(
                "title" to JsonPrimitive(checklist.name.ifBlank { "List" }),
                "items" to JsonArray(rendered),
            ),
        )
        return ExportContent(
            ExportNaming.cleanJsonFileName(checklist.name),
            prettyJson.encodeToString(JsonElement.serializer(), root).toByteArray(),
        )
    }

    internal val prettyJson = Json { prettyPrint = true }

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
