package com.pik.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.BalanceCalculator
import com.pik.app.domain.Period
import com.pik.app.domain.SafeToSpend
import com.pik.app.domain.SafeToSpendCalculator
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
import java.time.LocalDate
import javax.inject.Inject

data class AccountWithBalance(val account: AccountEntity, val balanceCents: Long)

data class HomeUiState(
    /** The current payday month (calendar month when "Month starts on" is 1). */
    val period: Period,
    val isLoading: Boolean = true,
    /** Sum of all active account balances (cash + bank). Debts are never mixed in. */
    val totalCents: Long = 0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val monthInCents: Long = 0,
    val monthOutCents: Long = 0,
    val safeToSpend: SafeToSpend = SafeToSpendCalculator.compute(null, 0, 0, 1),
    val recent: List<TransactionRow> = emptyList(),
    val hasTransactions: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    prefs: PikPreferences,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())

    /** Call when the screen resumes: moves to the new period if the app stayed open past midnight. */
    fun refreshMonth() = todayFlow.update { LocalDate.now() }

    fun checkBalance(accountId: Long, realCents: Long, note: String) {
        viewModelScope.launch { transactionRepository.applyBalanceCheck(accountId, realCents, note) }
    }

    private data class Settings(val today: LocalDate, val startDay: Int, val expectedIncomeCents: Long?)

    val state: StateFlow<HomeUiState> = combine(
        todayFlow,
        prefs.monthStartDay,
        prefs.expectedIncomeCents,
    ) { today, startDay, expected -> Settings(today, startDay, expected) }
        .flatMapLatest { settings ->
            val period = Period.containing(settings.today, settings.startDay)
            combine(
                accountRepository.observeAccounts(),
                accountRepository.observeBalances(),
                transactionRepository.observeRange(period.start, period.endInclusive),
                transactionRepository.observeRecent(RECENT_COUNT),
                transactionRepository.observeCount(),
            ) { accounts, balances, periodRows, recent, count ->
                val withBalance = accounts.map { AccountWithBalance(it, balances[it.id] ?: it.startingBalanceCents) }
                val totals = BalanceCalculator.monthTotals(
                    periodRows.map { TxFacts(it.type, it.amountCents, it.accountId, it.toAccountId) },
                )
                HomeUiState(
                    period = period,
                    isLoading = false,
                    totalCents = withBalance.sumOf { it.balanceCents },
                    accounts = withBalance,
                    monthInCents = totals.incomeCents,
                    monthOutCents = totals.expenseCents,
                    safeToSpend = SafeToSpendCalculator.compute(
                        expectedIncomeCents = settings.expectedIncomeCents,
                        incomeReceivedCents = totals.incomeCents,
                        spentCents = totals.expenseCents,
                        daysLeft = period.daysLeft(settings.today),
                    ),
                    recent = recent,
                    hasTransactions = count > 0,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(period = Period.containing(todayFlow.value, 1)),
        )

    private companion object {
        const val RECENT_COUNT = 5
    }
}
