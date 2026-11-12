package com.pik.app.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.InsightsCalculator
import com.pik.app.domain.InsightsSummary
import com.pik.app.domain.Period
import com.pik.app.ui.screens.activity.ActivityFocus
import com.pik.app.ui.screens.activity.ActivityFocusRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class InsightsUiState(
    val isLoading: Boolean = true,
    val summary: InsightsSummary? = null,
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    /** The period the bars end at (the one that contains today). */
    val currentPeriod: Period? = null,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    prefs: PikPreferences,
    private val focus: ActivityFocus,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    /** How many periods back the "Where it went" section looks. 0 = the current period. */
    private val stepsBack = MutableStateFlow(0)

    /** Call when the screen resumes so a new period shows up after midnight. */
    fun refreshToday() = today.update { LocalDate.now() }

    fun previousPeriod() = stepsBack.update { it + 1 }

    fun nextPeriod() = stepsBack.update { (it - 1).coerceAtLeast(0) }

    /** Opens Activity on the transactions behind a slice. Returns after the request is queued. */
    fun showInActivity(request: ActivityFocusRequest) = focus.request(request)

    val state: StateFlow<InsightsUiState> = combine(
        transactionRepository.observeInsightFacts(),
        prefs.monthStartDay,
        categoryRepository.observeAll(),
        today,
        stepsBack,
    ) { facts, startDay, categories, now, back ->
        val current = Period.containing(now, startDay)
        var selected = Period(current.start.minusMonths(back.toLong()), startDay)
        val first = facts.minOfOrNull { it.date }?.let { Period.containing(it, startDay) }
        // Never past the first period that has data, nor into the future.
        if (first != null && selected.start < first.start) selected = first
        val summary = InsightsCalculator.compute(facts, startDay, now, selected)
        InsightsUiState(
            isLoading = false,
            summary = summary,
            categories = categories.associateBy { it.id },
            canGoPrevious = summary.firstPeriod != null && selected.start > summary.firstPeriod.start,
            canGoNext = selected.start < current.start,
            currentPeriod = current,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())
}
