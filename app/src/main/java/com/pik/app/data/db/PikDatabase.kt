package com.pik.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        DebtEntity::class, DebtEntryEntity::class, RecurringRuleEntity::class,
        ImportItemEntity::class, MerchantRuleEntity::class,
    ],
    version = 6,
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
    abstract fun importDao(): ImportDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        const val NAME = "pik.db"
    }
}
