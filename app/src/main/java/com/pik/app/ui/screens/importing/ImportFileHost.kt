package com.pik.app.ui.screens.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.importing.StatementFormat
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.theme.PikColors

private val IMPORT_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/plain",
    "*/*",
)

/** Returns a function that opens the system file picker for a bank, Revolut or PayPal export. */
@Composable
fun rememberImportFileLauncher(viewModel: ImportFileViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }
    return remember(launcher) { { launcher.launch(IMPORT_MIME_TYPES) } }
}

/** The account question after a file was read, and the result messages (shown in [snackbarHostState]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportFileDialogs(viewModel: ImportFileViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val message = state.message
    val messageText: String? = message?.let {
        when (it.kind) {
            ImportFileMessageKind.DONE -> stringResource(R.string.import_file_done, it.added, it.alreadyKnown)
            ImportFileMessageKind.OLD_EXCEL -> stringResource(R.string.import_file_old_excel)
            ImportFileMessageKind.UNREADABLE -> stringResource(R.string.import_file_unreadable)
            ImportFileMessageKind.NO_ROWS -> stringResource(R.string.import_file_no_rows)
            ImportFileMessageKind.FAILED -> stringResource(R.string.import_file_failed)
        }
    }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val pending = state.pending ?: return
    var selected by remember(pending) { mutableStateOf(pending.suggestedAccountId) }
    val formatName = when (pending.result.format) {
        StatementFormat.REVOLUT -> stringResource(R.string.import_format_revolut)
        StatementFormat.PAYPAL -> stringResource(R.string.import_format_paypal)
        StatementFormat.GENERIC -> stringResource(R.string.import_format_generic)
    }
    PikDialog(
        title = stringResource(R.string.import_file_account_title),
        onDismiss = viewModel::cancel,
        confirmLabel = stringResource(R.string.import_file_confirm),
        onConfirm = { selected?.let { viewModel.confirm(it) } },
        confirmEnabled = selected != null,
    ) {
        Text(
            text = stringResource(
                R.string.import_file_found,
                pending.result.rows.size,
                formatName,
                pending.result.skipped,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pending.accounts.forEach { account ->
                PikFilterChip(
                    label = account.name,
                    selected = account.id == selected,
                    onClick = { selected = account.id },
                )
            }
        }
    }
}
