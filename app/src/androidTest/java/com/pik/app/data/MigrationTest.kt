package com.pik.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pik.app.data.db.MIGRATION_1_2
import com.pik.app.data.db.MIGRATION_2_3
import com.pik.app.data.db.MIGRATION_3_4
import com.pik.app.data.db.MIGRATION_4_5
import com.pik.app.data.db.MIGRATION_5_6
import com.pik.app.data.db.PikDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests. Schemas are exported to app/schemas and added to the androidTest assets by
 * the Room Gradle plugin. Every new database version needs a test here that creates the old
 * version, inserts rows, migrates and checks the rows survived. No destructive fallback is allowed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PikDatabase::class.java,
    )

    @Test
    fun createVersion1AndOpenWithRoom() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PikDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        // Opening validates the schema against the current entities.
        db.openHelper.writableDatabase
        db.close()
    }

    @Test
    fun migrate1To2KeepsDataAndAddsBudgets() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO accounts (id, name, type, startingBalanceCents, archived, sortOrder, createdAt) " +
                    "VALUES (1, 'Bank', 'BANK', 100000, 0, 0, 1)",
            )
            execSQL(
                "INSERT INTO categories (id, name, kind, colorKey, iconKey, archived, sortOrder) " +
                    "VALUES (7, 'Groceries', 'EXPENSE', 'mint', 'cart', 0, 0)",
            )
            execSQL(
                "INSERT INTO transactions (id, type, amountCents, date, accountId, toAccountId, categoryId, " +
                    "note, debtId, recurringId, createdAt, updatedAt) " +
                    "VALUES (3, 'EXPENSE', 1250, 20400, 1, NULL, 7, 'Pizza', NULL, NULL, 2, 2)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT name, startingBalanceCents FROM accounts WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Bank", it.getString(0))
            assertEquals(100000L, it.getLong(1))
        }
        db.query("SELECT name FROM categories WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("Groceries", it.getString(0))
        }
        db.query("SELECT amountCents, categoryId, note FROM transactions WHERE id = 3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1250L, it.getLong(0))
            assertEquals(7L, it.getLong(1))
            assertEquals("Pizza", it.getString(2))
        }
        db.query("SELECT COUNT(*) FROM budgets").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }

        // The new table accepts rows: an overall budget (NULL category) and a category budget.
        db.execSQL("INSERT INTO budgets (categoryId, limitCents, startPeriod, createdAt) VALUES (NULL, 200000, '2026-11', 1)")
        db.execSQL("INSERT INTO budgets (categoryId, limitCents, startPeriod, createdAt) VALUES (7, 30000, '2026-11', 1)")
        db.query("SELECT COUNT(*) FROM budgets").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate2To3KeepsDataAndAddsDebts() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertBaseRows(this)
            execSQL("INSERT INTO budgets (id, categoryId, limitCents, startPeriod, createdAt) VALUES (1, 7, 30000, '2026-11', 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        assertBaseRowsSurvived(db)
        db.query("SELECT limitCents FROM budgets WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(30000L, it.getLong(0))
        }
        assertDebtTablesWork(db)
        db.close()
    }

    @Test
    fun migrate1To3KeepsDataAndAddsBudgetsAndDebts() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertBaseRows(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        assertBaseRowsSurvived(db)
        db.query("SELECT COUNT(*) FROM budgets").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertDebtTablesWork(db)
        db.close()
    }

    private fun insertBaseRows(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO accounts (id, name, type, startingBalanceCents, archived, sortOrder, createdAt) " +
                "VALUES (1, 'Bank', 'BANK', 100000, 0, 0, 1)",
        )
        db.execSQL(
            "INSERT INTO categories (id, name, kind, colorKey, iconKey, archived, sortOrder) " +
                "VALUES (7, 'Groceries', 'EXPENSE', 'mint', 'cart', 0, 0)",
        )
        db.execSQL(
            "INSERT INTO transactions (id, type, amountCents, date, accountId, toAccountId, categoryId, " +
                "note, debtId, recurringId, createdAt, updatedAt) " +
                "VALUES (3, 'EXPENSE', 1250, 20400, 1, NULL, 7, 'Pizza', NULL, NULL, 2, 2)",
        )
    }

    private fun assertBaseRowsSurvived(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("SELECT name, startingBalanceCents FROM accounts WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Bank", it.getString(0))
            assertEquals(100000L, it.getLong(1))
        }
        db.query("SELECT name FROM categories WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("Groceries", it.getString(0))
        }
        db.query("SELECT amountCents, categoryId, note FROM transactions WHERE id = 3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1250L, it.getLong(0))
            assertEquals(7L, it.getLong(1))
            assertEquals("Pizza", it.getString(2))
        }
    }

    /** The new tables exist, are empty, and accept rows (a debt, an entry, a debt payment). */
    private fun assertDebtTablesWork(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        for (table in listOf("debts", "debt_entries")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        db.execSQL(
            "INSERT INTO debts (id, name, startingBalanceCents, originalAmountCents, aprBasisPoints, " +
                "minPaymentCents, dueDay, archived, sortOrder, createdAt) " +
                "VALUES (1, 'Car loan', 500000, NULL, 599, 25000, 15, 0, 0, 1)",
        )
        db.execSQL(
            "INSERT INTO debt_entries (id, debtId, type, amountCents, date, note, createdAt) " +
                "VALUES (1, 1, 'INTEREST', 2500, 20400, 'Oct', 1)",
        )
        db.execSQL(
            "INSERT INTO transactions (type, amountCents, date, accountId, toAccountId, categoryId, " +
                "note, debtId, recurringId, createdAt, updatedAt) " +
                "VALUES ('EXPENSE', 25000, 20401, 1, NULL, NULL, '', 1, NULL, 2, 2)",
        )
        db.query("SELECT d.name, e.amountCents FROM debts d JOIN debt_entries e ON e.debtId = d.id").use {
            assertTrue(it.moveToFirst())
            assertEquals("Car loan", it.getString(0))
            assertEquals(2500L, it.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM transactions WHERE debtId = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrate3To4KeepsDebtsAndAddsTypeAndLoanColumns() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertBaseRows(this)
            insertVersion3Debt(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        assertBaseRowsSurvived(db)
        assertOldDebtSurvived(db)
        assertNewDebtColumnsWork(db)
        db.close()
    }

    @Test
    fun migrate1To4KeepsDataThroughEveryMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertBaseRows(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        assertBaseRowsSurvived(db)
        db.query("SELECT COUNT(*) FROM debts").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertNewDebtColumnsWork(db)
        db.close()
    }

    private fun insertVersion3Debt(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO debts (id, name, startingBalanceCents, originalAmountCents, aprBasisPoints, " +
                "minPaymentCents, dueDay, archived, sortOrder, createdAt) " +
                "VALUES (1, 'Old loan', 800000, 1000000, 750, 20000, 10, 0, 0, 5)",
        )
        db.execSQL(
            "INSERT INTO debt_entries (id, debtId, type, amountCents, date, note, createdAt) " +
                "VALUES (1, 1, 'FEE', 500, 20400, '', 1)",
        )
    }

    /** The version 3 debt keeps its values, gets type OTHER and NULL in the new nullable columns. */
    private fun assertOldDebtSurvived(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query(
            "SELECT name, startingBalanceCents, aprBasisPoints, minPaymentCents, taegBasisPoints, type, " +
                "totalInstallments, installmentsPaidBefore FROM debts WHERE id = 1",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("Old loan", it.getString(0))
            assertEquals(800000L, it.getLong(1))
            assertEquals(750, it.getInt(2))
            assertEquals(20000L, it.getLong(3))
            assertTrue(it.isNull(4))
            assertEquals("OTHER", it.getString(5))
            assertTrue(it.isNull(6))
            assertTrue(it.isNull(7))
        }
        db.query("SELECT amountCents FROM debt_entries WHERE debtId = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(500L, it.getLong(0))
        }
    }

    private fun assertNewDebtColumnsWork(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO debts (id, name, startingBalanceCents, originalAmountCents, aprBasisPoints, " +
                "minPaymentCents, dueDay, archived, sortOrder, createdAt, taegBasisPoints, type, " +
                "totalInstallments, installmentsPaidBefore) " +
                "VALUES (50, 'Auto', 1500000, NULL, 599, 29000, 15, 0, 9, 6, 712, 'CAR_LOAN', 60, 18)",
        )
        db.query("SELECT taegBasisPoints, type, totalInstallments, installmentsPaidBefore FROM debts WHERE id = 50").use {
            assertTrue(it.moveToFirst())
            assertEquals(712, it.getInt(0))
            assertEquals("CAR_LOAN", it.getString(1))
            assertEquals(60, it.getInt(2))
            assertEquals(18, it.getInt(3))
        }
        // a debt inserted without the new columns (like old code) gets the default type
        db.execSQL(
            "INSERT INTO debts (id, name, startingBalanceCents, originalAmountCents, aprBasisPoints, " +
                "minPaymentCents, dueDay, archived, sortOrder, createdAt) " +
                "VALUES (51, 'Plain', 100, NULL, 0, 0, 1, 0, 10, 7)",
        )
        db.query("SELECT type FROM debts WHERE id = 51").use {
            assertTrue(it.moveToFirst())
            assertEquals("OTHER", it.getString(0))
        }
    }

    @Test
    fun migrate4To5KeepsEverythingAndAddsRecurringRules() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertBaseRows(this)
            insertVersion3Debt(this)
            // a transaction that already carries a recurringId (the column exists since version 1)
            execSQL(
                "INSERT INTO transactions (id, type, amountCents, date, accountId, toAccountId, categoryId, " +
                    "note, debtId, recurringId, createdAt, updatedAt) " +
                    "VALUES (4, 'EXPENSE', 900, 20410, 1, NULL, 7, 'Old', NULL, 77, 3, 3)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertBaseRowsSurvived(db)
        assertOldDebtSurvived(db)
        db.query("SELECT recurringId FROM transactions WHERE id = 4").use {
            assertTrue(it.moveToFirst())
            assertEquals(77L, it.getLong(0))
        }
        assertRecurringTableWorks(db)
        db.close()
    }

    @Test
    fun migrate1To5KeepsDataThroughEveryMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertBaseRows(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        )

        assertBaseRowsSurvived(db)
        assertNewDebtColumnsWork(db)
        assertRecurringTableWorks(db)
        db.close()
    }

    /**
     * The recurring_rules table exists, is empty, accepts the three kinds of rule, defaults
     * occurrencesDone to 0 and has the expected foreign keys (RESTRICT, SET NULL, CASCADE).
     * Needs account 1 and category 7 (insertBaseRows); it inserts its own debt, spare account and category.
     */
    private fun assertRecurringTableWorks(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM recurring_rules").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT OR IGNORE INTO debts (id, name, startingBalanceCents, originalAmountCents, aprBasisPoints, " +
                "minPaymentCents, dueDay, archived, sortOrder, createdAt) " +
                "VALUES (90, 'Rule debt', 100000, NULL, 500, 20000, 10, 0, 90, 8)",
        )
        // a bill with a category, without occurrencesDone: the column default (0) applies
        db.execSQL(
            "INSERT INTO recurring_rules (id, name, kind, transactionType, amountCents, accountId, categoryId, " +
                "debtId, note, startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences, " +
                "nextDueDate, remindDayBefore, active, createdAt) " +
                "VALUES (1, 'Rent', 'BILL', 'EXPENSE', 65000, 1, 7, NULL, '', 20400, 1, 'MONTH', 5, NULL, NULL, " +
                "20400, 1, 1, 9)",
        )
        // a debt payment and an income
        db.execSQL(
            "INSERT INTO recurring_rules (id, name, kind, transactionType, amountCents, accountId, categoryId, " +
                "debtId, note, startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences, " +
                "occurrencesDone, nextDueDate, remindDayBefore, active, createdAt) " +
                "VALUES (2, 'Loan', 'DEBT_PAYMENT', 'EXPENSE', 20000, 1, NULL, 90, '', 20400, 1, 'MONTH', 10, NULL, " +
                "NULL, 2, 20460, 0, 1, 9)",
        )
        db.execSQL(
            "INSERT INTO recurring_rules (id, name, kind, transactionType, amountCents, accountId, categoryId, " +
                "debtId, note, startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences, " +
                "occurrencesDone, nextDueDate, remindDayBefore, active, createdAt) " +
                "VALUES (3, 'Salary', 'INCOME', 'INCOME', 200000, 1, NULL, NULL, 'x', 20400, 2, 'WEEK', NULL, 20800, " +
                "12, 0, 20400, 0, 1, 9)",
        )
        db.query("SELECT occurrencesDone, intervalUnit, dayOfMonth FROM recurring_rules WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
            assertEquals("MONTH", it.getString(1))
            assertEquals(5, it.getInt(2))
        }
        db.query("SELECT endDate, maxOccurrences, dayOfMonth FROM recurring_rules WHERE id = 3").use {
            assertTrue(it.moveToFirst())
            assertEquals(20800L, it.getLong(0))
            assertEquals(12, it.getInt(1))
            assertTrue(it.isNull(2))
        }
        // deleting the category clears the rule's category, the rule stays
        db.execSQL("INSERT INTO categories (id, name, kind, colorKey, iconKey, archived, sortOrder) VALUES (8, 'Tmp', 'EXPENSE', 'mint', 'cart', 0, 9)")
        db.execSQL("UPDATE recurring_rules SET categoryId = 8 WHERE id = 1")
        db.execSQL("DELETE FROM categories WHERE id = 8")
        db.query("SELECT categoryId FROM recurring_rules WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        // deleting the debt deletes its rule
        db.execSQL("DELETE FROM debts WHERE id = 90")
        db.query("SELECT COUNT(*) FROM recurring_rules WHERE id = 2").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        // an account used by a rule cannot be deleted
        db.execSQL("INSERT INTO accounts (id, name, type, startingBalanceCents, archived, sortOrder, createdAt) VALUES (9, 'Spare', 'CASH', 0, 0, 5, 1)")
        db.execSQL(
            "INSERT INTO recurring_rules (id, name, kind, transactionType, amountCents, accountId, categoryId, " +
                "debtId, note, startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences, " +
                "occurrencesDone, nextDueDate, remindDayBefore, active, createdAt) " +
                "VALUES (4, 'Gym', 'SUBSCRIPTION', 'EXPENSE', 3000, 9, NULL, NULL, '', 20400, 1, 'MONTH', 1, NULL, NULL, " +
                "0, 20400, 0, 1, 9)",
        )
        var blocked = false
        try {
            db.execSQL("DELETE FROM accounts WHERE id = 9")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            blocked = true
        }
        assertTrue("an account with a recurring rule must not be deletable", blocked)
    }

    @Test
    fun migrate5To6KeepsEverythingAndAddsImportTables() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertBaseRows(this)
            insertVersion3Debt(this)
            execSQL(
                "INSERT INTO recurring_rules (id, name, kind, transactionType, amountCents, accountId, categoryId, " +
                    "debtId, note, startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences, " +
                    "occurrencesDone, nextDueDate, remindDayBefore, active, createdAt) " +
                    "VALUES (1, 'Rent', 'BILL', 'EXPENSE', 65000, 1, 7, NULL, '', 20400, 1, 'MONTH', 5, NULL, NULL, " +
                    "0, 20400, 1, 1, 9)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        assertBaseRowsSurvived(db)
        db.query("SELECT name, amountCents FROM recurring_rules WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Rent", it.getString(0))
            assertEquals(65000L, it.getLong(1))
        }
        assertImportTablesWork(db)
        db.close()
    }

    @Test
    fun migrate1To6KeepsDataThroughEveryMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertBaseRows(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        )

        assertBaseRowsSurvived(db)
        assertNewDebtColumnsWork(db)
        assertRecurringTableWorks(db)
        assertImportTablesWork(db)
        db.close()
    }

    /**
     * import_items and merchant_rules exist, are empty and accept rows; sourceKey is unique; deleting an
     * account clears the item's account (SET NULL); deleting a category deletes its merchant rules (CASCADE).
     * Needs account 1 and category 7 (insertBaseRows); it inserts its own spare account and category.
     */
    private fun assertImportTablesWork(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        for (table in listOf("import_items", "merchant_rules")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO accounts (id, name, type, startingBalanceCents, archived, sortOrder, createdAt) VALUES (20, 'Wallet', 'BANK', 0, 0, 7, 1)")
        db.execSQL("INSERT INTO categories (id, name, kind, colorKey, iconKey, archived, sortOrder) VALUES (20, 'Tmp', 'EXPENSE', 'mint', 'cart', 0, 9)")
        db.execSQL(
            "INSERT INTO import_items (id, source, sourceKey, accountId, direction, amountCents, merchant, date, " +
                "rawText, status, suggestion, transferAccountId, suggestedCategoryId, transactionId, restored, createdAt) " +
                "VALUES (1, 'NEXI', 'key-1', 20, 'OUT', 1250, 'CONAD', 20400, 'Pagamento di 12,50 EUR presso CONAD', " +
                "'PENDING', 'EXPENSE', NULL, 7, NULL, 0, 5)",
        )
        db.query("SELECT merchant, amountCents, restored, suggestedCategoryId FROM import_items WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("CONAD", it.getString(0))
            assertEquals(1250L, it.getLong(1))
            assertEquals(0, it.getInt(2))
            assertEquals(7L, it.getLong(3))
        }
        // the same sourceKey cannot be stored twice
        var duplicateBlocked = false
        try {
            db.execSQL(
                "INSERT INTO import_items (source, sourceKey, accountId, direction, amountCents, merchant, date, " +
                    "rawText, status, suggestion, restored, createdAt) " +
                    "VALUES ('NEXI', 'key-1', NULL, 'OUT', 1, 'X', 20400, '', 'PENDING', 'EXPENSE', 0, 5)",
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            duplicateBlocked = true
        }
        assertTrue("sourceKey must be unique", duplicateBlocked)
        // deleting the account keeps the item, without an account
        db.execSQL("DELETE FROM accounts WHERE id = 20")
        db.query("SELECT accountId FROM import_items WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        // a merchant rule is deleted together with its category
        db.execSQL("INSERT INTO merchant_rules (merchantKey, categoryId, updatedAt) VALUES ('CONAD', 20, 6)")
        db.execSQL("INSERT INTO merchant_rules (merchantKey, categoryId, updatedAt) VALUES ('COOP', 7, 6)")
        db.execSQL("DELETE FROM categories WHERE id = 20")
        db.query("SELECT merchantKey FROM merchant_rules").use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("COOP", it.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
