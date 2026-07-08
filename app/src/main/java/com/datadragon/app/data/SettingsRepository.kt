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

    /** Which Home view (Forms or Lists) was open last, so it reopens there. */
    var lastView: HomeView
        get() = HomeView.fromKey(prefs.getString(KEY_LAST_VIEW, null))
        set(value) { prefs.edit().putString(KEY_LAST_VIEW, value.key).apply() }

    /** What a checked-off list item shows: a checkmark or a checked box. Global. */
    var completeIcon: CompleteIcon
        get() = CompleteIcon.fromKey(prefs.getString(KEY_COMPLETE_ICON, null))
        set(value) { prefs.edit().putString(KEY_COMPLETE_ICON, value.key).apply() }

    /** Strike through a list item's text once it's completed. Global. */
    var crossOutWhenCompleted: Boolean
        get() = prefs.getBoolean(KEY_CROSS_OUT, false)
        set(value) { prefs.edit().putBoolean(KEY_CROSS_OUT, value).apply() }

    /** Move a list item to the bottom of its list when it's marked complete. Global. */
    var moveCompletedToBottom: Boolean
        get() = prefs.getBoolean(KEY_MOVE_BOTTOM, false)
        set(value) { prefs.edit().putBoolean(KEY_MOVE_BOTTOM, value).apply() }

    /**
     * The document URI last written by an export or backup, remembered so the next
     * "Save to…" sheet opens in the same folder. Shared across every export and the
     * backup file; null until the first save (then the picker defaults to Downloads).
     */
    var lastExportDir: String?
        get() = prefs.getString(KEY_LAST_EXPORT_DIR, null)
        set(value) { prefs.edit().putString(KEY_LAST_EXPORT_DIR, value).apply() }

    companion object {
        private const val PREFS_NAME = "data_dragon_settings"
        private const val KEY_LABELS = "auto_capitalize_labels"
        private const val KEY_OPTIONS = "auto_capitalize_options"
        private const val KEY_LAST_VIEW = "last_home_view"
        private const val KEY_COMPLETE_ICON = "list_complete_icon"
        private const val KEY_CROSS_OUT = "list_cross_out_completed"
        private const val KEY_MOVE_BOTTOM = "list_move_completed_bottom"
        private const val KEY_LAST_EXPORT_DIR = "last_export_dir"
    }
}

/** The two Home views the top-bar icons switch between. */
enum class HomeView(val key: String) {
    FORMS("forms"),
    LISTS("lists");

    companion object {
        fun fromKey(key: String?): HomeView = entries.firstOrNull { it.key == key } ?: FORMS
    }
}

/** What mark a completed list item shows. */
enum class CompleteIcon(val key: String) {
    CHECKMARK("checkmark"),
    CHECKED_BOX("checked_box");

    companion object {
        fun fromKey(key: String?): CompleteIcon = entries.firstOrNull { it.key == key } ?: CHECKED_BOX
    }
}
