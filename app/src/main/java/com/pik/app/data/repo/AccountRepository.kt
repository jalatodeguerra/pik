package com.pik.app.data.repo

import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.AccountEntity
import com.pik.app.domain.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
) {
    /** Non-archived accounts only (pickers, Home). */
    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeActive()

    /** Every account including archived ones (settings, Activity filter). */
    fun observeAllAccounts(): Flow<List<AccountEntity>> = dao.observeAll()

    /** Map of accountId to calculated balance in cents. */
    fun observeBalances(): Flow<Map<Long, Long>> =
        dao.observeBalances().map { list -> list.associate { it.accountId to it.balanceCents } }

    /** Map of accountId to the number of transactions that use it. */
    fun observeTransactionCounts(): Flow<Map<Long, Int>> =
        dao.observeUsage().map { list -> list.associate { it.accountId to it.transactionCount } }

    suspend fun getAccounts(): List<AccountEntity> = dao.getAll()

    /** Inserts all accounts in one transaction. Returns the new ids in order. */
    suspend fun createAccounts(accounts: List<AccountEntity>): List<Long> = dao.insertAll(accounts)

    suspend fun addAccount(name: String, type: AccountType, startingBalanceCents: Long): Long =
        dao.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                startingBalanceCents = startingBalanceCents,
                sortOrder = dao.maxSortOrder() + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun updateAccount(id: Long, name: String, type: AccountType, startingBalanceCents: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name.trim(), type = type, startingBalanceCents = startingBalanceCents))
    }

    /** Refuses to archive the last active account (the Add screen needs at least one). */
    suspend fun setArchived(id: Long, archived: Boolean): Boolean {
        val existing = dao.getById(id) ?: return false
        if (archived && !existing.archived && dao.getAll().count { !it.archived } <= 1) return false
        dao.update(existing.copy(archived = archived))
        return true
    }

    /** Deletes only an account that has no transactions. Returns false when it is in use. */
    suspend fun deleteIfUnused(id: Long): Boolean {
        if (dao.transactionCount(id) > 0) return false
        dao.deleteById(id)
        return true
    }

    /** Current calculated balance of one account. */
    suspend fun balanceOf(id: Long): Long =
        dao.balanceOf(id)?.balanceCents ?: dao.getById(id)?.startingBalanceCents ?: 0L
}
