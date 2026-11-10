package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AccountBalance(
    val accountId: Long,
    val balanceCents: Long,
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    /**
     * Balance per account, calculated from the transactions:
     * starting + income - expense - transfers out + transfers in + adjustments.
     * Mirrors domain.BalanceCalculator.
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.startingBalanceCents + COALESCE((
                   SELECT SUM(CASE
                       WHEN t.accountId = a.id AND t.type = 'INCOME' THEN t.amountCents
                       WHEN t.accountId = a.id AND t.type = 'EXPENSE' THEN -t.amountCents
                       WHEN t.accountId = a.id AND t.type = 'TRANSFER' THEN -t.amountCents
                       WHEN t.accountId = a.id AND t.type = 'ADJUSTMENT' THEN t.amountCents
                       WHEN t.toAccountId = a.id AND t.type = 'TRANSFER' THEN t.amountCents
                       ELSE 0
                   END)
                   FROM transactions t
                   WHERE t.accountId = a.id OR t.toAccountId = a.id
               ), 0) AS balanceCents
        FROM accounts a
        """,
    )
    fun observeBalances(): Flow<List<AccountBalance>>
}
