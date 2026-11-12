package com.pik.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.DebtRepository
import com.pik.app.data.repo.ImportRepository
import com.pik.app.data.repo.RecurringRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.BalanceCalculator
import com.pik.app.domain.DebtPaymentsDue
import com.pik.app.domain.InsightTx
import com.pik.app.domain.InsightsCalculator
import com.pik.app.domain.Period
import com.pik.app.domain.RecurringPlanner
import com.pik.app.domain.SafeToSpend
import com.pik.app.domain.SafeToSpendCalculator
import com.pik.app.domain.TxFacts
import com.pik.app.ui.screens.recurring.DueItemUi
import com.pik.app.ui.screens.recurring.buildDueItems
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
    /** What you still owe: total remaining of the non-archived debts. Never mixed with [totalCents]. */
    val owedCents: Long = 0,
    val debtCount: Int = 0,
    /** Unconfirmed recurring items that are overdue or due within 7 days, oldest first. */
    val due: List<DueItemUi> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Number of recurring rules (for the Recurring shortcut). */
    val recurringCount: Int = 0,
    /** Cumulative spending per day of the period up to today (the sparkline on the spending card). */
    val spendingLine: List<Long> = emptyList(),
    /** Payments found by the automatic import that wait in the Inbox. */
    val pendingImportCount: Int = 0,
)

/** Debt figures Home needs: the open debts and the extra-per-month setting. */
private data class DebtInfo(
    val inputs: List<DebtPaymentsDue.Input>,
    val extraPerMonthCents: Long,
)

/** Debt and recurring figures Home needs next to the rest. */
private data class PlanInfo(
    val debts: DebtInfo,
    val rules: List<com.pik.app.data.db.RecurringRuleEntity>,
    val pendingImports: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    debtRepository: DebtRepository,
    private val recurringRepository: RecurringRepository,
    importRepository: ImportRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())

    /** Call when the screen resumes: moves to the new period if the app stayed open past midnight. */
    fun refreshMonth() = todayFlow.update { LocalDate.now() }

    fun checkBalance(accountId: Long, realCents: Long, note: String) {
        viewModelScope.launch { transactionRepository.applyBalanceCheck(accountId, realCents, note) }
    }

    private data class Settings(val today: LocalDate, val startDay: Int, val expectedIncomeCents: Long?)

    private val debtInfo = combine(debtRepository.observeDebts(), prefs.extraPerMonthCents) { debts, extra ->
        val open = debts.filter { !it.debt.archived }
        DebtInfo(
            inputs = open.map { DebtPaymentsDue.Input(it.debt.id, it.remainingCents, it.debt.minPaymentCents) },
            extraPerMonthCents = extra,
        )
    }

    private val planInfo = combine(
        debtInfo,
        recurringRepository.observeRules(),
        importRepository.observePendingCount(),
    ) { debts, rules, pendingImports ->
        PlanInfo(debts, rules, pendingImports)
    }

    /** Confirms the next pending occurrence of a recurring rule (a transaction is created). */
    fun confirmDue(ruleId: Long, amountCents: Long, accountId: Long, date: LocalDate) {
        viewModelScope.launch {
            recurringRepository.confirm(ruleId, amountCents, accountId, date)
            prefs.setLastUsedAccountId(accountId)
        }
    }

    /** Skips the next pending occurrence of a recurring rule (no transaction). */
    fun skipDue(ruleId: Long) {
        viewModelScope.launch { recurringRepository.skip(ruleId) }
    }

    val state: StateFlow<HomeUiState> = combine(
        todayFlow,
        prefs.monthStartDay,
        prefs.expectedIncomeCents,
    ) { today, startDay, expected -> Settings(today, startDay, expected) }
        .flatMapLatest { settings ->
            val period = Period.containing(settings.today, settings.startDay)
            combine(
                combine(accountRepository.observeAccounts(), accountRepository.observeBalances()) { a, b -> a to b },
                transactionRepository.observeRange(period.start, period.endInclusive),
                transactionRepository.observeRecent(RECENT_COUNT),
                transactionRepository.observeCount(),
                planInfo,
            ) { accountsAndBalances, periodRows, recent, count, plan ->
                val debts = plan.debts
                val (accounts, balances) = accountsAndBalances
                val withBalance = accounts.map { AccountWithBalance(it, balances[it.id] ?: it.startingBalanceCents) }
                val facts = periodRows.map {
                    TxFacts(it.type, it.amountCents, it.accountId, it.toAccountId, it.categoryId, it.debtId)
                }
                val totals = BalanceCalculator.monthTotals(facts)
                val stillDue = RecurringPlanner.stillDue(
                    plan.rules.map { it.toFacts() }, settings.today, period.endInclusive,
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
                        debtPaymentsDueCents = DebtPaymentsDue.compute(debts.inputs, facts, debts.extraPerMonthCents),
                        billsStillDueCents = stillDue.billsCents,
                        incomeStillDueCents = stillDue.incomeCents,
                    ),
                    recent = recent,
                    hasTransactions = count > 0,
                    owedCents = debts.inputs.sumOf { it.remainingCents },
                    debtCount = debts.inputs.count { it.remainingCents > 0 },
                    due = buildDueItems(plan.rules, settings.today),
                    today = settings.today,
                    recurringCount = plan.rules.size,
                    pendingImportCount = plan.pendingImports,
                    spendingLine = InsightsCalculator.cumulativeSpending(
                        periodRows.map { InsightTx(it.date, it.type, it.amountCents, it.categoryId, it.debtId) },
                        period,
                        settings.today,
                    ),
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
