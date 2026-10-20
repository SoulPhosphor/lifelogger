package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comparison fields on Clicker cards store their rows by item id inside the
 * card's string values, keep those ids through item changes, and survive a
 * full backup and Replace restore along with the log's New Option setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickerComparisonTest {

    private lateinit var db: AppDatabase

    private val field = ClickerField(
        id = "field-meals",
        type = ClickerFieldType.COMPARISON,
        label = "Meals",
        startingNumber = 5,
        incrementAmount = 2,
        useClickerCounting = true,
        firstLabel = "Planned",
        choices = listOf(ChoiceOption("turkey", "Turkey Dinner"), ChoiceOption("ham", "Ham Dinner")),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theClickerFieldReadsAsAComparisonWithItsTrackerSettings() {
        val def = field.comparisonDef()
        assertEquals(FieldType.COMPARISON, def.type)
        assertTrue(def.clickerCounting)
        assertEquals(5, def.clickerStartingNumber)
        assertEquals(2, def.clickerIncrement)
        assertEquals("Planned", def.comparisonFirstLabel)
        assertEquals(field.choices, def.choices)
    }

    @Test
    fun rowsAreStoredAsTextInsideTheCardValues() {
        val stored = ClickerComparison.stored(field.comparisonDef(), listOf(ComparisonRow("turkey", "3", ""), ComparisonRow()))!!
        val values = ClickerValues.decode(ClickerValues.encode(mapOf(field.id to stored, "other" to "7")))
        // The Starting Number counts once the row has an item; the placeholder row isn't stored.
        assertEquals(listOf(ComparisonRow("turkey", "3", "5")), ClickerComparison.rows(values, field.id))
        assertEquals("7", values["other"])
        assertNull(ClickerComparison.stored(field.comparisonDef(), listOf(ComparisonRow())))
    }

    @Test
    fun aNumberTypedBeforeAnItemIsKeptAsADraftButNeverCounted() {
        val def = field.comparisonDef()
        val stored = ClickerComparison.stored(def, listOf(ComparisonRow("", "7", ""), ComparisonRow("turkey", "3", "4")))!!
        val values = ClickerValues.decode(ClickerValues.encode(mapOf(field.id to stored)))
        val rows = ClickerComparison.rows(values, field.id)
        // The draft survives leaving the card, so the typed number isn't lost.
        assertEquals(listOf(ComparisonRow("", "7", ""), ComparisonRow("turkey", "3", "4")), rows)
        // Only the answer counts in text and statistics.
        assertEquals("Turkey Dinner: Planned 3, Actual 4, Δ 1; Total Difference: 1", ComparisonValues.display(def, rows))
        val card = ClickerCard(clickerLogId = 1, uuid = "c", createdAt = 0, valuesJson = ClickerValues.encode(mapOf(field.id to stored)))
        val stats = ClickerStatisticsCalculator.calculate(
            ClickerStatisticsConfig(), listOf(field), listOf(card), ClickerStatisticsRange.AllTime,
            java.time.LocalDate.of(2026, 10, 20), java.time.ZoneOffset.UTC,
        ).single().comparison!!
        assertEquals(0, stats.totalDifference!!.compareTo(java.math.BigDecimal.ONE))
        assertEquals(1, stats.timesDifferent)
    }

    @Test
    fun deletingAnItemDropsOnlyItsRows() {
        val stored = ClickerComparison.stored(
            field.comparisonDef(),
            listOf(ComparisonRow("turkey", "3", "1"), ComparisonRow("ham", "2", "2")),
        )!!
        val card = ClickerValues.encode(mapOf(field.id to stored, ClickerValues.FOLLOW_UP_KEY to "keep"))
        val after = ClickerValues.decode(ClickerComparison.withoutOption(card, field.id, "turkey"))
        assertEquals(listOf(ComparisonRow("ham", "2", "2")), ClickerComparison.rows(after, field.id))
        assertEquals("keep", after[ClickerValues.FOLLOW_UP_KEY])
        assertEquals(card, ClickerComparison.withoutOption(card, field.id, "beef"))
    }

    @Test
    fun newOptionsKeepEveryExistingIdAndRefuseDuplicates() {
        val log = ClickerLog(
            uuid = "log", title = "Food", createdAt = 1, lastAccessedAt = 1,
            fieldsJson = ClickerComparison.encodeFields(listOf(field)),
        )
        val (updated, added) = ClickerComparison.withNewOptions(log, field.id, listOf("Beef Stew", "turkey dinner"), capitalize = false)
        val choices = ClickerComparison.decodeFields(updated!!.fieldsJson)!!.single().choices
        assertEquals(listOf("turkey", "ham"), choices.take(2).map { it.id })
        assertEquals(listOf("Turkey Dinner", "Ham Dinner", "Beef Stew"), choices.map { it.label })
        assertEquals(listOf(choices[2].id), added)
    }

    @Test
    fun newItemsFillTheLastEmptyRowThenNewRows() {
        val rows = ComparisonValues.placeItems(listOf(ComparisonRow("turkey", "1", ""), ComparisonRow("", "4", "")), listOf("ham", "beef"))
        assertEquals(
            listOf(ComparisonRow("turkey", "1", ""), ComparisonRow("ham", "4", ""), ComparisonRow("beef")),
            rows,
        )
    }

    @Test
    fun maxDigitsFloorComesFromSavedCards() {
        val cards = listOf(
            ClickerCard(
                clickerLogId = 1, uuid = "c1", createdAt = 1,
                valuesJson = ClickerValues.encode(
                    mapOf(field.id to ClickerComparison.stored(field.comparisonDef(), listOf(ComparisonRow("turkey", "3", "300")))!!),
                ),
            ),
        )
        assertEquals(3, ClickerComparison.savedDigits(cards, field.id))
        assertNull(ClickerComparison.savedDigits(emptyList(), field.id))
    }

    @Test
    fun backupAndRestoreKeepItemIdsRowsAndTheNewOptionSetting() = runBlocking {
        val dao = db.clickerDao()
        val logId = dao.insertLog(
            ClickerLog(
                uuid = "log-food", title = "Food", createdAt = 1, lastAccessedAt = 1,
                fieldsJson = ClickerComparison.encodeFields(listOf(field.copy(choices = field.choices + ChoiceOption("beef", "Beef", retired = true)))),
                allowAdditionsFromForm = true,
            ),
        )
        val stored = ClickerComparison.stored(field.comparisonDef(), listOf(ComparisonRow("beef", "2", "9"), ComparisonRow("turkey", "34", "")))!!
        dao.insertCard(ClickerCard(clickerLogId = logId, uuid = "card-1", createdAt = 1, valuesJson = ClickerValues.encode(mapOf(field.id to stored))))

        val repository = BackupRepository(db)
        val decoded = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        assertEquals(true, decoded.clickerLogs.single().allowAdditionsFromForm)
        repository.restore(decoded, RestoreMode.REPLACE)

        val log = dao.getAllLogsOnce().single()
        assertTrue(log.allowAdditionsFromForm)
        val restoredField = ClickerComparison.decodeFields(log.fieldsJson)!!.single()
        assertEquals(listOf("turkey", "ham", "beef"), restoredField.choices.map { it.id })
        assertTrue(restoredField.useClickerCounting)
        val card = dao.getCardsForLog(log.id).single()
        assertEquals(
            listOf(ComparisonRow("beef", "2", "9"), ComparisonRow("turkey", "34", "5")),
            ClickerComparison.rows(ClickerValues.decode(card.valuesJson), field.id),
        )
    }

    @Test
    fun aLogWithNewOptionOffEncodesWithoutTheSetting() {
        val log = ClickerLog(uuid = "log", title = "Food", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]")
        assertNull(BackupCodec.clickerOf(log, emptyList()).allowAdditionsFromForm)
        assertEquals(true, BackupCodec.clickerOf(log.copy(allowAdditionsFromForm = true), emptyList()).allowAdditionsFromForm)
    }
}
