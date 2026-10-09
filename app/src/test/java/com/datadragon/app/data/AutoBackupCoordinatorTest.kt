package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 5: change-aware automatic backup through the shared snapshot, codec, and verified writer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupCoordinatorTest {

    private val day = 24L * 60L * 60L * 1000L
    private val start = 1_790_000_000_000L // 2026-09-21T14:13:20Z

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var store: AutoBackupStateStore
    private lateinit var folders: FakeFolders
    private lateinit var scheduler: FakeScheduler
    private var now = start

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("data_dragon_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(AutoBackupStateStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .build()
        db.openHelper.writableDatabase
        settings = SettingsRepository(context)
        store = AutoBackupStateStore(context)
        folders = FakeFolders()
        scheduler = FakeScheduler()
    }

    @After
    fun tearDown() = db.close()

    // --- Enable state and folder selection -----------------------------------

    @Test
    fun automaticBackupIsOffByDefaultAndCannotBeEnabledWithoutAVerifiedFolder() = runBlocking {
        assertFalse(store.read().enabled)
        assertNull(store.read().folderUri)
        assertTrue(runCatching { coordinator().setEnabled(true) }.isFailure)
        assertFalse(store.read().enabled)
    }

    @Test
    fun selectingAFolderVerifiesItBeforeSavingAndKeepsAReadableLabel() = runBlocking {
        val folder = folders.add("content://tree/A", label = "Documents/Data Dragon")
        assertEquals(AutoBackupFolderSelection.SELECTED, coordinator().selectFolder("content://tree/A"))

        assertEquals("content://tree/A", store.read().folderUri)
        assertEquals("Documents/Data Dragon", store.read().folderLabel)
        assertTrue(folder.probeCreated > 0)
        assertTrue("The folder check leaves nothing behind", folder.files.isEmpty())
        assertTrue("content://tree/A" in folders.permissions)
    }

    @Test
    fun folderReplacementVerifiesTheNewDestinationBeforeReleasingTheOldOne() = runBlocking {
        folders.add("content://tree/A")
        folders.add("content://tree/B")
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")
        folders.events.clear()
        folders.onRelease = { released ->
            if (released == "content://tree/A") {
                assertEquals("content://tree/B", store.read().folderUri)
            }
        }

        assertEquals(AutoBackupFolderSelection.SELECTED, coordinator.selectFolder("content://tree/B"))

        val acquire = folders.events.indexOf("acquire:content://tree/B")
        val probe = folders.events.indexOfFirst { it.startsWith("probe-delete:content://tree/B") }
        val release = folders.events.indexOf("release:content://tree/A")
        assertTrue(acquire in 0 until probe)
        assertTrue(probe < release)
        assertFalse("content://tree/A" in folders.permissions)
        assertTrue("content://tree/B" in folders.permissions)
    }

    @Test
    fun permissionFailureDoesNotReplaceAWorkingDestination() = runBlocking {
        folders.add("content://tree/A")
        folders.add("content://tree/B", grantsPermission = false)
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")

        assertEquals(AutoBackupFolderSelection.PERMISSION_DENIED, coordinator.selectFolder("content://tree/B"))

        assertEquals("content://tree/A", store.read().folderUri)
        assertTrue("content://tree/A" in folders.permissions)
        assertFalse("content://tree/A" in folders.released)
    }

    @Test
    fun probeFailureDoesNotReplaceAWorkingDestinationAndReleasesTheRejectedFolder() = runBlocking {
        folders.add("content://tree/A")
        folders.add("content://tree/B").failCreate = true
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")

        assertEquals(AutoBackupFolderSelection.CANNOT_SAVE, coordinator.selectFolder("content://tree/B"))

        assertEquals("content://tree/A", store.read().folderUri)
        assertTrue("content://tree/A" in folders.permissions)
        assertFalse("content://tree/B" in folders.permissions)
    }

    // --- Change-aware backup ---------------------------------------------------

    @Test
    fun dueAndDirtyCreatesAVerifiedBackupThroughTheSharedFormat() = runBlocking {
        val folder = enabledWith("content://tree/A")

        val file = folder.automaticFiles().single()
        assertEquals("datadragon_autobackup_2026-09-21.json", file)
        val decoded = BackupCodec.decode(folder.text(file))
        assertEquals(BackupFile.VERSION, decoded.version)
        assertEquals(BackupCategory.entries, decoded.includedCategories)
        BackupRestoreValidator.validate(decoded)
        assertEquals(start, store.read().lastSuccessAt)
        assertEquals("content://tree/A", store.read().lastSuccessDestination)
        assertNull(store.read().error)
    }

    @Test
    fun noBackupIsCreatedWhenNothingChanged() = runBlocking {
        val folder = enabledWith("content://tree/A")
        val before = folder.files.keys.toSet()

        now = start + 30 * day
        assertEquals(AutoBackupRunResult.CLEAN, coordinator().runIfNeeded())

        assertEquals(before, folder.files.keys.toSet())
        assertTrue(folder.deleted.isEmpty())
        assertNull("Clean state leaves no unchanged job pending", scheduler.scheduledAt)
    }

    @Test
    fun aChangedDatabaseBecomesDirtyAndIsBackedUpWhenDue() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Groceries")

        now = start + day
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator().runIfNeeded())
        assertEquals(2, folder.automaticFiles().size)
        assertTrue(folder.text(folder.newestAutomatic()).contains("Groceries"))
    }

    @Test
    fun aChangedPortablePreferenceBecomesDirtyAndIsBackedUpWhenDue() = runBlocking {
        val folder = enabledWith("content://tree/A")
        settings.navStyle = NavStyle.DROPDOWN

        now = start + day
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator().runIfNeeded())
        val latest = BackupCodec.decode(folder.text(folder.newestAutomatic()))
        assertEquals(NavStyle.DROPDOWN.key, latest.payload.portablePreferences!!.navStyle)
    }

    @Test
    fun dirtyButNotDueStateStaysScheduledForLater() = runBlocking {
        val folder = enabledWith("content://tree/A")
        settings.automaticBackupCadence = AutoBackupCadence.WEEKLY
        insertList("Later")

        now = start + 2 * day
        assertEquals(AutoBackupRunResult.NOT_DUE, coordinator().runIfNeeded())

        assertEquals(1, folder.automaticFiles().size)
        assertEquals(start + 7 * day, scheduler.scheduledAt)
        assertEquals(start + 7 * day, store.read().scheduledAt)
    }

    @Test
    fun customCadenceSchedulesTheNextBackupAtTheCustomInterval() = runBlocking {
        enabledWith("content://tree/A")
        settings.automaticBackupCadence = AutoBackupCadence.CUSTOM
        settings.automaticBackupCustomDays = 10
        insertList("Custom")

        now = start + day
        assertEquals(AutoBackupRunResult.NOT_DUE, coordinator().runIfNeeded())
        assertEquals(start + 10 * day, scheduler.scheduledAt)
    }

    @Test
    fun aChangeMadeDuringBackupRemainsDirtyAfterwards() = runBlocking {
        folders.add("content://tree/A").onWrite = {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO checklists (uuid, name, createdAt, draft) VALUES ('during-backup', 'During', 1, 0)"
            )
        }
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator.setEnabled(true))

        val local = store.read()
        val current = db.backupStateDao().dataRevision()!!
        assertTrue(current > local.protectedDataRevision)
        assertEquals(start + day, scheduler.scheduledAt)
        assertFalse(folders.folder("content://tree/A").text(folders.folder("content://tree/A").newestAutomatic()).contains("During"))
    }

    @Test
    fun aChangedDestinationRequiresAnInitialBackupEvenWhenNothingChanged() = runBlocking {
        enabledWith("content://tree/A")
        val b = folders.add("content://tree/B")

        now = start + 60_000
        assertEquals(AutoBackupFolderSelection.SELECTED, coordinator().selectFolder("content://tree/B"))

        assertEquals(1, b.automaticFiles().size)
        assertEquals("content://tree/B", store.read().lastSuccessDestination)
    }

    // --- Failure safety ---------------------------------------------------------

    @Test
    fun writeFailureDoesNotRecordSuccessDeletesThePartialFileAndSchedulesARetry() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folder.failWrite = true

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())

        assertEquals(start, store.read().lastSuccessAt)
        assertEquals(AutoBackupError.WRITE_FAILED, store.read().error)
        assertEquals(1, folder.automaticFiles().size)
        assertEquals(now + AutoBackupPolicy.RETRY_DELAY_MILLIS, scheduler.scheduledAt)
        assertFalse("A retried save failure never raises the dialog", store.read().accessNoticePending)
    }

    @Test
    fun reopenOrValidationFailureDoesNotRecordSuccessAndPreviousBackupsSurvive() = runBlocking {
        val folder = enabledWith("content://tree/A")
        val previous = folder.automaticFiles().single()
        val previousText = folder.text(previous)
        insertList("Changed")
        folder.corruptReads = true

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())

        assertEquals(start, store.read().lastSuccessAt)
        assertEquals(AutoBackupError.VERIFY_FAILED, store.read().error)
        assertEquals(listOf(previous), folder.automaticFiles())
        assertEquals(previousText, String(folder.files.getValue(previous)))
    }

    @Test
    fun aFailedAttemptCanBeRetriedSuccessfully() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folder.failWrite = true
        now = start + day
        coordinator().runIfNeeded()

        folder.failWrite = false
        now += AutoBackupPolicy.RETRY_DELAY_MILLIS
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator().runIfNeeded())
        assertNull(store.read().error)
        assertEquals(now, store.read().lastSuccessAt)
    }

    @Test
    fun lostPermissionIsReportedOnceForEachNewFailureAndKeepsAutomaticBackupOn() = runBlocking {
        enabledWith("content://tree/A")
        insertList("Changed")
        folders.permissions.remove("content://tree/A")

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())
        assertEquals(AutoBackupError.PERMISSION_LOST, store.read().error)
        assertTrue(store.read().accessNoticePending)
        assertTrue(store.read().enabled)

        store.consumeAccessNotice()
        now += day
        coordinator().runIfNeeded()
        assertFalse("The same unresolved failure is not announced again", store.read().accessNoticePending)

        folders.permissions.add("content://tree/A")
        now += day
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator().runIfNeeded())
        insertList("Again")
        folders.permissions.remove("content://tree/A")
        now += day
        coordinator().runIfNeeded()
        assertTrue("A new failure is announced", store.read().accessNoticePending)
    }

    @Test
    fun lostPermissionDoesNotScheduleATimedRetryAndKeepsTheFolderAndError() = runBlocking {
        enabledWith("content://tree/A")
        insertList("Changed")
        folders.permissions.remove("content://tree/A")

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())

        val local = store.read()
        assertTrue(local.enabled)
        assertEquals("content://tree/A", local.folderUri)
        assertEquals(AutoBackupError.PERMISSION_LOST, local.error)
        assertNull("No background retry for a folder-access problem", scheduler.scheduledAt)
        assertNull(local.scheduledAt)

        // Leaving the app does not re-arm a timed retry for the same problem.
        coordinator().ensureScheduled()
        assertNull(scheduler.scheduledAt)
        assertEquals(AutoBackupError.PERMISSION_LOST, store.read().error)

        // The next foreground check retries; still blocked, still no timer.
        now += 60 * 60 * 1000
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun foregroundCheckClearsARecoveredFolderErrorAndBacksUp() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folder.missing = true
        now = start + day
        coordinator().runIfNeeded()
        assertEquals(AutoBackupError.FOLDER_MISSING, store.read().error)
        assertNull(scheduler.scheduledAt)

        folder.missing = false
        now += 60 * 60 * 1000
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator().runIfNeeded())
        assertNull(store.read().error)
        assertFalse(store.read().accessNoticePending)
        assertEquals(now, store.read().lastSuccessAt)
        assertEquals(2, folder.automaticFiles().size)
    }

    @Test
    fun reselectingTheFolderRetriesAfterLostPermission() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folders.permissions.remove("content://tree/A")
        now = start + day
        coordinator().runIfNeeded()
        assertEquals(AutoBackupError.PERMISSION_LOST, store.read().error)

        now += 60_000
        assertEquals(AutoBackupFolderSelection.SELECTED, coordinator().selectFolder("content://tree/A"))

        assertNull(store.read().error)
        assertEquals(now, store.read().lastSuccessAt)
        assertEquals(2, folder.automaticFiles().size)
    }

    @Test
    fun accessLostDuringTheWriteIsReportedWithoutATimedRetry() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folder.onWrite = { folders.permissions.remove("content://tree/A") }
        folder.failWrite = true

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())

        assertEquals(AutoBackupError.PERMISSION_LOST, store.read().error)
        assertTrue(store.read().accessNoticePending)
        assertNull(scheduler.scheduledAt)
        assertEquals(start, store.read().lastSuccessAt)
    }

    @Test
    fun verificationFailureStillSchedulesTheTimedRetry() = runBlocking {
        val folder = enabledWith("content://tree/A")
        insertList("Changed")
        folder.corruptReads = true

        now = start + day
        assertEquals(AutoBackupRunResult.FAILED, coordinator().runIfNeeded())
        assertEquals(AutoBackupError.VERIFY_FAILED, store.read().error)
        assertEquals(now + AutoBackupPolicy.RETRY_DELAY_MILLIS, scheduler.scheduledAt)
    }

    @Test
    fun aFolderProblemWithNothingDirtyIsShownWithoutTheDialogOrARetry() = runBlocking {
        enabledWith("content://tree/A")
        folders.permissions.remove("content://tree/A")

        now = start + day
        assertEquals(AutoBackupRunResult.CLEAN, coordinator().runIfNeeded())
        assertEquals(AutoBackupError.PERMISSION_LOST, store.read().error)
        assertFalse(store.read().accessNoticePending)
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun missingFolderIsReportedAsItsOwnError() = runBlocking {
        enabledWith("content://tree/A")
        insertList("Changed")
        folders.folder("content://tree/A").missing = true

        now = start + day
        coordinator().runIfNeeded()
        assertEquals(AutoBackupError.FOLDER_MISSING, store.read().error)
        assertTrue(store.read().accessNoticePending)
    }

    @Test
    fun foregroundAndWorkerAttemptsCannotRunConcurrently() = runBlocking {
        folders.add("content://tree/A")
        val first = coordinator()
        first.selectFolder("content://tree/A")
        store.setEnabled(true)
        val folder = folders.folder("content://tree/A")
        val release = CountDownLatch(1)
        folder.blockWrites = release

        val worker = async(Dispatchers.IO) { coordinator().runIfNeeded() }
        assertTrue(folder.writeStarted.await(5, TimeUnit.SECONDS))
        val foreground = async(Dispatchers.IO) { coordinator().runIfNeeded() }
        Thread.sleep(200)
        assertEquals(1, folder.maxConcurrentWrites.get())
        release.countDown()

        val results = listOf(worker.await(), foreground.await())
        assertEquals(1, results.count { it == AutoBackupRunResult.BACKED_UP })
        assertEquals(1, results.count { it == AutoBackupRunResult.CLEAN })
        assertEquals(1, folder.maxConcurrentWrites.get())
        assertEquals(1, folder.automaticFiles().size)
    }

    @Test
    fun processRestartPreservesRevisionsAndScheduling() = runBlocking {
        enabledWith("content://tree/A")
        insertList("Before restart")
        now = start + 2 * 60 * 60 * 1000
        assertEquals(AutoBackupRunResult.NOT_DUE, coordinator().runIfNeeded())

        // A new process: new state store, settings, and coordinator over the same persisted data.
        val restartedStore = AutoBackupStateStore(context)
        val restarted = coordinator(stateStore = restartedStore, settingsRepository = SettingsRepository(context))
        assertEquals(start + day, restartedStore.read().scheduledAt)
        assertTrue(db.backupStateDao().dataRevision()!! > restartedStore.read().protectedDataRevision)

        now = start + day
        assertEquals(AutoBackupRunResult.BACKED_UP, restarted.runIfNeeded())
    }

    // --- Rotation ----------------------------------------------------------------

    @Test
    fun rotationDeletesOnlyAutomaticBackupsBeyondRetention() = runBlocking {
        val folder = folders.add("content://tree/A")
        val manual = "datadragon_backup_2026-09-01.json"
        val unrelated = listOf("notes.json", "datadragon_autobackup_notes.json", "photo.jpg")
        (listOf(manual) + unrelated).forEach { folder.files[it] = "user file".toByteArray() }
        listOf("2026-09-01", "2026-09-02", "2026-09-03").forEach {
            folder.files["datadragon_autobackup_$it.json"] = "older".toByteArray()
        }
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")

        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator.setEnabled(true))

        assertEquals(
            listOf(
                "datadragon_autobackup_2026-09-02.json",
                "datadragon_autobackup_2026-09-03.json",
                "datadragon_autobackup_2026-09-21.json",
            ),
            folder.automaticFiles(),
        )
        assertEquals(listOf("datadragon_autobackup_2026-09-01.json"), folder.deleted)
        assertTrue(folder.files.containsKey(manual))
        unrelated.forEach { assertTrue(folder.files.containsKey(it)) }
    }

    @Test
    fun retentionUsesTheSelectedCount() = runBlocking {
        settings.automaticBackupRetention = 5
        val folder = folders.add("content://tree/A")
        (1..6).forEach { folder.files["datadragon_autobackup_2026-09-0$it.json"] = "older".toByteArray() }
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")
        coordinator.setEnabled(true)
        assertEquals(5, folder.automaticFiles().size)
    }

    @Test
    fun unchangedStateDoesNotRotateFiles() = runBlocking {
        val folder = folders.add("content://tree/A")
        (1..5).forEach { folder.files["datadragon_autobackup_2026-09-0$it.json"] = "older".toByteArray() }
        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")
        coordinator.setEnabled(true)
        val afterFirst = folder.files.keys.toSet()
        val deletedAfterFirst = folder.deleted.toList()

        now = start + 10 * day
        assertEquals(AutoBackupRunResult.CLEAN, coordinator().runIfNeeded())
        assertEquals(afterFirst, folder.files.keys.toSet())
        assertEquals(deletedAfterFirst, folder.deleted)
    }

    @Test
    fun manualBackupsAreNeverTouchedAndDoNotAffectAutomaticHistory() = runBlocking {
        val folder = folders.add("content://tree/A")
        val manualName = "datadragon_backup_2026-09-21.json"
        val manual = BackupCodec.encode(BackupRepository(db).buildFull())
        BackupFileWriter().writeAndVerify(folder.destination(manualName), manual)
        assertNull(store.read().lastSuccessAt)

        val coordinator = coordinator()
        coordinator.selectFolder("content://tree/A")
        coordinator.setEnabled(true)
        repeat(8) { index ->
            insertList("List $index")
            now += day
            coordinator.runIfNeeded()
        }
        assertEquals(manual, folder.text(manualName))
        assertEquals(3, folder.automaticFiles().size)
    }

    // --- Device-local state -------------------------------------------------------

    @Test
    fun portableBackupExcludesAllDeviceLocalAutomaticBackupState() = runBlocking {
        enabledWith("content://tree/secret-folder")
        val encoded = BackupCodec.encode(BackupRepository(db, settings::portableBackupSnapshot).buildFull())

        listOf(
            "content://tree/secret-folder",
            "secret-folder",
            "folder_uri",
            "folder_label",
            "last_success",
            "protected_data_revision",
            "portable_preferences_revision",
            "dataRevision",
            "scheduled_at",
            "access_notice",
            WorkManagerAutoBackupScheduler.UNIQUE_WORK_NAME,
        ).forEach { token -> assertFalse("Portable backup contains $token", encoded.contains(token)) }
        assertTrue(encoded.contains("automaticBackupCadence"))
        assertTrue(encoded.contains("automaticBackupRetention"))
    }

    @Test
    fun restoringToAFreshDeviceKeepsAutomaticBackupOffWithoutFolderAccess() = runBlocking {
        val oldDevice = BackupPortablePreferences.defaults().copy(
            automaticBackupCadence = AutoBackupCadence.CUSTOM.key,
            automaticBackupCustomDays = 12,
            automaticBackupRetention = 7,
        )
        val backup = BackupCodec.decode(
            BackupCodec.encode(BackupRepository(db, { oldDevice }).buildFull()),
        )

        BackupRepository(db, settings::portableBackupSnapshot, "test", settings::applyPortableBackup)
            .restore(backup, RestoreMode.REPLACE)

        val local = store.read()
        assertFalse(local.enabled)
        assertNull(local.folderUri)
        assertNull(local.lastSuccessAt)
        assertTrue(folders.permissions.isEmpty())
        assertEquals(AutoBackupCadence.CUSTOM, settings.automaticBackupCadence)
        assertEquals(12, settings.automaticBackupCustomDays)
        assertEquals(7, settings.automaticBackupRetention)
        assertEquals(AutoBackupRunResult.DISABLED, coordinator().runIfNeeded())
    }

    @Test
    fun restoringOnTheSameDeviceKeepsItsFolderAndHistory() = runBlocking {
        enabledWith("content://tree/A")
        val before = store.read()
        val backup = BackupCodec.decode(BackupCodec.encode(BackupRepository(db, settings::portableBackupSnapshot).buildFull()))

        BackupRepository(db, settings::portableBackupSnapshot, "test", settings::applyPortableBackup)
            .restore(backup, RestoreMode.REPLACE)

        val after = store.read()
        assertEquals(before.folderUri, after.folderUri)
        assertEquals(before.lastSuccessAt, after.lastSuccessAt)
        assertTrue(after.enabled)
    }

    @Test
    fun aVersion3BackupLeavesTheCurrentAutomaticBackupPreferencesUnchanged() = runBlocking {
        settings.automaticBackupCadence = AutoBackupCadence.WEEKLY
        settings.automaticBackupRetention = 5
        val version3 = BackupCodec.decode(
            BackupCodec.encode(
                BackupFile.full(
                    exportedAt = "2026-09-22T00:00:00Z",
                    sourceAppVersion = "v3",
                    roomSchemaVersion = 19,
                    payload = BackupPayload(
                        forms = emptyList(),
                        lists = emptyList(),
                        ideaLogs = emptyList(),
                        dailyTasks = emptyList(),
                        clickerData = emptyList(),
                        savedColorPresets = emptyList(),
                        portablePreferences = BackupPortablePreferences(navStyle = NavStyle.DROPDOWN.key),
                    ),
                ),
            ),
        )
        assertEquals(BackupFile.FIRST_ENVELOPE_VERSION, version3.version)

        BackupRepository(db, settings::portableBackupSnapshot, "test", settings::applyPortableBackup)
            .restore(version3, RestoreMode.REPLACE)

        assertEquals(NavStyle.DROPDOWN, settings.navStyle)
        assertEquals(AutoBackupCadence.WEEKLY, settings.automaticBackupCadence)
        assertEquals(5, settings.automaticBackupRetention)
    }

    @Test
    fun disablingCancelsPendingWork() = runBlocking {
        enabledWith("content://tree/A")
        insertList("Pending")
        coordinator().ensureScheduled()
        assertEquals(start + day, scheduler.scheduledAt)

        coordinator().setEnabled(false)
        assertNull(scheduler.scheduledAt)
        assertFalse(store.read().enabled)
    }

    // --- Helpers ------------------------------------------------------------------

    private suspend fun enabledWith(uri: String): FakeFolder {
        val folder = folders.add(uri)
        val coordinator = coordinator()
        assertEquals(AutoBackupFolderSelection.SELECTED, coordinator.selectFolder(uri))
        assertEquals(AutoBackupRunResult.BACKED_UP, coordinator.setEnabled(true))
        return folder
    }

    private fun insertList(name: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO checklists (uuid, name, createdAt, draft) " +
                "VALUES ('${java.util.UUID.randomUUID()}', '$name', 1, 0)"
        )
    }

    private fun coordinator(
        stateStore: AutoBackupStateStore = store,
        settingsRepository: SettingsRepository = settings,
    ): AutoBackupCoordinator {
        val repository = BackupRepository(
            db,
            settingsRepository::portableBackupSnapshot,
            "phase-5-test",
            settingsRepository::applyPortableBackup,
        )
        return AutoBackupCoordinator(
            state = stateStore,
            settings = object : AutoBackupSettingsSource {
                override val intervalMillis: Long get() = settingsRepository.automaticBackupIntervalMillis
                override val retention: Int get() = settingsRepository.automaticBackupRetention
                override val preferencesRevision: Long get() = settingsRepository.portablePreferencesRevision
            },
            snapshots = object : AutoBackupSnapshotSource {
                override suspend fun capture(): CapturedBackup =
                    repository.buildFullCaptured(settingsRepository::portableBackupSnapshotWithRevision)

                override suspend fun currentDataRevision(): Long = repository.currentDataRevision()
            },
            folders = folders,
            scheduler = scheduler,
            clock = { now },
            zone = { ZoneOffset.UTC },
        )
    }

    private class FakeScheduler : AutoBackupScheduler {
        var scheduledAt: Long? = null

        override fun schedule(atMillis: Long, nowMillis: Long) {
            scheduledAt = atMillis
        }

        override fun cancel() {
            scheduledAt = null
        }
    }

    private class FakeFolders : AutoBackupFolderAccess {
        private val folders = mutableMapOf<String, FakeFolder>()
        private val grants = mutableMapOf<String, Boolean>()
        private val labels = mutableMapOf<String, String>()
        val permissions: MutableSet<String> = mutableSetOf()
        val released = mutableListOf<String>()
        val events = mutableListOf<String>()
        var onRelease: (String) -> Unit = {}

        fun add(uri: String, label: String = uri.substringAfterLast('/'), grantsPermission: Boolean = true): FakeFolder {
            val folder = FakeFolder(uri, events)
            folders[uri] = folder
            grants[uri] = grantsPermission
            labels[uri] = label
            return folder
        }

        fun folder(uri: String): FakeFolder = folders.getValue(uri)

        override fun acquirePermission(uri: String): Boolean {
            events += "acquire:$uri"
            val granted = grants[uri] == true
            if (granted) permissions += uri
            return granted
        }

        override fun releasePermission(uri: String) {
            onRelease(uri)
            events += "release:$uri"
            released += uri
            permissions -= uri
        }

        override fun status(uri: String): AutoBackupFolderStatus = when {
            uri !in permissions -> AutoBackupFolderStatus.PERMISSION_LOST
            folder(uri).missing -> AutoBackupFolderStatus.MISSING
            else -> AutoBackupFolderStatus.AVAILABLE
        }

        override fun open(uri: String): AutoBackupFolder = folder(uri)

        override fun label(uri: String): String = labels.getValue(uri)
    }

    private class FakeFolder(private val uri: String, private val events: MutableList<String>) : AutoBackupFolder {
        val files = sortedMapOf<String, ByteArray>()
        val deleted = mutableListOf<String>()
        var failCreate = false
        var failWrite = false
        var corruptReads = false
        var missing = false
        var probeCreated = 0
        var onWrite: () -> Unit = {}
        var blockWrites: CountDownLatch? = null
        val writeStarted = CountDownLatch(1)
        val maxConcurrentWrites = AtomicInteger(0)
        private val activeWrites = AtomicInteger(0)

        fun automaticFiles(): List<String> = files.keys.filter(AutoBackupFiles::isAutomaticBackup).sorted()
        fun newestAutomatic(): String = files.keys.filter(AutoBackupFiles::isAutomaticBackup)
            .maxBy { AutoBackupFiles.parse(it)!! }
        fun text(name: String): String = String(files.getValue(name))

        fun destination(name: String) = object : BackupDestination {
            override fun openOutputStream(): OutputStream = output(name)
            override fun openInputStream(): InputStream = ByteArrayInputStream(files.getValue(name))
        }

        override fun listFiles(): List<AutoBackupFolderFile> {
            if (missing) throw IOException("missing")
            return files.keys.map { AutoBackupFolderFile(it, it) }
        }

        override fun createFile(name: String, mimeType: String): AutoBackupFolderFile {
            if (failCreate || missing) throw IOException("cannot create")
            check(name !in files) { "Would overwrite $name" }
            if (!AutoBackupFiles.isAutomaticBackup(name)) probeCreated++
            events += "create:$uri:$name"
            files[name] = ByteArray(0)
            return AutoBackupFolderFile(name, name)
        }

        override fun openOutputStream(file: AutoBackupFolderFile): OutputStream = output(file.name)

        override fun openInputStream(file: AutoBackupFolderFile): InputStream {
            val bytes = files[file.id] ?: throw IOException("gone")
            val text = String(bytes)
            return ByteArrayInputStream((if (corruptReads) text.dropLast(10) else text).toByteArray())
        }

        override fun delete(file: AutoBackupFolderFile): Boolean {
            if (!AutoBackupFiles.isAutomaticBackup(file.name)) {
                events += "probe-delete:$uri:${file.name}"
            } else {
                deleted += file.name
            }
            return files.remove(file.id) != null
        }

        private fun output(name: String): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                val automatic = AutoBackupFiles.isAutomaticBackup(name)
                if (automatic) {
                    val active = activeWrites.incrementAndGet()
                    maxConcurrentWrites.accumulateAndGet(active) { a, b -> maxOf(a, b) }
                    writeStarted.countDown()
                    try {
                        blockWrites?.await(5, TimeUnit.SECONDS)
                        onWrite()
                        if (failWrite) throw IOException("disk full")
                    } finally {
                        activeWrites.decrementAndGet()
                    }
                }
                files[name] = toByteArray()
            }
        }
    }
}
