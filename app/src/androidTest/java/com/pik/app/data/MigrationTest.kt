package com.pik.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pik.app.data.db.MIGRATION_1_2
import com.pik.app.data.db.MIGRATION_2_3
import com.pik.app.data.db.MIGRATION_3_4
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
