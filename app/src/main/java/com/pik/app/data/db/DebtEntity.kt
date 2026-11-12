package com.pik.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pik.app.domain.DebtType

/**
 * A debt. What you owe is calculated, never stored:
 * startingBalanceCents + sum(debt_entries) - sum(EXPENSE transactions with this debtId).
 *
 * [startingBalanceCents] is what you owed when you added the debt to Pik. [originalAmountCents] is
 * optional (the size of an old loan, to show how much is already paid off). [aprBasisPoints] is the
 * TAN (tasso annuo nominale) in basis points (1250 = 12,50 %): the rate used for all interest maths
 * (the column keeps its old name so no table rebuild is needed). [taegBasisPoints] is the optional TAEG
 * (tasso annuo effettivo globale, total yearly cost including fees): display and payoff ranking only.
 * [dueDay] is the day of the month (1..28). [minPaymentCents] is the minimum payment, or the monthly
 * instalment (rata) for loan types with [DebtType.hasInstalments].
 *
 * [totalInstallments] is the number of instalments of the whole loan (e.g. 60) and
 * [installmentsPaidBefore] how many were already paid before the loan was added to Pik; both are null
 * when not tracked. Instalments paid in Pik are counted from the payments (see Installments).
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
    val taegBasisPoints: Int? = null,
    // Room compares the default with the DEFAULT of the migration: a string default keeps its quotes.
    @ColumnInfo(defaultValue = "'OTHER'") val type: DebtType = DebtType.OTHER,
    val totalInstallments: Int? = null,
    val installmentsPaidBefore: Int? = null,
)
