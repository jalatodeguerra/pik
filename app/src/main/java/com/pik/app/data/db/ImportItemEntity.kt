package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pik.app.domain.importing.ImportDirection
import com.pik.app.domain.importing.ImportSource
import com.pik.app.domain.importing.ImportStatus
import com.pik.app.domain.importing.ImportSuggestion
import java.time.LocalDate

/**
 * A payment found by the automatic import (a notification or a statement file row) that the user has not
 * handled yet. [amountCents] is always positive; [direction] says whether money went out or came in.
 * [sourceKey] is a hash that is the same when the same payment is read twice (unique, so the second insert
 * is ignored). [transferAccountId] and [suggestedCategoryId] have no foreign key: they are only suggestions.
 */
@Entity(
    tableName = "import_items",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index("status"),
        Index("date"),
        Index("accountId"),
    ],
)
data class ImportItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: ImportSource,
    val sourceKey: String,
    val accountId: Long?,
    val direction: ImportDirection,
    val amountCents: Long,
    val merchant: String,
    val date: LocalDate,
    val rawText: String,
    val status: ImportStatus,
    val suggestion: ImportSuggestion,
    val transferAccountId: Long? = null,
    val suggestedCategoryId: Long? = null,
    val transactionId: Long? = null,
    /** True after the user brought the item back from "Already in Pik": it is not checked for duplicates again. */
    val restored: Boolean = false,
    val createdAt: Long,
)
