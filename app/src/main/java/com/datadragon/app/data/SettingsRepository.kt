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

    /**
     * Device-local operational counter for automatic backup. Every write that
     * changes a portable preference increases it in the same editor commit as
     * the change itself. It is not a portable preference and is never exported.
     */
    val portablePreferencesRevision: Long
        get() = prefs.getLong(KEY_PORTABLE_PREFERENCES_REVISION, 0L)

    /**
     * Apply a portable-preference change and its revision increase together.
     * Writes that leave the effective value unchanged do not count as changes.
     */
    private inline fun editPortable(changed: () -> Boolean, block: SharedPreferences.Editor.() -> Unit) {
        synchronized(PORTABLE_LOCK) {
            if (!changed()) return
            prefs.edit()
                .apply(block)
                .putLong(KEY_PORTABLE_PREFERENCES_REVISION, portablePreferencesRevision + 1)
                .apply()
        }
    }

    /** Auto-capitalize the major words of field labels as new fields are created. */
    var autoCapitalizeLabels: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) { editPortable({ autoCapitalizeLabels != value }) { putBoolean(KEY_LABELS, value) } }

    /** Auto-capitalize the major words of dropdown/multiple options as they're created. */
    var autoCapitalizeOptions: Boolean
        get() = prefs.getBoolean(KEY_OPTIONS, true)
        set(value) { editPortable({ autoCapitalizeOptions != value }) { putBoolean(KEY_OPTIONS, value) } }

    /** Which Home view (Forms, Lists or Ideas) was open last, so it reopens there. */
    var lastView: HomeView
        get() = HomeView.fromKey(prefs.getString(KEY_LAST_VIEW, null))
        set(value) { editPortable({ lastView != value }) { putString(KEY_LAST_VIEW, value.key) } }

    // --- Navigation menu preferences (all global) ----------------------------

    /** How the Home bar presents the data modes: a row of icons, or a dropdown. */
    var navStyle: NavStyle
        get() = NavStyle.fromKey(prefs.getString(KEY_NAV_STYLE, null))
        set(value) { editPortable({ navStyle != value }) { putString(KEY_NAV_STYLE, value.key) } }

    /** In dropdown mode, show the current mode's label instead of its single icon. */
    var useModeLabelInDropdown: Boolean
        get() = prefs.getBoolean(KEY_NAV_USE_LABEL, true)
        set(value) { editPortable({ useModeLabelInDropdown != value }) { putBoolean(KEY_NAV_USE_LABEL, value) } }

    /**
     * Whether a data mode is shown in the navigation. Hiding a mode only affects
     * the menu — it never removes or alters that mode's data, which stays in Room
     * and reappears the moment the mode is shown again.
     */
    fun isModeEnabled(view: HomeView): Boolean =
        prefs.getBoolean(modeEnabledKey(view), true)

    fun setModeEnabled(view: HomeView, enabled: Boolean) {
        editPortable({ isModeEnabled(view) != enabled }) { putBoolean(modeEnabledKey(view), enabled) }
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
        set(value) { editPortable({ completeIcon != value }) { putString(KEY_COMPLETE_ICON, value.key) } }

    /** Strike through a list item's text once it's completed. Global. */
    var crossOutWhenCompleted: Boolean
        get() = prefs.getBoolean(KEY_CROSS_OUT, false)
        set(value) { editPortable({ crossOutWhenCompleted != value }) { putBoolean(KEY_CROSS_OUT, value) } }

    /** Move a list item to the bottom of its list when it's marked complete. Global. */
    var moveCompletedToBottom: Boolean
        get() = prefs.getBoolean(KEY_MOVE_BOTTOM, false)
        set(value) { editPortable({ moveCompletedToBottom != value }) { putBoolean(KEY_MOVE_BOTTOM, value) } }

    /** Last Merge conflict policy. Device-local restore workflow state, never portable. */
    var restoreConflictPolicy: RestoreConflictPolicy
        get() = RestoreConflictPolicy.fromKey(prefs.getString(KEY_RESTORE_CONFLICT_POLICY, null))
        set(value) { prefs.edit().putString(KEY_RESTORE_CONFLICT_POLICY, value.key).apply() }

    // --- Daily List preferences (all global; per-day data stays in Room) -----

    /** The heading shown atop Daily List editors when nothing custom is set. */
    var dailyListHeading: String
        get() = prefs.getString(KEY_DL_HEADING, "").orEmpty()
        set(value) { editPortable({ dailyListHeading != value }) { putString(KEY_DL_HEADING, value) } }

    /** Automatically renew unfinished items from the last previous card. */
    var dailyListAutoRenew: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_RENEW, false)
        set(value) { editPortable({ dailyListAutoRenew != value }) { putBoolean(KEY_DL_AUTO_RENEW, value) } }

    /** Show completed items on Daily List main-view cards. */
    var dailyListShowCompleted: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_COMPLETED, true)
        set(value) { editPortable({ dailyListShowCompleted != value }) { putBoolean(KEY_DL_SHOW_COMPLETED, value) } }

    /** Show today's unfinished items on its card. */
    var dailyListShowCurrentUnfinished: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_CURRENT_UNFINISHED, true)
        set(value) { editPortable({ dailyListShowCurrentUnfinished != value }) { putBoolean(KEY_DL_SHOW_CURRENT_UNFINISHED, value) } }

    /** Show past days' unfinished items on their cards. */
    var dailyListShowPastUnfinished: Boolean
        get() = prefs.getBoolean(KEY_DL_SHOW_PAST_UNFINISHED, false)
        set(value) { editPortable({ dailyListShowPastUnfinished != value }) { putBoolean(KEY_DL_SHOW_PAST_UNFINISHED, value) } }

    /** Permanently trash unfinished items from past days during maintenance. */
    var dailyListAutoTrashPast: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_TRASH_PAST, false)
        set(value) { editPortable({ dailyListAutoTrashPast != value }) { putBoolean(KEY_DL_AUTO_TRASH_PAST, value) } }

    /**
     * How many of the most recent past Daily List cards keep their unfinished
     * items during cleanup. The visible dropdown offers 2, 3, 7, and 14; this
     * is a card count (not calendar days), so gaps between dates never matter.
     */
    var dailyListAutoTrashKeepPast: Int
        get() = prefs.getInt(KEY_DL_AUTO_TRASH_KEEP, 7)
        set(value) { editPortable({ dailyListAutoTrashKeepPast != value }) { putInt(KEY_DL_AUTO_TRASH_KEEP, value) } }

    /** Reopen straight into today's Daily List on app start (when it was the last mode). */
    var dailyListAutoReopen: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_REOPEN, false)
        set(value) { editPortable({ dailyListAutoReopen != value }) { putBoolean(KEY_DL_AUTO_REOPEN, value) } }

    /** Offer the optional one-line per-day title in Daily List editors. */
    var dailyListAllowTitle: Boolean
        get() = prefs.getBoolean(KEY_DL_ALLOW_TITLE, false)
        set(value) { editPortable({ dailyListAllowTitle != value }) { putBoolean(KEY_DL_ALLOW_TITLE, value) } }

    /** Mark all-completed days with the chosen celebration icon on the Daily List main view. */
    var dailyListCelebrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DL_CELEBRATION_ENABLED, true)
        set(value) { editPortable({ dailyListCelebrationEnabled != value }) { putBoolean(KEY_DL_CELEBRATION_ENABLED, value) } }

    /** Which celebration icon all-completed cards show. */
    var dailyListCelebrationIcon: CelebrationIcon
        get() = CelebrationIcon.fromKey(prefs.getString(KEY_DL_CELEBRATION_ICON, null))
        set(value) { editPortable({ dailyListCelebrationIcon != value }) { putString(KEY_DL_CELEBRATION_ICON, value.key) } }

    /** Never auto-delete a favorited card through whole-card retention. */
    var dailyListProtectFavorited: Boolean
        get() = prefs.getBoolean(KEY_DL_PROTECT_FAVORITED, true)
        set(value) { editPortable({ dailyListProtectFavorited != value }) { putBoolean(KEY_DL_PROTECT_FAVORITED, value) } }

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
            editPortable({ dailyListRetentionRaw != text }) { putString(KEY_DL_RETENTION, text) }
        }

    /** The raw retention text as typed ("" = blank = disabled), for the field itself. */
    var dailyListRetentionRaw: String
        get() = prefs.getString(KEY_DL_RETENTION, "").orEmpty()
        set(value) {
            val text = value.filter { it.isDigit() }.take(3)
            editPortable({ dailyListRetentionRaw != text }) { putString(KEY_DL_RETENTION, text) }
        }

    // --- Automatic backup preferences (portable; folder and history are device-local) ---

    var automaticBackupCadence: AutoBackupCadence
        get() = AutoBackupCadence.fromKey(prefs.getString(KEY_AUTO_BACKUP_CADENCE, null))
        set(value) { editPortable({ automaticBackupCadence != value }) { putString(KEY_AUTO_BACKUP_CADENCE, value.key) } }

    /** Days between backups when the cadence is Custom, 1 through 365. */
    var automaticBackupCustomDays: Int
        get() = prefs.getInt(KEY_AUTO_BACKUP_CUSTOM_DAYS, AutoBackupPolicy.DEFAULT_CUSTOM_DAYS)
            .takeIf { it in AutoBackupPolicy.CUSTOM_DAYS_RANGE } ?: AutoBackupPolicy.DEFAULT_CUSTOM_DAYS
        set(value) {
            require(value in AutoBackupPolicy.CUSTOM_DAYS_RANGE) { "Custom days must be 1 to 365." }
            editPortable({ automaticBackupCustomDays != value }) { putInt(KEY_AUTO_BACKUP_CUSTOM_DAYS, value) }
        }

    /** How many automatic backups rotation keeps: 3, 5, or 7. */
    var automaticBackupRetention: Int
        get() = AutoBackupPolicy.normalizeRetention(
            prefs.getInt(KEY_AUTO_BACKUP_RETENTION, AutoBackupPolicy.DEFAULT_RETENTION),
        )
        set(value) {
            require(value in AutoBackupPolicy.RETENTION_CHOICES) { "Retention must be 3, 5, or 7." }
            editPortable({ automaticBackupRetention != value }) { putInt(KEY_AUTO_BACKUP_RETENTION, value) }
        }

    val automaticBackupIntervalMillis: Long
        get() = AutoBackupPolicy.intervalMillis(automaticBackupCadence, automaticBackupCustomDays)

    /**
     * The portable preferences and the revision they represent. The revision is
     * read first, so a change racing this read can only make the captured
     * revision older than the values, which leaves the backup dirty (never the
     * reverse).
     */
    fun portableBackupSnapshotWithRevision(): Pair<BackupPortablePreferences, Long> =
        synchronized(PORTABLE_LOCK) {
            val revision = portablePreferencesRevision
            portableBackupSnapshot() to revision
        }

    /** The explicit portable allowlist. Device-local execution state is never read here. */
    fun portableBackupSnapshot(): BackupPortablePreferences = BackupPortablePreferences(
        autoCapitalizeLabels = autoCapitalizeLabels,
        autoCapitalizeOptions = autoCapitalizeOptions,
        lastHomeView = lastView.key,
        navStyle = navStyle.key,
        navUseModeLabel = useModeLabelInDropdown,
        modeEnabledForms = isModeEnabled(HomeView.FORMS),
        modeEnabledLists = isModeEnabled(HomeView.LISTS),
        modeEnabledIdeas = isModeEnabled(HomeView.IDEAS),
        modeEnabledDailyList = isModeEnabled(HomeView.DAILY_LIST),
        modeEnabledClicker = isModeEnabled(HomeView.CLICKER),
        listCompleteIcon = completeIcon.key,
        listCrossOutCompleted = crossOutWhenCompleted,
        listMoveCompletedBottom = moveCompletedToBottom,
        dailyListHeading = dailyListHeading,
        dailyListAutoRenew = dailyListAutoRenew,
        dailyListShowCompleted = dailyListShowCompleted,
        dailyListShowCurrentUnfinished = dailyListShowCurrentUnfinished,
        dailyListShowPastUnfinished = dailyListShowPastUnfinished,
        dailyListAutoTrashPast = dailyListAutoTrashPast,
        dailyListAutoTrashKeep = dailyListAutoTrashKeepPast,
        dailyListAutoReopen = dailyListAutoReopen,
        dailyListAllowTitle = dailyListAllowTitle,
        dailyListCelebrationEnabled = dailyListCelebrationEnabled,
        dailyListCelebrationIcon = dailyListCelebrationIcon.key,
        dailyListProtectFavorited = dailyListProtectFavorited,
        dailyListRetention = dailyListRetentionRaw,
        automaticBackupCadence = automaticBackupCadence.key,
        automaticBackupCustomDays = automaticBackupCustomDays,
        automaticBackupRetention = automaticBackupRetention,
    )

    /** Apply the complete portable allowlist synchronously so restore can detect write failure. */
    fun applyPortableBackup(preferences: BackupPortablePreferences) = synchronized(PORTABLE_LOCK) {
        val editor = prefs.edit()
            .putBoolean(KEY_LABELS, preferences.autoCapitalizeLabels)
            .putBoolean(KEY_OPTIONS, preferences.autoCapitalizeOptions)
            .putString(KEY_LAST_VIEW, preferences.lastHomeView)
            .putString(KEY_NAV_STYLE, preferences.navStyle)
            .putBoolean(KEY_NAV_USE_LABEL, preferences.navUseModeLabel)
            .putBoolean(modeEnabledKey(HomeView.FORMS), preferences.modeEnabledForms)
            .putBoolean(modeEnabledKey(HomeView.LISTS), preferences.modeEnabledLists)
            .putBoolean(modeEnabledKey(HomeView.IDEAS), preferences.modeEnabledIdeas)
            .putBoolean(modeEnabledKey(HomeView.DAILY_LIST), preferences.modeEnabledDailyList)
            .putBoolean(modeEnabledKey(HomeView.CLICKER), preferences.modeEnabledClicker)
            .putString(KEY_COMPLETE_ICON, preferences.listCompleteIcon)
            .putBoolean(KEY_CROSS_OUT, preferences.listCrossOutCompleted)
            .putBoolean(KEY_MOVE_BOTTOM, preferences.listMoveCompletedBottom)
            .putString(KEY_DL_HEADING, preferences.dailyListHeading)
            .putBoolean(KEY_DL_AUTO_RENEW, preferences.dailyListAutoRenew)
            .putBoolean(KEY_DL_SHOW_COMPLETED, preferences.dailyListShowCompleted)
            .putBoolean(KEY_DL_SHOW_CURRENT_UNFINISHED, preferences.dailyListShowCurrentUnfinished)
            .putBoolean(KEY_DL_SHOW_PAST_UNFINISHED, preferences.dailyListShowPastUnfinished)
            .putBoolean(KEY_DL_AUTO_TRASH_PAST, preferences.dailyListAutoTrashPast)
            .putInt(KEY_DL_AUTO_TRASH_KEEP, preferences.dailyListAutoTrashKeep)
            .putBoolean(KEY_DL_AUTO_REOPEN, preferences.dailyListAutoReopen)
            .putBoolean(KEY_DL_ALLOW_TITLE, preferences.dailyListAllowTitle)
            .putBoolean(KEY_DL_CELEBRATION_ENABLED, preferences.dailyListCelebrationEnabled)
            .putString(KEY_DL_CELEBRATION_ICON, preferences.dailyListCelebrationIcon)
            .putBoolean(KEY_DL_PROTECT_FAVORITED, preferences.dailyListProtectFavorited)
            .putString(KEY_DL_RETENTION, preferences.dailyListRetention)
        // A version-3 backup never carried these, so the current values stay.
        preferences.automaticBackupCadence?.let { editor.putString(KEY_AUTO_BACKUP_CADENCE, it) }
        preferences.automaticBackupCustomDays?.let { editor.putInt(KEY_AUTO_BACKUP_CUSTOM_DAYS, it) }
        preferences.automaticBackupRetention?.let { editor.putInt(KEY_AUTO_BACKUP_RETENTION, it) }
        editor.putLong(KEY_PORTABLE_PREFERENCES_REVISION, portablePreferencesRevision + 1)
        val committed = editor.commit()
        check(committed) { "Portable preferences could not be saved." }
    }

    companion object {
        private val PORTABLE_LOCK = Any()
        private const val PREFS_NAME = "data_dragon_settings"
        private const val KEY_PORTABLE_PREFERENCES_REVISION = "portable_preferences_revision"
        private const val KEY_AUTO_BACKUP_CADENCE = "automatic_backup_cadence"
        private const val KEY_AUTO_BACKUP_CUSTOM_DAYS = "automatic_backup_custom_days"
        private const val KEY_AUTO_BACKUP_RETENTION = "automatic_backup_retention"
        private const val KEY_LABELS = "auto_capitalize_labels"
        private const val KEY_OPTIONS = "auto_capitalize_options"
        private const val KEY_LAST_VIEW = "last_home_view"
        private const val KEY_NAV_STYLE = "nav_style"
        private const val KEY_NAV_USE_LABEL = "nav_use_mode_label"
        private const val KEY_MODE_ENABLED_PREFIX = "mode_enabled_"
        private const val KEY_COMPLETE_ICON = "list_complete_icon"
        private const val KEY_CROSS_OUT = "list_cross_out_completed"
        private const val KEY_MOVE_BOTTOM = "list_move_completed_bottom"
        private const val KEY_RESTORE_CONFLICT_POLICY = "restore_conflict_policy"
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
