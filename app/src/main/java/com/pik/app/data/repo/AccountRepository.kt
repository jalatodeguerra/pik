package com.pik.app.data.repo

import com.pik.app.data.db.AccountDao
import com.pik.app.data.db.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
) {
    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeActive()

    /** Map of accountId to calculated balance in cents. */
    fun observeBalances(): Flow<Map<Long, Long>> =
        dao.observeBalances().map { list -> list.associate { it.accountId to it.balanceCents } }

    suspend fun getAccounts(): List<AccountEntity> = dao.getAll()

    /** Inserts all accounts in one transaction. Returns the new ids in order. */
    suspend fun createAccounts(accounts: List<AccountEntity>): List<Long> = dao.insertAll(accounts)
}
