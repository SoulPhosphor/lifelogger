package com.datadragon.app.data

import android.content.Context

/**
 * Small key/value store for app-wide preferences, backed by [android.content.SharedPreferences].
 *
 * Only lightweight on/off settings live here (not user data, which is in Room).
 * Reads are cheap and synchronous, so callers can read a flag at the moment they
 * need it (e.g. when saving a new field) to honor the current toggle state.
 */
class SettingsRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Auto-capitalize the major words of field labels as new fields are created. */
    var autoCapitalizeLabels: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) { prefs.edit().putBoolean(KEY_LABELS, value).apply() }

    /** Auto-capitalize the major words of dropdown/multiple options as they're created. */
    var autoCapitalizeOptions: Boolean
        get() = prefs.getBoolean(KEY_OPTIONS, true)
        set(value) { prefs.edit().putBoolean(KEY_OPTIONS, value).apply() }

    companion object {
        private const val PREFS_NAME = "data_dragon_settings"
        private const val KEY_LABELS = "auto_capitalize_labels"
        private const val KEY_OPTIONS = "auto_capitalize_options"
    }
}
