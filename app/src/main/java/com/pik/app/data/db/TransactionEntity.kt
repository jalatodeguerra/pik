package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pik.app.domain.TransactionType
import java.time.LocalDate

/**
 * amountCents is positive for EXPENSE / INCOME / TRANSFER.
 * ADJUSTMENT is signed: positive adds to the account balance, negative removes.
 * [date] is a calendar day (stored as epoch day), not a timestamp.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("date"),
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    /**
     * Set on an EXPENSE that pays a debt (then categoryId is null). No foreign key: DebtRepository
     * refuses to delete a debt that has payments.
     */
    val debtId: Long? = null,
    /** Reserved for recurring transactions (no foreign key yet). */
    val recurringId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
