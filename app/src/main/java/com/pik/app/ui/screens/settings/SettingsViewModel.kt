package com.pik.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.backup.BackupError
import com.pik.app.backup.BackupFile
import com.pik.app.backup.BackupFormatException
import com.pik.app.backup.BackupJson
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.BackupRepository
import com.pik.app.data.repo.RecurringRepository
import com.pik.app.data.repo.TransactionRepository
import com.pik.app.domain.AccountType
import com.pik.app.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class SettingsAccount(
    val account: AccountEntity,
    val balanceCents: Long,
    val transactionCount: Int,
)

/** A validated backup waiting for the user's confirmation. */
data class PendingRestore(val file: BackupFile) {
    val accountCount: Int get() = file.accounts.size
    val categoryCount: Int get() = file.categories.size
    val transactionCount: Int get() = file.transactions.size
    val debtCount: Int get() = file.debts.size
}

enum class SettingsMessage {
    BACKUP_SAVED,
    BACKUP_FAILED,
    RESTORE_DONE,
    RESTORE_READ_FAILED,
    RESTORE_NOT_A_BACKUP,
    RESTORE_UNSUPPORTED_VERSION,
    RESTORE_INVALID_DATA,
    RESTORE_FAILED,
    CANNOT_ARCHIVE_LAST_ACCOUNT,
    ACCOUNT_IN_USE,
}

data class SettingsUiState(
    val accounts: List<SettingsAccount> = emptyList(),
    val lastBackupEpochDay: Long? = null,
    val monthStartDay: Int = 1,
    val expectedIncomeCents: Long? = null,
    /** Hour of the day (0..23) of the daily reminder. */
    val reminderHour: Int = 9,
    /** True when at least one recurring rule has "remind me the day before" on. */
    val hasReminders: Boolean = false,
    val versionName: String = "",
    val busy: Boolean = false,
    val pendingRestore: PendingRestore? = null,
    val message: SettingsMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val backupRepository: BackupRepository,
    private val recurringRepository: RecurringRepository,
    private val reminderScheduler: ReminderScheduler,
    private val prefs: PikPreferences,
) : ViewModel() {

    private val versionName: String = readVersionName()

    private val localState = MutableStateFlow(TransientState())

    private data class TransientState(
        val busy: Boolean = false,
        val pendingRestore: PendingRestore? = null,
        val message: SettingsMessage? = null,
    )

    private val accountItems = combine(
        accountRepository.observeAllAccounts(),
        accountRepository.observeBalances(),
        accountRepository.observeTransactionCounts(),
    ) { accounts, balances, counts ->
        accounts.map {
            SettingsAccount(
                account = it,
                balanceCents = balances[it.id] ?: it.startingBalanceCents,
                transactionCount = counts[it.id] ?: 0,
            )
        }
    }

    private data class PlanPrefs(
        val monthStartDay: Int,
        val expectedIncomeCents: Long?,
        val reminderHour: Int,
        val hasReminders: Boolean,
    )

    private val planPrefs = combine(
        prefs.monthStartDay,
        prefs.expectedIncomeCents,
        prefs.reminderHour,
        recurringRepository.observeRules().map { rules -> rules.any { it.remindDayBefore && it.active } },
    ) { startDay, expectedIncome, hour, hasReminders ->
        PlanPrefs(startDay, expectedIncome, hour, hasReminders)
    }

    val state: StateFlow<SettingsUiState> = combine(
        accountItems,
        prefs.lastBackupEpochDay,
        localState,
        planPrefs,
    ) { accounts, lastBackup, t, plan ->
        SettingsUiState(
            accounts = accounts,
            lastBackupEpochDay = lastBackup,
            monthStartDay = plan.monthStartDay,
            expectedIncomeCents = plan.expectedIncomeCents,
            reminderHour = plan.reminderHour,
            hasReminders = plan.hasReminders,
            versionName = versionName,
            busy = t.busy,
            pendingRestore = t.pendingRestore,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = versionName))

    @Suppress("DEPRECATION")
    private fun readVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    fun consumeMessage() = localState.update { it.copy(message = null) }

    private fun post(message: SettingsMessage) = localState.update { it.copy(message = message) }

    // ---- accounts ----

    fun addAccount(name: String, type: AccountType, startingBalanceCents: Long) {
        viewModelScope.launch { accountRepository.addAccount(name, type, startingBalanceCents) }
    }

    fun updateAccount(id: Long, name: String, type: AccountType, startingBalanceCents: Long) {
        viewModelScope.launch { accountRepository.updateAccount(id, name, type, startingBalanceCents) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            if (!accountRepository.setArchived(id, archived)) post(SettingsMessage.CANNOT_ARCHIVE_LAST_ACCOUNT)
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            if (!accountRepository.deleteIfUnused(id)) post(SettingsMessage.ACCOUNT_IN_USE)
        }
    }

    fun checkBalance(accountId: Long, realCents: Long, note: String) {
        viewModelScope.launch { transactionRepository.applyBalanceCheck(accountId, realCents, note) }
    }

    // ---- plan ----

    fun setMonthStartDay(day: Int) {
        viewModelScope.launch { prefs.setMonthStartDay(day) }
    }

    /** null clears the expected income. */
    fun setExpectedIncome(cents: Long?) {
        viewModelScope.launch { prefs.setExpectedIncomeCents(cents) }
    }

    // ---- reminders ----

    /** Saves the reminder hour and re-schedules the daily worker with the new delay. */
    fun setReminderHour(hour: Int) {
        viewModelScope.launch {
            prefs.setReminderHour(hour)
            reminderScheduler.schedule(hour, replace = true)
        }
    }

    // ---- backup ----

    fun saveBackup(uri: Uri) {
        localState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val ok = try {
                val text = backupRepository.export(versionName)
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException("Cannot open output")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                prefs.setLastBackupEpochDay(LocalDate.now().toEpochDay())
                true
            } catch (e: Exception) {
                false
            }
            localState.update {
                it.copy(busy = false, message = if (ok) SettingsMessage.BACKUP_SAVED else SettingsMessage.BACKUP_FAILED)
            }
        }
    }

    /** Reads and validates the chosen file. Nothing changes until [confirmRestore]. */
    fun prepareRestore(uri: Uri) {
        localState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
            } catch (e: Exception) {
                null
            }
            if (text == null) {
                localState.update { it.copy(busy = false, message = SettingsMessage.RESTORE_READ_FAILED) }
                return@launch
            }
            try {
                val file = BackupJson.parse(text)
                localState.update { it.copy(busy = false, pendingRestore = PendingRestore(file)) }
            } catch (e: BackupFormatException) {
                val message = when (e.error) {
                    BackupError.NOT_A_BACKUP -> SettingsMessage.RESTORE_NOT_A_BACKUP
                    BackupError.UNSUPPORTED_VERSION -> SettingsMessage.RESTORE_UNSUPPORTED_VERSION
                    BackupError.INVALID_DATA -> SettingsMessage.RESTORE_INVALID_DATA
                }
                localState.update { it.copy(busy = false, message = message) }
            }
        }
    }

    fun cancelRestore() = localState.update { it.copy(pendingRestore = null) }

    fun confirmRestore() {
        val pending = localState.value.pendingRestore ?: return
        localState.update { it.copy(busy = true, pendingRestore = null) }
        viewModelScope.launch {
            val ok = try {
                backupRepository.replaceAll(pending.file)
                prefs.setOnboardingComplete(true)
                true
            } catch (e: Exception) {
                false
            }
            localState.update {
                it.copy(busy = false, message = if (ok) SettingsMessage.RESTORE_DONE else SettingsMessage.RESTORE_FAILED)
            }
        }
    }
}
