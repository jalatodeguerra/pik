package com.pik.app.domain.importing

import com.pik.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportMatcherTest {

    private val bank = AccountRef(1, "Relax Banking", true)
    private val cash = AccountRef(2, "Cash", false)
    private val paypal = AccountRef(3, "PayPal", false)
    private val revolut = AccountRef(4, "Revolut", true)
    private val accounts = listOf(bank, cash, paypal, revolut)

    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    private var nextId = 1L

    private fun item(
        id: Long = nextId++,
        source: ImportSource = ImportSource.RELAX,
        account: Long? = 1,
        direction: ImportDirection = ImportDirection.OUT,
        cents: Long = 1250,
        merchant: String = "CONAD",
        raw: String = "",
        date: LocalDate = d(10),
        status: ImportStatus = ImportStatus.PENDING,
        suggestion: ImportSuggestion = ImportSuggestion.EXPENSE,
        transfer: Long? = null,
        category: Long? = null,
        restored: Boolean = false,
    ) = ItemRef(id, source, account, direction, cents, merchant, raw, date, status, suggestion, transfer, category, restored)

    private fun tx(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        cents: Long = 1250,
        date: LocalDate = d(10),
        account: Long = 1,
        to: Long? = null,
    ) = TxRef(id, type, cents, date, account, to)

    private fun ctx(
        transactions: List<TxRef> = emptyList(),
        linked: Set<Long> = emptySet(),
        others: List<ItemRef> = emptyList(),
        rules: Map<String, Long> = emptyMap(),
        bankId: Long? = 1,
    ) = MatchContext(accounts, transactions, linked, others, rules, bankId)

    private fun match(items: List<ItemRef>, context: MatchContext = ctx(others = items)) =
        ImportMatcher.matchPending(items, context)

    private fun one(item: ItemRef, context: MatchContext? = null): MatchResult =
        match(listOf(item), context ?: ctx(others = listOf(item))).single()

    // ---- plain items ----

    @Test fun anExpenseStaysPending() {
        val r = one(item())
        assertEquals(ImportStatus.PENDING, r.status)
        assertEquals(ImportSuggestion.EXPENSE, r.suggestion)
        assertNull(r.transferAccountId)
        assertNull(r.suggestedCategoryId)
    }

    @Test fun incomeSuggestion() {
        val r = one(item(direction = ImportDirection.IN, merchant = "ACME SRL"))
        assertEquals(ImportSuggestion.INCOME, r.suggestion)
    }

    // ---- duplicates of transactions ----

    @Test fun sameAmountAccountAndDayIsADuplicate() {
        val i = item()
        val r = one(i, ctx(transactions = listOf(tx(10)), others = listOf(i)))
        assertEquals(ImportStatus.DUPLICATE, r.status)
    }

    @Test fun threeDaysApartIsStillADuplicateFourIsNot() {
        val a = item(date = d(10))
        assertEquals(ImportStatus.DUPLICATE, one(a, ctx(transactions = listOf(tx(10, date = d(13))), others = listOf(a))).status)
        assertEquals(ImportStatus.DUPLICATE, one(a, ctx(transactions = listOf(tx(10, date = d(7))), others = listOf(a))).status)
        assertEquals(ImportStatus.PENDING, one(a, ctx(transactions = listOf(tx(10, date = d(14))), others = listOf(a))).status)
    }

    @Test fun differentAmountOrAccountOrDirectionIsNotADuplicate() {
        val a = item()
        fun status(t: TxRef) = one(a, ctx(transactions = listOf(t), others = listOf(a))).status
        assertEquals(ImportStatus.PENDING, status(tx(10, cents = 1251)))
        assertEquals(ImportStatus.PENDING, status(tx(10, account = 2)))
        assertEquals(ImportStatus.PENDING, status(tx(10, type = TransactionType.INCOME)))
    }

    @Test fun incomeMatchesIncome() {
        val a = item(direction = ImportDirection.IN)
        val r = one(a, ctx(transactions = listOf(tx(10, type = TransactionType.INCOME)), others = listOf(a)))
        assertEquals(ImportStatus.DUPLICATE, r.status)
    }

    @Test fun anExistingTransferMatchesItsBothSides() {
        val out = item(account = 1)
        val into = item(account = 3, direction = ImportDirection.IN, merchant = "Bank")
        val transfer = tx(10, type = TransactionType.TRANSFER, account = 1, to = 3)
        assertEquals(ImportStatus.DUPLICATE, one(out, ctx(transactions = listOf(transfer), others = listOf(out))).status)
        assertEquals(ImportStatus.DUPLICATE, one(into, ctx(transactions = listOf(transfer), others = listOf(into))).status)
    }

    @Test fun balanceAdjustmentsMatchBySign() {
        val out = item()
        val adjMinus = tx(10, type = TransactionType.ADJUSTMENT, cents = -1250)
        val adjPlus = tx(11, type = TransactionType.ADJUSTMENT, cents = 1250)
        assertEquals(ImportStatus.DUPLICATE, one(out, ctx(transactions = listOf(adjMinus), others = listOf(out))).status)
        assertEquals(ImportStatus.PENDING, one(out, ctx(transactions = listOf(adjPlus), others = listOf(out))).status)
    }

    @Test fun aTransactionAlreadyLinkedToAnItemDoesNotCount() {
        val a = item()
        val r = one(a, ctx(transactions = listOf(tx(10)), linked = setOf(10), others = listOf(a)))
        assertEquals(ImportStatus.PENDING, r.status)
    }

    @Test fun oneTransactionCoversOnlyOneItem() {
        val a = item(id = 1)
        val b = item(id = 2)
        val results = match(listOf(a, b), ctx(transactions = listOf(tx(10)), others = listOf(a, b)))
        assertEquals(listOf(ImportStatus.DUPLICATE, ImportStatus.PENDING), results.map { it.status })
    }

    @Test fun anItemWithoutAnAccountIsNeverADuplicate() {
        val a = item(account = null)
        assertEquals(ImportStatus.PENDING, one(a, ctx(transactions = listOf(tx(10)), others = listOf(a))).status)
    }

    @Test fun aRestoredItemIsNotCheckedAgain() {
        val a = item(restored = true)
        assertEquals(ImportStatus.PENDING, one(a, ctx(transactions = listOf(tx(10)), others = listOf(a))).status)
    }

    // ---- duplicates between items ----

    @Test fun aFileRowAfterANotificationIsADuplicate() {
        val notification = item(id = 1, source = ImportSource.RELAX)
        val file = item(id = 2, source = ImportSource.FILE, date = d(11))
        val results = match(listOf(notification, file), ctx(others = listOf(notification, file)))
        assertEquals(listOf(ImportStatus.PENDING, ImportStatus.DUPLICATE), results.map { it.status })
    }

    @Test fun anAddedNotificationAlsoCoversALaterFileRow() {
        val added = item(id = 1, source = ImportSource.RELAX, status = ImportStatus.ADDED)
        val file = item(id = 2, source = ImportSource.FILE)
        assertEquals(ImportStatus.DUPLICATE, one(file, ctx(others = listOf(added, file))).status)
    }

    @Test fun aDismissedItemDoesNotCover() {
        val dismissed = item(id = 1, source = ImportSource.RELAX, status = ImportStatus.DISMISSED)
        val file = item(id = 2, source = ImportSource.FILE)
        assertEquals(ImportStatus.PENDING, one(file, ctx(others = listOf(dismissed, file))).status)
    }

    @Test fun twoNotificationsWithTheSameAmountAreTwoPayments() {
        val a = item(id = 1, source = ImportSource.RELAX)
        val b = item(id = 2, source = ImportSource.RELAX)
        assertEquals(listOf(ImportStatus.PENDING, ImportStatus.PENDING), match(listOf(a, b)).map { it.status })
    }

    @Test fun twoIdenticalFileRowsAreTwoPayments() {
        val a = item(id = 1, source = ImportSource.FILE)
        val b = item(id = 2, source = ImportSource.FILE)
        assertEquals(listOf(ImportStatus.PENDING, ImportStatus.PENDING), match(listOf(a, b)).map { it.status })
    }

    @Test fun itemsOnOtherAccountsDoNotCover() {
        val notification = item(id = 1, source = ImportSource.RELAX, account = 1)
        val file = item(id = 2, source = ImportSource.FILE, account = 2)
        assertEquals(ImportStatus.PENDING, one(file, ctx(others = listOf(notification, file))).status)
    }

    // ---- transfers ----

    @Test fun bankPaymentToPaypalIsATransferToPaypal() {
        val r = one(item(merchant = "PAYPAL *AMAZON EU"))
        assertEquals(ImportSuggestion.TRANSFER_OUT, r.suggestion)
        assertEquals(3L, r.transferAccountId)
    }

    @Test fun bankPaymentToRevolutIsATransferToRevolut() {
        val r = one(item(merchant = "Revolut Top-up"))
        assertEquals(ImportSuggestion.TRANSFER_OUT, r.suggestion)
        assertEquals(4L, r.transferAccountId)
    }

    @Test fun noPaypalAccountMeansAnOrdinaryExpense() {
        val i = item(merchant = "PAYPAL *AMAZON")
        val context = MatchContext(listOf(bank, cash), emptyList(), emptySet(), listOf(i), emptyMap(), 1)
        assertEquals(ImportSuggestion.EXPENSE, ImportMatcher.matchPending(listOf(i), context).single().suggestion)
    }

    @Test fun anExpenseOnPaypalItselfIsNotATransferToPaypal() {
        val r = one(item(account = 3, source = ImportSource.PAYPAL, merchant = "PayPal fee"))
        assertEquals(ImportSuggestion.EXPENSE, r.suggestion)
    }

    @Test fun walletTopUpIsATransferFromTheBank() {
        val r = one(
            item(
                account = 4, source = ImportSource.REVOLUT, direction = ImportDirection.IN,
                merchant = "Top-Up by *1234", raw = "Top-Up by *1234 | TOPUP",
            ),
        )
        assertEquals(ImportSuggestion.TRANSFER_IN, r.suggestion)
        assertEquals(1L, r.transferAccountId)
    }

    @Test fun moneyReceivedFromAPersonIsPlainIncome() {
        val r = one(item(account = 4, source = ImportSource.REVOLUT, direction = ImportDirection.IN, merchant = "Mario"))
        assertEquals(ImportSuggestion.INCOME, r.suggestion)
    }

    @Test fun fundingWithoutABankAccountStaysIncome() {
        val i = item(account = 3, direction = ImportDirection.IN, merchant = "General Card Deposit")
        val context = MatchContext(accounts, emptyList(), emptySet(), listOf(i), emptyMap(), null)
        assertEquals(ImportSuggestion.INCOME, ImportMatcher.matchPending(listOf(i), context).single().suggestion)
    }

    @Test fun theIncomingLegIsADuplicateOfTheOutgoingLeg() {
        val out = item(id = 1, source = ImportSource.RELAX, merchant = "PAYPAL *AMAZON", cents = 2000, date = d(11))
        val into = item(
            id = 2, source = ImportSource.FILE, account = 3, direction = ImportDirection.IN, cents = 2000,
            merchant = "General Card Deposit", date = d(11),
        )
        val results = match(listOf(out, into), ctx(others = listOf(out, into)))
        assertEquals(ImportSuggestion.TRANSFER_OUT, results[0].suggestion)
        assertEquals(ImportStatus.PENDING, results[0].status)
        assertEquals(ImportSuggestion.TRANSFER_IN, results[1].suggestion)
        assertEquals(ImportStatus.DUPLICATE, results[1].status)
    }

    @Test fun theLegsFindEachOtherWhateverTheOrder() {
        val into = item(
            id = 1, source = ImportSource.FILE, account = 3, direction = ImportDirection.IN, cents = 2000,
            merchant = "General Card Deposit", date = d(11),
        )
        val out = item(id = 2, source = ImportSource.RELAX, merchant = "PAYPAL *AMAZON", cents = 2000, date = d(12))
        val results = match(listOf(into, out), ctx(others = listOf(into, out)))
        assertEquals(ImportStatus.DUPLICATE, results[0].status)
        assertEquals(ImportStatus.PENDING, results[1].status)
    }

    @Test fun anIncomingLegWithoutAnOutgoingLegStaysPending() {
        val into = item(
            id = 1, source = ImportSource.FILE, account = 3, direction = ImportDirection.IN, cents = 2000,
            merchant = "General Card Deposit",
        )
        assertEquals(ImportStatus.PENDING, one(into).status)
    }

    // ---- categories ----

    @Test fun merchantRulesSuggestACategory() {
        val r = one(item(merchant = "CONAD SUPERSTORE 123"), ctx(rules = mapOf("CONAD SUPERSTORE" to 7L)))
        assertEquals(7L, r.suggestedCategoryId)
    }

    @Test fun aCategoryChosenByTheUserIsKept() {
        val i = item(merchant = "CONAD", category = 9)
        assertEquals(9L, one(i, ctx(rules = mapOf("CONAD" to 7L), others = listOf(i))).suggestedCategoryId)
    }

    @Test fun transfersGetNoCategory() {
        val r = one(item(merchant = "PAYPAL *CONAD"), ctx(rules = mapOf("CONAD" to 7L)))
        assertNull(r.suggestedCategoryId)
    }

    @Test fun anUnknownMerchantHasNoCategory() {
        assertNull(one(item(merchant = "")).suggestedCategoryId)
    }

    // ---- helpers ----

    @Test fun pickBankAccountPrefersTheMappedOne() {
        val second = AccountRef(5, "Other bank", true)
        val list = listOf(revolut, bank, second)
        assertEquals(5L, ImportMatcher.pickBankAccount(list, listOf(99, 5)))
        assertEquals(1L, ImportMatcher.pickBankAccount(list, emptyList())) // revolut is a wallet
        assertNull(ImportMatcher.pickBankAccount(listOf(cash, paypal), emptyList()))
    }

    @Test fun defaultAccountsBySource() {
        assertEquals(3L, ImportMatcher.defaultAccountFor(ImportSource.PAYPAL, accounts))
        assertEquals(4L, ImportMatcher.defaultAccountFor(ImportSource.REVOLUT, accounts))
        assertEquals(1L, ImportMatcher.defaultAccountFor(ImportSource.RELAX, accounts))
        assertEquals(1L, ImportMatcher.defaultAccountFor(ImportSource.NEXI, accounts))
        assertNull(ImportMatcher.defaultAccountFor(ImportSource.OTHER_APP, accounts))
        assertTrue(ImportMatcher.isWalletName("My PayPal"))
    }
}
