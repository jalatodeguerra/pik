package com.pik.app.ui.screens.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.DebtItem
import com.pik.app.data.repo.DebtRepository
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtHistoryItem
import com.pik.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DebtDetailUiState(
    val isLoading: Boolean = true,
    /** null once loaded means the debt does not exist (any more). */
    val item: DebtItem? = null,
    val history: List<DebtHistoryItem> = emptyList(),
    /** Active accounts for the payment dialog. */
    val accounts: List<AccountEntity> = emptyList(),
    val lastUsedAccountId: Long? = null,
)

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debtRepository: DebtRepository,
    accountRepository: AccountRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    val debtId: Long = savedStateHandle.get<Long>(Routes.ARG_DEBT_ID) ?: -1L

    val state: StateFlow<DebtDetailUiState> = combine(
        debtRepository.observeDebt(debtId),
        debtRepository.observeHistory(debtId),
        accountRepository.observeAccounts(),
        prefs.lastUsedAccountId,
    ) { item, history, accounts, lastUsed ->
        DebtDetailUiState(
            isLoading = false,
            item = item,
            history = history,
            accounts = accounts,
            lastUsedAccountId = lastUsed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailUiState())

    /** Creates the EXPENSE transaction with this debtId (no category) and remembers the account. */
    fun logPayment(accountId: Long, amountCents: Long, date: LocalDate, note: String) {
        viewModelScope.launch {
            debtRepository.logPayment(debtId, accountId, amountCents, date, note)
            prefs.setLastUsedAccountId(accountId)
        }
    }

    fun addEntry(type: DebtEntryType, amountCents: Long, date: LocalDate, note: String) {
        viewModelScope.launch { debtRepository.addEntry(debtId, type, amountCents, date, note) }
    }

    fun checkBalance(realCents: Long, note: String) {
        viewModelScope.launch { debtRepository.checkBalance(debtId, realCents, note) }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { debtRepository.deleteEntry(entryId) }
    }
}
