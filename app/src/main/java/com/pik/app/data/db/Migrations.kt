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

/**
 * Version 4 -> 5: adds the recurring_rules table and its three indices. Existing tables are untouched
 * (transactions.recurringId already exists and has no foreign key). The SQL must match what Room
 * generates for [RecurringRuleEntity] (see app/schemas/.../5.json): columns in the order of the
 * entity, occurrencesDone has DEFAULT 0, and the three foreign keys are RESTRICT (account),
 * SET NULL (category) and CASCADE (debt).
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`transactionType` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`debtId` INTEGER, " +
                "`note` TEXT NOT NULL, " +
                "`startDate` INTEGER NOT NULL, " +
                "`intervalCount` INTEGER NOT NULL, " +
                "`intervalUnit` TEXT NOT NULL, " +
                "`dayOfMonth` INTEGER, " +
                "`endDate` INTEGER, " +
                "`maxOccurrences` INTEGER, " +
                "`occurrencesDone` INTEGER NOT NULL DEFAULT 0, " +
                "`nextDueDate` INTEGER NOT NULL, " +
                "`remindDayBefore` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` ON `recurring_rules` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` ON `recurring_rules` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_debtId` ON `recurring_rules` (`debtId`)")
    }
}

/**
 * Version 5 -> 6: adds the import_items table (the automatic import inbox) and the merchant_rules table.
 * Existing tables are untouched. The SQL must match what Room generates for [ImportItemEntity] and
 * [MerchantRuleEntity] (see app/schemas/.../6.json): the item's account is SET NULL, the rule's category
 * is CASCADE, sourceKey has a unique index.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `import_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`sourceKey` TEXT NOT NULL, " +
                "`accountId` INTEGER, " +
                "`direction` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`merchant` TEXT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`rawText` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`suggestion` TEXT NOT NULL, " +
                "`transferAccountId` INTEGER, " +
                "`suggestedCategoryId` INTEGER, " +
                "`transactionId` INTEGER, " +
                "`restored` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_import_items_sourceKey` ON `import_items` (`sourceKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_status` ON `import_items` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_date` ON `import_items` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_items_accountId` ON `import_items` (`accountId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                "`merchantKey` TEXT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`merchantKey`), " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_rules_categoryId` ON `merchant_rules` (`categoryId`)")
    }
}
