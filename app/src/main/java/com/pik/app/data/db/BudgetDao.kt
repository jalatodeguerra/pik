package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    /** `IS` compares NULL with NULL, so this also finds the overall budget row. */
    @Query("SELECT * FROM budgets WHERE categoryId IS :categoryId AND startPeriod = :startPeriod LIMIT 1")
    suspend fun find(categoryId: Long?, startPeriod: String): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>): List<Long>

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
