package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** "This merchant is always this category", learned every time an inbox item is added with a category. */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class MerchantRuleEntity(
    /** The merchant name from domain.importing.MerchantKey.normalise. */
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long,
)
