package com.datadragon.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One unique, one-time WorkManager request. Re-scheduling replaces the pending
 * request, so automatic-backup jobs never accumulate, and a request that
 * already targets the same time is left alone.
 */
class WorkManagerAutoBackupScheduler(context: Context) : AutoBackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(atMillis: Long, nowMillis: Long) {
        val targetTag = "$TARGET_TAG_PREFIX$atMillis"
        val existing = runCatching { workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get() }
            .getOrDefault(emptyList())
        if (existing.any { it.state == WorkInfo.State.ENQUEUED && targetTag in it.tags }) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay((atMillis - nowMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .addTag(targetTag)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "data_dragon_automatic_backup"
        const val WORK_TAG = "data_dragon_automatic_backup"
        private const val TARGET_TAG_PREFIX = "automatic_backup_due_at:"
    }
}

/** Durable background entry point. It runs the same shared coordinator as the foreground check. */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = AutoBackupService.get(applicationContext)
        runCatching { service.coordinator.runIfNeeded() }
        service.refresh()
        // The coordinator schedules its own retry or next run, so WorkManager
        // never repeats this request on its own.
        return Result.success()
    }
}

/**
 * Process-wide automatic-backup entry points for the app: the foreground
 * check, background scheduling, and the Settings actions. Everything funnels
 * into the one [AutoBackupCoordinator].
 */
class AutoBackupService private constructor(context: Context) {
    private val app = context.applicationContext
    private val settings = SettingsRepository(app)
    private val stateStore = AutoBackupStateStore(app)
    private val repository = BackupRepository(
        db = AppDatabase.getInstance(app),
        portablePreferences = settings::portableBackupSnapshot,
        applyPortablePreferences = settings::applyPortableBackup,
        sourceAppVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "unknown",
    )

    val coordinator = AutoBackupCoordinator(
        state = stateStore,
        settings = object : AutoBackupSettingsSource {
            override val intervalMillis: Long get() = settings.automaticBackupIntervalMillis
            override val retention: Int get() = settings.automaticBackupRetention
            override val preferencesRevision: Long get() = settings.portablePreferencesRevision
        },
        snapshots = object : AutoBackupSnapshotSource {
            override suspend fun capture(): CapturedBackup =
                repository.buildFullCaptured(settings::portableBackupSnapshotWithRevision)

            override suspend fun currentDataRevision(): Long = repository.currentDataRevision()
        },
        folders = SafAutoBackupFolders(app),
        scheduler = WorkManagerAutoBackupScheduler(app),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(stateStore.read())

    /** Device-local status for Settings and the access-failure dialog. */
    val state: StateFlow<AutoBackupLocalState> = _state

    fun refresh() {
        _state.value = stateStore.read()
    }

    /** App came to the foreground: verify, back up if dirty and overdue, or repair scheduling. */
    fun onForeground() {
        scope.launch {
            runCatching { coordinator.runIfNeeded() }
            refresh()
        }
    }

    /** App left the foreground: make sure changes made in this session are scheduled for protection. */
    fun onBackground() {
        scope.launch {
            runCatching { coordinator.ensureScheduled() }
            refresh()
        }
    }

    suspend fun selectFolder(uri: String): AutoBackupFolderSelection {
        val result = runCatching { coordinator.selectFolder(uri) }
            .getOrDefault(AutoBackupFolderSelection.CANNOT_SAVE)
        refresh()
        return result
    }

    suspend fun setEnabled(enabled: Boolean) {
        runCatching { coordinator.setEnabled(enabled) }
        refresh()
    }

    /** Cadence or retention changed. */
    fun onSettingsChanged() {
        scope.launch {
            runCatching { coordinator.ensureScheduled() }
            refresh()
        }
    }

    fun consumeAccessNotice() {
        _state.value = _state.value.copy(accessNoticePending = false)
        scope.launch {
            runCatching { stateStore.consumeAccessNotice() }
            refresh()
        }
    }

    companion object {
        @Volatile
        private var instance: AutoBackupService? = null

        fun get(context: Context): AutoBackupService =
            instance ?: synchronized(this) {
                instance ?: AutoBackupService(context).also { instance = it }
            }
    }
}
