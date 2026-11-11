package com.pik.app.ui.screens.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.DebtItem
import com.pik.app.data.repo.DebtRepository
import com.pik.app.domain.DebtCalculator
import com.pik.app.domain.DebtProgress
import com.pik.app.domain.OrderComparison
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.PayoffResult
import com.pik.app.domain.PayoffSimulator
import com.pik.app.domain.SimDebt
import com.pik.app.domain.WhatIfResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/** A debt in the list. [orderNumber] is its place in the payoff order (null once it is paid off). */
data class DebtRowUi(val item: DebtItem, val orderNumber: Int?)

data class DebtsUiState(
    val isLoading: Boolean = true,
    /** Non-archived debts: the ones still owed in payoff order, then the paid-off ones. */
    val rows: List<DebtRowUi> = emptyList(),
    val archived: List<DebtItem> = emptyList(),
    val overall: DebtProgress = DebtCalculator.overall(emptyList()),
    val totalRemainingCents: Long = 0,
    val order: PayoffOrder = PayoffOrder.HIGHEST_INTEREST,
    val savedExtraCents: Long = 0,
    /** Payoff plan for the saved extra and the chosen order. */
    val plan: PayoffResult? = null,
    /** Both orders at the saved extra. */
    val comparison: OrderComparison? = null,
    /** The slider value (the saved extra until the slider is moved). */
    val whatIfExtraCents: Long = 0,
    val whatIf: WhatIfResult? = null,
    val sliderMaxCents: Long = SLIDER_BASE_MAX_CENTS,
) {
    val hasDebts: Boolean get() = rows.isNotEmpty() || archived.isNotEmpty()
    val hasOpenDebts: Boolean get() = rows.any { it.item.remainingCents > 0 }
    val whatIfChanged: Boolean get() = whatIfExtraCents != savedExtraCents

    companion object {
        const val SLIDER_STEP_CENTS = 500L
        const val SLIDER_BASE_MAX_CENTS = 50_000L
    }
}

@HiltViewModel
class DebtsViewModel @Inject constructor(
    debtRepository: DebtRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    /** null = the slider follows the saved extra. */
    private val whatIfOverride = MutableStateFlow<Long?>(null)

    val state: StateFlow<DebtsUiState> = combine(
        debtRepository.observeDebts(),
        prefs.extraPerMonthCents,
        prefs.payoffOrder,
        whatIfOverride,
    ) { debts, savedExtra, order, manualExtra ->
        val active = debts.filter { !it.debt.archived }
        val archived = debts.filter { it.debt.archived }
        val open = active.filter { it.remainingCents > 0 }
        val paidOff = active.filter { it.remainingCents == 0L }
        val sim = open.map { SimDebt(it.debt.id, it.remainingCents, it.debt.aprBasisPoints, it.debt.minPaymentCents) }
        val startMonth = YearMonth.now()

        val targetOrder = PayoffSimulator.targetOrder(sim, order)
        val byId = open.associateBy { it.debt.id }
        val rows = targetOrder.mapIndexedNotNull { index, id ->
            byId[id]?.let { DebtRowUi(it, index + 1) }
        } + paidOff.map { DebtRowUi(it, null) }

        val whatIfExtra = manualExtra ?: savedExtra
        val sliderMax = maxOf(
            DebtsUiState.SLIDER_BASE_MAX_CENTS,
            roundUpToStep(maxOf(savedExtra, whatIfExtra)),
        )
        DebtsUiState(
            isLoading = false,
            rows = rows,
            archived = archived,
            overall = DebtCalculator.overall(active.map { it.progress }),
            totalRemainingCents = open.sumOf { it.remainingCents },
            order = order,
            savedExtraCents = savedExtra,
            plan = PayoffSimulator.simulate(sim, savedExtra, order, startMonth),
            comparison = PayoffSimulator.compareOrders(sim, savedExtra, startMonth),
            whatIfExtraCents = whatIfExtra,
            whatIf = PayoffSimulator.whatIf(sim, whatIfExtra, order, startMonth),
            sliderMaxCents = sliderMax,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState())

    private fun roundUpToStep(cents: Long): Long {
        val step = DebtsUiState.SLIDER_STEP_CENTS
        return (cents + step - 1) / step * step
    }

    fun setOrder(order: PayoffOrder) {
        viewModelScope.launch { prefs.setPayoffOrder(order) }
    }

    fun setWhatIfExtra(cents: Long) {
        whatIfOverride.value = cents.coerceAtLeast(0L)
    }

    /** Saves the slider value as the extra per month. */
    fun useWhatIf() {
        val cents = whatIfOverride.value ?: return
        viewModelScope.launch {
            prefs.setExtraPerMonthCents(cents)
            whatIfOverride.value = null
        }
    }
}
