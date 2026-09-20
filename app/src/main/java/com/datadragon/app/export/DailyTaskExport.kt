package com.datadragon.app.export

import com.datadragon.app.data.DailyList
import com.datadragon.app.data.DailyListItem
import com.datadragon.app.data.ExportNaming
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The formats a single Daily Task log can be exported as — the same set Lists offer. */
enum class DailyTaskExportFormat { MARKDOWN, JSON, TEXT, PDF }

/**
 * Builds each downloadable representation of one Daily Task log. Like the other
 * exporters it only produces the file bytes; the caller saves them where the
 * user chooses. A completed task shows `[x]`, an open one `[ ]`; a sub-item (one
 * level deep) is indented under the task above it.
 */
object DailyTaskExport {

    private val jsonCodec = Json { prettyPrint = true }

    private val DISPLAY_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

    fun of(
        format: DailyTaskExportFormat,
        card: DailyList,
        items: List<DailyListItem>,
    ): ExportContent = when (format) {
        DailyTaskExportFormat.MARKDOWN -> markdown(card, items)
        DailyTaskExportFormat.JSON -> json(card, items)
        DailyTaskExportFormat.TEXT -> text(card, items)
        DailyTaskExportFormat.PDF -> pdf(card, items)
    }

    fun markdown(card: DailyList, items: List<DailyListItem>): ExportContent {
        val body = buildString {
            append("# ").append(headingFor(card)).append('\n')
            append('_').append(displayDate(card.date)).append("_\n\n")
            items.filter { it.text.isNotBlank() }.forEach { item ->
                val indent = if (item.indent == 1) "  " else ""
                val box = if (item.completed) "[x]" else "[ ]"
                append(indent).append("- ").append(box).append(' ').append(item.text).append('\n')
            }
        }
        return ExportContent("${baseName(card)}.md", body.toByteArray())
    }

    fun text(card: DailyList, items: List<DailyListItem>): ExportContent {
        val body = buildString {
            append(headingFor(card)).append('\n')
            append(displayDate(card.date)).append("\n\n")
            items.filter { it.text.isNotBlank() }.forEach { item ->
                val indent = if (item.indent == 1) "    " else ""
                val box = if (item.completed) "[x]" else "[ ]"
                append(indent).append(box).append(' ').append(item.text).append('\n')
            }
        }
        return ExportContent("${baseName(card)}.txt", body.toByteArray())
    }

    fun json(card: DailyList, items: List<DailyListItem>): ExportContent {
        val dto = DailyTaskDto(
            date = card.date.toString(),
            title = card.title,
            items = items.filter { it.text.isNotBlank() }
                .map { ItemDto(it.text, it.completed, it.indent) },
        )
        return ExportContent(
            "${baseName(card)}.json",
            jsonCodec.encodeToString(dto).toByteArray(),
        )
    }

    fun pdf(card: DailyList, items: List<DailyListItem>): ExportContent =
        ExportContent(
            "${baseName(card)}.pdf",
            ListPdf.writeToBytes(
                title = headingFor(card),
                rows = items.map { ListPdfRow(it.text, it.completed, it.indent) },
            ),
        )

    /** The document heading: the per-day title when set, else the readable date. */
    private fun headingFor(card: DailyList): String =
        card.title.ifBlank { displayDate(card.date) }

    private fun baseName(card: DailyList): String =
        ExportNaming.base(card.title.ifBlank { "Daily Task ${card.date}" })

    private fun displayDate(date: LocalDate): String = DISPLAY_DATE.format(date)

    @Serializable
    private data class DailyTaskDto(
        val date: String,
        val title: String,
        val items: List<ItemDto>,
    )

    @Serializable
    private data class ItemDto(
        val text: String,
        val completed: Boolean,
        val indent: Int,
    )
}
