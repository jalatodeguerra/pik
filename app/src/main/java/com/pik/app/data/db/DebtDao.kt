package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A sum per debt: entries (signed) or payments (EXPENSE transactions). */
data class DebtSum(
    val debtId: Long,
    val sumCents: Long,
)

@Dao
interface DebtDao {

    @Query("SELECT * FROM debts ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY id ASC")
    suspend fun getAll(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM debts")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(debt: DebtEntity): Long

    @Insert
    suspend fun insertAll(debts: List<DebtEntity>): List<Long>

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()

    // ---- entries ----

    @Query("SELECT * FROM debt_entries ORDER BY id ASC")
    suspend fun getAllEntries(): List<DebtEntryEntity>

    @Query("SELECT * FROM debt_entries WHERE debtId = :debtId")
    fun observeEntries(debtId: Long): Flow<List<DebtEntryEntity>>

    @Insert
    suspend fun insertEntry(entry: DebtEntryEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<DebtEntryEntity>): List<Long>

    @Query("DELETE FROM debt_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM debt_entries")
    suspend fun deleteAllEntries()

    // ---- sums ----

    @Query("SELECT debtId AS debtId, SUM(amountCents) AS sumCents FROM debt_entries GROUP BY debtId")
    fun observeEntrySums(): Flow<List<DebtSum>>

    /** Payments = EXPENSE transactions that have a debtId. */
    @Query(
        """
        SELECT debtId AS debtId, SUM(amountCents) AS sumCents
        FROM transactions
        WHERE debtId IS NOT NULL AND type = 'EXPENSE'
        GROUP BY debtId
        """,
    )
    fun observePaymentSums(): Flow<List<DebtSum>>

    /**
     * Per debt: how many payments count as a rata (EXPENSE with debtId, amount >= 90 % of the debt's
     * minPaymentCents). sumCents holds the count. See domain/Installments.kt.
     */
    @Query(
        """
        SELECT t.debtId AS debtId, COUNT(*) AS sumCents
        FROM transactions t INNER JOIN debts d ON d.id = t.debtId
        WHERE t.type = 'EXPENSE' AND d.minPaymentCents > 0 AND t.amountCents * 10 >= d.minPaymentCents * 9
        GROUP BY t.debtId
        """,
    )
    fun observeRataCounts(): Flow<List<DebtSum>>

    /** Per debt: the epoch day of the latest payment that counts as a rata (sumCents holds the day). */
    @Query(
        """
        SELECT t.debtId AS debtId, MAX(t.date) AS sumCents
        FROM transactions t INNER JOIN debts d ON d.id = t.debtId
        WHERE t.type = 'EXPENSE' AND d.minPaymentCents > 0 AND t.amountCents * 10 >= d.minPaymentCents * 9
        GROUP BY t.debtId
        """,
    )
    fun observeLastRataDays(): Flow<List<DebtSum>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM debt_entries WHERE debtId = :debtId")
    suspend fun entriesSum(debtId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE debtId = :debtId AND type = 'EXPENSE'")
    suspend fun paymentsSum(debtId: Long): Long

    /** Every transaction that points to the debt, whatever its type (decides whether it can be deleted). */
    @Query("SELECT COUNT(*) FROM transactions WHERE debtId = :debtId")
    suspend fun transactionCount(debtId: Long): Int
}
