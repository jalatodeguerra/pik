package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtType
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.TransactionType
import java.time.LocalDate

/** Plain copies of the database rows (all columns). No Room or Android types, so they are JVM testable. */
data class BackupAccount(
    val id: Long,
    val name: String,
    val type: AccountType,
    val startingBalanceCents: Long,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
)

data class BackupCategory(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val colorKey: String,
    val iconKey: String,
    val archived: Boolean,
    val sortOrder: Int,
)

data class BackupTransaction(
    val id: Long,
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val note: String,
    val debtId: Long?,
    val recurringId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupBudget(
    val id: Long,
    /** null = the overall budget. */
    val categoryId: Long?,
    val limitCents: Long,
    /** Calendar month "yyyy-MM" from which this limit applies. */
    val startPeriod: String,
    val createdAt: Long,
)

data class BackupDebt(
    val id: Long,
    val name: String,
    val startingBalanceCents: Long,
    val originalAmountCents: Long?,
    /** TAN (nominal annual rate) in basis points: 1250 = 12,50 %. The key keeps its old name. */
    val aprBasisPoints: Int,
    val minPaymentCents: Long,
    val dueDay: Int,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    /** TAEG in basis points. formatVersion 4; null in older files. */
    val taegBasisPoints: Int? = null,
    /** formatVersion 4; OTHER in older files. */
    val type: DebtType = DebtType.OTHER,
    /** Total number of instalments of the loan. formatVersion 4; null in older files. */
    val totalInstallments: Int? = null,
    /** Instalments paid before the loan was added to Pik. formatVersion 4; null in older files. */
    val installmentsPaidBefore: Int? = null,
)

data class BackupDebtEntry(
    val id: Long,
    val debtId: Long,
    val type: DebtEntryType,
    /** Signed: positive raises the debt. */
    val amountCents: Long,
    val date: LocalDate,
    val note: String,
    val createdAt: Long,
)

/** The DataStore settings that belong to the data (not the one-off flags like onboarding). */
data class BackupSettings(
    val monthStartDay: Int,
    val expectedIncomeCents: Long?,
    val extraPerMonthCents: Long,
    val payoffOrder: PayoffOrder,
)

data class BackupFile(
    val formatVersion: Int,
    val appVersion: String,
    /** ISO-8601 instant, e.g. 2026-11-10T09:30:00Z. */
    val exportedAt: String,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    /** Empty for formatVersion 1 files (they have no budgets). */
    val budgets: List<BackupBudget> = emptyList(),
    /** Empty for formatVersion 1 and 2 files (they have no debts). */
    val debts: List<BackupDebt> = emptyList(),
    val debtEntries: List<BackupDebtEntry> = emptyList(),
    /** Optional (formatVersion 3 only). null = leave the current settings alone on restore. */
    val settings: BackupSettings? = null,
)

enum class BackupError {
    /** Not JSON, or JSON without the Pik backup header. */
    NOT_A_BACKUP,
    UNSUPPORTED_VERSION,
    /** Missing or malformed fields, duplicate ids, or references to rows that are not in the file. */
    INVALID_DATA,
}

class BackupFormatException(val error: BackupError) : Exception(error.name)
