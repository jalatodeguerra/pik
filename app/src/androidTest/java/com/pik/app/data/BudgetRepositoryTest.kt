package com.pik.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.repo.BudgetRepository
import com.pik.app.domain.CategoryKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetRepositoryTest {
    private lateinit var db: PikDatabase
    private lateinit var repo: BudgetRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PikDatabase::class.java).build()
        repo = BudgetRepository(db, db.budgetDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun category(): Long = db.categoryDao().insert(
        CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, colorKey = "mint", iconKey = "cart"),
    )

    @Test
    fun overallBudgetIsUpsertedNotDuplicated() = runBlocking {
        repo.saveLimitsFrom("2026-11", mapOf(null to 100_000L))
        repo.saveLimitsFrom("2026-11", mapOf(null to 120_000L))
        val rows = db.budgetDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(120_000L, rows[0].limitCents)
        assertNull(rows[0].categoryId)
    }

    @Test
    fun laterPeriodAddsRowAndKeepsEarlierOne() = runBlocking {
        val cat = category()
        repo.saveLimitsFrom("2026-10", mapOf(cat to 10_000L))
        repo.saveLimitsFrom("2026-11", mapOf(cat to 20_000L))
        assertEquals(10_000L, db.budgetDao().find(cat, "2026-10")!!.limitCents)
        assertEquals(20_000L, db.budgetDao().find(cat, "2026-11")!!.limitCents)
    }

    @Test
    fun unchangedLimitWritesNothing() = runBlocking {
        val cat = category()
        repo.saveLimitsFrom("2026-10", mapOf(cat to 10_000L))
        repo.saveLimitsFrom("2026-11", mapOf(cat to 10_000L))
        assertEquals(1, db.budgetDao().getAll().size)
    }

    @Test
    fun zeroWithoutExistingBudgetWritesNothingAndZeroOverridesOlderOne() = runBlocking {
        val cat = category()
        repo.saveLimitsFrom("2026-10", mapOf(cat to 0L))
        assertEquals(0, db.budgetDao().getAll().size)
        repo.saveLimitsFrom("2026-10", mapOf(cat to 5_000L))
        repo.saveLimitsFrom("2026-12", mapOf(cat to 0L))
        assertEquals(0L, db.budgetDao().find(cat, "2026-12")!!.limitCents)
    }

    @Test
    fun deletingCategoryRemovesItsBudgets() = runBlocking {
        val cat = category()
        repo.saveLimitsFrom("2026-11", mapOf(cat to 5_000L, null to 9_000L))
        db.categoryDao().deleteById(cat)
        val rows = db.budgetDao().getAll()
        assertEquals(1, rows.size)
        assertNull(rows[0].categoryId)
    }
}
