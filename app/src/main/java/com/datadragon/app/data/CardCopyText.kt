package com.datadragon.app.data

/** Plain text for pasting elsewhere; preview limits never shorten copied data. */
object CardCopyText {
    fun form(entry: LogEntry, fields: List<FieldDef>, notes: List<EntryNote>, timestamp: Boolean): String {
        val values = EntryValues.decode(entry.valuesJson)
        return buildList {
            if (timestamp) add(EntryValues.displayEntryTimestamp(entry.createdAt))
            if (notes.isNotEmpty()) {
                add("Follow-Up Notes")
                notes.forEach { add("${EntryValues.displayEntryTimestamp(it.createdAt)}: ${it.text}") }
            }
            fields.forEach { field -> EntryValues.displayValue(field, values)?.let { add("${field.label}: $it") } }
            EntryValues.notes(values)?.let { add("Notes: $it") }
        }.joinToString("\n")
    }

    fun idea(entry: IdeaEntry, fields: List<IdeaFieldDef>, timestamp: Boolean): String {
        val values = IdeaValues.decode(entry.valuesJson)
        return buildList {
            if (timestamp) add(EntryValues.displayEntryTimestamp(entry.createdAt))
            fields.forEach { field -> IdeaValues.displayValue(values, field)?.let { add("${field.label}: $it") } }
        }.joinToString("\n")
    }

    data class Item(val text: String, val completed: Boolean = false, val indent: Int = 0)

    fun list(title: String, items: List<Item>, lucky: Boolean = false): String = buildList {
        if (title.isNotBlank()) add(title)
        val filled = items.filter { it.text.isNotBlank() }
        fun section(label: String?, rows: List<Item>) {
            if (rows.isEmpty()) return
            add(buildList {
                if (label != null) add(label)
                rows.forEach { add("${if (it.indent == 1) "  " else ""}• ${it.text}") }
            }.joinToString("\n"))
        }
        if (lucky) section(null, filled) else {
            section("Unfinished Items", filled.filterNot { it.completed })
            section("Completed Items", filled.filter { it.completed })
        }
    }.joinToString("\n\n")

    fun clicker(log: ClickerLog, card: ClickerCard, fields: List<ClickerField>): String {
        val report = ClickerReport.build(log, fields, listOf(card), includeFollowUps = true).cards.single()
        return buildList {
            val date = ClickerReport.visibleDate(log, card)
            val time = ClickerReport.visibleTime(log, card)
            if (date != null || time != null) add(report.heading)
            report.lines.forEach { add("${it.label}: ${it.value}") }
            report.followUp?.let { add("Follow-Up Notes: $it") }
        }.joinToString("\n")
    }
}
