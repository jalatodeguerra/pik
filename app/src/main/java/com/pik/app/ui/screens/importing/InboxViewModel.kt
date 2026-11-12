package com.pik.app.ui.screens.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.ImportItemEntity
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.data.repo.ImportRepository
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.importing.ImportSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An inbox item with the names the card shows. */
data class InboxItemUi(
    val item: ImportItemEntity,
    val accountName: String?,
    val transferAccountName: String?,
    val category: CategoryEntity?,
) {
    val isTransfer: Boolean
        get() = item.suggestion == ImportSuggestion.TRANSFER_OUT || item.suggestion == ImportSuggestion.TRANSFER_IN
}

data class InboxUiState(
    val isLoading: Boolean = true,
    val pending: List<InboxItemUi> = emptyList(),
    val duplicates: List<InboxItemUi> = emptyList(),
    /** Accounts that can be picked (not archived). */
    val accounts: List<AccountEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    /** How many "Add all with a category" would add. */
    val addAllCount: Int = 0,
    /** Set after "Add all": how many were added. Cleared by [InboxViewModel.consumeAddedAll]. */
    val addedAll: Int? = null,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val imports: ImportRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val addedAll = MutableStateFlow<Int?>(null)

    init {
        // Forget old handled items and look again for duplicates of the pending ones.
        viewModelScope.launch { imports.refresh() }
    }

    val state: StateFlow<InboxUiState> = combine(
        imports.observePending(),
        imports.observeDuplicates(),
        accountRepository.observeAllAccounts(),
        categoryRepository.observeAll(),
        addedAll,
    ) { pending, duplicates, accounts, categories, added ->
        val accountNames = accounts.associate { it.id to it.name }
        val categoriesById = categories.associateBy { it.id }
        fun ui(item: ImportItemEntity) = InboxItemUi(
            item = item,
            accountName = item.accountId?.let { accountNames[it] },
            transferAccountName = item.transferAccountId?.let { accountNames[it] },
            category = item.suggestedCategoryId?.let { categoriesById[it] },
        )
        val pendingUi = pending.map(::ui)
        InboxUiState(
            isLoading = false,
            pending = pendingUi,
            duplicates = duplicates.map(::ui),
            accounts = accounts.filter { !it.archived },
            expenseCategories = categories.filter { !it.archived && it.kind == CategoryKind.EXPENSE },
            incomeCategories = categories.filter { !it.archived && it.kind == CategoryKind.INCOME },
            addAllCount = pendingUi.count { !it.isTransfer && it.category != null && it.item.accountId != null },
            addedAll = added,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun setCategory(itemId: Long, categoryId: Long) {
        viewModelScope.launch { imports.setCategory(itemId, categoryId) }
    }

    /**
     * Adds the item to Pik with the chosen account and category (or the item's own). The UI asks for a
     * missing account or category first.
     */
    fun add(itemId: Long, accountId: Long? = null, categoryId: Long? = null) {
        viewModelScope.launch { imports.addToPik(itemId, accountId = accountId, categoryId = categoryId) }
    }

    fun dismiss(itemId: Long) {
        viewModelScope.launch { imports.dismiss(itemId) }
    }

    fun restore(itemId: Long) {
        viewModelScope.launch { imports.restore(itemId) }
    }

    fun addAll() {
        viewModelScope.launch { addedAll.value = imports.addAllWithCategory() }
    }

    fun consumeAddedAll() = addedAll.update { null }
}
