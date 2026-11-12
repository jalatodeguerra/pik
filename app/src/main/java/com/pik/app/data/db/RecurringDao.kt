package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    @Query("SELECT * FROM recurring_rules ORDER BY nextDueDate ASC, id ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE active = 1 ORDER BY id ASC")
    suspend fun getActive(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE debtId = :debtId ORDER BY id ASC LIMIT 1")
    suspend fun getByDebtId(debtId: Long): RecurringRuleEntity?

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE debtId = :debtId")
    fun observeCountForDebt(debtId: Long): Flow<Int>

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringRuleEntity>): List<Long>

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
