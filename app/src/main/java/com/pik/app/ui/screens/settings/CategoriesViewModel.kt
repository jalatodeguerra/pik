package com.pik.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.CategoryWithUsage
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.domain.CategoryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CategoriesMessage { CANNOT_ARCHIVE_LAST, IN_USE }

data class CategoriesUiState(
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val items: List<CategoryWithUsage> = emptyList(),
    val message: CategoriesMessage? = null,
) {
    val activeCount: Int get() = items.count { !it.category.archived }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val message = MutableStateFlow<CategoriesMessage?>(null)

    val state: StateFlow<CategoriesUiState> = combine(
        kind,
        kind.flatMapLatest { repository.observeWithUsage(it) },
        message,
    ) { k, items, m ->
        // The two flows can briefly disagree while switching kind: only show matching rows.
        CategoriesUiState(kind = k, items = items.filter { it.category.kind == k }, message = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun selectKind(value: CategoryKind) = kind.update { value }

    fun consumeMessage() = message.update { null }

    fun add(name: String, iconKey: String, colorKey: String) {
        val k = kind.value
        viewModelScope.launch { repository.addCategory(name, k, iconKey, colorKey) }
    }

    fun update(id: Long, name: String, iconKey: String, colorKey: String) {
        viewModelScope.launch { repository.updateCategory(id, name, iconKey, colorKey) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            if (!repository.setArchived(id, archived)) message.update { CategoriesMessage.CANNOT_ARCHIVE_LAST }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            if (!repository.deleteIfUnused(id)) message.update { CategoriesMessage.IN_USE }
        }
    }
}
