package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spending limit. [categoryId] null is the overall budget (total spending).
 * [startPeriod] is the calendar month "yyyy-MM" of the period start from which the limit applies;
 * the limit of a period is the row with the latest startPeriod <= the period's key.
 * limitCents == 0 means "no budget from this period on".
 *
 * SQLite treats NULLs as distinct in a UNIQUE index, so uniqueness of the overall budget per
 * startPeriod is enforced by BudgetRepository, not by the index.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["categoryId", "startPeriod"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val limitCents: Long,
    val startPeriod: String,
    val createdAt: Long,
)
