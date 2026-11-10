package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pik.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A transaction joined with the names/styles needed to show it in a list. */
data class TransactionRow(
    val id: Long,
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val note: String,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?,
)

private const val ROW_SELECT = """
    SELECT t.id AS id, t.type AS type, t.amountCents AS amountCents, t.date AS date,
           t.accountId AS accountId, t.toAccountId AS toAccountId, t.categoryId AS categoryId,
           t.note AS note,
           a.name AS accountName, ta.name AS toAccountName,
           c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorKey AS categoryColorKey
    FROM transactions t
    INNER JOIN accounts a ON a.id = t.accountId
    LEFT JOIN accounts ta ON ta.id = t.toAccountId
    LEFT JOIN categories c ON c.id = t.categoryId
"""

@Dao
interface TransactionDao {

    /** Transactions whose calendar day is between the two epoch days (inclusive), newest first. */
    @Query(ROW_SELECT + " WHERE t.date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY t.date DESC, t.id DESC")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TransactionRow>>

    @Query(ROW_SELECT + " ORDER BY t.date DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionRow>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
