package com.datadragon.app.data

import android.content.Context
import android.content.SharedPreferences

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

    /** Which Home view (Forms, Lists or Ideas) was open last, so it reopens there. */
    var lastView: HomeView
        get() = HomeView.fromKey(prefs.getString(KEY_LAST_VIEW, null))
        set(value) { prefs.edit().putString(KEY_LAST_VIEW, value.key).apply() }

    // --- Navigation menu preferences (all global) ----------------------------

    /** How the Home bar presents the data modes: a row of icons, or a dropdown. */
    var navStyle: NavStyle
        get() = NavStyle.fromKey(prefs.getString(KEY_NAV_STYLE, null))
        set(value) { prefs.edit().putString(KEY_NAV_STYLE, value.key).apply() }

    /** In dropdown mode, show the current mode's label instead of its single icon. */
    var useModeLabelInDropdown: Boolean
        get() = prefs.getBoolean(KEY_NAV_USE_LABEL, true)
        set(value) { prefs.edit().putBoolean(KEY_NAV_USE_LABEL, value).apply() }

    /**
     * Whether a data mode is shown in the navigation. Hiding a mode only affects
     * the menu — it never removes or alters that mode's data, which stays in Room
     * and reappears the moment the mode is shown again.
     */
    fun isModeEnabled(view: HomeView): Boolean =
        prefs.getBoolean(modeEnabledKey(view), true)

    fun setModeEnabled(view: HomeView, enabled: Boolean) {
        prefs.edit().putBoolean(modeEnabledKey(view), enabled).apply()
    }

    /** The data modes currently shown in the navigation, in their on-screen order. */
    val enabledModes: List<HomeView>
        get() = HomeView.entries.filter { isModeEnabled(it) }

    private fun modeEnabledKey(view: HomeView): String = "$KEY_MODE_ENABLED_PREFIX${view.key}"

    /** Keep Home's bar in sync with changes made on the Settings screen. */
    fun registerOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

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

    // --- Daily List preferences (all global; per-day data stays in Room) -----

    /** The heading shown atop Daily List editors when nothing custom is set. */
    var dailyListHeading: String
        get() = prefs.getString(KEY_DL_HEADING, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_DL_HEADING, value).apply() }

    /** Automatically renew unfinished items from the last previous card. */
    var dailyListAutoRenew: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_RENEW, false)
        set(value) { prefs.edit().putBoolean(KEY_DL_AUTO_RENEW, value).apply() }

    /** Show completed items on Daily List main-view cards. */
    var dailyListShowCompleted: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_COMPLETED, true)
        set(value) { prefs.edit().putBoolean(KEY_DL_SHOW_COMPLETED, value).apply() }

    /** Show today's unfinished items on its card. */
    var dailyListShowCurrentUnfinished: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_CURRENT_UNFINISHED, true)
        set(value) { prefs.edit().putBoolean(KEY_DL_SHOW_CURRENT_UNFINISHED, value).apply() }

    /** Show past days' unfinished items on their cards. */
    var dailyListShowPastUnfinished: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_PAST_UNFINISHED, false)
        set(value) { prefs.edit().putBoolean(KEY_DL_SHOW_PAST_UNFINISHED, value).apply() }

    /** Permanently trash unfinished items from past days during maintenance. */
    var dailyListAutoTrashPast: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_TRASH_PAST, false)
        set(value) { prefs.edit().putBoolean(KEY_DL_AUTO_TRASH_PAST, value).apply() }

    /**
     * How many of the most recent past Daily List cards keep their unfinished
     * items during cleanup. The visible dropdown offers 2, 3, 7, and 14; this
     * is a card count (not calendar days), so gaps between dates never matter.
     */
    var dailyListAutoTrashKeepPast: Int
        get() = prefs.getInt(KEY_DL_AUTO_TRASH_KEEP, 7)
        set(value) { prefs.edit().putInt(KEY_DL_AUTO_TRASH_KEEP, value).apply() }

    /** Reopen straight into today's Daily List on app start (when it was the last mode). */
    var dailyListAutoReopen: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_REOPEN, false)
        set(value) { prefs.edit().putBoolean(KEY_DL_AUTO_REOPEN, value).apply() }

    /** Offer the optional one-line per-day title in Daily List editors. */
    var dailyListAllowTitle: Boolean
        get() = prefs.getBoolean(KEY_DL_ALLOW_TITLE, false)
        set(value) { prefs.edit().putBoolean(KEY_DL_ALLOW_TITLE, value).apply() }

    /** Mark all-completed days with the chosen celebration icon on the Daily List main view. */
    var dailyListCelebrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DL_CELEBRATION_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_DL_CELEBRATION_ENABLED, value).apply() }

    /** Which celebration icon all-completed cards show. */
    var dailyListCelebrationIcon: CelebrationIcon
        get() = CelebrationIcon.fromKey(prefs.getString(KEY_DL_CELEBRATION_ICON, null))
        set(value) { prefs.edit().putString(KEY_DL_CELEBRATION_ICON, value.key).apply() }

    /** Never auto-delete a favorited card through whole-card retention. */
    var dailyListProtectFavorited: Boolean
        get() = prefs.getBoolean(KEY_DL_PROTECT_FAVORITED, true)
        set(value) { prefs.edit().putBoolean(KEY_DL_PROTECT_FAVORITED, value).apply() }

    /**
     * How many newest cards (by date) whole-card retention keeps, or null when
     * the numeric field is blank (disabled). 1 through 999.
     */
    var dailyListRetentionDays: Int?
        get() = prefs.getString(KEY_DL_RETENTION, null)
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?.takeIf { it in 1..999 }
        set(value) {
            val text = value?.toString().orEmpty()
            prefs.edit().putString(KEY_DL_RETENTION, text).apply()
        }

    /** The raw retention text as typed ("" = blank = disabled), for the field itself. */
    var dailyListRetentionRaw: String
        get() = prefs.getString(KEY_DL_RETENTION, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DL_RETENTION, value.filter { it.isDigit() }.take(3)).apply()
        }

    companion object {
        private const val PREFS_NAME = "data_dragon_settings"
        private const val KEY_LABELS = "auto_capitalize_labels"
        private const val KEY_OPTIONS = "auto_capitalize_options"
        private const val KEY_LAST_VIEW = "last_home_view"
        private const val KEY_NAV_STYLE = "nav_style"
        private const val KEY_NAV_USE_LABEL = "nav_use_mode_label"
        private const val KEY_MODE_ENABLED_PREFIX = "mode_enabled_"
        private const val KEY_COMPLETE_ICON = "list_complete_icon"
        private const val KEY_CROSS_OUT = "list_cross_out_completed"
        private const val KEY_MOVE_BOTTOM = "list_move_completed_bottom"
    private const val KEY_DL_HEADING = "daily_list_heading"
    private const val KEY_DL_AUTO_RENEW = "daily_list_auto_renew"
    private const val KEY_DL_SHOW_COMPLETED = "daily_list_show_completed"
    private const val KEY_DL_SHOW_CURRENT_UNFINISHED = "daily_list_show_current_unfinished"
    private const val KEY_DL_SHOW_PAST_UNFINISHED = "daily_list_show_past_unfinished"
    private const val KEY_DL_AUTO_TRASH_PAST = "daily_list_auto_trash_past"
    private const val KEY_DL_AUTO_TRASH_KEEP = "daily_list_auto_trash_keep"
    private const val KEY_DL_AUTO_REOPEN = "daily_list_auto_reopen"
    private const val KEY_DL_ALLOW_TITLE = "daily_list_allow_title"
    private const val KEY_DL_CELEBRATION_ENABLED = "daily_list_celebration_enabled"
    private const val KEY_DL_CELEBRATION_ICON = "daily_list_celebration_icon"
    private const val KEY_DL_PROTECT_FAVORITED = "daily_list_protect_favorited"
    private const val KEY_DL_RETENTION = "daily_list_retention"
    }
}

/** The Home views the top-bar icons switch between, in their on-screen order. */
enum class HomeView(val key: String) {
    FORMS("forms"),
    LISTS("lists"),
    IDEAS("ideas"),
    DAILY_LIST("daily_list"),
    CLICKER("clicker");

    companion object {
        fun fromKey(key: String?): HomeView = entries.firstOrNull { it.key == key } ?: FORMS
    }
}

/** How the Home navigation presents the data modes. */
enum class NavStyle(val key: String) {
    ICONS("icons"),
    DROPDOWN("dropdown");

    companion object {
        fun fromKey(key: String?): NavStyle = entries.firstOrNull { it.key == key } ?: ICONS
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

/**
 * The icon a fully-completed Daily List card may show. The visible choices
 * carry their exact Google Material Symbols names; four of the six are newer
 * Material Symbols bundled locally as vector drawables, so nothing is
 * hotlinked or loaded from the network at runtime.
 */
enum class CelebrationIcon(val key: String, val label: String) {
    FIRE_CHECK("fire_check", "Fire Check"),
    CHECK_CIRCLE("check_circle", "Check Circle"),
    CELEBRATION("celebration", "Celebration"),
    CHEER("cheer", "Cheer"),
    AWARD_STAR("award_star", "Award Star"),
    FAMILY_STAR("family_star", "Family Star");

    companion object {
        fun fromKey(key: String?): CelebrationIcon =
            entries.firstOrNull { it.key == key } ?: CHECK_CIRCLE
    }
}
