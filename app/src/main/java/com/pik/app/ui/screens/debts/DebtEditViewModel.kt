package com.pik.app.ui.screens.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.DebtEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.DebtInput
import com.pik.app.data.repo.DebtRepository
import com.pik.app.data.repo.RecurringRepository
import com.pik.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loaded once, so typing is never overwritten by database updates. */
data class DebtEditUiState(
    val isLoading: Boolean = true,
    /** null = adding a new debt. */
    val existing: DebtEntity? = null,
    /** Transactions that point to the debt: a debt with payments cannot be deleted. */
    val paymentCount: Int = 0,
    /** The id given to the screen does not exist. */
    val notFound: Boolean = false,
    /** The debt already has a recurring payment rule ("Remind me and track this payment" is on). */
    val hasRule: Boolean = false,
)

@HiltViewModel
class DebtEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>(Routes.ARG_DEBT_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(DebtEditUiState(isLoading = editingId != null))
    val state: StateFlow<DebtEditUiState> = _state.asStateFlow()

    init {
        val id = editingId
        if (id != null) {
            viewModelScope.launch {
                val debt = debtRepository.get(id)
                _state.value = DebtEditUiState(
                    isLoading = false,
                    existing = debt,
                    paymentCount = if (debt != null) debtRepository.paymentCount(id) else 0,
                    notFound = debt == null,
                    hasRule = debt != null && recurringRepository.getByDebt(id) != null,
                )
            }
        }
    }

    /**
     * [trackPayment] is the "Remind me and track this payment" switch: the debt owns a recurring payment rule
     * (amount = instalment, day = due day) that is created, updated with the debt, or removed.
     */
    fun save(input: DebtInput, trackPayment: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = editingId
            val debtId = if (id == null) debtRepository.add(input) else {
                debtRepository.update(id, input)
                id
            }
            val accountId = defaultAccountId()
            if (accountId != null) {
                recurringRepository.syncDebtRule(debtId, trackPayment && input.minPaymentCents > 0L, accountId)
            }
            onDone()
        }
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            debtRepository.setArchived(id, archived)
            // an archived debt stops asking for its payment
            recurringRepository.setDebtRuleActive(id, !archived)
            onDone()
        }
    }

    /** The account a new payment rule starts with: the last used one, otherwise the first active account. */
    private suspend fun defaultAccountId(): Long? {
        val active = accountRepository.getAccounts().filter { !it.archived }
        val lastUsed = prefs.lastUsedAccountId.first()
        return lastUsed?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
    }

    /** [onResult] gets false when the debt has payments and was not deleted. */
    fun delete(onResult: (deleted: Boolean) -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch { onResult(debtRepository.deleteIfNoPayments(id)) }
    }
}
