package com.pik.app.ui.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.AccountEntity
import com.pik.app.domain.AccountType
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.RelativeDay
import com.pik.app.domain.TransactionType
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikDatePickerDialog
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import java.time.LocalDate

private const val MAX_NOTE_LENGTH = 120

private val typeOptions = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

@Composable
fun AddTransactionScreen(
    onClose: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.add_saved)

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }
    LaunchedEffect(state.savedCount) {
        if (state.savedCount > 0) snackbarHostState.showSnackbar(savedMessage)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            AddTopBar(
                isEditing = state.isEditing,
                onClose = onClose,
                onDelete = { showDeleteConfirm = true },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isDebtPayment) {
                    DebtPaymentCard(state.debtName)
                } else {
                    PikSegmented(
                        options = listOf(
                            stringResource(R.string.type_expense),
                            stringResource(R.string.type_income),
                            stringResource(R.string.type_transfer),
                        ),
                        selectedIndex = typeOptions.indexOf(state.type).coerceAtLeast(0),
                        onSelect = { viewModel.selectType(typeOptions[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AmountDisplay(state)

                AmountKeypad(
                    onDigit = viewModel::typeDigit,
                    onComma = viewModel::typeComma,
                    onBackspace = viewModel::backspace,
                )

                if (state.type == TransactionType.TRANSFER) {
                    LabeledSection(stringResource(R.string.add_from)) {
                        AccountChips(state.accounts, state.accountId, viewModel::selectAccount)
                    }
                    LabeledSection(stringResource(R.string.add_to)) {
                        AccountChips(state.accounts, state.toAccountId, viewModel::selectToAccount)
                    }
                } else {
                    // A debt payment has no category: it is shown with its debt instead.
                    if (!state.isDebtPayment) {
                        LabeledSection(stringResource(R.string.add_category)) {
                            CategoryChips(state, viewModel::selectCategory)
                        }
                    }
                    LabeledSection(stringResource(R.string.add_account)) {
                        AccountChips(state.accounts, state.accountId, viewModel::selectAccount)
                    }
                }

                DateRow(date = state.date, onClick = { showDatePicker = true })

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.setNote(it.take(MAX_NOTE_LENGTH)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_note)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = pikTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
                // Room to scroll the last field fully above the pinned buttons.
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PikPrimaryButton(
                    text = stringResource(R.string.add_save),
                    onClick = { viewModel.save(addAnother = false) },
                    enabled = state.canSave,
                    // Narrower than "Save and add another" so its label fits on one line.
                    modifier = Modifier.weight(if (state.isEditing) 1f else 2f).height(52.dp),
                )
                if (!state.isEditing) {
                    PikSecondaryButton(
                        text = stringResource(R.string.add_save_and_add_another),
                        onClick = { viewModel.save(addAnother = true) },
                        enabled = state.canSave,
                        modifier = Modifier.weight(3f).height(52.dp),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PikColors.SurfaceRaised,
                contentColor = PikColors.TextPrimary,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = PikColors.SurfaceRaised,
            titleContentColor = PikColors.TextPrimary,
            textContentColor = PikColors.TextSecondary,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Coral),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.TextPrimary),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDatePicker) {
        PikDatePickerDialog(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.setDate(it)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun AddTopBar(isEditing: Boolean, onClose: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = PikColors.TextPrimary,
            )
        }
        Text(
            text = stringResource(if (isEditing) R.string.add_title_edit else R.string.add_title),
            style = MaterialTheme.typography.titleLarge,
            color = PikColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = PikColors.Coral,
                )
            }
        }
    }
}

/** Shown instead of the type switch and the category chips when editing a debt payment. */
@Composable
private fun DebtPaymentCard(debtName: String?) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircle(Icons.Filled.AccountBalanceWallet, PikColors.Blue, null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.add_debt_payment_for),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
                Text(
                    text = debtName ?: stringResource(R.string.transaction_debt_payment),
                    style = MaterialTheme.typography.titleMedium,
                    color = PikColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.add_debt_payment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AmountDisplay(state: AddUiState) {
    val text = state.amount.display() + Money.NBSP + Money.CURRENCY_SYMBOL
    val color: Color = when {
        state.amount.raw.isEmpty() -> PikColors.TextSecondary
        state.type == TransactionType.EXPENSE -> PikColors.Expense
        state.type == TransactionType.INCOME -> PikColors.Income
        else -> PikColors.TextPrimary
    }
    Text(
        text = text,
        style = PikNumberStyles.AmountDisplay.copy(fontSize = if (text.length > 11) 40.sp else 56.sp),
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = PikColors.TextSecondary,
        )
        content()
    }
}

@Composable
private fun PikChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PikColors.OnLime else iconTint,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = PikColors.Surface,
            labelColor = PikColors.TextPrimary,
            iconColor = PikColors.TextPrimary,
            selectedContainerColor = PikColors.Lime,
            selectedLabelColor = PikColors.OnLime,
            selectedLeadingIconColor = PikColors.OnLime,
        ),
        border = null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(state: AddUiState, onSelect: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.categories.forEach { category ->
            PikChip(
                label = category.name,
                selected = category.id == state.categoryId,
                onClick = { onSelect(category.id) },
                icon = CategoryStyle.icon(category.iconKey),
                iconTint = CategoryStyle.color(category.colorKey),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<AccountEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { account ->
            PikChip(
                label = account.name,
                selected = account.id == selectedId,
                onClick = { onSelect(account.id) },
                icon = if (account.type == AccountType.CASH) Icons.Filled.Payments else Icons.Filled.AccountBalance,
                iconTint = PikColors.Lime,
            )
        }
    }
}

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    val label = when (DateFormats.relativeDay(date, LocalDate.now())) {
        RelativeDay.TODAY -> stringResource(R.string.date_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
        RelativeDay.OTHER -> DateFormats.shortDate(date)
    }
    PikCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = PikColors.Lime,
            )
            Text(
                text = stringResource(R.string.add_date),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = PikColors.TextPrimary,
            )
        }
    }
}
