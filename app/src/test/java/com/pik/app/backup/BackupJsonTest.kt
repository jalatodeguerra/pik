package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.TransactionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupJsonTest {

    private fun sample(): BackupFile = BackupFile(
        formatVersion = 1,
        appVersion = "0.2.0",
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
                "", 77, 88, 12, 13,
            ),
            BackupTransaction(
                8, TransactionType.ADJUSTMENT, -321, LocalDate.of(2026, 11, 10), 5, null, null,
                "Balance check", null, null, 14, 15,
            ),
        ),
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
        val original = sample().copy(categories = emptyList(), transactions = emptyList())
        assertEquals(original, BackupJson.parse(BackupJson.serialize(original)))
    }

    @Test
    fun datesAreIsoAndHeaderIsPresent() {
        val root = JSONObject(BackupJson.serialize(sample()))
        assertEquals(1, root.getInt("formatVersion"))
        assertEquals("0.2.0", root.getString("appVersion"))
        assertEquals("2026-11-10T09:30:00Z", root.getString("exportedAt"))
        val tx = root.getJSONArray("transactions").getJSONObject(0)
        assertEquals("2026-11-03", tx.getString("date"))
        assertTrue(root.getJSONArray("transactions").getJSONObject(1).isNull("categoryId"))
    }

    @Test
    fun rejectsTextThatIsNotJson() {
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("hello"))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf(""))
        assertEquals(BackupError.NOT_A_BACKUP, errorOf("{\"foo\": 1}"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val text = BackupJson.serialize(sample()).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
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
