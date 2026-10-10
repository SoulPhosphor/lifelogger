package com.datadragon.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 5: WorkManager scheduling uses one unique request that never accumulates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupWorkSchedulingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun repeatedSchedulingKeepsExactlyOnePendingRequest() {
        val scheduler = WorkManagerAutoBackupScheduler(context)
        scheduler.schedule(atMillis = 10_000, nowMillis = 0)
        scheduler.schedule(atMillis = 20_000, nowMillis = 0)
        scheduler.schedule(atMillis = 30_000, nowMillis = 0)

        val pending = pending()
        assertEquals(1, pending.size)
        assertTrue(pending.single().tags.contains("automatic_backup_due_at:30000"))
    }

    @Test
    fun schedulingTheSameTimeAgainKeepsTheExistingRequest() {
        val scheduler = WorkManagerAutoBackupScheduler(context)
        scheduler.schedule(atMillis = 10_000, nowMillis = 0)
        val first = pending().single().id
        scheduler.schedule(atMillis = 10_000, nowMillis = 5_000)
        assertEquals(first, pending().single().id)
    }

    @Test
    fun cancelRemovesThePendingRequest() {
        val scheduler = WorkManagerAutoBackupScheduler(context)
        scheduler.schedule(atMillis = 10_000, nowMillis = 0)
        scheduler.cancel()
        assertTrue(pending().isEmpty())
    }

    private fun pending(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerAutoBackupScheduler.UNIQUE_WORK_NAME)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
}
