package com.pik.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.TransactionRow
import com.pik.app.data.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DayGroup(val date: LocalDate, val rows: List<TransactionRow>)

data class ActivityUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val groups: List<DayGroup> = emptyList(),
    val canGoNext: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    repository: TransactionRepository,
) : ViewModel() {

    private val currentMonth: YearMonth = YearMonth.now()
    private val selectedMonth = MutableStateFlow(currentMonth)

    val state: StateFlow<ActivityUiState> = selectedMonth
        .flatMapLatest { month ->
            repository.observeMonth(month).map { rows ->
                ActivityUiState(
                    month = month,
                    isLoading = false,
                    // rows are already newest first
                    groups = rows.groupBy { it.date }.map { (date, list) -> DayGroup(date, list) },
                    canGoNext = month < currentMonth,
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
}
