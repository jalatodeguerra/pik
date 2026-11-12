package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.data.db.DebtDao
import com.pik.app.data.db.PikDatabase
import com.pik.app.data.db.RecurringDao
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.data.db.TransactionDao
import com.pik.app.data.db.TransactionEntity
import com.pik.app.domain.RecurrenceCalculator
import com.pik.app.domain.RecurrenceSchedule
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the user edits on a bill, subscription or recurring income. */
data class RecurringInput(
    val name: String,
    val kind: RecurringKind,
    val amountCents: Long,
    val accountId: Long,
    val categoryId: Long?,
    val note: String,
    val startDate: LocalDate,
    val intervalCount: Int,
    val unit: RecurrenceUnit,
    /** For months and years; ignored (stored as null) for weeks. */
    val dayOfMonth: Int?,
    val endDate: LocalDate?,
    val maxOccurrences: Int?,
    val remindDayBefore: Boolean,
)

@Singleton
class RecurringRepository @Inject constructor(
    private val db: PikDatabase,
    private val dao: RecurringDao,
    private val transactionDao: TransactionDao,
    private val debtDao: DebtDao,
) {
    /** Every rule, soonest next date first. */
    fun observeRules(): Flow<List<RecurringRuleEntity>> = dao.observeAll()

    /** True while the debt has a recurring payment rule. */
    fun observeHasDebtRule(debtId: Long): Flow<Boolean> = dao.observeCountForDebt(debtId).map { it > 0 }

    suspend fun get(id: Long): RecurringRuleEntity? = dao.getById(id)

    suspend fun getByDebt(debtId: Long): RecurringRuleEntity? = dao.getByDebtId(debtId)

    /** The active rules (for the daily reminder). */
    suspend fun getActiveRules(): List<RecurringRuleEntity> = dao.getActive()

    // ---- add / edit / delete ----

    /**
     * Creates the rule ([id] null) or updates it. History is never backfilled: when a rule is created, or
     * when its start date, interval or unit change, the series restarts and the occurrences before the first
     * day of the current month count as done. Changing only the day, the end, the amount and so on keeps
     * the occurrences already done. A rule that belongs to a debt only takes the account, the reminder and
     * the note (the debt owns amount, day and name).
     */
    suspend fun save(id: Long?, input: RecurringInput, today: LocalDate = LocalDate.now()): Long = db.withTransaction {
        val existing = id?.let { dao.getById(it) }
        if (existing != null && existing.debtId != null) {
            dao.update(
                existing.copy(
                    accountId = input.accountId,
                    remindDayBefore = input.remindDayBefore,
                    note = input.note.trim(),
                ),
            )
            return@withTransaction existing.id
        }
        val isWeekly = input.unit == RecurrenceUnit.WEEK
        val every = input.intervalCount.coerceAtLeast(1)
        val day = if (isWeekly) null else (input.dayOfMonth ?: input.startDate.dayOfMonth).coerceIn(1, 31)
        val sameSeries = existing != null && existing.startDate == input.startDate &&
            existing.intervalCount == every && existing.intervalUnit == input.unit
        val schedule: RecurrenceSchedule
        val done: Int
        if (existing != null && sameSeries) {
            schedule = RecurrenceSchedule(existing.startDate, every, input.unit, day, input.endDate, input.maxOccurrences)
            done = existing.occurrencesDone
        } else {
            schedule = RecurrenceCalculator.normalized(
                RecurrenceSchedule(input.startDate, every, input.unit, day, input.endDate, input.maxOccurrences),
            )
            done = RecurrenceCalculator.countBefore(schedule, firstOfMonth(today))
        }
        val finished = RecurrenceCalculator.isFinished(schedule, done)
        val rule = RecurringRuleEntity(
            id = existing?.id ?: 0L,
            name = input.name.trim(),
            kind = input.kind,
            transactionType = if (input.kind == RecurringKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
            amountCents = input.amountCents,
            accountId = input.accountId,
            categoryId = input.categoryId,
            debtId = null,
            note = input.note.trim(),
            startDate = schedule.startDate,
            intervalCount = every,
            intervalUnit = input.unit,
            dayOfMonth = day,
            endDate = input.endDate,
            maxOccurrences = input.maxOccurrences,
            occurrencesDone = done,
            nextDueDate = RecurrenceCalculator.nextDueDate(schedule, done),
            remindDayBefore = input.remindDayBefore,
            active = !finished,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        if (existing == null) dao.insert(rule) else {
            dao.update(rule)
            rule.id
        }
    }

    /** Deletes the rule. The transactions it created stay as they are (their recurringId is not touched). */
    suspend fun delete(id: Long) = dao.deleteById(id)

    // ---- confirm / skip ----

    /**
     * Confirms the next pending occurrence of the rule: creates the transaction (with recurringId, and the
     * debtId for a debt payment, which has no category) on [date] for [amountCents] from [accountId], then moves
     * the rule on to the following occurrence. Returns the new transaction id, or null when there was nothing
     * to confirm (the rule is gone, inactive or finished, or the amount is not positive).
     */
    suspend fun confirm(ruleId: Long, amountCents: Long, accountId: Long, date: LocalDate): Long? = db.withTransaction {
        val rule = dao.getById(ruleId) ?: return@withTransaction null
        if (!rule.active || amountCents <= 0L) return@withTransaction null
        if (RecurrenceCalculator.isFinished(rule.schedule, rule.occurrencesDone)) return@withTransaction null
        val now = System.currentTimeMillis()
        val txId = transactionDao.insert(
            TransactionEntity(
                type = rule.transactionType,
                amountCents = amountCents,
                date = date,
                accountId = accountId,
                categoryId = if (rule.debtId != null) null else rule.categoryId,
                note = rule.note.ifBlank { if (rule.debtId == null) rule.name else "" },
                debtId = rule.debtId,
                recurringId = rule.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        advance(rule)
        txId
    }

    /** Skips the next pending occurrence: the rule moves on and no transaction is created. */
    suspend fun skip(ruleId: Long) = db.withTransaction {
        val rule = dao.getById(ruleId) ?: return@withTransaction
        if (!rule.active || RecurrenceCalculator.isFinished(rule.schedule, rule.occurrencesDone)) return@withTransaction
        advance(rule)
    }

    private suspend fun advance(rule: RecurringRuleEntity) {
        val done = rule.occurrencesDone + 1
        val schedule = rule.schedule
        dao.update(
            rule.copy(
                occurrencesDone = done,
                nextDueDate = RecurrenceCalculator.nextDueDate(schedule, done),
                active = !RecurrenceCalculator.isFinished(schedule, done),
            ),
        )
    }

    // ---- debts own a schedule ----

    /**
     * Makes the debt's payment rule match the debt. With [enabled] and a minimum payment above zero: creates the
     * DEBT_PAYMENT rule (amount = the minimum payment or instalment, day = the debt's due day, name = the debt's
     * name, account = [accountId]) or updates the existing one's amount, day and name. Otherwise deletes the rule.
     * A new rule starts on the due day of this month when it is still ahead and this month's instalment was not
     * paid yet, otherwise next month, so linking a debt never makes up overdue payments.
     */
    suspend fun syncDebtRule(debtId: Long, enabled: Boolean, accountId: Long, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val debt = debtDao.getById(debtId) ?: return@withTransaction
            val rule = dao.getByDebtId(debtId)
            if (!enabled || debt.minPaymentCents <= 0L) {
                if (rule != null) dao.deleteById(rule.id)
                return@withTransaction
            }
            if (rule == null) {
                val thisMonth = YearMonth.from(today).atDay(debt.dueDay.coerceIn(1, 28))
                val paidThisMonth = debtDao.rataPaymentCountSince(debtId, firstOfMonth(today).toEpochDay()) > 0
                val start = if (thisMonth < today || paidThisMonth) thisMonth.plusMonths(1) else thisMonth
                val schedule = RecurrenceSchedule(start, 1, RecurrenceUnit.MONTH, debt.dueDay)
                dao.insert(
                    RecurringRuleEntity(
                        name = debt.name,
                        kind = RecurringKind.DEBT_PAYMENT,
                        transactionType = TransactionType.EXPENSE,
                        amountCents = debt.minPaymentCents,
                        accountId = accountId,
                        debtId = debtId,
                        startDate = start,
                        intervalCount = 1,
                        intervalUnit = RecurrenceUnit.MONTH,
                        dayOfMonth = debt.dueDay,
                        occurrencesDone = 0,
                        nextDueDate = RecurrenceCalculator.nextDueDate(schedule, 0),
                        active = !debt.archived,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                val schedule = rule.schedule.copy(dayOfMonth = debt.dueDay)
                dao.update(
                    rule.copy(
                        name = debt.name,
                        amountCents = debt.minPaymentCents,
                        dayOfMonth = debt.dueDay,
                        nextDueDate = RecurrenceCalculator.nextDueDate(schedule, rule.occurrencesDone),
                    ),
                )
            }
        }
    }

    /**
     * Archiving a debt deactivates its rule, unarchiving it activates the rule again. On activation the
     * occurrences before the first day of this month count as done, so months that passed while the debt was
     * archived do not come back as overdue.
     */
    suspend fun setDebtRuleActive(debtId: Long, active: Boolean, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val rule = dao.getByDebtId(debtId) ?: return@withTransaction
            if (!active) {
                dao.update(rule.copy(active = false))
                return@withTransaction
            }
            val schedule = rule.schedule
            val done = maxOf(rule.occurrencesDone, RecurrenceCalculator.countBefore(schedule, firstOfMonth(today)))
            dao.update(
                rule.copy(
                    occurrencesDone = done,
                    nextDueDate = RecurrenceCalculator.nextDueDate(schedule, done),
                    active = !RecurrenceCalculator.isFinished(schedule, done),
                ),
            )
        }
    }

    private fun firstOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(1)
}
