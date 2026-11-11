package com.pik.app.data.repo

import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.domain.BalanceCheck
import com.pik.app.domain.InsightTx
import com.pik.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val accountDao: AccountDao,
) {
    fun observeMonth(month: YearMonth): Flow<List<TransactionRow>> =
        dao.observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    /** Transactions between two calendar days, both inclusive (a payday-month period). */
    fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<TransactionRow>> =
        dao.observeBetween(from.toEpochDay(), toInclusive.toEpochDay())

    /** Every transaction, for the Insights maths (periods are bucketed in the domain layer). */
    fun observeInsightFacts(): Flow<List<InsightTx>> = dao.observeInsightRows().map { rows ->
        rows.map { InsightTx(it.date, it.type, it.amountCents, it.categoryId, it.debtId) }
    }

    /** Every transaction with account, category and debt names, oldest first (CSV export). */
    suspend fun getAllRows(): List<TransactionRow> = dao.getAllRows()

    fun observeRecent(limit: Int): Flow<List<TransactionRow>> = dao.observeRecent(limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun get(id: Long): TransactionEntity? = dao.getById(id)

    /**
     * Inserts when id == 0, otherwise updates. createdAt is kept for edits, updatedAt is set to now.
     */
    suspend fun save(transaction: TransactionEntity): Long {
        val now = System.currentTimeMillis()
        return if (transaction.id == 0L) {
            dao.insert(transaction.copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(transaction.copy(updatedAt = now))
            transaction.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * Compares [realCents] with Pik's calculated balance of the account and, when they differ,
     * inserts an ADJUSTMENT dated today. Returns the signed adjustment (0 when it already matches).
     */
    suspend fun applyBalanceCheck(accountId: Long, realCents: Long, note: String): Long {
        val current = accountDao.balanceOf(accountId)?.balanceCents ?: return 0L
        val adjustment = BalanceCheck.adjustmentCents(realCents, current)
        if (adjustment == 0L) return 0L
        val now = System.currentTimeMillis()
        dao.insert(
            TransactionEntity(
                type = TransactionType.ADJUSTMENT,
                amountCents = adjustment,
                date = LocalDate.now(),
                accountId = accountId,
                note = note,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return adjustment
    }
}
