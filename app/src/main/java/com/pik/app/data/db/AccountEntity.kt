package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pik.app.domain.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val startingBalanceCents: Long,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)
