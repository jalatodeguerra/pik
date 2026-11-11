package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.data.db.DebtDao
import com.pik.app.data.db.DebtEntity
import com.pik.app.data.db.DebtEntryEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.domain.DebtBalanceCheck
import com.pik.app.domain.DebtCalculator
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtFigures
import com.pik.app.domain.DebtHistory
import com.pik.app.domain.DebtHistoryItem
import com.pik.app.domain.DebtProgress
import com.pik.app.domain.DebtType
import com.pik.app.domain.InstallmentStatus
import com.pik.app.domain.Installments
import com.pik.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the user can edit on a debt (TAN is stored in aprBasisPoints). */
data class DebtInput(
    val name: String,
    val startingBalanceCents: Long,
    val originalAmountCents: Long?,
    val tanBasisPoints: Int,
    val taegBasisPoints: Int?,
    val type: DebtType,
    val minPaymentCents: Long,
    val dueDay: Int,
    val totalInstallments: Int?,
    val installmentsPaidBefore: Int?,
)

/** A debt with its calculated figures. */
data class DebtItem(
    val debt: DebtEntity,
    val figures: DebtFigures,
    /** Payments logged in Pik that count as a rata (>= 90 % of the instalment). */
    val rataCount: Int = 0,
    /** Epoch day of the latest payment that counts as a rata. */
    val lastRataEpochDay: Long? = null,
) {
    /** Instalment progress, or null when the debt has no total number of instalments. */
    fun installments(now: YearMonth = YearMonth.now()): InstallmentStatus? {
        val paidThisMonth = lastRataEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) == now } == true
        return Installments.status(debt.totalInstallments, debt.installmentsPaidBefore, rataCount, paidThisMonth, now)
    }

    val remainingCents: Long get() = DebtCalculator.remaining(figures)
    val rawRemainingCents: Long get() = DebtCalculator.rawRemaining(figures)
    val progress: DebtProgress get() = DebtCalculator.progress(figures)
}

@Singleton
class DebtRepository @Inject constructor(
    private val db: PikDatabase,
    private val dao: DebtDao,
    private val transactionDao: TransactionDao,
) {
    /** Every debt (archived last) with its calculated balance. */
    fun observeDebts(): Flow<List<DebtItem>> = combine(
        dao.observeAll(),
        dao.observeEntrySums(),
        dao.observePaymentSums(),
        dao.observeRataCounts(),
        dao.observeLastRataDays(),
    ) { debts, entrySums, paymentSums, rataCounts, rataDays ->
        val entries = entrySums.associate { it.debtId to it.sumCents }
        val payments = paymentSums.associate { it.debtId to it.sumCents }
        val counts = rataCounts.associate { it.debtId to it.sumCents.toInt() }
        val lastDays = rataDays.associate { it.debtId to it.sumCents }
        debts.map { debt ->
            DebtItem(
                debt = debt,
                figures = DebtFigures(
                    startingBalanceCents = debt.startingBalanceCents,
                    originalAmountCents = debt.originalAmountCents,
                    entriesSumCents = entries[debt.id] ?: 0L,
                    paymentsSumCents = payments[debt.id] ?: 0L,
                ),
                rataCount = counts[debt.id] ?: 0,
                lastRataEpochDay = lastDays[debt.id],
            )
        }
    }

    fun observeDebt(id: Long): Flow<DebtItem?> = observeDebts().map { list -> list.firstOrNull { it.debt.id == id } }

    /** Payments (from transactions) and entries merged, newest first. */
    fun observeHistory(debtId: Long): Flow<List<DebtHistoryItem>> = combine(
        transactionDao.observeForDebt(debtId),
        dao.observeEntries(debtId),
    ) { rows, entries ->
        DebtHistory.merge(
            payments = rows.map {
                DebtHistoryItem.Payment(it.id, it.date, it.createdAt, it.amountCents, it.accountName, it.note)
            },
            entries = entries.map {
                DebtHistoryItem.Entry(it.id, it.date, it.createdAt, it.type, it.amountCents, it.note)
            },
        )
    }

    suspend fun get(id: Long): DebtEntity? = dao.getById(id)

    suspend fun add(input: DebtInput): Long = dao.insert(
        DebtEntity(
            name = input.name.trim(),
            startingBalanceCents = input.startingBalanceCents,
            originalAmountCents = input.originalAmountCents,
            aprBasisPoints = input.tanBasisPoints,
            minPaymentCents = input.minPaymentCents,
            dueDay = input.dueDay.coerceIn(1, 28),
            sortOrder = dao.maxSortOrder() + 1,
            createdAt = System.currentTimeMillis(),
            taegBasisPoints = input.taegBasisPoints,
            type = input.type,
            totalInstallments = input.totalInstallments,
            installmentsPaidBefore = input.installmentsPaidBefore,
        ),
    )

    suspend fun update(id: Long, input: DebtInput) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = input.name.trim(),
                startingBalanceCents = input.startingBalanceCents,
                originalAmountCents = input.originalAmountCents,
                aprBasisPoints = input.tanBasisPoints,
                taegBasisPoints = input.taegBasisPoints,
                type = input.type,
                minPaymentCents = input.minPaymentCents,
                dueDay = input.dueDay.coerceIn(1, 28),
                totalInstallments = input.totalInstallments,
                installmentsPaidBefore = input.installmentsPaidBefore,
            ),
        )
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(archived = archived))
    }

    /** Number of transactions (payments) that point to the debt. */
    suspend fun paymentCount(id: Long): Int = dao.transactionCount(id)

    /**
     * Deletes the debt (its entries go with it) only when no transaction points to it.
     * Returns false when it has payments: archive it instead. transactions.debtId has no
     * foreign key, so this check is the only protection.
     */
    suspend fun deleteIfNoPayments(id: Long): Boolean = db.withTransaction {
        if (dao.transactionCount(id) > 0) {
            false
        } else {
            dao.deleteById(id)
            true
        }
    }

    // ---- entries ----

    /** INTEREST / FEE entries are stored as given (normally positive: they raise the debt). */
    suspend fun addEntry(debtId: Long, type: DebtEntryType, amountCents: Long, date: LocalDate, note: String): Long =
        dao.insertEntry(
            DebtEntryEntity(
                debtId = debtId,
                type = type,
                amountCents = amountCents,
                date = date,
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    /**
     * "Check balance": compares what the bank says you owe with Pik's calculated balance and, when
     * they differ, adds an ADJUSTMENT entry dated today with the signed difference. Returns the
     * adjustment (0 when it already matches).
     */
    suspend fun checkBalance(debtId: Long, realCents: Long, note: String): Long = db.withTransaction {
        val debt = dao.getById(debtId) ?: return@withTransaction 0L
        val raw = debt.startingBalanceCents + dao.entriesSum(debtId) - dao.paymentsSum(debtId)
        val adjustment = DebtBalanceCheck.adjustmentCents(realCents, raw)
        if (adjustment != 0L) {
            dao.insertEntry(
                DebtEntryEntity(
                    debtId = debtId,
                    type = DebtEntryType.ADJUSTMENT,
                    amountCents = adjustment,
                    date = LocalDate.now(),
                    note = note,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        adjustment
    }

    // ---- payments ----

    /**
     * A debt payment is an EXPENSE transaction from [accountId] with debtId set and no category.
     * It counts as spending and lowers the debt.
     */
    suspend fun logPayment(debtId: Long, accountId: Long, amountCents: Long, date: LocalDate, note: String): Long {
        val now = System.currentTimeMillis()
        return transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amountCents = amountCents,
                date = date,
                accountId = accountId,
                categoryId = null,
                note = note.trim(),
                debtId = debtId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
