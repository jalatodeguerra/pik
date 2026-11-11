package com.pik.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        DebtEntity::class, DebtEntryEntity::class, RecurringRuleEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PikDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        const val NAME = "pik.db"
    }
}
