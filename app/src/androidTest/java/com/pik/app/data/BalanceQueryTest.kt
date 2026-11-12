package com.pik.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionEntity
import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BalanceQueryTest {
    private lateinit var db: PikDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PikDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tx(
        type: TransactionType,
        cents: Long,
        account: Long,
        to: Long? = null,
        category: Long? = null,
    ) = TransactionEntity(
        type = type,
        amountCents = cents,
        date = LocalDate.of(2026, 11, 3),
        accountId = account,
        toAccountId = to,
        categoryId = category,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun balancesMatchDomainRules() = runBlocking {
        val bank = db.accountDao().insert(
            AccountEntity(name = "Bank", type = AccountType.BANK, startingBalanceCents = 100_000, createdAt = 0),
        )
        val cash = db.accountDao().insert(
            AccountEntity(name = "Cash", type = AccountType.CASH, startingBalanceCents = 5_000, createdAt = 0),
        )
        val food = db.categoryDao().insert(
            CategoryEntity(name = "Food", kind = CategoryKind.EXPENSE, colorKey = "mint", iconKey = "cart"),
        )
        val salary = db.categoryDao().insert(
            CategoryEntity(name = "Salary", kind = CategoryKind.INCOME, colorKey = "lime", iconKey = "salary"),
        )
        val dao = db.transactionDao()
        dao.insert(tx(TransactionType.INCOME, 200_000, bank, category = salary))
        dao.insert(tx(TransactionType.EXPENSE, 4_550, bank, category = food))
        dao.insert(tx(TransactionType.TRANSFER, 10_000, bank, to = cash))
        dao.insert(tx(TransactionType.EXPENSE, 1_000, cash, category = food))
        dao.insert(tx(TransactionType.ADJUSTMENT, -50, cash))

        val balances = db.accountDao().observeBalances().first().associate { it.accountId to it.balanceCents }

        // bank: 100000 + 200000 - 4550 - 10000
        assertEquals(285_450L, balances[bank])
        // cash: 5000 + 10000 - 1000 - 50
        assertEquals(13_950L, balances[cash])
    }

    @Test
    fun accountWithoutTransactionsKeepsStartingBalance() = runBlocking {
        val id = db.accountDao().insert(
            AccountEntity(name = "Empty", type = AccountType.CASH, startingBalanceCents = 1_234, createdAt = 0),
        )
        val balances = db.accountDao().observeBalances().first().associate { it.accountId to it.balanceCents }
        assertEquals(1_234L, balances[id])
    }

    @Test
    fun categoriesOrderedByUsage() = runBlocking {
        val acc = db.accountDao().insert(
            AccountEntity(name = "Bank", type = AccountType.BANK, startingBalanceCents = 0, createdAt = 0),
        )
        val a = db.categoryDao().insert(
            CategoryEntity(name = "A", kind = CategoryKind.EXPENSE, colorKey = "mint", iconKey = "cart", sortOrder = 0),
        )
        val b = db.categoryDao().insert(
            CategoryEntity(name = "B", kind = CategoryKind.EXPENSE, colorKey = "mint", iconKey = "cart", sortOrder = 1),
        )
        db.transactionDao().insert(tx(TransactionType.EXPENSE, 100, acc, category = b))

        val ordered = db.categoryDao().observeByUsage(CategoryKind.EXPENSE).first().map { it.id }
        assertEquals(listOf(b, a), ordered)
    }
}
