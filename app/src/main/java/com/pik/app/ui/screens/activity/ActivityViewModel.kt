package com.pik.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.ActivityFilter
import com.pik.app.domain.TypeFilter
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
import java.time.YearMonth
import javax.inject.Inject

data class DayGroup(val date: LocalDate, val rows: List<TransactionRow>)

data class ActivityUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val groups: List<DayGroup> = emptyList(),
    val canGoNext: Boolean = false,
    val query: String = "",
    val typeFilter: TypeFilter = TypeFilter.ALL,
    /** null means all accounts. */
    val accountFilter: Long? = null,
    /** All accounts, archived included, for the filter chips. */
    val accounts: List<AccountEntity> = emptyList(),
    /** True when search or a filter is on. */
    val filtering: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: TransactionRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val currentMonth: YearMonth = YearMonth.now()
    private val selectedMonth = MutableStateFlow(currentMonth)
    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)
    private val accountFilter = MutableStateFlow<Long?>(null)

    val state: StateFlow<ActivityUiState> = selectedMonth
        .flatMapLatest { month ->
            combine(
                repository.observeMonth(month),
                query,
                typeFilter,
                accountFilter,
                accountRepository.observeAllAccounts(),
            ) { rows, q, type, account, accounts ->
                val filtered = rows.filter { row ->
                    ActivityFilter.matchesType(type, row.type) &&
                        ActivityFilter.matchesAccount(account, row.accountId, row.toAccountId) &&
                        ActivityFilter.matchesQuery(q, row.note, row.categoryName, row.accountName, row.toAccountName)
                }
                ActivityUiState(
                    month = month,
                    isLoading = false,
                    // rows are already newest first
                    groups = filtered.groupBy { it.date }.map { (date, list) -> DayGroup(date, list) },
                    canGoNext = month < currentMonth,
                    query = q,
                    typeFilter = type,
                    accountFilter = account,
                    accounts = accounts,
                    filtering = q.isNotBlank() || type != TypeFilter.ALL || account != null,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ActivityUiState(month = currentMonth),
        )

    fun previousMonth() = selectedMonth.update { it.minusMonths(1) }

    fun nextMonth() = selectedMonth.update { if (it < currentMonth) it.plusMonths(1) else it }

    fun setQuery(value: String) = query.update { value }

    fun setTypeFilter(value: TypeFilter) = typeFilter.update { value }

    fun setAccountFilter(accountId: Long?) = accountFilter.update { accountId }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
