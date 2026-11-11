package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A debt. What you owe is calculated, never stored:
 * startingBalanceCents + sum(debt_entries) - sum(EXPENSE transactions with this debtId).
 *
 * [startingBalanceCents] is what you owed when you added the debt to Pik. [originalAmountCents] is
 * optional (the size of an old loan, to show how much is already paid off). [aprBasisPoints] is the
 * annual rate in basis points (1250 = 12,50 %). [dueDay] is the day of the month (1..28).
 *
 * transactions.debtId has no foreign key (that would need a table rebuild), so DebtRepository
 * refuses to delete a debt that has payments: archive it instead.
 */
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startingBalanceCents: Long,
    val originalAmountCents: Long? = null,
    val aprBasisPoints: Int,
    val minPaymentCents: Long,
    val dueDay: Int,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)
