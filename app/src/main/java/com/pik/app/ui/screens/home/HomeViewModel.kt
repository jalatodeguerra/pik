package com.pik.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.BalanceCalculator
import com.pik.app.domain.TxFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AccountWithBalance(val account: AccountEntity, val balanceCents: Long)

data class HomeUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    /** Sum of all active account balances (cash + bank). Debts are never mixed in. */
    val totalCents: Long = 0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val monthInCents: Long = 0,
    val monthOutCents: Long = 0,
    val recent: List<TransactionRow> = emptyList(),
    val hasTransactions: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val monthFlow = MutableStateFlow(YearMonth.now())

    /** Call when the screen resumes: moves to the new month if the app stayed open past midnight. */
    fun refreshMonth() = monthFlow.update { YearMonth.now() }

    fun checkBalance(accountId: Long, realCents: Long, note: String) {
        viewModelScope.launch { transactionRepository.applyBalanceCheck(accountId, realCents, note) }
    }

    val state: StateFlow<HomeUiState> = monthFlow.flatMapLatest { month -> combine(
        accountRepository.observeAccounts(),
        accountRepository.observeBalances(),
        transactionRepository.observeMonth(month),
        transactionRepository.observeRecent(RECENT_COUNT),
        transactionRepository.observeCount(),
    ) { accounts, balances, monthRows, recent, count ->
        val withBalance = accounts.map { AccountWithBalance(it, balances[it.id] ?: it.startingBalanceCents) }
        val totals = BalanceCalculator.monthTotals(
            monthRows.map { TxFacts(it.type, it.amountCents, it.accountId, it.toAccountId) },
        )
        HomeUiState(
            month = month,
            isLoading = false,
            totalCents = withBalance.sumOf { it.balanceCents },
            accounts = withBalance,
            monthInCents = totals.incomeCents,
            monthOutCents = totals.expenseCents,
            recent = recent,
            hasTransactions = count > 0,
        )
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(month = monthFlow.value))

    private companion object {
        const val RECENT_COUNT = 5
    }
}
