package com.pik.app.ui.screens.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.data.repo.RecurringInput
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
data class RecurringEditUiState(
    val isLoading: Boolean = true,
    /** null = adding a new rule. */
    val existing: RecurringRuleEntity? = null,
    val notFound: Boolean = false,
    /** Active accounts (plus the rule's own account when it is archived). */
    val accounts: List<AccountEntity> = emptyList(),
    val defaultAccountId: Long? = null,
    /** Non-archived categories (plus the rule's own category). */
    val categories: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecurringRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>(Routes.ARG_RULE_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(RecurringEditUiState(isLoading = true))
    val state: StateFlow<RecurringEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = editingId?.let { repository.get(it) }
            val accounts = accountRepository.getAccounts().filter { !it.archived || it.id == existing?.accountId }
            val lastUsed = prefs.lastUsedAccountId.first()
            val categories = categoryRepository.observeAll().first()
                .filter { !it.archived || it.id == existing?.categoryId }
            _state.value = RecurringEditUiState(
                isLoading = false,
                existing = existing,
                notFound = editingId != null && existing == null,
                accounts = accounts,
                defaultAccountId = lastUsed?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id,
                categories = categories,
            )
        }
    }

    fun save(input: RecurringInput, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.save(editingId, input)
            prefs.setLastUsedAccountId(input.accountId)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}
