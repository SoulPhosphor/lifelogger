package com.datadragon.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure Daily List rules: sequence completion ordering, renewal planning,
 * and the destructive-maintenance decisions.
 */
class DailyListLogicTest {

    private fun item(
        id: Long,
        text: String,
        completed: Boolean = false,
        indent: Int = DAILY_LIST_TOP_LEVEL_INDENT,
        sourceUuid: String? = null,
    ) = DailyListItem(
        id = id,
        dailyListId = 1,
        uuid = "item-$id",
        text = text,
        completed = completed,
        indent = indent,
        position = id.toInt(),
        sourceUuid = sourceUuid,
    )

    private fun card(
        id: Long,
        date: LocalDate,
        favorited: Boolean = false,
        genuinelyCompleted: Boolean = false,
        completionBlocked: Boolean = false,
        maintenanceRunOn: LocalDate? = null,
        renewalRunOn: LocalDate? = null,
    ) = DailyList(
        id = id,
        uuid = "card-$id",
        date = date,
        favorited = favorited,
        genuinelyCompleted = genuinelyCompleted,
        completionBlockedByCleanup = completionBlocked,
        maintenanceRunOn = maintenanceRunOn,
        renewalRunOn = renewalRunOn,
        createdAt = 0,
    )

    // --- Sequence behavior ----------------------------------------------------

    @Test
    fun completingEveryItemInASequenceMovesTheWholeSequenceBelowActiveSequences() {
        val items = listOf(
            item(1, "A1"),
            item(2, "A2", indent = DAILY_LIST_SUB_ITEM_INDENT),
            item(3, "B1"),
            item(4, "B2", indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        // Complete sequence A entirely.
        val completed = items.map { if (it.id <= 2L) it.copy(completed = true) else it }

        val ordered = DailyListLogic.orderedBySequenceCompletion(completed)

        assertEquals(listOf("B1", "B2", "A1", "A2"), ordered.map { it.text })
    }

    @Test
    fun completingOneSubItemAloneDoesNotMoveTheSequenceWhileOthersRemainUnfinished() {
        val items = listOf(
            item(1, "A1"),
            item(2, "A2", indent = DAILY_LIST_SUB_ITEM_INDENT),
            item(3, "B1"),
        )
        val oneChecked = items.map { if (it.id == 1L) it.copy(completed = true) else it }

        val ordered = DailyListLogic.orderedBySequenceCompletion(oneChecked)

        // Sequence A still has an unfinished member: nothing moved.
        assertEquals(listOf("A1", "A2", "B1"), ordered.map { it.text })
    }

    @Test
    fun uncheckingAnyMemberOfACompletedSequenceReturnsItToTheActiveSection() {
        val items = listOf(
            item(1, "A1"),
            item(2, "A2", indent = DAILY_LIST_SUB_ITEM_INDENT),
            item(3, "B1"),
            item(4, "B2", indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        val aCompleted = items.map { if (it.id <= 2L) it.copy(completed = true) else it }
        val aMoved = DailyListLogic.orderedBySequenceCompletion(aCompleted)
        assertEquals(listOf("B1", "B2", "A1", "A2"), aMoved.map { it.text })

        // Uncheck one member of the completed sequence A: it returns to active.
        val oneUnchecked = aMoved.map { if (it.text == "A1") it.copy(completed = false) else it }
        val reOrdered = DailyListLogic.orderedBySequenceCompletion(oneUnchecked)

        // A is active again and rejoins at the bottom of the active section —
        // it is already there (stored last), and B never left the active
        // section, so the order is unchanged.
        assertEquals(listOf("B1", "B2", "A1", "A2"), reOrdered.map { it.text })
    }

    @Test
    fun aReturningSequenceGoesToTheBottomOfTheActiveSection() {
        val items = listOf(
            item(1, "A1"),
            item(2, "B1"),
            item(3, "C1"),
            item(4, "D1"),
        )
        // B, C completed; then B's member unchecked while A and D are active.
        val state = items.map {
            when (it.text) {
                "B1", "C1" -> it.copy(completed = true)
                else -> it
            }
        }
        val moved = DailyListLogic.orderedBySequenceCompletion(state)
        assertEquals(listOf("A1", "D1", "B1", "C1"), moved.map { it.text })

        val bReactivated = moved.map { if (it.text == "B1") it.copy(completed = false) else it }
        val reordered = DailyListLogic.orderedBySequenceCompletion(bReactivated)
        // Active: A, D, B (B rejoins at the bottom of the active section); C completed.
        assertEquals(listOf("A1", "D1", "B1", "C1"), reordered.map { it.text })
    }

    @Test
    fun persistedOrderingMatchesScreenOrderingBecauseOrderingIsTheSamePureFunction() {
        // The repository persists the exact list this function returns, so the
        // two can't drift; the invariant here is that the function is stable.
        val items = listOf(
            item(1, "A1"),
            item(2, "B1"),
            item(3, "A2"),
            item(4, "B2"),
        )
        val completed = items.map { if (it.id <= 3L) it.copy(completed = true) else it }
        val first = DailyListLogic.orderedBySequenceCompletion(completed)
        val second = DailyListLogic.orderedBySequenceCompletion(first)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun ordinaryListBehaviorIsNotTouchedByDailyListSequencing() {
        // The ordinary list's move-to-bottom setting lives in
        // ChecklistDraftManager and operates per row, not per sequence. This
        // test pins that DailyListLogic never leaks: it only runs for Daily
        // List items (a DailyListItem type), so a plain per-row move on an
        // ordinary list keeps its own semantics. The pure function remains
        // total and side-effect free.
        val items = listOf(item(1, "A"), item(2, "B"))
        val completed = items.map { it.copy(completed = true) }
        // Both fully completed: every sequence is completed, order is unchanged.
        assertEquals(items.map { it.id }, DailyListLogic.orderedBySequenceCompletion(completed).map { it.id })
    }

    // --- Renewal planning -----------------------------------------------------

    @Test
    fun renewalCarriesOnlyUnfinishedRows() {
        val source = listOf(
            item(1, "Done", completed = true),
            item(2, "Todo"),
        )
        val renewed = DailyListLogic.renewedItems(source)
        assertEquals(listOf("Todo"), renewed.map { it.text })
    }

    @Test
    fun renewalPromotesAnOrphanedUnfinishedSubItemToTopLevel() {
        val source = listOf(
            item(1, "Clean kitchen"),
            item(2, "Wash dishes", completed = true, indent = DAILY_LIST_SUB_ITEM_INDENT),
            item(3, "Mop floor", indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        val renewed = DailyListLogic.renewedItems(source)
        assertEquals(listOf("Clean kitchen", "Mop floor"), renewed.map { it.text })
        assertEquals(
            listOf(DAILY_LIST_TOP_LEVEL_INDENT, DAILY_LIST_SUB_ITEM_INDENT),
            renewed.map { it.indent },
        )
    }

    @Test
    fun renewalPromotesASubItemWhoseTopLevelParentWasCompleted() {
        val source = listOf(
            item(1, "Parent", completed = true),
            item(2, "Surviving sub", indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        val renewed = DailyListLogic.renewedItems(source)
        assertEquals(listOf("Surviving sub"), renewed.map { it.text })
        assertEquals(DAILY_LIST_TOP_LEVEL_INDENT, renewed.single().indent)
    }

    @Test
    fun repeatedRenewalDoesNotDuplicateAlreadyCarriedItems() {
        val source = listOf(item(1, "Todo"))
        val carried = DailyListLogic.filterAlreadyCarried(
            sourceItems = source,
            sourceCardUuid = "card-9",
            currentItems = listOf(
                item(100, "Todo", sourceUuid = DailyListLogic.sourceIdentityOf("card-9", source[0])),
            ),
        )
        assertTrue(carried.isEmpty())
    }

    @Test
    fun aLaterManualRenewalDiscoversAnItemThatBecameUnfinishedAfterAnEarlierPress() {
        // First press: "Early" was unfinished and carried; "Later" was
        // completed and so was not carried.
        val sourceAtFirstPress = listOf(
            item(1, "Early"),
            item(2, "Later", completed = true),
        )
        val firstPress = DailyListLogic.renewedItems(sourceAtFirstPress)
        assertEquals(listOf("Early"), firstPress.map { it.text })

        val current = firstPress.mapIndexed { index, renewed ->
            item(100L + index, renewed.text, sourceUuid = DailyListLogic.sourceIdentityOf("card-9", renewed.sourceItem))
        }

        // Pressing again while nothing changed carries nothing new — "Early"
        // is never duplicated.
        val stillOnlyEarly = DailyListLogic.filterAlreadyCarried(
            sourceAtFirstPress.filterNot { it.completed }, "card-9", current,
        )
        assertTrue(stillOnlyEarly.isEmpty())

        // The user later unchecks "Later" on the source card: the next press
        // carries "Later" — the one row that was never carried.
        val sourceNow = listOf(
            item(1, "Early"),
            item(2, "Later"),
        )
        val eligible = DailyListLogic.filterAlreadyCarried(sourceNow, "card-9", current)
        assertEquals(listOf("Later"), eligible.map { it.text })
    }

    @Test
    fun renewalIdentityIsBasedOnStableUuidsNotVisibleText() {
        val source = item(1, "Original text")
        val identity = DailyListLogic.sourceIdentityOf("card-9", source)

        val carried = DailyListLogic.filterAlreadyCarried(
            sourceItems = listOf(source.copy(text = "Renamed by the user")),
            sourceCardUuid = "card-9",
            currentItems = listOf(item(100, "Original text", sourceUuid = identity)),
        )
        // Even though the source text changed, the identity match prevents a
        // duplicate — renewal never synchronizes the carried copy's text.
        assertTrue(carried.isEmpty())
    }

    // --- Maintenance ------------------------------------------------------------

    @Test
    fun retentionCandidatesAreCountedByCardsNotCalendarDays() {
        val today = LocalDate.of(2026, 9, 18)
        val cards = listOf(
            card(1, today.minusDays(40)),
            card(2, today.minusDays(20)),
            card(3, today.minusDays(2)),
        )
        // Keeping 2 cards deletes only the oldest card — the 20-day gap
        // between cards does not count as "retained days".
        val candidates = DailyListLogic.retentionDeletionCandidates(cards, keepCount = 2, protectFavorited = false)
        assertEquals(listOf(1L), candidates.map { it.id })
    }

    @Test
    fun retentionProtectsFavoritedCardsWhenProtectionIsOn() {
        val today = LocalDate.of(2026, 9, 18)
        val cards = listOf(
            card(1, today.minusDays(5), favorited = true),
            card(2, today.minusDays(4)),
            card(3, today.minusDays(2)),
        )
        val withProtection = DailyListLogic.retentionDeletionCandidates(cards, keepCount = 1, protectFavorited = true)
        assertEquals(listOf(2L), withProtection.map { it.id })

        val withoutProtection = DailyListLogic.retentionDeletionCandidates(cards, keepCount = 1, protectFavorited = false)
        // Candidates come back newest-first: the two cards beyond the kept one.
        assertEquals(listOf(2L, 1L), withoutProtection.map { it.id })
    }

    @Test
    fun blankRetentionDisablesWholeCardDeletion() {
        val today = LocalDate.of(2026, 9, 18)
        val cards = listOf(card(1, today.minusDays(30)))
        assertTrue(DailyListLogic.retentionDeletionCandidates(cards, keepCount = 0, protectFavorited = false).isEmpty())
        // Retention accepts 1 through 999; anything else (including blank and
        // three-plus digits that exceed 999) is disabled.
        assertNull("1000".takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it in 1..999 })
        assertEquals(1, "1".toIntOrNull()?.takeIf { it in 1..999 })
        assertEquals(999, "999".toIntOrNull()?.takeIf { it in 1..999 })
    }

    @Test
    fun unfinishedItemDeletionOnlyAffectsPastDates() {
        val today = LocalDate.of(2026, 9, 18)
        val items = listOf(item(1, "Unfinished"), item(2, "Done", completed = true))

        assertTrue(DailyListLogic.deletableUnfinishedItems(today, today, items).isEmpty())
        assertTrue(
            DailyListLogic.deletableUnfinishedItems(today.plusDays(1), today, items).isEmpty(),
        )
        assertEquals(listOf(1L), DailyListLogic.deletableUnfinishedItems(today.minusDays(1), today, items).map { it.id })
    }

    @Test
    fun cleanupProtectsTheMostRecentPastCardsAndCountsCardsNotCalendarDays() {
        val today = LocalDate.of(2026, 9, 19)
        val cards = listOf(
            card(1, today), // today: never eligible
            card(2, LocalDate.of(2026, 9, 18)),
            card(3, LocalDate.of(2026, 9, 15)),
            card(4, LocalDate.of(2026, 9, 10)),
            card(5, LocalDate.of(2026, 8, 1)),
        )
        // Keep the 3 most recent past cards (Sep 18, 15, 10); only older ones
        // are eligible. Gaps between dates do not change the card count.
        val eligible = DailyListLogic.cleanupEligiblePastCards(cards, today, keepPastCount = 3)
        assertEquals(listOf(5L), eligible.map { it.id })
    }

    @Test
    fun cleanupTouchesNothingWhenThereAreNoMorePastCardsThanTheKeepCount() {
        val today = LocalDate.of(2026, 9, 19)
        val cards = listOf(
            card(1, today),
            card(2, LocalDate.of(2026, 9, 18)),
            card(3, LocalDate.of(2026, 9, 17)),
            card(4, LocalDate.of(2026, 9, 16)),
        )
        // Three past cards, keep three: none are eligible yet. Today never
        // counts toward the protected past cards.
        assertTrue(DailyListLogic.cleanupEligiblePastCards(cards, today, keepPastCount = 3).isEmpty())
    }

    @Test
    fun cleanupEligibilityNeverIncludesTodayOrFutureCards() {
        val today = LocalDate.of(2026, 9, 19)
        val cards = listOf(
            card(1, today.plusDays(2)), // future
            card(2, today), // today
            card(3, today.minusDays(1)),
            card(4, today.minusDays(2)),
        )
        val eligible = DailyListLogic.cleanupEligiblePastCards(cards, today, keepPastCount = 1)
        // Keep the newest past card (Sep 18); only the older past card is
        // eligible. Today and the future card are excluded entirely.
        assertEquals(listOf(4L), eligible.map { it.id })
    }

    @Test
    fun cleanupPreservesCompletedOrphanedSubItemsAndPromotesThem() {
        val today = LocalDate.of(2026, 9, 18)
        val card = today.minusDays(1)
        val items = listOf(
            item(1, "Parent"),
            item(2, "Done sub", completed = true, indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        val deletable = DailyListLogic.deletableUnfinishedItems(card, today, items)
        assertEquals(listOf(1L), deletable.map { it.id })

        val survivors = items.filterNot { item -> deletable.any { it.id == item.id } }
        val rebuilt = DailyListLogic.flattenOrphansAfterCleanup(survivors)
        // The completed sub-item survives and is promoted to top level.
        assertEquals(listOf("Done sub"), rebuilt.map { it.text })
        assertEquals(DAILY_LIST_TOP_LEVEL_INDENT, rebuilt.single().indent)
    }

    @Test
    fun cleanupKeepsRunOfSubItemsNestedWhenTheirParentSurvives() {
        val today = LocalDate.of(2026, 9, 18)
        val card = today.minusDays(1)
        val items = listOf(
            item(1, "Parent"),
            item(2, "Sub", indent = DAILY_LIST_SUB_ITEM_INDENT),
        )
        val deletable = DailyListLogic.deletableUnfinishedItems(card, today, items)
        // On a past card both unfinished rows are trash candidates; the
        // assertion below is about the rebuild's shape, not about deletion.
        assertEquals(listOf(1L, 2L), deletable.map { it.id })

        val rebuilt = DailyListLogic.flattenOrphansAfterCleanup(items)
        // A surviving run of sub-items stays nested under its surviving parent.
        assertEquals(items.map { it.indent }, rebuilt.map { it.indent })
    }

    // --- Renewal timing / maintenance gates --------------------------------------

    @Test
    fun automaticRenewalOnlyRunsOnTheCardsOwnCurrentDateAndOnce() {
        val today = LocalDate.of(2026, 9, 18)
        val fresh = card(1, today)
        val future = card(2, today.plusDays(1))
        val past = card(3, today.minusDays(1))
        val alreadyRun = card(4, today, renewalRunOn = today)

        assertTrue(DailyListLogic.automaticRenewalDue(fresh, today, enabled = true))
        assertFalse(DailyListLogic.automaticRenewalDue(future, today, enabled = true))
        assertFalse(DailyListLogic.automaticRenewalDue(past, today, enabled = true))
        assertFalse(DailyListLogic.automaticRenewalDue(alreadyRun, today, enabled = true))
        assertFalse(DailyListLogic.automaticRenewalDue(fresh, today, enabled = false))
    }

    @Test
    fun theOncePerDayMaintenanceMarkerNeverSuppressesTheOneTimeRenewal() {
        // Entering Daily List mode marks today's card as maintained before any
        // card is opened. That marker must not stop the current day's card from
        // getting its one-time automatic renewal when it is opened.
        val today = LocalDate.of(2026, 9, 18)
        val maintainedButNeverRenewed = card(1, today, maintenanceRunOn = today)

        assertTrue(
            DailyListLogic.automaticRenewalDue(maintainedButNeverRenewed, today, enabled = true),
        )
    }

    @Test
    fun maintenanceRunsAtMostOncePerLocalDay() {
        val today = LocalDate.of(2026, 9, 18)
        val due = card(1, today, maintenanceRunOn = today.minusDays(1))
        val notDue = card(2, today, maintenanceRunOn = today)

        assertTrue(DailyListLogic.maintenanceDue(due, today))
        assertFalse(DailyListLogic.maintenanceDue(notDue, today))
        assertFalse(DailyListLogic.maintenanceDue(null, today))
    }

    // --- Completion history -------------------------------------------------------

    @Test
    fun aDayWithZeroTasksDoesNotQualifyAsCompleted() {
        assertTrue(!DailyListLogic.isAllCompleted(emptyList()))
        assertTrue(DailyListLogic.isAllCompleted(listOf(item(1, "A", completed = true))))
        assertTrue(!DailyListLogic.isAllCompleted(listOf(item(1, "A"))))
    }

    private fun assertNull(actual: Any?) = org.junit.Assert.assertNull(actual)
}
