package com.pik.app.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.BudgetRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.domain.BudgetCalculator
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.Money
import com.pik.app.domain.Period
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetEditorCategory(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    /** Limit in force today as Italian text ("1.234,56"), or empty when there is none. */
    val initialText: String,
)

/** Loaded once, so typing is never overwritten by database updates. */
data class BudgetEditorUiState(
    val period: Period,
    val overallInitialText: String,
    val categories: List<BudgetEditorCategory>,
)

@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    prefs: PikPreferences,
) : ViewModel() {

    private var loadedPeriod: Period? = null

    /** null while loading. */
    val state: StateFlow<BudgetEditorUiState?> = flow {
        val period = Period.containing(LocalDate.now(), prefs.monthStartDay.first())
        loadedPeriod = period
        val limits = BudgetCalculator.effectiveLimits(budgetRepository.observeBudgets().first(), period.key)
        val categories = categoryRepository.observeWithUsage(CategoryKind.EXPENSE).first()
            .map { it.category }
            .filter { !it.archived }
            .map {
                BudgetEditorCategory(
                    id = it.id,
                    name = it.name,
                    iconKey = it.iconKey,
                    colorKey = it.colorKey,
                    initialText = limits[it.id]?.let { cents -> Money.formatPlain(cents) }.orEmpty(),
                )
            }
        emit(
            BudgetEditorUiState(
                period = period,
                overallInitialText = limits[null]?.let { cents -> Money.formatPlain(cents) }.orEmpty(),
                categories = categories,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * [overallCents] and [categoryCents] are limits in cents; 0 (blank field) means no budget.
     * Applied from the current period on; earlier periods keep their limits.
     */
    fun save(overallCents: Long, categoryCents: Map<Long, Long>, onDone: () -> Unit) {
        val period = loadedPeriod ?: return
        viewModelScope.launch {
            val limits = HashMap<Long?, Long>()
            limits[null] = overallCents
            categoryCents.forEach { (id, cents) -> limits[id] = cents }
            budgetRepository.saveLimitsFrom(period.key, limits)
            onDone()
        }
    }
}
