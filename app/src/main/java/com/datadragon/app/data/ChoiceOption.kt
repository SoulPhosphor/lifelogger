package com.datadragon.app.data

import kotlinx.serialization.Serializable

/**
 * One selectable option of a Form Single Choice / Multiple Choice field, or one
 * Idea Category.
 *
 * [id] is the option's permanent identity. It is generated once, when the option
 * is created, and never changes afterwards: renaming ([label]), reordering (its
 * position in the owning list), retiring and restoring all keep it. Saved
 * answers store this id, never the label, so a rename can't disconnect them.
 *
 * A [retired] option is hidden from new answers but keeps its id and every
 * answer already saved against it; it can be restored later. A retired option
 * is never pre-selected.
 *
 * [preselected] marks the option as selected automatically when a new entry
 * opens (Forms only — Idea Categories never set it). A Single Choice field holds
 * at most one pre-selected option.
 */
@Serializable
data class ChoiceOption(
    val id: String,
    val label: String,
    val retired: Boolean = false,
    val preselected: Boolean = false,
) {
    companion object {
        /** The only path that creates a brand-new option with a fresh identity. */
        fun createNew(label: String, preselected: Boolean = false): ChoiceOption =
            ChoiceOption(id = StableUuid.createNew(), label = label, preselected = preselected)
    }
}

/** The options still offered for new answers, in display order. */
val List<ChoiceOption>.activeChoices: List<ChoiceOption>
    get() = filter { !it.retired }

/** The retired options, in display order. */
val List<ChoiceOption>.retiredChoices: List<ChoiceOption>
    get() = filter { it.retired }

/**
 * Pure, identity-preserving edits to an option list. Every function returns a
 * new list and never creates, drops or changes an id except where its name says
 * so ([withNew] and [withoutId]).
 */
object ChoiceEdits {

    /** Rename the option [id] in place; its position and identity are unchanged. */
    fun renamed(choices: List<ChoiceOption>, id: String, label: String): List<ChoiceOption> =
        choices.map { if (it.id == id) it.copy(label = label) else it }

    /**
     * Move the active option [id] to [toActiveIndex] among the active options.
     * Retired options keep their relative order; only display order changes.
     */
    fun movedActive(choices: List<ChoiceOption>, id: String, toActiveIndex: Int): List<ChoiceOption> {
        val active = choices.activeChoices.toMutableList()
        val from = active.indexOfFirst { it.id == id }
        if (from < 0 || toActiveIndex !in active.indices) return choices
        active.add(toActiveIndex, active.removeAt(from))
        return active + choices.retiredChoices
    }

    /** Retire [id]: it leaves the active list, keeps its id, and is no longer pre-selected. */
    fun retired(choices: List<ChoiceOption>, id: String): List<ChoiceOption> {
        val target = choices.firstOrNull { it.id == id && !it.retired } ?: return choices
        val updated = target.copy(retired = true, preselected = false)
        return choices.filter { it.id != id }.activeChoices + choices.retiredChoices + updated
    }

    /**
     * Restore the retired option [id] to the end of the active list with its
     * original id. It comes back not pre-selected, so restoring never replaces an
     * existing Single Choice default.
     */
    fun restored(choices: List<ChoiceOption>, id: String): List<ChoiceOption> {
        val target = choices.firstOrNull { it.id == id && it.retired } ?: return choices
        val others = choices.filter { it.id != id }
        return others.activeChoices + target.copy(retired = false, preselected = false) + others.retiredChoices
    }

    /** Append a brand-new active option. */
    fun withNew(choices: List<ChoiceOption>, option: ChoiceOption): List<ChoiceOption> =
        choices.activeChoices + option + choices.retiredChoices

    /** Drop the option [id] entirely (permanent deletion). */
    fun withoutId(choices: List<ChoiceOption>, id: String): List<ChoiceOption> =
        choices.filter { it.id != id }

    /**
     * Turn pre-selection on for the active option [id]. When [single] is true
     * (Single Choice), every other option loses its pre-selection, so the field
     * keeps at most one.
     */
    fun preselected(choices: List<ChoiceOption>, id: String, single: Boolean): List<ChoiceOption> {
        if (choices.none { it.id == id && !it.retired }) return choices
        return choices.map {
            when {
                it.id == id -> it.copy(preselected = true)
                single -> it.copy(preselected = false)
                else -> it
            }
        }
    }

    /** Turn pre-selection off for [id]; no other option is affected. */
    fun notPreselected(choices: List<ChoiceOption>, id: String): List<ChoiceOption> =
        choices.map { if (it.id == id) it.copy(preselected = false) else it }
}
