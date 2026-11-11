package com.pik.app.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.BudgetRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.BalanceCalculator
import com.pik.app.domain.BudgetCalculator
import com.pik.app.domain.BudgetProgress
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
import java.time.LocalDate
import javax.inject.Inject

/** A category with a budget. Name/icon come from the category table (archived ones included). */
data class PlanBudgetLine(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val progress: BudgetProgress,
)

/** Spending in a category (or uncategorised, [name] null) that has no budget. */
data class PlanNotBudgeted(
    val name: String?,
    val iconKey: String?,
    val colorKey: String?,
    val spentCents: Long,
)

data class PlanUiState(
    val period: Period,
    val isLoading: Boolean = true,
    val isCurrent: Boolean = true,
    val canGoNext: Boolean = false,
    val incomeCents: Long = 0,
    val spentCents: Long = 0,
    /** Only for the current period; past periods show totals. */
    val safeToSpend: SafeToSpend? = null,
    val overall: BudgetProgress? = null,
    val lines: List<PlanBudgetLine> = emptyList(),
    val notBudgeted: List<PlanNotBudgeted> = emptyList(),
    val hasAnyBudget: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    prefs: PikPreferences,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())

    /** 0 = current period, -1 = the one before, ... Never positive. */
    private val offset = MutableStateFlow(0)

    fun refreshToday() = todayFlow.update { LocalDate.now() }

    fun previousPeriod() = offset.update { it - 1 }

    fun nextPeriod() = offset.update { if (it < 0) it + 1 else it }

    private data class Selection(
        val today: LocalDate,
        val period: Period,
        val isCurrent: Boolean,
        val expectedIncomeCents: Long?,
    )

    val state: StateFlow<PlanUiState> = combine(
        todayFlow,
        prefs.monthStartDay,
        prefs.expectedIncomeCents,
        offset,
    ) { today, startDay, expected, off ->
        var period = Period.containing(today, startDay)
        repeat(-off) { period = period.previous() }
        Selection(today, period, off == 0, expected)
    }.flatMapLatest { sel ->
        combine(
            transactionRepository.observeRange(sel.period.start, sel.period.endInclusive),
            budgetRepository.observeBudgets(),
            categoryRepository.observeAll(),
        ) { rows, budgets, categories ->
            val facts = rows.map { TxFacts(it.type, it.amountCents, it.accountId, it.toAccountId, it.categoryId) }
            val totals = BalanceCalculator.monthTotals(facts)
            val limits = BudgetCalculator.effectiveLimits(budgets, sel.period.key)
            val overview = BudgetCalculator.overview(limits, BudgetCalculator.spendByCategory(facts))
            val byId = categories.associateBy { it.id }

            PlanUiState(
                period = sel.period,
                isLoading = false,
                isCurrent = sel.isCurrent,
                canGoNext = !sel.isCurrent,
                incomeCents = totals.incomeCents,
                spentCents = totals.expenseCents,
                safeToSpend = if (sel.isCurrent) {
                    SafeToSpendCalculator.compute(
                        expectedIncomeCents = sel.expectedIncomeCents,
                        incomeReceivedCents = totals.incomeCents,
                        spentCents = totals.expenseCents,
                        daysLeft = sel.period.daysLeft(sel.today),
                    )
                } else {
                    null
                },
                overall = overview.overall,
                lines = overview.lines.mapNotNull { line ->
                    val category = byId[line.categoryId] ?: return@mapNotNull null
                    PlanBudgetLine(line.categoryId, category.name, category.iconKey, category.colorKey, line.progress)
                },
                notBudgeted = overview.notBudgeted.map { item ->
                    val category = item.categoryId?.let { byId[it] }
                    PlanNotBudgeted(
                        name = category?.name,
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey,
                        spentCents = item.spentCents,
                    )
                },
                hasAnyBudget = overview.hasAnyBudget,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlanUiState(period = Period.containing(todayFlow.value, 1)),
    )
}
