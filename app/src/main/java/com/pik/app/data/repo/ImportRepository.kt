package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.ImportDao
import com.pik.app.data.db.ImportItemEntity
import com.pik.app.data.db.MerchantRuleDao
import com.pik.app.data.db.MerchantRuleEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.domain.AccountType
import com.pik.app.domain.TransactionType
import com.pik.app.domain.importing.AccountRef
import com.pik.app.domain.importing.ImportAppConfig
import com.pik.app.domain.importing.ImportDirection
import com.pik.app.domain.importing.ImportKeys
import com.pik.app.domain.importing.ImportMatcher
import com.pik.app.domain.importing.ImportSource
import com.pik.app.domain.importing.ImportStatus
import com.pik.app.domain.importing.ImportSuggestion
import com.pik.app.domain.importing.ItemRef
import com.pik.app.domain.importing.MatchContext
import com.pik.app.domain.importing.MerchantKey
import com.pik.app.domain.importing.ParsedPayment
import com.pik.app.domain.importing.StatementRow
import com.pik.app.domain.importing.TxRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How many rows of a file are new in the Inbox and how many were already known. */
data class FileImportResult(val added: Int, val alreadyKnown: Int)

/** What happened when the user pressed "Add" on an inbox item. */
enum class AddItemResult { ADDED, NEEDS_ACCOUNT, NEEDS_CATEGORY, NOT_PENDING }

@Singleton
class ImportRepository @Inject constructor(
    private val db: PikDatabase,
    private val dao: ImportDao,
    private val ruleDao: MerchantRuleDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val prefs: PikPreferences,
) {
    fun observePending(): Flow<List<ImportItemEntity>> = dao.observePending()

    fun observeDuplicates(): Flow<List<ImportItemEntity>> = dao.observeDuplicates()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun get(id: Long): ImportItemEntity? = dao.getById(id)

    // ---- new items ----

    /**
     * Stores a payment found in a notification as a PENDING item for [accountId] (null when the app is not
     * linked to an account yet). Returns false when the same notification was already stored.
     */
    suspend fun addNotification(
        sourceKey: String,
        source: ImportSource,
        accountId: Long?,
        payment: ParsedPayment,
        date: LocalDate,
        rawText: String,
    ): Boolean {
        val inserted = dao.insert(
            ImportItemEntity(
                source = source,
                sourceKey = sourceKey,
                accountId = accountId,
                direction = payment.direction,
                amountCents = payment.amountCents,
                merchant = payment.merchant,
                date = date,
                rawText = rawText.take(MAX_RAW_TEXT),
                status = ImportStatus.PENDING,
                suggestion = ImportMatcher.defaultSuggestion(payment.direction),
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (inserted == -1L) return false
        runMatching()
        return true
    }

    /** Stores the rows of a statement file as PENDING items of [accountId]; rows seen before are ignored. */
    suspend fun addFileRows(accountId: Long, rows: List<StatementRow>): FileImportResult {
        var added = 0
        var known = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (row in rows) {
                val inserted = dao.insert(
                    ImportItemEntity(
                        source = ImportSource.FILE,
                        sourceKey = ImportKeys.fileRowKey(accountId, row),
                        accountId = accountId,
                        direction = row.direction,
                        amountCents = row.amountCents,
                        merchant = row.merchant,
                        date = row.date,
                        rawText = row.rawText.take(MAX_RAW_TEXT),
                        status = ImportStatus.PENDING,
                        suggestion = ImportMatcher.defaultSuggestion(row.direction),
                        createdAt = now,
                    ),
                )
                if (inserted == -1L) known++ else added++
            }
        }
        if (added > 0) runMatching()
        return FileImportResult(added, known)
    }

    // ---- housekeeping ----

    /** Called when the Inbox opens: forgets old handled items and checks the pending ones again. */
    suspend fun refresh() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HANDLED_KEEP_DAYS)
        dao.deleteHandledBefore(cutoff)
        runMatching()
    }

    /** Duplicates, transfers and category suggestions for every PENDING item. */
    private suspend fun runMatching() {
        val importApps = prefs.importApps.first()
        db.withTransaction {
            val pending = dao.getByStatus(ImportStatus.PENDING)
            if (pending.isEmpty()) return@withTransaction
            val others = dao.getNotDismissed()
            val first = pending.minOf { it.date.toEpochDay() } - ImportMatcher.DATE_TOLERANCE_DAYS
            val last = pending.maxOf { it.date.toEpochDay() } + ImportMatcher.DATE_TOLERANCE_DAYS
            val transactions = transactionDao.getBetween(first, last).map {
                TxRef(it.id, it.type, it.amountCents, it.date, it.accountId, it.toAccountId)
            }
            val accounts = accountDao.getAll().filter { !it.archived }.map {
                AccountRef(it.id, it.name, it.type == AccountType.BANK)
            }
            val bankIds = importApps
                .filter { it.source == ImportSource.RELAX || it.source == ImportSource.NEXI }
                .mapNotNull { it.accountId }
            val context = MatchContext(
                accounts = accounts,
                transactions = transactions,
                linkedTransactionIds = dao.getLinkedTransactionIds().toSet(),
                otherItems = others.map { it.toRef() },
                rules = ruleDao.getAll().associate { it.merchantKey to it.categoryId },
                bankAccountId = ImportMatcher.pickBankAccount(accounts, bankIds),
            )
            val results = ImportMatcher.matchPending(pending.map { it.toRef() }, context).associateBy { it.itemId }
            for (item in pending) {
                val r = results[item.id] ?: continue
                val updated = item.copy(
                    status = r.status,
                    suggestion = r.suggestion,
                    transferAccountId = r.transferAccountId,
                    suggestedCategoryId = r.suggestedCategoryId,
                )
                if (updated != item) dao.update(updated)
            }
        }
    }

    // ---- what the user does with an item ----

    /** Remembers the category the user chose for this item (it is also learned when the item is added). */
    suspend fun setCategory(id: Long, categoryId: Long?) {
        val item = dao.getById(id) ?: return
        dao.update(item.copy(suggestedCategoryId = categoryId))
    }

    suspend fun setAccount(id: Long, accountId: Long) {
        val item = dao.getById(id) ?: return
        dao.update(item.copy(accountId = accountId))
        runMatching()
    }

    suspend fun dismiss(id: Long) {
        val item = dao.getById(id) ?: return
        if (item.status == ImportStatus.ADDED) return
        dao.update(item.copy(status = ImportStatus.DISMISSED))
    }

    /** Brings back an item that was marked as "Already in Pik" (it is not checked for duplicates again). */
    suspend fun restore(id: Long) {
        val item = dao.getById(id) ?: return
        if (item.status != ImportStatus.DUPLICATE) return
        dao.update(item.copy(status = ImportStatus.PENDING, restored = true))
    }

    /**
     * Creates the transaction for an item with the data it has (the suggestion decides expense, income or
     * transfer) and marks the item added. The category is [categoryId] when given, else the suggested one.
     * Needs an account, and for an expense or income a category: otherwise nothing changes and the result says what is missing.
     */
    suspend fun addToPik(id: Long, accountId: Long? = null, categoryId: Long? = null): AddItemResult =
        db.withTransaction {
            val item = dao.getById(id) ?: return@withTransaction AddItemResult.NOT_PENDING
            if (item.status != ImportStatus.PENDING) return@withTransaction AddItemResult.NOT_PENDING
            val account = accountId ?: item.accountId ?: return@withTransaction AddItemResult.NEEDS_ACCOUNT
            val now = System.currentTimeMillis()
            val entity: TransactionEntity
            var learnedCategory: Long? = null
            val transfer = item.transferAccountId
            when {
                item.suggestion == ImportSuggestion.TRANSFER_OUT && transfer != null && transfer != account -> {
                    entity = TransactionEntity(
                        type = TransactionType.TRANSFER, amountCents = item.amountCents, date = item.date,
                        accountId = account, toAccountId = transfer, note = item.merchant.take(MAX_NOTE),
                        createdAt = now, updatedAt = now,
                    )
                }
                item.suggestion == ImportSuggestion.TRANSFER_IN && transfer != null && transfer != account -> {
                    entity = TransactionEntity(
                        type = TransactionType.TRANSFER, amountCents = item.amountCents, date = item.date,
                        accountId = transfer, toAccountId = account, note = item.merchant.take(MAX_NOTE),
                        createdAt = now, updatedAt = now,
                    )
                }
                else -> {
                    val category = categoryId ?: item.suggestedCategoryId ?: return@withTransaction AddItemResult.NEEDS_CATEGORY
                    learnedCategory = category
                    entity = TransactionEntity(
                        type = if (item.direction == ImportDirection.OUT) TransactionType.EXPENSE else TransactionType.INCOME,
                        amountCents = item.amountCents, date = item.date, accountId = account,
                        categoryId = category, note = item.merchant.take(MAX_NOTE),
                        createdAt = now, updatedAt = now,
                    )
                }
            }
            val txId = transactionDao.insert(entity)
            dao.update(item.copy(status = ImportStatus.ADDED, transactionId = txId, accountId = account))
            learnedCategory?.let { learn(item.merchant, it, now) }
            AddItemResult.ADDED
        }

    /** Adds every pending expense / income that has a suggested category and an account. Returns how many were added. */
    suspend fun addAllWithCategory(): Int {
        var count = 0
        for (item in dao.getByStatus(ImportStatus.PENDING)) {
            val isTransfer = item.suggestion == ImportSuggestion.TRANSFER_OUT || item.suggestion == ImportSuggestion.TRANSFER_IN
            if (isTransfer || item.suggestedCategoryId == null || item.accountId == null) continue
            if (addToPik(item.id) == AddItemResult.ADDED) count++
        }
        return count
    }

    /**
     * Called by the Add screen after the user saved an item through "Edit": links the new transaction to the
     * item and learns the merchant's category.
     */
    suspend fun markAdded(id: Long, transactionId: Long, categoryId: Long?) {
        db.withTransaction {
            val item = dao.getById(id) ?: return@withTransaction
            dao.update(item.copy(status = ImportStatus.ADDED, transactionId = transactionId))
            if (categoryId != null) learn(item.merchant, categoryId, System.currentTimeMillis())
        }
    }

    private suspend fun learn(merchant: String, categoryId: Long, now: Long) {
        val key = MerchantKey.normalise(merchant)
        if (key.isEmpty()) return
        ruleDao.upsert(MerchantRuleEntity(key, categoryId, now))
    }

    private fun ImportItemEntity.toRef() = ItemRef(
        id = id, source = source, accountId = accountId, direction = direction, amountCents = amountCents,
        merchant = merchant, rawText = rawText, date = date, status = status, suggestion = suggestion,
        transferAccountId = transferAccountId, suggestedCategoryId = suggestedCategoryId, restored = restored,
    )

    private companion object {
        const val HANDLED_KEEP_DAYS = 60L
        const val MAX_RAW_TEXT = 500
        const val MAX_NOTE = 120
    }
}

/** The import apps (package, source, account) a notification is checked against. */
fun List<ImportAppConfig>.forPackage(packageName: String): ImportAppConfig? = firstOrNull { it.packageName == packageName }
