package com.pik.app.ui.screens.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.AccountType
import com.pik.app.domain.Money
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors

@Composable
fun FirstRunScreen(
    onFinished: () -> Unit,
    viewModel: FirstRunViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    BackHandler(enabled = state.step == FirstRunStep.ACCOUNTS) { viewModel.goToWelcome() }

    when (state.step) {
        FirstRunStep.WELCOME -> WelcomeStep(onContinue = viewModel::goToAccounts)
        FirstRunStep.ACCOUNTS -> AccountsStep(state, viewModel)
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = PikColors.Lime,
            )
            Text(
                text = stringResource(R.string.firstrun_welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        PikPrimaryButton(
            text = stringResource(R.string.firstrun_get_started),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
    }
}

@Composable
private fun AccountsStep(state: FirstRunUiState, viewModel: FirstRunViewModel) {
    val newAccountName = stringResource(R.string.account_default_new)
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.firstrun_accounts_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = PikColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.firstrun_accounts_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                    )
                }
            }
            items(state.drafts, key = { it.key }) { draft ->
                AccountDraftCard(
                    draft = draft,
                    canRemove = state.drafts.size > 1,
                    onName = { viewModel.setName(draft.key, it) },
                    onType = { viewModel.setType(draft.key, it) },
                    onBalance = { viewModel.setBalance(draft.key, it) },
                    onRemove = { viewModel.removeAccount(draft.key) },
                )
            }
            item {
                PikSecondaryButton(
                    text = stringResource(R.string.firstrun_add_account),
                    onClick = { viewModel.addAccount(newAccountName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }
        }
        PikPrimaryButton(
            text = stringResource(R.string.firstrun_finish),
            onClick = viewModel::finish,
            enabled = state.canFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),
        )
    }
}

@Composable
private fun AccountDraftCard(
    draft: AccountDraft,
    canRemove: Boolean,
    onName: (String) -> Unit,
    onType: (AccountType) -> Unit,
    onBalance: (String) -> Unit,
    onRemove: () -> Unit,
) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onName,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.firstrun_account_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = pikTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.firstrun_remove_account),
                            tint = PikColors.TextSecondary,
                        )
                    }
                }
            }
            PikSegmented(
                options = listOf(
                    stringResource(R.string.account_type_cash),
                    stringResource(R.string.account_type_bank),
                ),
                selectedIndex = if (draft.type == AccountType.CASH) 0 else 1,
                onSelect = { onType(if (it == 0) AccountType.CASH else AccountType.BANK) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.balanceText,
                onValueChange = { value ->
                    if (value.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) onBalance(value)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.firstrun_starting_balance)) },
                placeholder = { Text(Money.formatPlain(0)) },
                suffix = { Text(Money.CURRENCY_SYMBOL) },
                singleLine = true,
                isError = draft.balanceCents == null,
                shape = RoundedCornerShape(16.dp),
                colors = pikTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}
