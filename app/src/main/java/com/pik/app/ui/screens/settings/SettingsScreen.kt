package com.pik.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.AccountType
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.Period
import com.pik.app.ui.components.CheckBalanceDialog
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import java.time.LocalDate

private const val MAX_NAME_LENGTH = 40

private fun SettingsMessage.stringRes(): Int = when (this) {
    SettingsMessage.BACKUP_SAVED -> R.string.backup_saved
    SettingsMessage.BACKUP_FAILED -> R.string.backup_failed
    SettingsMessage.RESTORE_DONE -> R.string.restore_done
    SettingsMessage.RESTORE_READ_FAILED -> R.string.restore_read_failed
    SettingsMessage.RESTORE_NOT_A_BACKUP -> R.string.restore_not_a_backup
    SettingsMessage.RESTORE_UNSUPPORTED_VERSION -> R.string.restore_unsupported_version
    SettingsMessage.RESTORE_INVALID_DATA -> R.string.restore_invalid_data
    SettingsMessage.RESTORE_FAILED -> R.string.restore_failed
    SettingsMessage.CANNOT_ARCHIVE_LAST_ACCOUNT -> R.string.account_cannot_archive_last
    SettingsMessage.ACCOUNT_IN_USE -> R.string.account_in_use
}

/** Which account the editor dialog works on: null item means "new account". */
private data class AccountEditorTarget(val item: SettingsAccount?)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.stringRes()) }

    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    var editor by remember { mutableStateOf<AccountEditorTarget?>(null) }
    var checking by remember { mutableStateOf<SettingsAccount?>(null) }
    var editingMonthStart by remember { mutableStateOf(false) }
    var editingIncome by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.saveBackup(uri) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.prepareRestore(uri) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PikBackTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_accounts))
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    state.accounts.forEach { item ->
                        AccountRow(
                            item = item,
                            onClick = { editor = AccountEditorTarget(item) },
                            onCheckBalance = { checking = item },
                        )
                        HorizontalDivider(color = PikColors.Outline)
                    }
                    AddRow(
                        label = stringResource(R.string.account_add),
                        onClick = { editor = AccountEditorTarget(null) },
                    )
                }

                SectionTitle(stringResource(R.string.settings_categories))
                PikCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenCategories) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconCircle(Icons.Filled.Category, PikColors.Lime, null)
                        Text(
                            text = stringResource(R.string.settings_manage_categories),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PikColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = PikColors.TextSecondary,
                        )
                    }
                }

                SectionTitle(stringResource(R.string.settings_plan))
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    ValueRow(
                        label = stringResource(R.string.settings_month_starts),
                        value = stringResource(R.string.settings_month_starts_value, state.monthStartDay),
                        onClick = { editingMonthStart = true },
                    )
                    HorizontalDivider(color = PikColors.Outline)
                    ValueRow(
                        label = stringResource(R.string.settings_expected_income),
                        value = state.expectedIncomeCents?.let { Money.format(it) }
                            ?: stringResource(R.string.settings_expected_income_not_set),
                        onClick = { editingIncome = true },
                    )
                }

                SectionTitle(stringResource(R.string.settings_backup))
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val lastSaved = state.lastBackupEpochDay?.let { DateFormats.shortDate(LocalDate.ofEpochDay(it)) }
                            ?: stringResource(R.string.backup_never)
                        Text(
                            text = stringResource(R.string.backup_last_saved, lastSaved),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PikColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.backup_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.TextSecondary,
                        )
                        PikPrimaryButton(
                            text = stringResource(R.string.backup_save),
                            onClick = { createLauncher.launch("pik-backup-${LocalDate.now()}.json") },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PikSecondaryButton(
                            text = stringResource(R.string.backup_restore),
                            onClick = { openLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SectionTitle(stringResource(R.string.settings_about))
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.about_version),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PikColors.TextPrimary,
                        )
                        Text(
                            text = state.versionName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PikColors.TextSecondary,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.about_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PikColors.SurfaceRaised,
                contentColor = PikColors.TextPrimary,
            )
        }
    }

    editor?.let { target ->
        AccountEditorDialog(
            item = target.item,
            activeAccountCount = state.accounts.count { !it.account.archived },
            onDismiss = { editor = null },
            onSave = { name, type, balance ->
                val existing = target.item
                if (existing == null) {
                    viewModel.addAccount(name, type, balance)
                } else {
                    viewModel.updateAccount(existing.account.id, name, type, balance)
                }
                editor = null
            },
            onSetArchived = { archived ->
                target.item?.let { viewModel.setArchived(it.account.id, archived) }
                editor = null
            },
            onDelete = {
                target.item?.let { viewModel.deleteAccount(it.account.id) }
                editor = null
            },
        )
    }

    if (editingMonthStart) {
        MonthStartDialog(
            current = state.monthStartDay,
            onDismiss = { editingMonthStart = false },
            onSave = { day ->
                viewModel.setMonthStartDay(day)
                editingMonthStart = false
            },
        )
    }

    if (editingIncome) {
        ExpectedIncomeDialog(
            currentCents = state.expectedIncomeCents,
            onDismiss = { editingIncome = false },
            onSave = { cents ->
                viewModel.setExpectedIncome(cents)
                editingIncome = false
            },
        )
    }

    checking?.let { item ->
        CheckBalanceDialog(
            accountName = item.account.name,
            currentCents = item.balanceCents,
            onDismiss = { checking = null },
            onConfirm = { real, note ->
                viewModel.checkBalance(item.account.id, real, note)
                checking = null
            },
        )
    }

    state.pendingRestore?.let { pending ->
        PikConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            message = stringResource(
                R.string.restore_confirm_message,
                pending.accountCount,
                pending.categoryCount,
                pending.transactionCount,
                pending.debtCount,
            ),
            confirmLabel = stringResource(R.string.backup_restore_confirm),
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
            destructive = true,
        )
    }
}

@Composable
private fun AccountRow(item: SettingsAccount, onClick: () -> Unit, onCheckBalance: () -> Unit) {
    val archived = item.account.archived
    val contentColor = if (archived) PikColors.TextSecondary else PikColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(
            icon = if (item.account.type == AccountType.CASH) Icons.Filled.Payments else Icons.Filled.AccountBalance,
            tint = if (archived) PikColors.TextSecondary else PikColors.Lime,
            contentDescription = null,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val typeLabel = stringResource(
                if (item.account.type == AccountType.CASH) R.string.account_type_cash else R.string.account_type_bank,
            )
            Text(
                text = if (archived) "$typeLabel · ${stringResource(R.string.account_archived)}" else typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 1,
            )
        }
        Text(
            text = Money.format(item.balanceCents),
            style = PikNumberStyles.Medium,
            color = contentColor,
            maxLines = 1,
        )
        IconButton(onClick = onCheckBalance) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.account_check_balance),
                tint = PikColors.Lime,
            )
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = PikColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = PikColors.TextSecondary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PikColors.TextSecondary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthStartDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    PikDialog(
        title = stringResource(R.string.settings_month_starts),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = { onSave(selected) },
    ) {
        Text(
            text = stringResource(R.string.settings_month_starts_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (day in Period.MIN_START_DAY..Period.MAX_START_DAY) {
                PikFilterChip(label = day.toString(), selected = day == selected, onClick = { selected = day })
            }
        }
    }
}

@Composable
private fun ExpectedIncomeDialog(currentCents: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(currentCents?.let { Money.formatPlain(it) }.orEmpty()) }
    // Blank clears the setting; otherwise it must be a valid non-negative amount.
    val parsed: Long? = if (text.isBlank()) null else Money.parse(text)
    val valid = text.isBlank() || (parsed != null && parsed >= 0)
    PikDialog(
        title = stringResource(R.string.settings_expected_income),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        confirmEnabled = valid,
        onConfirm = { onSave(parsed) },
    ) {
        Text(
            text = stringResource(R.string.settings_expected_income_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(15) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_expected_income_field)) },
            placeholder = { Text("0,00") },
            suffix = { Text(Money.CURRENCY_SYMBOL) },
            isError = !valid,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Text(
            text = stringResource(R.string.settings_expected_income_clear_hint),
            style = MaterialTheme.typography.bodySmall,
            color = PikColors.TextSecondary,
        )
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = PikColors.Lime)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = PikColors.Lime)
    }
}

@Composable
private fun AccountEditorDialog(
    item: SettingsAccount?,
    activeAccountCount: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, startingBalanceCents: Long) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item?.account?.name.orEmpty()) }
    var type by remember { mutableStateOf(item?.account?.type ?: AccountType.BANK) }
    var balanceText by remember {
        mutableStateOf(item?.account?.startingBalanceCents?.let { Money.formatPlain(it) }.orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val balance: Long? = if (balanceText.isBlank()) 0L else Money.parse(balanceText)
    val canSave = name.isNotBlank() && balance != null
    val types = listOf(AccountType.CASH, AccountType.BANK)

    PikDialog(
        title = stringResource(if (item == null) R.string.account_new_title else R.string.account_edit_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        confirmEnabled = canSave,
        onConfirm = { if (balance != null) onSave(name.trim(), type, balance) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.firstrun_account_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PikSegmented(
            options = listOf(
                stringResource(R.string.account_type_cash),
                stringResource(R.string.account_type_bank),
            ),
            selectedIndex = types.indexOf(type),
            onSelect = { type = types[it] },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.firstrun_starting_balance)) },
            placeholder = { Text("0,00") },
            suffix = { Text(Money.CURRENCY_SYMBOL) },
            isError = balance == null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        if (item != null) {
            val archived = item.account.archived
            val isLastActive = !archived && activeAccountCount <= 1
            TextButton(
                onClick = { onSetArchived(!archived) },
                enabled = !isLastActive,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = PikColors.Lime,
                    disabledContentColor = PikColors.TextSecondary,
                ),
            ) {
                Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive))
            }
            Text(
                text = stringResource(
                    if (isLastActive) R.string.account_cannot_archive_last else R.string.account_archive_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            if (item.transactionCount == 0) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Coral),
                ) { Text(stringResource(R.string.action_delete)) }
            } else {
                Text(
                    text = stringResource(R.string.account_delete_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
        }
    }

    if (confirmDelete) {
        PikConfirmDialog(
            title = stringResource(R.string.account_delete_title),
            message = stringResource(R.string.delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
