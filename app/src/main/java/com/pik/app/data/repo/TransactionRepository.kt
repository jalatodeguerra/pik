package com.pik.app.data.repo

import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.data.db.TransactionRow
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) {
    fun observeMonth(month: YearMonth): Flow<List<TransactionRow>> =
        dao.observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

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
}
