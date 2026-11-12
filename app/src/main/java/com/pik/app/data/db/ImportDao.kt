package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pik.app.domain.importing.ImportStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {

    /** Waiting for the user, newest first. */
    @Query("SELECT * FROM import_items WHERE status = 'PENDING' ORDER BY date DESC, id DESC")
    fun observePending(): Flow<List<ImportItemEntity>>

    /** Probably already in Pik, newest first. */
    @Query("SELECT * FROM import_items WHERE status = 'DUPLICATE' ORDER BY date DESC, id DESC")
    fun observeDuplicates(): Flow<List<ImportItemEntity>>

    @Query("SELECT COUNT(*) FROM import_items WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM import_items WHERE id = :id")
    suspend fun getById(id: Long): ImportItemEntity?

    @Query("SELECT * FROM import_items WHERE status = :status ORDER BY id ASC")
    suspend fun getByStatus(status: ImportStatus): List<ImportItemEntity>

    /** Everything the user has not dismissed (used to find duplicates). */
    @Query("SELECT * FROM import_items WHERE status != 'DISMISSED'")
    suspend fun getNotDismissed(): List<ImportItemEntity>

    /** Ids of the transactions that an inbox item created. */
    @Query("SELECT transactionId FROM import_items WHERE transactionId IS NOT NULL")
    suspend fun getLinkedTransactionIds(): List<Long>

    /** Returns -1 when an item with the same sourceKey exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ImportItemEntity): Long

    @Update
    suspend fun update(item: ImportItemEntity)

    /** Added and dismissed items created before the cutoff (epoch millis). */
    @Query("DELETE FROM import_items WHERE status IN ('ADDED', 'DISMISSED') AND createdAt < :cutoffMillis")
    suspend fun deleteHandledBefore(cutoffMillis: Long)

    @Query("DELETE FROM import_items")
    suspend fun deleteAll()
}
