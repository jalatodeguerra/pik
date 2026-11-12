package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtBalanceTest {
    private fun figures(start: Long, original: Long? = null, entries: Long = 0, payments: Long = 0) =
        DebtFigures(start, original, entries, payments)

    @Test fun remainingIsStartPlusEntriesMinusPayments() {
        val f = figures(100_000, entries = 2_500 - 300, payments = 40_000)
        assertEquals(62_200L, DebtCalculator.remaining(f))
    }

    @Test fun overPaidShowsZeroButKeepsRawNegative() {
        val f = figures(10_000, payments = 12_000)
        assertEquals(-2_000L, DebtCalculator.rawRemaining(f))
        assertEquals(0L, DebtCalculator.remaining(f))
        val p = DebtCalculator.progress(f)
        assertTrue(p.paidOff)
        assertEquals(1f, p.fraction, 0f)
    }

    @Test fun progressWithoutOriginalUsesStartingBalance() {
        val p = DebtCalculator.progress(figures(100_000, payments = 25_000))
        assertEquals(25_000L, p.paidCents)
        assertEquals(75_000L, p.remainingCents)
        assertEquals(0.25f, p.fraction, 0.0001f)
        assertFalse(p.paidOff)
    }

    @Test fun originalAmountCountsWhatWasPaidBeforePik() {
        // 10.000 loan, 6.000 left when added, 1.000 paid in Pik: 5.000 paid so far of 10.000
        val p = DebtCalculator.progress(figures(600_000, original = 1_000_000, payments = 100_000))
        assertEquals(500_000L, p.paidCents)
        assertEquals(500_000L, p.remainingCents)
        assertEquals(0.5f, p.fraction, 0.0001f)
    }

    @Test fun originalSmallerThanStartingIsIgnored() {
        assertEquals(0L, DebtCalculator.priorPaid(figures(600_000, original = 500_000)))
    }

    @Test fun interestRaisesRemainingAndTotal() {
        // 1.000 owed, +50 interest, 300 paid: paid 300, remaining 750
        val p = DebtCalculator.progress(figures(100_000, entries = 5_000, payments = 30_000))
        assertEquals(30_000L, p.paidCents)
        assertEquals(75_000L, p.remainingCents)
        assertEquals(30_000f / 105_000f, p.fraction, 0.0001f)
    }

    @Test fun nothingAtAllIsZeroProgressNotPaidOff() {
        val p = DebtCalculator.progress(figures(0))
        assertEquals(0f, p.fraction, 0f)
        assertFalse(p.paidOff)
    }

    @Test fun overallSumsPaidAndRemaining() {
        val a = DebtCalculator.progress(figures(100_000, payments = 50_000)) // 500 paid, 500 left
        val b = DebtCalculator.progress(figures(300_000)) // 0 paid, 3.000 left
        val all = DebtCalculator.overall(listOf(a, b))
        assertEquals(50_000L, all.paidCents)
        assertEquals(350_000L, all.remainingCents)
        assertEquals(50_000f / 400_000f, all.fraction, 0.0001f)
        assertEquals(0f, DebtCalculator.overall(emptyList()).fraction, 0f)
    }

    @Test fun balanceCheckIsSignedDifference() {
        assertEquals(1_250L, DebtBalanceCheck.adjustmentCents(realCents = 51_250, rawRemainingCents = 50_000))
        assertEquals(-3_000L, DebtBalanceCheck.adjustmentCents(realCents = 47_000, rawRemainingCents = 50_000))
        assertEquals(0L, DebtBalanceCheck.adjustmentCents(50_000, 50_000))
        // over-paid: calculated -20,00, bank says 0 -> +20,00 makes the calculation 0
        assertEquals(2_000L, DebtBalanceCheck.adjustmentCents(0, -2_000))
    }

    @Test fun historyIsNewestFirstAndMergesBothKinds() {
        val pay1 = DebtHistoryItem.Payment(1, LocalDate.of(2026, 11, 1), 10, 5_000, "Bank", "")
        val pay2 = DebtHistoryItem.Payment(2, LocalDate.of(2026, 11, 20), 11, 5_000, "Bank", "")
        val fee = DebtHistoryItem.Entry(9, LocalDate.of(2026, 11, 10), 12, DebtEntryType.FEE, 500, "")
        val sameDayEarlier = DebtHistoryItem.Entry(8, LocalDate.of(2026, 11, 20), 5, DebtEntryType.INTEREST, 300, "")
        val merged = DebtHistory.merge(listOf(pay1, pay2), listOf(fee, sameDayEarlier))
        assertEquals(listOf(pay2, sameDayEarlier, fee, pay1), merged)
    }

    // ---- APR text ----

    @Test fun aprParsesItalianInput() {
        assertEquals(1250, Apr.parse("12,5"))
        assertEquals(1250, Apr.parse("12.5"))
        assertEquals(1200, Apr.parse("12"))
        assertEquals(1225, Apr.parse(" 12,25 % "))
        assertEquals(0, Apr.parse("0"))
        assertEquals(5, Apr.parse("0,05"))
        assertEquals(10_000, Apr.parse("100"))
        assertEquals(50, Apr.parse(",5"))
    }

    @Test fun aprRejectsGarbage() {
        assertNull(Apr.parse(""))
        assertNull(Apr.parse("abc"))
        assertNull(Apr.parse("-3"))
        assertNull(Apr.parse("12,345"))
        assertNull(Apr.parse("1,2,3"))
        assertNull(Apr.parse("100,01"))
        assertNull(Apr.parse("1000"))
        assertNull(Apr.parse(","))
    }

    @Test fun aprFormatsShortest() {
        assertEquals("12", Apr.formatPlain(1200))
        assertEquals("12,5", Apr.formatPlain(1250))
        assertEquals("12,25", Apr.formatPlain(1225))
        assertEquals("0,05", Apr.formatPlain(5))
        assertEquals("0", Apr.formatPlain(0))
        assertEquals("12,5%", Apr.format(1250))
        for (bp in listOf(0, 5, 50, 1250, 1225, 10_000)) assertEquals(bp, Apr.parse(Apr.formatPlain(bp)))
    }

    // ---- debt payments due (safe to spend) ----

    private fun due(id: Long, remaining: Long, min: Long, paid: Long) = DebtDueFacts(id, remaining, min, paid)

    @Test fun unpaidMinimumsAreDue() {
        val total = DebtPaymentsDue.compute(listOf(due(1, 100_000, 5_000, 0), due(2, 50_000, 2_500, 0)), 0)
        assertEquals(7_500L, total)
    }

    @Test fun paidMinimumIsNoLongerDue() {
        val total = DebtPaymentsDue.compute(listOf(due(1, 95_000, 5_000, 5_000), due(2, 50_000, 2_500, 1_000)), 0)
        assertEquals(1_500L, total)
    }

    @Test fun minimumIsCappedAtWhatIsStillOwed() {
        assertEquals(1_000L, DebtPaymentsDue.compute(listOf(due(1, 1_000, 5_000, 0)), 0))
    }

    @Test fun paidOffDebtsDueNothing() {
        assertEquals(0L, DebtPaymentsDue.compute(listOf(due(1, 0, 5_000, 0)), 10_000))
    }

    @Test fun extraIsDueUntilPaidBeyondMinimums() {
        val debts = listOf(due(1, 100_000, 5_000, 0))
        assertEquals(5_000L + 20_000L, DebtPaymentsDue.compute(debts, 20_000))
        // 5.000 minimum and 8.000 extra already paid: 12.000 of the extra is still due
        val partly = listOf(due(1, 87_000, 5_000, 13_000))
        assertEquals(12_000L, DebtPaymentsDue.compute(partly, 20_000))
        // extra fully paid
        val full = listOf(due(1, 75_000, 5_000, 25_000))
        assertEquals(0L, DebtPaymentsDue.compute(full, 20_000))
        val over = listOf(due(1, 70_000, 5_000, 30_000))
        assertEquals(0L, DebtPaymentsDue.compute(over, 20_000))
    }

    @Test fun extraPaidOnAnotherDebtCountsAndMinimumsStayPerDebt() {
        // debt 1: minimum 50 paid, 100 extra paid; debt 2: minimum 25 not paid yet. Extra 150/month.
        val debts = listOf(due(1, 80_000, 5_000, 15_000), due(2, 40_000, 2_500, 0))
        // minDue = 0 + 25,00; extraDue = 150 - 100 = 50,00
        assertEquals(2_500L + 5_000L, DebtPaymentsDue.compute(debts, 15_000))
    }

    @Test fun dueIsBuiltFromThePeriodsTransactions() {
        val debts = listOf(
            DebtPaymentsDue.Input(1, 95_000, 5_000),
            DebtPaymentsDue.Input(2, 50_000, 2_500),
        )
        val txs = listOf(
            TxFacts(TransactionType.EXPENSE, 5_000, 1, null, null, debtId = 1),
            TxFacts(TransactionType.EXPENSE, 1_000, 1, null, null, debtId = 2),
            TxFacts(TransactionType.EXPENSE, 9_999, 1, null, 3), // normal expense: not a debt payment
            TxFacts(TransactionType.INCOME, 9_999, 1, null, null, debtId = 2), // not a payment
            TxFacts(TransactionType.EXPENSE, 7_000, 1, null, null, debtId = 77), // archived/unknown debt
        )
        // debt 1: nothing due; debt 2: 25,00 - 10,00 = 15,00
        assertEquals(1_500L, DebtPaymentsDue.compute(debts, txs, 0))
        // with 100,00 extra: nothing paid beyond minimums yet
        assertEquals(1_500L + 10_000L, DebtPaymentsDue.compute(debts, txs, 10_000))
    }

    @Test fun noDebtsNothingDue() {
        assertEquals(0L, DebtPaymentsDue.compute(emptyList(), 10_000))
    }

    @Test fun debtDueLowersSafeToSpendOnce() {
        // income 2.000, spent 300 (of which 50 already paid to the debt), minimum 50 -> nothing more due
        val dueNow = DebtPaymentsDue.compute(listOf(due(1, 95_000, 5_000, 5_000)), 0)
        val safe = SafeToSpendCalculator.compute(200_000, 0, 30_000, 10, debtPaymentsDueCents = dueNow)
        assertEquals(170_000L, safe.safeCents)
        // before paying: spent 250, 50 still due -> same safe amount
        val before = SafeToSpendCalculator.compute(
            200_000, 0, 25_000, 10,
            debtPaymentsDueCents = DebtPaymentsDue.compute(listOf(due(1, 100_000, 5_000, 0)), 0),
        )
        assertEquals(170_000L, before.safeCents)
    }
}
