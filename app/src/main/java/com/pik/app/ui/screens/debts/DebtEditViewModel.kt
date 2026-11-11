package com.pik.app.ui.screens.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.DebtEntity
import com.pik.app.data.repo.DebtInput
import com.pik.app.data.repo.DebtRepository
import com.pik.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

@HiltViewModel
class DebtEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
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
                )
            }
        }
    }

    fun save(input: DebtInput, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = editingId
            if (id == null) debtRepository.add(input) else debtRepository.update(id, input)
            onDone()
        }
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            debtRepository.setArchived(id, archived)
            onDone()
        }
    }

    /** [onResult] gets false when the debt has payments and was not deleted. */
    fun delete(onResult: (deleted: Boolean) -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch { onResult(debtRepository.deleteIfNoPayments(id)) }
    }
}
