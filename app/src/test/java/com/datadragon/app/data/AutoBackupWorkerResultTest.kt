package com.datadragon.app.data

import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 5 follow-up: the worker reports success only when the coordinator completed. */
class AutoBackupWorkerResultTest {

    @Test
    fun normalCompletionIsSuccessAndRefreshesState() = runBlocking {
        var refreshed = 0
        val result = runAutoBackupWork(run = { }, refresh = { refreshed++ })
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, refreshed)
    }

    @Test
    fun aBackupFailureHandledByTheCoordinatorIsStillSuccess() = runBlocking {
        // Write, verification, and folder failures are recorded and scheduled by
        // the coordinator itself; it returns normally with FAILED.
        var refreshed = 0
        val result = runAutoBackupWork(
            run = { AutoBackupRunResult.FAILED },
            refresh = { refreshed++ },
        )
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, refreshed)
    }

    @Test
    fun anUnexpectedExceptionIsRetriedNotReportedAsSuccess() = runBlocking {
        var refreshed = 0
        val result = runAutoBackupWork(
            run = { throw IllegalStateException("unexpected") },
            refresh = { refreshed++ },
        )
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, refreshed)
    }

    @Test
    fun cancellationIsNotSwallowed() = runBlocking {
        val error = runCatching {
            runAutoBackupWork(run = { throw CancellationException("stopped") }, refresh = { })
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
