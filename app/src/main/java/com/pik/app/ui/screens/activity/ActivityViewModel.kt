package com.pik.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.ActivityFilter
import com.pik.app.domain.CategoryFilter
import com.pik.app.domain.Period
import com.pik.app.domain.TransactionType
import com.pik.app.domain.TypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/** A category (or the debt payments group) that can be picked in the category filter. */
data class CategoryOption(val filter: CategoryFilter, val name: String?, val iconKey: String?, val colorKey: String?)

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
    /** Categories (and debt payments) found in the shown transactions, plus the selected one. */
    val categoryOptions: List<CategoryOption> = emptyList(),
    val categoryFilter: CategoryFilter = CategoryFilter.All,
    /** Set when Insights asked for a payday period: it replaces the month arrows. */
    val period: Period? = null,
    /** True when search or a filter is on. */
    val filtering: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: TransactionRepository,
    accountRepository: AccountRepository,
    private val focus: ActivityFocus,
) : ViewModel() {

    private val currentMonth = MutableStateFlow(YearMonth.now())
    private val selectedMonth = MutableStateFlow(currentMonth.value)
    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)
    private val accountFilter = MutableStateFlow<Long?>(null)
    private val categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    private val period = MutableStateFlow<Period?>(null)

    /** Everything that decides which transactions are loaded. */
    private data class Source(val month: YearMonth, val current: YearMonth, val period: Period?)

    private data class Filters(
        val query: String,
        val type: TypeFilter,
        val account: Long?,
        val category: CategoryFilter,
    )

    private val source: Flow<Source> = combine(selectedMonth, currentMonth, period) { month, current, p ->
        Source(month, current, p)
    }

    private val filters: Flow<Filters> = combine(query, typeFilter, accountFilter, categoryFilter) { q, type, account, category ->
        Filters(q, type, account, category)
    }

    val state: StateFlow<ActivityUiState> = source
        .flatMapLatest { src ->
            val rowsFlow = src.period?.let { repository.observeRange(it.start, it.endInclusive) }
                ?: repository.observeMonth(src.month)
            combine(rowsFlow, filters, accountRepository.observeAllAccounts()) { rows, f, accounts ->
                val filtered = rows.filter { row ->
                    ActivityFilter.matchesType(f.type, row.type) &&
                        ActivityFilter.matchesAccount(f.account, row.accountId, row.toAccountId) &&
                        ActivityFilter.matchesCategory(f.category, row.type, row.categoryId, row.debtId) &&
                        ActivityFilter.matchesQuery(f.query, row.note, row.categoryName, row.accountName, row.toAccountName, row.debtName)
                }
                ActivityUiState(
                    month = src.month,
                    isLoading = false,
                    // rows are already newest first
                    groups = filtered.groupBy { it.date }.map { (date, list) -> DayGroup(date, list) },
                    canGoNext = src.month < src.current,
                    query = f.query,
                    typeFilter = f.type,
                    accountFilter = f.account,
                    accounts = accounts,
                    categoryOptions = categoryOptions(rows, f.category),
                    categoryFilter = f.category,
                    period = src.period,
                    filtering = f.query.isNotBlank() || f.type != TypeFilter.ALL || f.account != null ||
                        f.category != CategoryFilter.All,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ActivityUiState(month = currentMonth.value),
        )

    init {
        // Insights can ask for a category inside a payday period.
        viewModelScope.launch {
            focus.pending.collect { request ->
                if (request != null) {
                    categoryFilter.value = request.category
                    typeFilter.value = TypeFilter.ALL
                    accountFilter.value = null
                    period.value = request.period
                    focus.clear()
                }
            }
        }
    }

    /** The categories of the loaded rows (expenses and income), by name, with debt payments last. */
    private fun categoryOptions(rows: List<TransactionRow>, selected: CategoryFilter): List<CategoryOption> {
        val byCategory = rows
            .filter { it.categoryId != null && it.type != TransactionType.TRANSFER && it.type != TransactionType.ADJUSTMENT }
            .distinctBy { it.categoryId }
            .map { CategoryOption(CategoryFilter.Category(it.categoryId!!), it.categoryName, it.categoryIconKey, it.categoryColorKey) }
            .sortedBy { it.name.orEmpty().lowercase() }
        val hasDebt = rows.any { it.type == TransactionType.EXPENSE && it.debtId != null }
        val hasUncategorised = rows.any { it.type == TransactionType.EXPENSE && it.categoryId == null && it.debtId == null }
        val options = buildList {
            addAll(byCategory)
            if (hasUncategorised) add(CategoryOption(CategoryFilter.Uncategorised, null, null, null))
            if (hasDebt) add(CategoryOption(CategoryFilter.DebtPayments, null, null, null))
        }
        // The selected filter stays visible even if this month has nothing in it.
        return if (selected == CategoryFilter.All || options.any { it.filter == selected }) {
            options
        } else {
            options + CategoryOption(selected, null, null, null)
        }
    }

    /** Call when the screen resumes: moves to the new month if the app stayed open past a month change. */
    fun refreshMonth() {
        val now = YearMonth.now()
        val old = currentMonth.value
        if (now == old) return
        val followed = selectedMonth.value == old
        currentMonth.value = now
        if (followed) selectedMonth.value = now
    }

    fun previousMonth() = selectedMonth.update { it.minusMonths(1) }

    fun nextMonth() = selectedMonth.update { if (it < currentMonth.value) it.plusMonths(1) else it }

    /** Leaves the payday period shown by Insights and goes back to calendar months (this month). */
    fun clearPeriod() {
        period.value = null
        selectedMonth.value = currentMonth.value
    }

    fun setQuery(value: String) = query.update { value }

    fun setTypeFilter(value: TypeFilter) = typeFilter.update { value }

    fun setAccountFilter(accountId: Long?) = accountFilter.update { accountId }

    fun setCategoryFilter(value: CategoryFilter) = categoryFilter.update { value }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
