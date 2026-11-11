package com.pik.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 -> 2: adds the budgets table (and its unique index). Existing tables are untouched.
 * The SQL must match what Room generates for [BudgetEntity] (see app/schemas/.../2.json).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`limitCents` INTEGER NOT NULL, " +
                "`startPeriod` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_startPeriod` " +
                "ON `budgets` (`categoryId`, `startPeriod`)",
        )
    }
}

/**
 * Version 2 -> 3: adds the debts and debt_entries tables (and the index on debt_entries.debtId).
 * Existing tables are untouched; transactions.debtId already exists (no foreign key).
 * The SQL must match what Room generates for [DebtEntity] and [DebtEntryEntity]
 * (see app/schemas/.../3.json).
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`startingBalanceCents` INTEGER NOT NULL, " +
                "`originalAmountCents` INTEGER, " +
                "`aprBasisPoints` INTEGER NOT NULL, " +
                "`minPaymentCents` INTEGER NOT NULL, " +
                "`dueDay` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debt_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`debtId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_entries_debtId` ON `debt_entries` (`debtId`)")
    }
}

/**
 * Version 3 -> 4: debts get the TAEG, the loan type and the instalment counters. Only ADD COLUMN, so
 * existing debts keep all their data (type becomes 'OTHER', the rest NULL). The SQL must match what Room
 * generates for [DebtEntity] (see app/schemas/.../4.json): the type default is 'OTHER' (with quotes).
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `taegBasisPoints` INTEGER")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `totalInstallments` INTEGER")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `installmentsPaidBefore` INTEGER")
    }
}
