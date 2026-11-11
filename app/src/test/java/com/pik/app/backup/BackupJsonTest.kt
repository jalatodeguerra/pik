package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.PayoffOrder
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
        formatVersion = 3,
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
            BackupDebt(1, "Car \"loan\"", 1_000_000, 1_500_000, 599, 25_000, 15, false, 0, 30),
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
        assertEquals(3, root.getInt("formatVersion"))
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
        val text = BackupJson.serialize(sample()).replace("\"formatVersion\": 3", "\"formatVersion\": 4")
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
}
