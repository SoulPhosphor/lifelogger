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
    const val SETTINGS = "settings"
    const val BACKUP = "backup"

    // Parameterised routes.
    const val LOG_ARG = "logId"
    const val LOG = "log/{$LOG_ARG}"
    const val NEW_ENTRY = "log/{$LOG_ARG}/newEntry"

    fun log(logId: String) = "log/$logId"
    fun newEntry(logId: String) = "log/$logId/newEntry"
}
