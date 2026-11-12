package com.pik.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.RecurringRepository
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.RecurringPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One day of the week strip with the number of items due on it. */
data class WeekDayUi(val date: LocalDate, val dueCount: Int)

/** The rules of one kind, soonest first. */
data class RecurringGroup(val kind: RecurringKind, val rules: List<RecurringRuleEntity>)

data class RecurringUiState(
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val week: List<WeekDayUi> = emptyList(),
    val due: List<DueItemUi> = emptyList(),
    val groups: List<RecurringGroup> = emptyList(),
    /** Outgoing rules (bills, subscriptions, debt payments) per month / per year on average. */
    val monthlyOutCents: Long = 0,
    val yearlyOutCents: Long = 0,
    val hasRules: Boolean = false,
    /** Active accounts for the confirm sheet. */
    val accounts: List<AccountEntity> = emptyList(),
)

private val GROUP_ORDER = listOf(
    RecurringKind.BILL,
    RecurringKind.SUBSCRIPTION,
    RecurringKind.DEBT_PAYMENT,
    RecurringKind.INCOME,
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repository: RecurringRepository,
    accountRepository: AccountRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())

    /** Call when the screen resumes, so "today" follows the calendar if the app stayed open past midnight. */
    fun refreshToday() = todayFlow.update { LocalDate.now() }

    val state: StateFlow<RecurringUiState> = combine(
        todayFlow,
        repository.observeRules(),
        accountRepository.observeAccounts(),
    ) { today, rules, accounts ->
        val due = buildDueItems(rules, today)
        val facts = rules.map { it.toFacts() }
        RecurringUiState(
            today = today,
            isLoading = false,
            week = (0..6).map { offset ->
                val day = today.plusDays(offset.toLong())
                WeekDayUi(day, due.count { it.date == day })
            },
            due = due,
            groups = GROUP_ORDER.mapNotNull { kind ->
                val ofKind = rules.filter { it.kind == kind }
                // active rules first, soonest next date first (observeAll is already ordered by next date)
                if (ofKind.isEmpty()) null else RecurringGroup(kind, ofKind.sortedBy { !it.active })
            },
            monthlyOutCents = RecurringPlanner.monthlyOutgoingCents(facts),
            yearlyOutCents = RecurringPlanner.yearlyOutgoingCents(facts),
            hasRules = rules.isNotEmpty(),
            accounts = accounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun confirm(ruleId: Long, amountCents: Long, accountId: Long, date: LocalDate) {
        viewModelScope.launch {
            repository.confirm(ruleId, amountCents, accountId, date)
            prefs.setLastUsedAccountId(accountId)
        }
    }

    fun skip(ruleId: Long) {
        viewModelScope.launch { repository.skip(ruleId) }
    }
}
