package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtType
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.TransactionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupJsonTest {

    private fun sample(): BackupFile = BackupFile(
        formatVersion = 4,
        appVersion = "0.4.0",
        exportedAt = "2026-11-10T09:30:00Z",
        accounts = listOf(
            BackupAccount(1, "Cash", AccountType.CASH, 12_345, false, 0, 1_700_000_000_000),
            BackupAccount(5, "Bank \"Main\" / è", AccountType.BANK, -2_000, true, 1, 1_700_000_000_001),
        ),
        categories = listOf(
            BackupCategory(3, "Groceries", CategoryKind.EXPENSE, "mint", "cart", false, 0),
            BackupCategory(9, "Salary", CategoryKind.INCOME, "lime", "salary", true, 4),
        ),
        transactions = listOf(
            BackupTransaction(
                2, TransactionType.EXPENSE, 1_250, LocalDate.of(2026, 11, 3), 1, null, 3,
                "Pizza\nwith \"friends\" \\ 🍕", null, null, 10, 11,
            ),
            BackupTransaction(
                4, TransactionType.TRANSFER, 5_000, LocalDate.of(2026, 1, 31), 1, 5, null,
                "", null, 88, 12, 13,
            ),
            BackupTransaction(
                6, TransactionType.EXPENSE, 10_000, LocalDate.of(2026, 11, 5), 5, null, null,
                "Rata", 2, null, 16, 17,
            ),
            BackupTransaction(
                8, TransactionType.ADJUSTMENT, -321, LocalDate.of(2026, 11, 10), 5, null, null,
                "Balance check", null, null, 14, 15,
            ),
        ),
        budgets = listOf(
            BackupBudget(1, null, 200_000, "2026-01", 20),
            BackupBudget(2, 3, 30_000, "2026-01", 21),
            BackupBudget(3, 3, 0, "2026-11", 22),
        ),
        debts = listOf(
            BackupDebt(
                1, "Car \"loan\"", 1_000_000, 1_500_000, 599, 25_000, 15, false, 0, 30,
                taegBasisPoints = 712, type = DebtType.CAR_LOAN, totalInstallments = 60, installmentsPaidBefore = 18,
            ),
            BackupDebt(2, "Card è", 0, null, 0, 0, 28, true, 1, 31),
        ),
        debtEntries = listOf(
            BackupDebtEntry(1, 1, DebtEntryType.INTEREST, 4_990, LocalDate.of(2026, 10, 15), "Oct interest", 40),
            BackupDebtEntry(2, 1, DebtEntryType.ADJUSTMENT, -120, LocalDate.of(2026, 11, 10), "Balance check", 41),
            BackupDebtEntry(3, 2, DebtEntryType.FEE, 500, LocalDate.of(2026, 11, 1), "", 42),
        ),
        settings = BackupSettings(27, 250_000, 5_000, PayoffOrder.SMALLEST_BALANCE),
    )

    private fun errorOf(text: String): BackupError {
        try {
            BackupJson.parse(text)
        } catch (e: BackupFormatException) {
            return e.error
        }
        fail("expected a BackupFormatException")
        throw IllegalStateException()
    }

    @Test
    fun roundTripKeepsEverything() {
        val original = sample()
        val parsed = BackupJson.parse(BackupJson.serialize(original))
        assertEquals(original, parsed)
    }

    @Test
    fun emptyCategoriesAndTransactionsRoundTrip() {
        val original = sample().copy(
            categories = emptyList(), transactions = emptyList(), budgets = emptyList(),
            debts = emptyList(), debtEntries = emptyList(),
        )
        assertEquals(original, BackupJson.parse(BackupJson.serialize(original)))
    }

    @Test
    fun datesAreIsoAndHeaderIsPresent() {
        val root = JSONObject(BackupJson.serialize(sample()))
        assertEquals(4, root.getInt("formatVersion"))
        assertEquals("0.4.0", root.getString("appVersion"))
        assertEquals("2026-11-10T09:30:00Z", root.getString("exportedAt"))
        val tx = root.getJSONArray("transactions").getJSONObject(0)
        assertEquals("2026-11-03", tx.getString("date"))
        assertTrue(root.getJSONArray("transactions").getJSONObject(1).isNull("categoryId"))
        val budgets = root.getJSONArray("budgets")
        assertEquals(3, budgets.length())
        assertTrue(budgets.getJSONObject(0).isNull("categoryId"))
        assertEquals("2026-01", budgets.getJSONObject(1).getString("startPeriod"))
        val debts = root.getJSONArray("debts")
        assertEquals(2, debts.length())
        assertTrue(debts.getJSONObject(1).isNull("originalAmountCents"))
        assertEquals(599, debts.getJSONObject(0).getInt("aprBasisPoints"))
        assertEquals("2026-10-15", root.getJSONArray("debtEntries").getJSONObject(0).getString("date"))
        val settings = root.getJSONObject("settings")
        assertEquals(27, settings.getInt("monthStartDay"))
        assertEquals("SMALLEST_BALANCE", settings.getString("payoffOrder"))
    }

    @Test
    fun settingsAreOptional() {
        val without = sample().copy(settings = null)
        val text = BackupJson.serialize(without)
        assertTrue(!JSONObject(text).has("settings"))
        assertNull(BackupJson.parse(text).settings)
        assertEquals(without, BackupJson.parse(text))
    }

    @Test
    fun settingsWithoutExpectedIncomeRoundTrip() {
        val file = sample().copy(settings = BackupSettings(1, null, 0, PayoffOrder.HIGHEST_INTEREST))
        assertEquals(file, BackupJson.parse(BackupJson.serialize(file)))
    }

    @Test
    fun settingsMissingKeysFallBackToDefaults() {
        val root = JSONObject(BackupJson.serialize(sample()))
        root.put("settings", JSONObject().put("monthStartDay", 5))
        val parsed = BackupJson.parse(root.toString())
        assertEquals(BackupSettings(5, null, 0, PayoffOrder.HIGHEST_INTEREST), parsed.settings)
    }

    @Test
    fun rejectsBadSettings() {
        val s = sample()
        for (bad in listOf(
            BackupSettings(0, null, 0, PayoffOrder.HIGHEST_INTEREST),
            BackupSettings(29, null, 0, PayoffOrder.HIGHEST_INTEREST),
            BackupSettings(1, null, -1, PayoffOrder.HIGHEST_INTEREST),
            BackupSettings(1, -5, 0, PayoffOrder.HIGHEST_INTEREST),
        )) {
            assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(s.copy(settings = bad))))
        }
        val badOrder = JSONObject(BackupJson.serialize(s))
        badOrder.getJSONObject("settings").put("payoffOrder", "RANDOM")
        assertEquals(BackupError.INVALID_DATA, errorOf(badOrder.toString()))
    }

    @Test
    fun version2FileStillRestoresWithoutDebts() {
        val root = JSONObject(BackupJson.serialize(sample()))
        root.put("formatVersion", 2)
        root.remove("debts")
        root.remove("debtEntries")
        root.remove("settings")
        // a stray debtId in an old file is dropped: there were no debts back then
        val parsed = BackupJson.parse(root.toString())
        assertEquals(2, parsed.formatVersion)
        assertTrue(parsed.debts.isEmpty())
        assertTrue(parsed.debtEntries.isEmpty())
        assertNull(parsed.settings)
        assertEquals(sample().budgets, parsed.budgets)
        assertTrue(parsed.transactions.all { it.debtId == null })
        assertEquals(sample().transactions.size, parsed.transactions.size)
    }

    @Test
    fun version2ModelMustNotCarryDebtsOrSettings() {
        for (bad in listOf(
            sample().copy(formatVersion = 2, transactions = emptyList(), debtEntries = emptyList(), settings = null),
            sample().copy(formatVersion = 2, transactions = emptyList(), debts = emptyList(), debtEntries = emptyList()),
        )) {
            try {
                BackupJson.validate(bad)
                fail("expected a BackupFormatException")
            } catch (e: BackupFormatException) {
                assertEquals(BackupError.INVALID_DATA, e.error)
            }
        }
    }

    @Test
    fun version3FileWithoutDebtArraysIsInvalid() {
        val noDebts = JSONObject(BackupJson.serialize(sample()))
        noDebts.remove("debts")
        assertEquals(BackupError.INVALID_DATA, errorOf(noDebts.toString()))
        val noEntries = JSONObject(BackupJson.serialize(sample()))
        noEntries.remove("debtEntries")
        assertEquals(BackupError.INVALID_DATA, errorOf(noEntries.toString()))
    }

    @Test
    fun rejectsBadDebtsAndEntries() {
        val s = sample()
        val d = s.debts[0]
        fun withDebt(debt: BackupDebt) = s.copy(debts = listOf(debt, s.debts[1]))

        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(id = 2)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(id = 0)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(dueDay = 0)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(dueDay = 29)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(aprBasisPoints = -1)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(aprBasisPoints = 10_001)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(minPaymentCents = -1)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(startingBalanceCents = -1)))))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(withDebt(d.copy(originalAmountCents = -1)))))

        val e = s.debtEntries[0]
        val missingDebt = s.copy(debtEntries = listOf(e.copy(debtId = 99)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(missingDebt)))
        val dupEntry = s.copy(debtEntries = listOf(e, e.copy()))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(dupEntry)))
        val badType = JSONObject(BackupJson.serialize(s))
        badType.getJSONArray("debtEntries").getJSONObject(0).put("type", "BONUS")
        assertEquals(BackupError.INVALID_DATA, errorOf(badType.toString()))
    }

    @Test
    fun transactionDebtIdMustExist() {
        val s = sample()
        val bad = s.copy(transactions = s.transactions.map { if (it.debtId != null) it.copy(debtId = 99) else it })
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(bad)))
        // without debts at all, a debt payment cannot be restored
        val noDebts = s.copy(debts = emptyList(), debtEntries = emptyList())
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(noDebts)))
    }

    @Test
    fun version1FileWithoutBudgetsStillRestores() {
        val root = JSONObject(BackupJson.serialize(sample()))
        root.put("formatVersion", 1)
        root.remove("budgets")
        root.remove("debts")
        root.remove("debtEntries")
        root.remove("settings")
        val parsed = BackupJson.parse(root.toString())
        assertEquals(1, parsed.formatVersion)
        assertTrue(parsed.budgets.isEmpty())
        assertTrue(parsed.debts.isEmpty())
        assertEquals(sample().accounts, parsed.accounts)
        assertEquals(sample().transactions.map { it.copy(debtId = null) }, parsed.transactions)
    }

    @Test
    fun version1ModelMustNotCarryBudgets() {
        try {
            BackupJson.validate(sample().copy(formatVersion = 1))
            fail("expected a BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals(BackupError.INVALID_DATA, e.error)
        }
    }

    @Test
    fun version2FileWithoutBudgetsArrayIsInvalid() {
        val root = JSONObject(BackupJson.serialize(sample()))
        root.remove("budgets")
        assertEquals(BackupError.INVALID_DATA, errorOf(root.toString()))
    }

    @Test
    fun rejectsBadBudgets() {
        val s = sample()
        val missingCategory = s.copy(budgets = listOf(BackupBudget(1, 99, 100, "2026-01", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(missingCategory)))

        val negative = s.copy(budgets = listOf(BackupBudget(1, 3, -1, "2026-01", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(negative)))

        val badPeriod = s.copy(budgets = listOf(BackupBudget(1, 3, 100, "2026-13", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(badPeriod)))
        val badPeriod2 = s.copy(budgets = listOf(BackupBudget(1, 3, 100, "26-01", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(badPeriod2)))

        val dupId = s.copy(budgets = listOf(BackupBudget(1, 3, 100, "2026-01", 0), BackupBudget(1, 3, 100, "2026-02", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(dupId)))

        val dupKey = s.copy(budgets = listOf(BackupBudget(1, 3, 100, "2026-01", 0), BackupBudget(2, 3, 200, "2026-01", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(dupKey)))

        val dupOverall = s.copy(budgets = listOf(BackupBudget(1, null, 100, "2026-01", 0), BackupBudget(2, null, 200, "2026-01", 0)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(dupOverall)))
    }

    @Test
    fun rejectsTextThatIsNotJson() {
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("hello"))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf(""))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("{\"foo\": 1}"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val text = BackupJson.serialize(sample()).replace("\"formatVersion\": 4", "\"formatVersion\": 6")
        assertEquals(BackupError.UNSUPPORTED_VERSION, errorOf(text))
    }

    @Test
    fun rejectsMissingField() {
        val root = JSONObject(BackupJson.serialize(sample()))
        root.getJSONArray("accounts").getJSONObject(0).remove("name")
        assertEquals(BackupError.INVALID_DATA, errorOf(root.toString()))
    }

    @Test
    fun rejectsBadEnumAndBadDate() {
        val badType = JSONObject(BackupJson.serialize(sample()))
        badType.getJSONArray("accounts").getJSONObject(0).put("type", "CREDIT")
        assertEquals(BackupError.INVALID_DATA, errorOf(badType.toString()))

        val badDate = JSONObject(BackupJson.serialize(sample()))
        badDate.getJSONArray("transactions").getJSONObject(0).put("date", "03/11/2026")
        assertEquals(BackupError.INVALID_DATA, errorOf(badDate.toString()))
    }

    @Test
    fun rejectsTransactionPointingToMissingAccountOrCategory() {
        val s = sample()
        val badAccount = s.copy(transactions = s.transactions.map { it.copy(accountId = 99) })
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(badAccount)))

        val badCategory = s.copy(transactions = s.transactions.map { it.copy(categoryId = 99) })
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(badCategory)))

        val badTo = s.copy(transactions = s.transactions.map { it.copy(toAccountId = 99) })
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(badTo)))
    }

    @Test
    fun rejectsDuplicateIdsAndTransferWithoutDestination() {
        val s = sample()
        val dup = s.copy(accounts = s.accounts.map { it.copy(id = 1) })
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(dup)))

        val transfer = s.transactions[1]
        val noTo = s.copy(transactions = listOf(transfer.copy(toAccountId = null)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(noTo)))
        val sameTo = s.copy(transactions = listOf(transfer.copy(toAccountId = transfer.accountId)))
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(sameTo)))
    }

    @Test
    fun rejectsBackupWithoutAccounts() {
        val s = sample().copy(accounts = emptyList(), transactions = emptyList())
        assertEquals(BackupError.INVALID_DATA, errorOf(BackupJson.serialize(s)))
    }

    @Test
    fun version4DebtFieldsAreWritten() {
        val debt = JSONObject(BackupJson.serialize(sample())).getJSONArray("debts").getJSONObject(0)
        assertEquals(712, debt.getInt("taegBasisPoints"))
        assertEquals("CAR_LOAN", debt.getString("type"))
        assertEquals(60, debt.getInt("totalInstallments"))
        assertEquals(18, debt.getInt("installmentsPaidBefore"))
        val second = JSONObject(BackupJson.serialize(sample())).getJSONArray("debts").getJSONObject(1)
        assertTrue(second.isNull("taegBasisPoints"))
        assertEquals("OTHER", second.getString("type"))
    }

    @Test
    fun version3FileStillRestoresWithDefaultDebtFields() {
        // A version 3 file: debts without the version 4 keys.
        val v3 = sample().copy(
            formatVersion = 3,
            debts = sample().debts.map {
                it.copy(taegBasisPoints = null, type = DebtType.OTHER, totalInstallments = null, installmentsPaidBefore = null)
            },
        )
        val text = BackupJson.serialize(v3)
        val debt = JSONObject(text).getJSONArray("debts").getJSONObject(0)
        assertTrue(!debt.has("type") && !debt.has("taegBasisPoints"))
        val parsed = BackupJson.parse(text)
        assertEquals(3, parsed.formatVersion)
        assertEquals(v3.debts, parsed.debts)
        assertEquals(DebtType.OTHER, parsed.debts[0].type)
        assertNull(parsed.debts[0].taegBasisPoints)
        assertNull(parsed.debts[0].totalInstallments)
    }

    @Test
    fun version3ModelMustNotCarryVersion4DebtFields() {
        try {
            BackupJson.validate(sample().copy(formatVersion = 3))
            fail("expected a BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals(BackupError.INVALID_DATA, e.error)
        }
    }

    @Test
    fun rejectsBadVersion4DebtFields() {
        val s = sample()
        val d = s.debts[0]
        for (bad in listOf(
            d.copy(taegBasisPoints = 10_001),
            d.copy(taegBasisPoints = -1),
            d.copy(totalInstallments = 0),
            d.copy(installmentsPaidBefore = -1),
            d.copy(installmentsPaidBefore = 61),
        )) {
            try {
                BackupJson.validate(s.copy(debts = listOf(bad, s.debts[1])))
                fail("expected a BackupFormatException for $bad")
            } catch (e: BackupFormatException) {
                assertEquals(BackupError.INVALID_DATA, e.error)
            }
        }
        val badType = JSONObject(BackupJson.serialize(s))
        badType.getJSONArray("debts").getJSONObject(0).put("type", "SPACESHIP")
        assertEquals(BackupError.INVALID_DATA, errorOf(badType.toString()))
    }

    // ---- format version 5: recurring rules ----

    private fun sample5(): BackupFile = sample().copy(
        formatVersion = 5,
        recurringRules = listOf(
            BackupRecurringRule(
                id = 1, name = "Rent \"Via Roma\"", kind = RecurringKind.BILL,
                transactionType = TransactionType.EXPENSE, amountCents = 65_000, accountId = 5, categoryId = 3,
                debtId = null, note = "è affitto", startDate = LocalDate.of(2026, 1, 31), intervalCount = 1,
                intervalUnit = RecurrenceUnit.MONTH, dayOfMonth = 31, endDate = LocalDate.of(2027, 12, 31),
                maxOccurrences = null, occurrencesDone = 10, nextDueDate = LocalDate.of(2026, 11, 30),
                remindDayBefore = true, active = true, createdAt = 50,
            ),
            BackupRecurringRule(
                id = 2, name = "Car loan", kind = RecurringKind.DEBT_PAYMENT,
                transactionType = TransactionType.EXPENSE, amountCents = 25_000, accountId = 1, categoryId = null,
                debtId = 1, note = "", startDate = LocalDate.of(2026, 11, 15), intervalCount = 1,
                intervalUnit = RecurrenceUnit.MONTH, dayOfMonth = 15, endDate = null, maxOccurrences = null,
                occurrencesDone = 0, nextDueDate = LocalDate.of(2026, 11, 15), remindDayBefore = false,
                active = false, createdAt = 51,
            ),
            BackupRecurringRule(
                id = 3, name = "Salary", kind = RecurringKind.INCOME, transactionType = TransactionType.INCOME,
                amountCents = 200_000, accountId = 5, categoryId = 9, debtId = null, note = "",
                startDate = LocalDate.of(2026, 11, 2), intervalCount = 2, intervalUnit = RecurrenceUnit.WEEK,
                dayOfMonth = null, endDate = null, maxOccurrences = 26, occurrencesDone = 3,
                nextDueDate = LocalDate.of(2026, 12, 14), remindDayBefore = false, active = true, createdAt = 52,
            ),
        ),
    )

    private fun expectInvalid(file: BackupFile) {
        try {
            BackupJson.validate(file)
            fail("expected a BackupFormatException for $file")
        } catch (e: BackupFormatException) {
            assertEquals(BackupError.INVALID_DATA, e.error)
        }
    }

    @Test
    fun version5RoundTripKeepsRecurringRules() {
        val original = sample5()
        val parsed = BackupJson.parse(BackupJson.serialize(original))
        assertEquals(original, parsed)
        assertEquals(3, parsed.recurringRules.size)
    }

    @Test
    fun version5FieldsAreWritten() {
        val root = JSONObject(BackupJson.serialize(sample5()))
        assertEquals(5, root.getInt("formatVersion"))
        val rules = root.getJSONArray("recurringRules")
        assertEquals(3, rules.length())
        val rent = rules.getJSONObject(0)
        assertEquals("BILL", rent.getString("kind"))
        assertEquals("2026-01-31", rent.getString("startDate"))
        assertEquals("2027-12-31", rent.getString("endDate"))
        assertTrue(rent.isNull("debtId"))
        assertTrue(rent.isNull("maxOccurrences"))
        assertEquals(true, rent.getBoolean("remindDayBefore"))
        val salary = rules.getJSONObject(2)
        assertEquals("WEEK", salary.getString("intervalUnit"))
        assertTrue(salary.isNull("dayOfMonth"))
        assertTrue(salary.isNull("endDate"))
        assertEquals(26, salary.getInt("maxOccurrences"))
    }

    @Test
    fun version4FileStillRestoresWithoutRecurringRules() {
        val text = BackupJson.serialize(sample())
        assertTrue(!JSONObject(text).has("recurringRules"))
        val parsed = BackupJson.parse(text)
        assertEquals(4, parsed.formatVersion)
        assertTrue(parsed.recurringRules.isEmpty())
    }

    @Test
    fun oldModelsMustNotCarryRecurringRules() {
        expectInvalid(sample5().copy(formatVersion = 4))
    }

    @Test
    fun version5FileWithoutTheRecurringArrayIsInvalid() {
        val root = JSONObject(BackupJson.serialize(sample5()))
        root.remove("recurringRules")
        assertEquals(BackupError.INVALID_DATA, errorOf(root.toString()))
    }

    @Test
    fun version5WithNoRulesRoundTrips() {
        val empty = sample5().copy(recurringRules = emptyList())
        assertEquals(empty, BackupJson.parse(BackupJson.serialize(empty)))
    }

    @Test
    fun rejectsBadRecurringRules() {
        val s = sample5()
        val rent = s.recurringRules[0]
        val debtRule = s.recurringRules[1]
        val salary = s.recurringRules[2]
        fun withRules(vararg rules: BackupRecurringRule) = s.copy(recurringRules = rules.toList())
        // references to rows that are not in the file
        expectInvalid(withRules(rent.copy(accountId = 99)))
        expectInvalid(withRules(rent.copy(categoryId = 99)))
        expectInvalid(withRules(rent.copy(debtId = 99)))
        expectInvalid(withRules(debtRule.copy(debtId = 99)))
        // numbers out of range
        expectInvalid(withRules(rent.copy(intervalCount = 0)))
        expectInvalid(withRules(rent.copy(intervalCount = 121)))
        expectInvalid(withRules(rent.copy(dayOfMonth = 0)))
        expectInvalid(withRules(rent.copy(dayOfMonth = 32)))
        expectInvalid(withRules(rent.copy(maxOccurrences = 0)))
        expectInvalid(withRules(rent.copy(occurrencesDone = -1)))
        expectInvalid(withRules(rent.copy(amountCents = -1)))
        // kind and transaction type / debt must agree
        expectInvalid(withRules(rent.copy(transactionType = TransactionType.INCOME)))
        expectInvalid(withRules(salary.copy(transactionType = TransactionType.EXPENSE)))
        expectInvalid(withRules(rent.copy(debtId = 1))) // a bill with a debt
        expectInvalid(withRules(debtRule.copy(debtId = null))) // a debt payment without a debt
        expectInvalid(withRules(debtRule.copy(categoryId = 3))) // a debt payment has no category
        // ids
        expectInvalid(withRules(rent.copy(id = 0)))
        expectInvalid(withRules(rent, salary.copy(id = rent.id)))
        // the valid ones pass
        BackupJson.validate(s)
    }

    @Test
    fun rejectsBadRecurringEnumsAndDates() {
        val badKind = JSONObject(BackupJson.serialize(sample5()))
        badKind.getJSONArray("recurringRules").getJSONObject(0).put("kind", "LOTTERY")
        assertEquals(BackupError.INVALID_DATA, errorOf(badKind.toString()))
        val badDate = JSONObject(BackupJson.serialize(sample5()))
        badDate.getJSONArray("recurringRules").getJSONObject(0).put("nextDueDate", "31/11/2026")
        assertEquals(BackupError.INVALID_DATA, errorOf(badDate.toString()))
        val badUnit = JSONObject(BackupJson.serialize(sample5()))
        badUnit.getJSONArray("recurringRules").getJSONObject(1).put("intervalUnit", "DECADE")
        assertEquals(BackupError.INVALID_DATA, errorOf(badUnit.toString()))
    }

    @Test
    fun unsupportedFutureVersionStillRejected() {
        val root = JSONObject(BackupJson.serialize(sample5()))
        root.put("formatVersion", 6)
        assertEquals(BackupError.UNSUPPORTED_VERSION, errorOf(root.toString()))
    }
}
