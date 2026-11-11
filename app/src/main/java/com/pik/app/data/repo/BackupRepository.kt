package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.backup.BackupAccount
import com.pik.app.backup.BackupBudget
import com.pik.app.backup.BackupCategory
import com.pik.app.backup.BackupDebt
import com.pik.app.backup.BackupDebtEntry
import com.pik.app.backup.BackupSettings
import com.pik.app.backup.BackupFile
import com.pik.app.backup.BackupJson
import com.pik.app.backup.BackupTransaction
import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.BudgetDao
import com.pik.app.data.db.BudgetEntity
import com.pik.app.data.db.CategoryDao
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.DebtDao
import com.pik.app.data.db.DebtEntity
import com.pik.app.data.db.DebtEntryEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.data.prefs.PikPreferences
import kotlinx.coroutines.flow.first
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
    private val budgetDao: BudgetDao,
    private val debtDao: DebtDao,
    private val prefs: PikPreferences,
) {
    /** Consistent snapshot of every row, serialized as the backup JSON text. */
    suspend fun export(appVersion: String): String {
        val settings = BackupSettings(
            monthStartDay = prefs.monthStartDay.first(),
            expectedIncomeCents = prefs.expectedIncomeCents.first(),
            extraPerMonthCents = prefs.extraPerMonthCents.first(),
            payoffOrder = prefs.payoffOrder.first(),
        )
        val file = db.withTransaction {
            BackupFile(
                formatVersion = BackupJson.FORMAT_VERSION,
                appVersion = appVersion,
                exportedAt = Instant.now().toString(),
                accounts = accountDao.getAll().map { it.toBackup() },
                categories = categoryDao.getAll().map { it.toBackup() },
                transactions = transactionDao.getAll().map { it.toBackup() },
                budgets = budgetDao.getAll().map { it.toBackup() },
                debts = debtDao.getAll().map { it.toBackup() },
                debtEntries = debtDao.getAllEntries().map { it.toBackup() },
                settings = settings,
            )
        }
        return BackupJson.serialize(file)
    }

    /**
     * Deletes everything and inserts the backup rows with their original ids, all in one Room
     * transaction: if anything fails, nothing changes. The file must already be validated.
     * The settings of the file (when it has them) are written to DataStore afterwards.
     */
    suspend fun replaceAll(file: BackupFile) {
        BackupJson.validate(file)
        db.withTransaction {
            transactionDao.deleteAll()
            budgetDao.deleteAll()
            categoryDao.deleteAll()
            accountDao.deleteAll()
            debtDao.deleteAllEntries()
            debtDao.deleteAll()
            accountDao.insertAll(file.accounts.map { it.toEntity() })
            categoryDao.insertAll(file.categories.map { it.toEntity() })
            debtDao.insertAll(file.debts.map { it.toEntity() })
            debtDao.insertEntries(file.debtEntries.map { it.toEntity() })
            transactionDao.insertAll(file.transactions.map { it.toEntity() })
            budgetDao.insertAll(file.budgets.map { it.toEntity() })
        }
        file.settings?.let {
            prefs.restorePlanSettings(it.monthStartDay, it.expectedIncomeCents, it.extraPerMonthCents, it.payoffOrder)
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

private fun BudgetEntity.toBackup() = BackupBudget(id, categoryId, limitCents, startPeriod, createdAt)

private fun DebtEntity.toBackup() = BackupDebt(
    id = id, name = name, startingBalanceCents = startingBalanceCents, originalAmountCents = originalAmountCents,
    aprBasisPoints = aprBasisPoints, minPaymentCents = minPaymentCents, dueDay = dueDay, archived = archived,
    sortOrder = sortOrder, createdAt = createdAt, taegBasisPoints = taegBasisPoints, type = type,
    totalInstallments = totalInstallments, installmentsPaidBefore = installmentsPaidBefore,
)

private fun DebtEntryEntity.toBackup() = BackupDebtEntry(id, debtId, type, amountCents, date, note, createdAt)

private fun BackupDebt.toEntity() = DebtEntity(
    id = id, name = name, startingBalanceCents = startingBalanceCents, originalAmountCents = originalAmountCents,
    aprBasisPoints = aprBasisPoints, minPaymentCents = minPaymentCents, dueDay = dueDay, archived = archived,
    sortOrder = sortOrder, createdAt = createdAt, taegBasisPoints = taegBasisPoints, type = type,
    totalInstallments = totalInstallments, installmentsPaidBefore = installmentsPaidBefore,
)

private fun BackupDebtEntry.toEntity() = DebtEntryEntity(id, debtId, type, amountCents, date, note, createdAt)

private fun BackupBudget.toEntity() = BudgetEntity(id, categoryId, limitCents, startPeriod, createdAt)

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
