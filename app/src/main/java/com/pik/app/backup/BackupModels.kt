package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
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

data class BackupFile(
    val formatVersion: Int,
    val appVersion: String,
    /** ISO-8601 instant, e.g. 2026-11-10T09:30:00Z. */
    val exportedAt: String,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
)

enum class BackupError {
    /** Not JSON, or JSON without the Pik backup header. */
    NOT_A_BACKUP,
    UNSUPPORTED_VERSION,
    /** Missing or malformed fields, duplicate ids, or references to rows that are not in the file. */
    INVALID_DATA,
}

class BackupFormatException(val error: BackupError) : Exception(error.name)
