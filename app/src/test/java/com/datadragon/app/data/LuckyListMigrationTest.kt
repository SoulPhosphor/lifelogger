package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LuckyListMigrationTest {
    @Test fun upgradingInstalledVersion20PreservesListsAndClickerStatistics() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "lucky-upgrade-${UUID.randomUUID()}.db"
        val seed = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val oldListId = seed.checklistDao().insertChecklist(Checklist(uuid = "old-list", name = "Keep", createdAt = 1))
        seed.checklistDao().insertItem(ChecklistItem(checklistId = oldListId, text = "Existing", position = 0))
        seed.openHelper.writableDatabase.execSQL("INSERT INTO clicker_logs(uuid, title, createdAt, lastAccessedAt, lastModifiedAt, fieldsJson, displayOnlyClickerDateTime, autoDateStamp, autoTimeStamp, allowFollowUp, statisticsJson) VALUES('old-clicker', 'Keep Stats', 1, 1, 1, '[]', 0, 0, 0, 0, '{}')")
        seed.close()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(AppDatabase.SCHEMA_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase.apply {
            execSQL("DROP TABLE lucky_list_items"); execSQL("DROP TABLE lucky_lists"); execSQL("DROP TABLE backup_state")
            version = 20
        }
        helper.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK).addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
        try {
            assertEquals("old-list", upgraded.checklistDao().getChecklist(oldListId)!!.uuid)
            assertEquals("Existing", upgraded.checklistDao().getItemsOnce(oldListId).single().text)
            assertEquals("{}", upgraded.clickerDao().getLogByUuid("old-clicker")!!.statisticsJson)
            assertTrue(upgraded.luckyListDao().getAllOnce().isEmpty())
            assertTrue(BackupRevisionTracking.installedTriggerNames(upgraded.openHelper.readableDatabase).containsAll(BackupRevisionTracking.expectedTriggerNames()))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
