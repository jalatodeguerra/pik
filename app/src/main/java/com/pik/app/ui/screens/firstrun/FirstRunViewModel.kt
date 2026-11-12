package com.pik.app.ui.screens.firstrun

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.R
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.CategoryRepository
import com.pik.app.domain.AccountType
import com.pik.app.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirstRunStep { WELCOME, ACCOUNTS }

data class AccountDraft(
    val key: Int,
    val name: String,
    val type: AccountType,
    /** What the user typed, Italian style ("1.234,56"). Blank means 0. */
    val balanceText: String,
) {
    /** Starting balance in cents, or null when the text is not a valid amount. */
    val balanceCents: Long? get() = if (balanceText.isBlank()) 0L else Money.parse(balanceText)
    val isValid: Boolean get() = name.isNotBlank() && balanceCents != null
}

data class FirstRunUiState(
    val step: FirstRunStep = FirstRunStep.WELCOME,
    val drafts: List<AccountDraft> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val canFinish: Boolean get() = !saving && drafts.isNotEmpty() && drafts.all { it.isValid }
}

@HiltViewModel
class FirstRunViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val prefs: PikPreferences,
) : ViewModel() {

    private var nextKey = 0

    private val _state = MutableStateFlow(
        FirstRunUiState(
            drafts = listOf(
                AccountDraft(nextKey++, context.getString(R.string.account_default_bank), AccountType.BANK, ""),
                AccountDraft(nextKey++, context.getString(R.string.account_default_cash), AccountType.CASH, ""),
            ),
        ),
    )
    val state: StateFlow<FirstRunUiState> = _state.asStateFlow()

    fun goToAccounts() = _state.update { it.copy(step = FirstRunStep.ACCOUNTS) }

    fun goToWelcome() = _state.update { it.copy(step = FirstRunStep.WELCOME) }

    fun setName(key: Int, name: String) = updateDraft(key) { it.copy(name = name.take(30)) }

    fun setType(key: Int, type: AccountType) = updateDraft(key) { it.copy(type = type) }

    fun setBalance(key: Int, text: String) = updateDraft(key) { it.copy(balanceText = text.take(16)) }

    private fun updateDraft(key: Int, transform: (AccountDraft) -> AccountDraft) {
        _state.update { s -> s.copy(drafts = s.drafts.map { if (it.key == key) transform(it) else it }) }
    }

    fun addAccount(defaultName: String) {
        _state.update { s ->
            s.copy(drafts = s.drafts + AccountDraft(nextKey++, defaultName, AccountType.BANK, ""))
        }
    }

    fun removeAccount(key: Int) {
        _state.update { s ->
            if (s.drafts.size <= 1) s else s.copy(drafts = s.drafts.filterNot { it.key == key })
        }
    }

    fun finish() {
        val s = _state.value
        if (!s.canFinish) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entities = s.drafts.mapIndexed { index, d ->
                AccountEntity(
                    name = d.name.trim(),
                    type = d.type,
                    startingBalanceCents = d.balanceCents ?: 0L,
                    sortOrder = index,
                    createdAt = now,
                )
            }
            val ids = accounts.createAccounts(entities)
            categories.ensureDefaultCategories()
            ids.firstOrNull()?.let { prefs.setLastUsedAccountId(it) }
            prefs.setOnboardingComplete(true)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }
}
