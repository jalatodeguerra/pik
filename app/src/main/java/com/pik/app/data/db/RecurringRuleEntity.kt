package com.pik.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pik.app.domain.RecurrenceSchedule
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.RecurringRuleFacts
import com.pik.app.domain.TransactionType
import java.time.LocalDate

/**
 * A recurring rule: a bill, a subscription, a repeating income or the payment of a debt.
 * Nothing is ever logged automatically: each occurrence waits in the "Due" list until the user
 * confirms (a transaction is created) or skips it.
 *
 * [transactionType] is EXPENSE for BILL, SUBSCRIPTION and DEBT_PAYMENT, INCOME for INCOME. A
 * DEBT_PAYMENT rule has a [debtId] (and no category): its amount and day come from the debt, which
 * owns the schedule. The schedule is "every [intervalCount] [intervalUnit]s from [startDate]", on
 * [dayOfMonth] for months and years (clamped to the last day of a short month), ending on [endDate]
 * and/or after [maxOccurrences] occurrences (see RecurrenceCalculator).
 *
 * [occurrencesDone] counts the occurrences already confirmed or skipped; the next one to handle is
 * occurrence number [occurrencesDone], dated [nextDueDate] (when the series is over it keeps the date
 * of the last occurrence and [active] becomes false). Deleting a rule keeps the transactions it
 * created: their recurringId (no foreign key) just stays as it is.
 *
 * Foreign keys: the account cannot be deleted while a rule uses it (RESTRICT), a deleted category
 * clears [categoryId] (SET NULL), a deleted debt takes its rule with it (CASCADE).
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("debtId"),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: RecurringKind,
    val transactionType: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val debtId: Long? = null,
    val note: String = "",
    val startDate: LocalDate,
    val intervalCount: Int,
    val intervalUnit: RecurrenceUnit,
    val dayOfMonth: Int? = null,
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
    @ColumnInfo(defaultValue = "0") val occurrencesDone: Int = 0,
    val nextDueDate: LocalDate,
    val remindDayBefore: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long,
) {
    val schedule: RecurrenceSchedule
        get() = RecurrenceSchedule(startDate, intervalCount, intervalUnit, dayOfMonth, endDate, maxOccurrences)

    fun toFacts(): RecurringRuleFacts =
        RecurringRuleFacts(id, kind, amountCents, schedule, occurrencesDone, active, remindDayBefore)
}
