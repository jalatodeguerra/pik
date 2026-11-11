package com.pik.app.ui.screens.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.TransactionEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.AmountInput
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.TransactionType
import com.pik.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    /** EXPENSE, INCOME or TRANSFER. */
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: AmountInput = AmountInput(),
    val accounts: List<AccountEntity> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val saving: Boolean = false,
    /** Incremented after "Save and add another" so the UI can confirm. */
    val savedCount: Int = 0,
    /** True when the screen should close. */
    val finished: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving && amount.toCents() > 0 && when (type) {
            TransactionType.TRANSFER ->
                accountId != null && toAccountId != null && accountId != toAccountId
            else -> accountId != null && categoryId != null
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val editingId: Long? =
        savedStateHandle.get<Long>(Routes.ARG_TRANSACTION_ID)?.takeIf { it > 0 }

    private var original: TransactionEntity? = null

    private val _state = MutableStateFlow(AddUiState(isEditing = editingId != null))
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lastUsed = prefs.lastUsedAccountId.first()
            val existing = editingId?.let { transactions.get(it) }
            original = existing
            if (existing?.type == TransactionType.ADJUSTMENT) {
                // Balance adjustments are not editable here (they can be deleted from Activity).
                _state.update { it.copy(isLoading = false, finished = true) }
                return@launch
            }
            _state.update { s ->
                if (existing != null) {
                    s.copy(
                        isLoading = false,
                        type = existing.type,
                        amount = AmountInput.fromCents(existing.amountCents),
                        accountId = existing.accountId,
                        toAccountId = existing.toAccountId,
                        categoryId = existing.categoryId,
                        date = existing.date,
                        note = existing.note,
                    )
                } else {
                    s.copy(isLoading = false, accountId = lastUsed)
                }
            }
            launch {
                // Archived accounts are hidden from the pickers, except the ones this transaction
                // already uses, so editing never silently moves it to another account.
                val keep = setOfNotNull(existing?.accountId, existing?.toAccountId)
                accountRepository.observeAllAccounts().collect { all ->
                    onAccounts(all.filter { !it.archived || it.id in keep }, lastUsed)
                }
            }
            launch {
                _state.map { it.type }
                    .distinctUntilChanged()
                    .flatMapLatest { type -> categoryRepository.observeByUsage(kindFor(type)) }
                    .collect { list -> _state.update { it.copy(categories = list) } }
            }
        }
    }

    private fun kindFor(type: TransactionType): CategoryKind =
        if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    private fun onAccounts(accounts: List<AccountEntity>, lastUsed: Long?) {
        _state.update { s ->
            val ids = accounts.map { it.id }.toSet()
            val account = when {
                s.accountId != null && s.accountId in ids -> s.accountId
                lastUsed != null && lastUsed in ids -> lastUsed
                else -> accounts.firstOrNull()?.id
            }
            val to = if (s.type == TransactionType.TRANSFER) {
                pickToAccount(accounts, account, s.toAccountId)
            } else {
                s.toAccountId
            }
            s.copy(accounts = accounts, accountId = account, toAccountId = to)
        }
    }

    private fun pickToAccount(accounts: List<AccountEntity>, from: Long?, current: Long?): Long? =
        if (current != null && current != from && accounts.any { it.id == current }) {
            current
        } else {
            accounts.firstOrNull { it.id != from }?.id
        }

    fun selectType(type: TransactionType) {
        _state.update { s ->
            if (s.type == type) return@update s
            s.copy(
                type = type,
                categoryId = null,
                categories = if (kindFor(type) == kindFor(s.type)) s.categories else emptyList(),
                toAccountId = if (type == TransactionType.TRANSFER) {
                    pickToAccount(s.accounts, s.accountId, s.toAccountId)
                } else {
                    null
                },
            )
        }
    }

    fun typeDigit(digit: Char) = _state.update { it.copy(amount = it.amount.typeDigit(digit)) }
    fun typeComma() = _state.update { it.copy(amount = it.amount.typeComma()) }
    fun backspace() = _state.update { it.copy(amount = it.amount.backspace()) }

    fun selectCategory(id: Long) = _state.update { it.copy(categoryId = id) }

    fun selectAccount(id: Long) = _state.update { s ->
        val to = if (s.type == TransactionType.TRANSFER) {
            pickToAccount(s.accounts, id, s.toAccountId)
        } else {
            s.toAccountId
        }
        s.copy(accountId = id, toAccountId = to)
    }

    fun selectToAccount(id: Long) = _state.update { s ->
        // If the same account is chosen as destination, move the source to the other one.
        if (id == s.accountId) {
            s.copy(toAccountId = id, accountId = s.accounts.firstOrNull { it.id != id }?.id)
        } else {
            s.copy(toAccountId = id)
        }
    }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun save(addAnother: Boolean) {
        val s = _state.value
        if (!s.canSave) return
        val accountId = s.accountId ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val old = original
            val entity = TransactionEntity(
                id = old?.id ?: 0L,
                type = s.type,
                amountCents = s.amount.toCents(),
                date = s.date,
                accountId = accountId,
                toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                categoryId = if (s.type == TransactionType.TRANSFER) null else s.categoryId,
                note = s.note.trim(),
                debtId = old?.debtId,
                recurringId = old?.recurringId,
                createdAt = old?.createdAt ?: 0L,
                updatedAt = old?.updatedAt ?: 0L,
            )
            transactions.save(entity)
            prefs.setLastUsedAccountId(accountId)
            if (addAnother && old == null) {
                _state.update {
                    it.copy(
                        saving = false,
                        amount = AmountInput(),
                        note = "",
                        categoryId = null,
                        savedCount = it.savedCount + 1,
                    )
                }
            } else {
                _state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

    fun delete() {
        val id = editingId ?: return
        viewModelScope.launch {
            transactions.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}
