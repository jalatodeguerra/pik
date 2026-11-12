package com.pik.app.ui.screens.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.ImportRepository
import com.pik.app.domain.AccountType
import com.pik.app.domain.importing.AccountRef
import com.pik.app.domain.importing.FileParseOutcome
import com.pik.app.domain.importing.ImportMatcher
import com.pik.app.domain.importing.ImportSource
import com.pik.app.domain.importing.StatementFile
import com.pik.app.domain.importing.StatementFormat
import com.pik.app.domain.importing.StatementResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/** A file that was read and is waiting for the user to say which Pik account it belongs to. */
data class PendingFile(
    val result: StatementResult,
    val accounts: List<AccountEntity>,
    val suggestedAccountId: Long?,
)

enum class ImportFileMessageKind { DONE, OLD_EXCEL, UNREADABLE, NO_ROWS, FAILED }

data class ImportFileMessage(val kind: ImportFileMessageKind, val added: Int = 0, val alreadyKnown: Int = 0)

data class ImportFileState(
    val busy: Boolean = false,
    val pending: PendingFile? = null,
    val message: ImportFileMessage? = null,
)

/** Reads a chosen bank / Revolut / PayPal file and puts its payments in the Inbox. Used by Settings and the Inbox. */
@HiltViewModel
class ImportFileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imports: ImportRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportFileState())
    val state: StateFlow<ImportFileState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome: FileParseOutcome? = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
                    if (bytes == null) null else StatementFile.parse(bytes)
                } catch (e: Exception) {
                    null
                }
            }
            when (outcome) {
                null -> post(ImportFileMessage(ImportFileMessageKind.FAILED))
                FileParseOutcome.OldExcel -> post(ImportFileMessage(ImportFileMessageKind.OLD_EXCEL))
                FileParseOutcome.Unreadable -> post(ImportFileMessage(ImportFileMessageKind.UNREADABLE))
                FileParseOutcome.NoTransactions -> post(ImportFileMessage(ImportFileMessageKind.NO_ROWS))
                is FileParseOutcome.Parsed -> {
                    val accounts = accountRepository.getAccounts().filter { !it.archived }
                    val refs = accounts.map { AccountRef(it.id, it.name, it.type == AccountType.BANK) }
                    val source = when (outcome.result.format) {
                        StatementFormat.REVOLUT -> ImportSource.REVOLUT
                        StatementFormat.PAYPAL -> ImportSource.PAYPAL
                        StatementFormat.GENERIC -> ImportSource.RELAX
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            pending = PendingFile(outcome.result, accounts, ImportMatcher.defaultAccountFor(source, refs)),
                        )
                    }
                }
            }
        }
    }

    private fun post(message: ImportFileMessage) =
        _state.update { it.copy(busy = false, pending = null, message = message) }

    fun cancel() = _state.update { it.copy(pending = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** The user chose the account: the rows go to the Inbox. */
    fun confirm(accountId: Long) {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(busy = true, pending = null) }
        viewModelScope.launch {
            val message = try {
                val result = imports.addFileRows(accountId, pending.result.rows)
                ImportFileMessage(ImportFileMessageKind.DONE, result.added, result.alreadyKnown)
            } catch (e: Exception) {
                ImportFileMessage(ImportFileMessageKind.FAILED)
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) throw java.io.IOException("file too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_FILE_BYTES = 30 * 1024 * 1024
    }
}
