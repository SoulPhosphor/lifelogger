package com.datadragon.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTargetTest {
    @Test
    fun dailyTaskRoundTrips() {
        val target = ResumeTarget.DailyTask(LocalDate.of(2026, 9, 21))
        val (kind, value) = target.toStoredValues()

        assertEquals(target, ResumeTarget.fromStoredValues(kind, value))
    }

    @Test
    fun clickerRoundTrips() {
        val target = ResumeTarget.Clicker(42)
        val (kind, value) = target.toStoredValues()

        assertEquals(target, ResumeTarget.fromStoredValues(kind, value))
    }

    @Test
    fun invalidStoredTargetFallsBackToHome() {
        assertEquals(ResumeTarget.Home, ResumeTarget.fromStoredValues("clicker", "not-an-id"))
        assertEquals(ResumeTarget.Home, ResumeTarget.fromStoredValues("daily_task", "not-a-date"))
        assertEquals(ResumeTarget.Home, ResumeTarget.fromStoredValues("unknown", "anything"))
    }
}
