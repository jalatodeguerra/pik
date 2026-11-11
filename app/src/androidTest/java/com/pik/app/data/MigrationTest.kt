package com.pik.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pik.app.data.db.MIGRATION_1_2
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
        ).addMigrations(MIGRATION_1_2).build()
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
