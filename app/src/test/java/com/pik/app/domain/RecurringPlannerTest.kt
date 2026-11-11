package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringPlannerTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private val today = d(2026, 11, 11)

    private fun rule(
        id: Long,
        kind: RecurringKind,
        amount: Long,
        start: LocalDate,
        done: Int = 0,
        day: Int? = null,
        unit: RecurrenceUnit = RecurrenceUnit.MONTH,
        every: Int = 1,
        active: Boolean = true,
        remind: Boolean = false,
        max: Int? = null,
    ) = RecurringRuleFacts(
        id, kind, amount, RecurrenceSchedule(start, every, unit, day, null, max), done, active, remind,
    )

    // ---- dueItems ----

    @Test fun dueItemsHaveOverdueAndSoonOnes() {
        val rent = rule(1, RecurringKind.BILL, 50_000, d(2026, 1, 5), done = 10, day = 5) // next: 5 Nov (overdue)
        val netflix = rule(2, RecurringKind.SUBSCRIPTION, 1_299, d(2026, 1, 15), done = 10, day = 15) // 15 Nov
        val far = rule(3, RecurringKind.BILL, 9_900, d(2026, 1, 25), done = 10, day = 25) // 25 Nov: too far
        val items = RecurringPlanner.dueItems(listOf(far, netflix, rent), today)
        assertEquals(listOf(1L, 2L), items.map { it.ruleId })
        assertTrue(items[0].isOverdue)
        assertFalse(items[1].isOverdue)
        assertEquals(d(2026, 11, 5), items[0].date)
    }

    @Test fun theWindowIsSevenDaysInclusive() {
        val onTheEdge = rule(1, RecurringKind.BILL, 100, d(2026, 11, 18), day = 18)
        val past = rule(2, RecurringKind.BILL, 100, d(2026, 11, 19), day = 19)
        val items = RecurringPlanner.dueItems(listOf(onTheEdge, past), today)
        assertEquals(listOf(1L), items.map { it.ruleId })
    }

    @Test fun dueTodayIsNotOverdue() {
        val items = RecurringPlanner.dueItems(listOf(rule(1, RecurringKind.BILL, 100, today)), today)
        assertEquals(1, items.size)
        assertFalse(items[0].isOverdue)
    }

    @Test fun missedOccurrencesAllShowAsOverdueAndOnlyTheOldestIsNext() {
        // never opened since September: Sep, Oct and Nov of the 5th are all pending
        val r = rule(1, RecurringKind.BILL, 10_000, d(2026, 1, 5), done = 8, day = 5)
        val items = RecurringPlanner.dueItems(listOf(r), today)
        assertEquals(listOf(d(2026, 9, 5), d(2026, 10, 5), d(2026, 11, 5)), items.map { it.date })
        assertTrue(items.all { it.isOverdue })
        assertEquals(listOf(true, false, false), items.map { it.isNext })
        assertEquals(listOf(8, 9, 10), items.map { it.index })
    }

    @Test fun confirmedOccurrencesDoNotShowAgain() {
        // done = 11 means Jan..Nov are done; the next one is 5 Dec (outside the window)
        val r = rule(1, RecurringKind.BILL, 10_000, d(2026, 1, 5), done = 11, day = 5)
        assertTrue(RecurringPlanner.dueItems(listOf(r), today).isEmpty())
    }

    @Test fun inactiveAndFinishedRulesAreNotDue() {
        val inactive = rule(1, RecurringKind.BILL, 100, d(2026, 11, 5), day = 5, active = false)
        val finished = rule(2, RecurringKind.BILL, 100, d(2026, 11, 5), day = 5, done = 1, max = 1)
        assertTrue(RecurringPlanner.dueItems(listOf(inactive, finished), today).isEmpty())
    }

    @Test fun dueItemsAreSortedByDate() {
        val a = rule(1, RecurringKind.BILL, 100, d(2026, 11, 14), day = 14)
        val b = rule(2, RecurringKind.INCOME, 100, d(2026, 11, 9), day = 9)
        val c = rule(3, RecurringKind.DEBT_PAYMENT, 100, d(2026, 11, 14), day = 14)
        assertEquals(listOf(2L, 1L, 3L), RecurringPlanner.dueItems(listOf(a, b, c), today).map { it.ruleId })
    }

    // ---- stillDue (safe to spend) ----

    @Test fun stillDueSumsBillsSubscriptionsAndIncomeFromTodayToPeriodEnd() {
        val periodEnd = d(2026, 11, 30)
        val bill = rule(1, RecurringKind.BILL, 8_000, d(2026, 1, 20), done = 10, day = 20) // 20 Nov
        val sub = rule(2, RecurringKind.SUBSCRIPTION, 1_299, d(2026, 1, 11), done = 10, day = 11) // today
        val salary = rule(3, RecurringKind.INCOME, 200_000, d(2026, 1, 27), done = 10, day = 27)
        val december = rule(4, RecurringKind.BILL, 5_000, d(2026, 1, 1), done = 11, day = 1) // 1 Dec: after the period
        val due = RecurringPlanner.stillDue(listOf(bill, sub, salary, december), today, periodEnd)
        assertEquals(9_299L, due.billsCents)
        assertEquals(200_000L, due.incomeCents)
    }

    @Test fun debtPaymentRulesAreNeverInBillsStillDue() {
        val debt = rule(1, RecurringKind.DEBT_PAYMENT, 25_000, d(2026, 1, 20), done = 10, day = 20)
        val due = RecurringPlanner.stillDue(listOf(debt), today, d(2026, 11, 30))
        assertEquals(0L, due.billsCents)
        assertEquals(0L, due.incomeCents)
    }

    @Test fun aConfirmedBillIsNoLongerStillDue() {
        val periodEnd = d(2026, 11, 30)
        val before = rule(1, RecurringKind.BILL, 8_000, d(2026, 1, 20), done = 10, day = 20)
        val after = before.copy(occurrencesDone = 11)
        assertEquals(8_000L, RecurringPlanner.stillDue(listOf(before), today, periodEnd).billsCents)
        assertEquals(0L, RecurringPlanner.stillDue(listOf(after), today, periodEnd).billsCents)
    }

    @Test fun overdueBillsStillCountButOverdueIncomeDoesNot() {
        val overdue = rule(1, RecurringKind.BILL, 8_000, d(2026, 1, 5), done = 10, day = 5) // 5 Nov, before today
        assertEquals(8_000L, RecurringPlanner.stillDue(listOf(overdue), today, d(2026, 11, 30)).billsCents)
        val overdueIncome = rule(2, RecurringKind.INCOME, 150_000, d(2026, 1, 5), done = 10, day = 5)
        assertEquals(0L, RecurringPlanner.stillDue(listOf(overdueIncome), today, d(2026, 11, 30)).incomeCents)
    }

    @Test fun weeklyBillsCountEveryOccurrenceInThePeriod() {
        // every Friday from 13 Nov: 13, 20, 27 Nov
        val weekly = rule(1, RecurringKind.BILL, 1_000, d(2026, 11, 13), unit = RecurrenceUnit.WEEK)
        assertEquals(3_000L, RecurringPlanner.stillDue(listOf(weekly), today, d(2026, 11, 30)).billsCents)
    }

    @Test fun inactiveRulesAreIgnoredByStillDue() {
        val off = rule(1, RecurringKind.BILL, 8_000, d(2026, 11, 20), day = 20, active = false)
        assertEquals(0L, RecurringPlanner.stillDue(listOf(off), today, d(2026, 11, 30)).billsCents)
    }

    // ---- reminders ----

    @Test fun reminderListsOnlyTomorrowsItemsWithTheReminderOn() {
        val tomorrowOn = rule(1, RecurringKind.BILL, 100, d(2026, 11, 12), day = 12, remind = true)
        val tomorrowOff = rule(2, RecurringKind.BILL, 100, d(2026, 11, 12), day = 12, remind = false)
        val later = rule(3, RecurringKind.BILL, 100, d(2026, 11, 15), day = 15, remind = true)
        val summary = RecurringPlanner.reminders(listOf(tomorrowOn, tomorrowOff, later), today)
        assertEquals(listOf(1L), summary.tomorrow.map { it.ruleId })
        assertEquals(0, summary.overdueCount)
        assertFalse(summary.isEmpty)
    }

    @Test fun reminderCountsOverdueItems() {
        val overdue = rule(1, RecurringKind.BILL, 100, d(2026, 1, 5), done = 8, day = 5) // Sep, Oct, Nov overdue
        val tomorrow = rule(2, RecurringKind.SUBSCRIPTION, 100, d(2026, 11, 12), day = 12, remind = true)
        val summary = RecurringPlanner.reminders(listOf(overdue, tomorrow), today)
        assertEquals(3, summary.overdueCount)
        assertEquals(1, summary.tomorrow.size)
    }

    @Test fun nothingTomorrowIsAnEmptyReminder() {
        val r = rule(1, RecurringKind.BILL, 100, d(2026, 11, 20), day = 20, remind = true)
        assertTrue(RecurringPlanner.reminders(listOf(r), today).isEmpty)
    }

    // ---- totals ----

    @Test fun outgoingTotalsNormaliseEveryFrequency() {
        val rent = rule(1, RecurringKind.BILL, 50_000, d(2026, 1, 5)) // monthly
        val insurance = rule(2, RecurringKind.BILL, 12_000, d(2026, 1, 5), unit = RecurrenceUnit.YEAR) // yearly 120
        val weekly = rule(3, RecurringKind.SUBSCRIPTION, 1_000, d(2026, 1, 5), unit = RecurrenceUnit.WEEK)
        val salary = rule(4, RecurringKind.INCOME, 200_000, d(2026, 1, 27)) // not outgoing
        val off = rule(5, RecurringKind.BILL, 99_999, d(2026, 1, 5), active = false)
        val debt = rule(6, RecurringKind.DEBT_PAYMENT, 25_000, d(2026, 1, 15))
        val all = listOf(rent, insurance, weekly, salary, off, debt)
        assertEquals(50_000L + 1_000L + 4_333L + 25_000L, RecurringPlanner.monthlyOutgoingCents(all))
        assertEquals(600_000L + 12_000L + 52_000L + 300_000L, RecurringPlanner.yearlyOutgoingCents(all))
    }
}
