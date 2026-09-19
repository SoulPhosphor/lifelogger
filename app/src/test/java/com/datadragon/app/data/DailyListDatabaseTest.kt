package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-database tests (Robolectric provides SQLite): the v15→v16 migration
 * preserving existing data, the persistence-level date-uniqueness rule, and
 * the DAO's insert/update/delete behavior for Daily List rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyListDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DailyListDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyListDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun card(date: LocalDate, title: String = "") = DailyList(
        date = date,
        title = title,
        createdAt = date.toEpochDay(),
    )

    // --- Migration --------------------------------------------------------------

    @Test
    fun migration15To16CreatesTheDailyListTablesAndPreservesExistingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // The v15 shape of the tables the migration must leave intact.
                    db.execSQL(
                        "CREATE TABLE checklists (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, draft INTEGER NOT NULL DEFAULT 0)",
                    )
                    db.execSQL(
                        "CREATE TABLE log_templates (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, schemaJson TEXT NOT NULL, " +
                            "formMarkdown TEXT NOT NULL DEFAULT '', " +
                            "locked INTEGER NOT NULL DEFAULT 1, " +
                            "allowAppendedNotes INTEGER NOT NULL DEFAULT 0, " +
                            "automaticTimestamping INTEGER NOT NULL DEFAULT 0, " +
                            "sortTimestampLabel TEXT, " +
                            "sortNewestFirst INTEGER NOT NULL DEFAULT 1, " +
                            "integrateCalendar INTEGER NOT NULL DEFAULT 0)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL("INSERT INTO checklists (uuid, name, createdAt) VALUES ('l1', 'Groceries', 100)")
        sqlite.execSQL(
            "INSERT INTO log_templates (uuid, name, createdAt, schemaJson) VALUES ('f1', 'Mood', 300, '[]')",
        )

        AppDatabase.MIGRATION_15_16.migrate(sqlite)

        // Existing data survives untouched.
        sqlite.query("SELECT name FROM checklists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Groceries", cursor.getString(0))
        }
        sqlite.query("SELECT name FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mood", cursor.getString(0))
        }

        // The new tables exist with the unique date index.
        sqlite.query("SELECT COUNT(*) FROM daily_lists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.query("SELECT COUNT(*) FROM daily_list_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_daily_lists_date'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        // Both once-only markers exist as their own columns.
        sqlite.query("SELECT COUNT(*) FROM daily_lists WHERE maintenanceRunOn IS NULL").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.query("SELECT COUNT(*) FROM daily_lists WHERE renewalRunOn IS NULL").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.close()
    }

    // --- Date uniqueness ----------------------------------------------------------

    @Test
    fun twoCardsForTheSameDateArePreventedAtThePersistenceLevel() = runBlocking {
        val date = LocalDate.of(2026, 9, 18)
        val first = dao.insertDailyList(card(date))
        val second = dao.insertDailyList(card(date))

        assertTrue(first != -1L)
        assertEquals(-1L, second) // the UNIQUE index aborted the second insert
        assertEquals(1, dao.getAllDailyListsOnce().size)
    }

    @Test
    fun aCardStoresAndSortsByItsDateOnly() = runBlocking {
        dao.insertDailyList(card(LocalDate.of(2026, 9, 10)))
        dao.insertDailyList(card(LocalDate.of(2026, 9, 18)))
        dao.insertDailyList(card(LocalDate.of(2026, 9, 12)))

        val dates = dao.getAllDailyListsOnce().map { it.date }
        // Newest first, by the card's date (never a creation timestamp).
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 10),
            ),
            dates,
        )
    }

    @Test
    fun aSavedCardKeepsItsImmutableDateThroughTitleAndFavoriteUpdates() = runBlocking {
        val date = LocalDate.of(2026, 9, 18)
        val id = dao.insertDailyList(card(date))
        val before = dao.getDailyList(id)!!

        dao.setTitle(id, "Beach day")
        dao.setFavorited(id, true)

        val after = dao.getDailyList(id)!!
        assertEquals(before.date, after.date)
        assertEquals("Beach day", after.title)
        assertTrue(after.favorited)
    }

    @Test
    fun deletingEveryTaskDoesNotDeleteTheCard() = runBlocking {
        val id = dao.insertDailyList(card(LocalDate.of(2026, 9, 18)))
        dao.insertItem(DailyListItem(dailyListId = id, text = "One", position = 0))
        dao.insertItem(DailyListItem(dailyListId = id, text = "Two", position = 1))

        dao.deleteBlankItems(id) // would remove blanks; tasks have text
        assertEquals(2, dao.getItemsOnce(id).size)

        // Deleting the individual tasks leaves the card itself.
        dao.getItemsOnce(id).forEach { dao.deleteItem(it.id) }
        assertNull(dao.getItemsOnce(id).firstOrNull())
        assertEquals(LocalDate.of(2026, 9, 18), dao.getDailyList(id)!!.date)
    }

    @Test
    fun previousBeforeReturnsTheLatestEarlierCardRegardlessOfGaps() = runBlocking {
        val friday = LocalDate.of(2026, 9, 18)
        val tuesday = LocalDate.of(2026, 9, 15)
        val monday = LocalDate.of(2026, 9, 14)

        dao.insertDailyList(card(monday))
        dao.insertDailyList(card(tuesday))

        // Friday's renewal source is Tuesday — the latest card before Friday,
        // even though Wednesday and Thursday have no cards.
        assertEquals(tuesday, dao.getPreviousBefore(friday)!!.date)

        // A future-planned card is never a source for an earlier date.
        dao.insertDailyList(card(LocalDate.of(2026, 9, 20)))
        assertEquals(tuesday, dao.getPreviousBefore(friday)!!.date)
    }

    @Test
    fun deletingAWholeCardRemovesItsItemsWithIt() = runBlocking {
        val id = dao.insertDailyList(card(LocalDate.of(2026, 9, 18)))
        dao.insertItem(DailyListItem(dailyListId = id, text = "One", position = 0))

        dao.deleteDailyListWithItems(id)

        assertNull(dao.getDailyList(id))
        assertTrue(dao.getItemsOnce(id).isEmpty())
    }

    @Test
    fun itemsKeepStableIdentityAndOrder() = runBlocking {
        val listId = dao.insertDailyList(card(LocalDate.of(2026, 9, 18)))
        val one = dao.insertItem(DailyListItem(dailyListId = listId, text = "One", position = 0))
        val two = dao.insertItem(DailyListItem(dailyListId = listId, text = "Two", position = 1))

        dao.applyOrder(listOf(two, one))

        assertEquals(listOf("Two", "One"), dao.getItemsOnce(listId).map { it.text })
        assertNotEquals(dao.getItemsOnce(listId)[0].uuid, dao.getItemsOnce(listId)[1].uuid)
    }

    // --- Typed-first creation -----------------------------------------------------

    @Test
    fun theFirstTypedItemCreatesTheCardAndIsStoredWithIt() = runBlocking {
        val repo = DailyListRepository(db)
        val date = LocalDate.of(2026, 9, 18)
        val typed = DailyListItem(dailyListId = 0, uuid = "typed-1", text = "First task", position = 0)

        val created = repo.createWithFirstItem(date, now = 500, typed = typed)

        assertTrue(created != null)
        assertEquals(date, created!!.date)
        assertEquals(listOf("First task"), dao.getItemsOnce(created.id).map { it.text })
        // The stored row keeps the typed row's stable uuid (duplicate-proof autosave).
        assertEquals("typed-1", dao.getItemsOnce(created.id).single().uuid)
    }

    @Test
    fun typedFirstCreationOnAnExistingDateInsertsIntoThatCardWithoutDuplicatingIt() = runBlocking {
        val repo = DailyListRepository(db)
        val date = LocalDate.of(2026, 9, 18)
        dao.insertDailyList(card(date))
        val typed = DailyListItem(dailyListId = 0, text = "Late addition", position = 0)

        val result = repo.createWithFirstItem(date, now = 500, typed = typed)

        assertEquals(1, dao.getAllDailyListsOnce().size) // no duplicate card
        assertEquals(date, result!!.date)
        assertEquals(listOf("Late addition"), dao.getItemsOnce(result.id).map { it.text })
    }

    @Test
    fun renewalsOneTimeMarkerIsStoredSeparatelyFromMaintenances() = runBlocking {
        val repo = DailyListRepository(db)
        val date = LocalDate.of(2026, 9, 18)
        val id = dao.insertDailyList(card(date))

        repo.markMaintenanceRun(id, date)
        assertNull(dao.getDailyList(id)!!.renewalRunOn) // maintenance did not touch renewal

        repo.markRenewalRun(id, date)
        assertEquals(date, dao.getDailyList(id)!!.renewalRunOn)
    }
}
