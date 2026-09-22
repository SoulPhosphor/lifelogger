package com.datadragon.app.data

import android.content.Context
import android.content.SharedPreferences

/** Why the most recent automatic-backup attempt on this device failed. */
enum class AutoBackupError(val key: String, val needsUserAction: Boolean) {
    /** Android no longer grants access to the selected folder. */
    PERMISSION_LOST("permission_lost", true),

    /** The selected folder no longer exists or cannot be reached. */
    FOLDER_MISSING("folder_missing", true),

    /** The backup file could not be created, written, or closed. */
    WRITE_FAILED("write_failed", false),

    /** The saved backup did not reopen and validate. */
    VERIFY_FAILED("verify_failed", false);

    companion object {
        fun fromKey(key: String?): AutoBackupError? = entries.firstOrNull { it.key == key }
    }
}

/** Device-local automatic-backup state as the Settings screen and coordinator read it. */
data class AutoBackupLocalState(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderLabel: String? = null,
    val lastSuccessAt: Long? = null,
    val lastSuccessDestination: String? = null,
    val protectedDataRevision: Long = 0,
    val protectedPreferencesRevision: Long = 0,
    val error: AutoBackupError? = null,
    val accessNoticePending: Boolean = false,
    val accessNoticeIssued: Boolean = false,
    val lastAttemptAt: Long? = null,
    val scheduledAt: Long? = null,
)

/**
 * Device-local automatic-backup state. This lives in its own preferences file,
 * separate from the portable settings allowlist, so none of it can be exported
 * or restored: the folder, its permission, the enabled flag, success history,
 * errors, protected revisions, and scheduling state all stay on this device.
 * Every write is committed synchronously so a process restart keeps it.
 */
class AutoBackupStateStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun read(): AutoBackupLocalState = AutoBackupLocalState(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        folderUri = prefs.getString(KEY_FOLDER_URI, null),
        folderLabel = prefs.getString(KEY_FOLDER_LABEL, null),
        lastSuccessAt = prefs.getLongOrNull(KEY_LAST_SUCCESS_AT),
        lastSuccessDestination = prefs.getString(KEY_LAST_SUCCESS_DESTINATION, null),
        protectedDataRevision = prefs.getLong(KEY_PROTECTED_DATA_REVISION, 0L),
        protectedPreferencesRevision = prefs.getLong(KEY_PROTECTED_PREFERENCES_REVISION, 0L),
        error = AutoBackupError.fromKey(prefs.getString(KEY_ERROR, null)),
        accessNoticePending = prefs.getBoolean(KEY_ACCESS_NOTICE_PENDING, false),
        accessNoticeIssued = prefs.getBoolean(KEY_ACCESS_NOTICE_ISSUED, false),
        lastAttemptAt = prefs.getLongOrNull(KEY_LAST_ATTEMPT_AT),
        scheduledAt = prefs.getLongOrNull(KEY_SCHEDULED_AT),
    )

    fun setEnabled(enabled: Boolean) = commit { putBoolean(KEY_ENABLED, enabled) }

    /** Makes a verified folder the destination and clears errors that belonged to the old one. */
    fun setDestination(uri: String, label: String) = commit {
        putString(KEY_FOLDER_URI, uri)
        putString(KEY_FOLDER_LABEL, label)
        clearErrorFields()
    }

    fun recordSuccess(at: Long, destination: String, dataRevision: Long, preferencesRevision: Long) = commit {
        putLong(KEY_LAST_SUCCESS_AT, at)
        putString(KEY_LAST_SUCCESS_DESTINATION, destination)
        putLong(KEY_PROTECTED_DATA_REVISION, dataRevision)
        putLong(KEY_PROTECTED_PREFERENCES_REVISION, preferencesRevision)
        putLong(KEY_LAST_ATTEMPT_AT, at)
        clearErrorFields()
    }

    /**
     * Records a failed attempt. A folder-access failure raises the one-time
     * notice when this failure is new; a repeat of the same unresolved failure
     * does not raise it again.
     */
    fun recordFailure(at: Long, error: AutoBackupError, raiseNotice: Boolean = true) {
        val current = read()
        val newAccessFailure = error.needsUserAction && (current.error != error || !current.accessNoticeIssued)
        commit {
            putLong(KEY_LAST_ATTEMPT_AT, at)
            putString(KEY_ERROR, error.key)
            if (error.needsUserAction) {
                if (!raiseNotice) {
                    // Recorded for display only; a later blocked backup raises the notice.
                    if (current.error != error) putBoolean(KEY_ACCESS_NOTICE_ISSUED, false)
                } else if (newAccessFailure) {
                    putBoolean(KEY_ACCESS_NOTICE_PENDING, true)
                    putBoolean(KEY_ACCESS_NOTICE_ISSUED, true)
                }
            } else {
                putBoolean(KEY_ACCESS_NOTICE_PENDING, false)
                putBoolean(KEY_ACCESS_NOTICE_ISSUED, false)
            }
        }
    }

    /** Folder access works again, so the access error no longer applies. */
    fun clearAccessError() = commit { clearErrorFields() }

    /** The access-failure dialog has been shown and answered. */
    fun consumeAccessNotice() = commit { putBoolean(KEY_ACCESS_NOTICE_PENDING, false) }

    fun setScheduledAt(at: Long?) = commit {
        if (at == null) remove(KEY_SCHEDULED_AT) else putLong(KEY_SCHEDULED_AT, at)
    }

    private fun SharedPreferences.Editor.clearErrorFields() {
        remove(KEY_ERROR)
        putBoolean(KEY_ACCESS_NOTICE_PENDING, false)
        putBoolean(KEY_ACCESS_NOTICE_ISSUED, false)
    }

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        val committed = prefs.edit().apply(block).commit()
        check(committed) { "Automatic backup state could not be saved." }
    }

    private fun SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    companion object {
        const val PREFS_NAME = "data_dragon_automatic_backup_local"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_FOLDER_LABEL = "folder_label"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_LAST_SUCCESS_DESTINATION = "last_success_destination"
        private const val KEY_PROTECTED_DATA_REVISION = "protected_data_revision"
        private const val KEY_PROTECTED_PREFERENCES_REVISION = "protected_preferences_revision"
        private const val KEY_ERROR = "error"
        private const val KEY_ACCESS_NOTICE_PENDING = "access_notice_pending"
        private const val KEY_ACCESS_NOTICE_ISSUED = "access_notice_issued"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}
