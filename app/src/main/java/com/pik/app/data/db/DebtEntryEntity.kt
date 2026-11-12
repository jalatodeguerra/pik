package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pik.app.domain.DebtEntryType
import java.time.LocalDate

/**
 * Interest, a fee or an adjustment on a debt. Interest is stored as entries (what the lender
 * really charged), never recomputed from the rate over history.
 * [amountCents] is signed: positive raises the debt, negative lowers it.
 * [date] is a calendar day (stored as epoch day).
 */
@Entity(
    tableName = "debt_entries",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("debtId")],
)
data class DebtEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val type: DebtEntryType,
    val amountCents: Long,
    val date: LocalDate,
    val note: String = "",
    val createdAt: Long,
)
