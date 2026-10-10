package com.datadragon.app.data

import kotlin.random.Random

/** One winner-dialog session. Stable row IDs distinguish equal-text options. Never persisted. */
class LuckySelectionSession(private val random: Random = Random.Default) {
    private val selected = mutableSetOf<Long>()
    fun select(items: List<ChecklistItem>, excludePreviouslySelected: Boolean): ChecklistItem? {
        val eligible = items.filter { it.text.isNotBlank() && (!excludePreviouslySelected || it.id !in selected) }
        val winner = eligible.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] } ?: return null
        selected += winner.id
        return winner
    }
    fun reset() { selected.clear() }
}
