package com.datadragon.app.navigation

/**
 * Central list of navigation routes.
 *
 * The route strings are the single source of truth used by both the NavHost and
 * the screens that trigger navigation, so they can never drift apart.
 */
object Routes {
    const val HOME = "home"
    const val CREATE_LOG = "createLog"
    const val CREATE_CHECKLIST = "createChecklist"
    const val CREATE_IDEA_LOG = "createIdeaLog"
    const val SETTINGS = "settings"

    // Parameterised routes.
    const val LOG_ARG = "logId"
    const val ENTRY_ARG = "entryId"
    const val NOTE_ARG = "noteId"
    const val CHECKLIST_ARG = "checklistId"
    const val LOG = "log/{$LOG_ARG}"
    const val NEW_ENTRY = "log/{$LOG_ARG}/newEntry"
    const val EDIT_ENTRY = "log/{$LOG_ARG}/entry/{$ENTRY_ARG}/edit"
    const val EDIT_FORM = "log/{$LOG_ARG}/editForm"
    const val CHECKLIST = "checklist/{$CHECKLIST_ARG}"

    // Ideas. An Idea Log's own screen, its editor, and one idea's detail /
    // create / edit screens.
    const val IDEA_LOG_ARG = "ideaLogId"
    const val IDEA_ARG = "ideaId"
    const val IDEA_LOG = "ideaLog/{$IDEA_LOG_ARG}"
    const val EDIT_IDEA_LOG = "ideaLog/{$IDEA_LOG_ARG}/edit"
    const val NEW_IDEA = "ideaLog/{$IDEA_LOG_ARG}/newIdea"
    const val IDEA_DETAIL = "ideaLog/{$IDEA_LOG_ARG}/idea/{$IDEA_ARG}"
    const val EDIT_IDEA = "ideaLog/{$IDEA_LOG_ARG}/idea/{$IDEA_ARG}/edit"

    // Add or edit a follow-up note. The optional noteId is absent when adding a
    // new note and present (as a query arg) when editing an existing one.
    const val FOLLOW_UP = "log/{$LOG_ARG}/entry/{$ENTRY_ARG}/note?$NOTE_ARG={$NOTE_ARG}"

    fun log(logId: String) = "log/$logId"
    fun newEntry(logId: String) = "log/$logId/newEntry"
    fun editEntry(logId: String, entryId: Long) = "log/$logId/entry/$entryId/edit"
    fun editForm(logId: String) = "log/$logId/editForm"
    fun checklist(checklistId: Long) = "checklist/$checklistId"

    fun ideaLog(ideaLogId: Long) = "ideaLog/$ideaLogId"
    fun editIdeaLog(ideaLogId: Long) = "ideaLog/$ideaLogId/edit"
    fun newIdea(ideaLogId: Long) = "ideaLog/$ideaLogId/newIdea"
    fun ideaDetail(ideaLogId: Long, ideaId: Long) = "ideaLog/$ideaLogId/idea/$ideaId"
    fun editIdea(ideaLogId: Long, ideaId: Long) = "ideaLog/$ideaLogId/idea/$ideaId/edit"

    fun followUp(logId: String, entryId: Long, noteId: Long? = null): String =
        "log/$logId/entry/$entryId/note" + (noteId?.let { "?$NOTE_ARG=$it" } ?: "")
}
