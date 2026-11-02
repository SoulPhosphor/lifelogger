package com.datadragon.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeGroupingOrderTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsRepository

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("data_dragon_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(context)
    }

    @Test fun orderingIsIndependentPerModeAndSurvivesReopeningAndRename() {
        HomeGroupingOrder.modes.forEachIndexed { index, mode -> settings.setHomeGroupingOrder(mode, listOf("b$index", "a$index")) }
        val reopened = SettingsRepository(context)
        HomeGroupingOrder.modes.forEachIndexed { index, mode -> assertEquals(listOf("b$index", "a$index"), reopened.homeGroupingOrders()[mode.key]) }
        data class Group(val uuid: String, val name: String)
        val rows = listOf(Group("a", "Renamed"), Group("b", "Same Name"), Group("c", "Same Name"))
        assertEquals(listOf("b", "a", "c"), HomeGroupingOrder.apply(rows, listOf("removed", "b", "a")) { it.uuid }.map { it.uuid })
        assertThrows(IllegalArgumentException::class.java) { settings.setHomeGroupingOrder(HomeView.DAILY_LIST, listOf("a")) }
    }

    @Test fun backupRoundTripReplaceUndoAndRevisionProtectEveryOrder() {
        val before = settings.portableBackupSnapshot()
        val revision = settings.portablePreferencesRevision
        settings.setHomeGroupingOrder(HomeView.FORMS, listOf("b", "a"))
        assertTrue(settings.portablePreferencesRevision > revision)
        val after = settings.portablePreferencesRevision
        settings.setHomeGroupingOrder(HomeView.FORMS, listOf("b", "a"))
        assertEquals(after, settings.portablePreferencesRevision)
        val file = BackupFile.single(exportedAt = "2026-11-01T12:00:00Z", category = BackupCategory.PORTABLE_PREFERENCES, payload = BackupPayload(portablePreferences = settings.portableBackupSnapshot()))
        val encoded = BackupCodec.encode(file)
        val decoded = BackupCodec.decode(encoded)
        assertEquals(14, decoded.version)
        assertEquals(encoded, BackupCodec.encode(decoded))
        settings.applyPortableBackup(before)
        assertTrue(settings.homeGroupingOrders().isEmpty())
        settings.applyPortableBackup(decoded.payload.portablePreferences!!)
        assertEquals(listOf("b", "a"), settings.homeGroupingOrders()[HomeView.FORMS.key])
        // An older file that never carried ordering leaves the current order alone.
        settings.applyPortableBackup(BackupPortablePreferences.defaults())
        assertEquals(listOf("b", "a"), settings.homeGroupingOrders()[HomeView.FORMS.key])
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(encoded.replace("\"version\": 14", "\"version\": 13")) }
        assertThrows(IllegalArgumentException::class.java) { HomeGroupingOrder.validate(mapOf(HomeView.FORMS.key to listOf("a", "a"))) }
    }
}
