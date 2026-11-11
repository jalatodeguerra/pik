package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.backup.BackupAccount
import com.pik.app.backup.BackupCategory
import com.pik.app.backup.BackupFile
import com.pik.app.backup.BackupJson
import com.pik.app.backup.BackupTransaction
import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryDao
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the whole database into a [BackupFile] and replaces it from one. */
@Singleton
class BackupRepository @Inject constructor(
    private val db: PikDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {
    /** Consistent snapshot of every row, serialized as the backup JSON text. */
    suspend fun export(appVersion: String): String {
        val file = db.withTransaction {
            BackupFile(
                formatVersion = BackupJson.FORMAT_VERSION,
                appVersion = appVersion,
                exportedAt = Instant.now().toString(),
                accounts = accountDao.getAll().map { it.toBackup() },
                categories = categoryDao.getAll().map { it.toBackup() },
                transactions = transactionDao.getAll().map { it.toBackup() },
            )
        }
        return BackupJson.serialize(file)
    }

    /**
     * Deletes everything and inserts the backup rows with their original ids, all in one Room
     * transaction: if anything fails, nothing changes. The file must already be validated.
     */
    suspend fun replaceAll(file: BackupFile) {
        BackupJson.validate(file)
        db.withTransaction {
            transactionDao.deleteAll()
            categoryDao.deleteAll()
            accountDao.deleteAll()
            accountDao.insertAll(file.accounts.map { it.toEntity() })
            categoryDao.insertAll(file.categories.map { it.toEntity() })
            transactionDao.insertAll(file.transactions.map { it.toEntity() })
        }
    }
}

private fun AccountEntity.toBackup() =
    BackupAccount(id, name, type, startingBalanceCents, archived, sortOrder, createdAt)

private fun CategoryEntity.toBackup() =
    BackupCategory(id, name, kind, colorKey, iconKey, archived, sortOrder)

private fun TransactionEntity.toBackup() = BackupTransaction(
    id, type, amountCents, date, accountId, toAccountId, categoryId, note, debtId, recurringId,
    createdAt, updatedAt,
)

private fun BackupAccount.toEntity() =
    AccountEntity(id, name, type, startingBalanceCents, archived, sortOrder, createdAt)

private fun BackupCategory.toEntity() =
    CategoryEntity(id, name, kind, colorKey, iconKey, archived, sortOrder)

private fun BackupTransaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    amountCents = amountCents,
    date = date,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    debtId = debtId,
    recurringId = recurringId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
