package com.pik.app.ui.screens.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.pik.app.domain.CategoryFilter
import com.pik.app.domain.TypeFilter
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.pikTextFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.DateFormats
import com.pik.app.domain.RelativeDay
import com.pik.app.domain.TransactionType
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.TransactionListItem
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

@Composable
fun ActivityScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "This month" follows the calendar if the app stays open across a month change (like Home).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshMonth()
        onPauseOrDispose { }
    }
    ActivityContent(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onQueryChange = viewModel::setQuery,
        onTypeFilter = viewModel::setTypeFilter,
        onAccountFilter = viewModel::setAccountFilter,
        onCategoryFilter = viewModel::setCategoryFilter,
        onClearPeriod = viewModel::clearPeriod,
        onTransactionClick = onTransactionClick,
        onDeleteAdjustment = viewModel::deleteTransaction,
    )
}

@Composable
private fun ActivityContent(
    state: ActivityUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTypeFilter: (TypeFilter) -> Unit,
    onAccountFilter: (Long?) -> Unit,
    onCategoryFilter: (CategoryFilter) -> Unit,
    onClearPeriod: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
) {
    val today = LocalDate.now()
    // The text field keeps its own copy so typing never fights with state round trips.
    var queryText by rememberSaveable { mutableStateOf(state.query) }
    var adjustmentToDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.activity_title),
            style = MaterialTheme.typography.headlineLarge,
            color = PikColors.TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        if (state.period != null) {
            // Insights asked for a payday period: show it instead of the month arrows.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = state.period.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = PikColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onClearPeriod,
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
                ) {
                    Text(
                        text = stringResource(R.string.activity_back_to_months),
                        maxLines = 2,
                        textAlign = TextAlign.End,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_month),
                        tint = PikColors.TextPrimary,
                    )
                }
                Text(
                    text = DateFormats.monthYear(state.month),
                    style = MaterialTheme.typography.titleLarge,
                    color = PikColors.TextPrimary,
                )
                IconButton(onClick = onNext, enabled = state.canGoNext) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_month),
                        tint = if (state.canGoNext) PikColors.TextPrimary else PikColors.Outline,
                    )
                }
            }
        }

        OutlinedTextField(
            value = queryText,
            onValueChange = {
                queryText = it
                onQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.activity_search_hint)) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = PikColors.TextSecondary)
            },
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = {
                        queryText = ""
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.activity_search_clear),
                            tint = PikColors.TextPrimary,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeFilter.entries.forEach { filter ->
                PikFilterChip(
                    label = stringResource(filter.labelRes()),
                    selected = state.typeFilter == filter,
                    onClick = { onTypeFilter(filter) },
                )
            }
        }
        if (state.accounts.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PikFilterChip(
                    label = stringResource(R.string.filter_all_accounts),
                    selected = state.accountFilter == null,
                    onClick = { onAccountFilter(null) },
                )
                state.accounts.forEach { account ->
                    PikFilterChip(
                        label = account.name,
                        selected = state.accountFilter == account.id,
                        onClick = { onAccountFilter(account.id) },
                    )
                }
            }
        }

        if (state.categoryOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PikFilterChip(
                    label = stringResource(R.string.filter_all_categories),
                    selected = state.categoryFilter == CategoryFilter.All,
                    onClick = { onCategoryFilter(CategoryFilter.All) },
                )
                state.categoryOptions.forEach { option ->
                    PikFilterChip(
                        label = option.label(),
                        selected = state.categoryFilter == option.filter,
                        onClick = { onCategoryFilter(option.filter) },
                    )
                }
            }
        }

        if (!state.isLoading && state.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconCircle(
                        icon = if (state.filtering) Icons.Filled.Search else Icons.Filled.Receipt,
                        tint = PikColors.Lime,
                        contentDescription = null,
                        size = 64,
                    )
                    Text(
                        text = stringResource(
                            if (state.filtering) R.string.activity_no_matches else R.string.activity_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PikColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.groups, key = { it.date.toEpochDay() }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = dayLabel(group.date, today),
                            style = MaterialTheme.typography.labelLarge,
                            color = PikColors.TextSecondary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        PikCard(modifier = Modifier.fillMaxWidth()) {
                            group.rows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(color = PikColors.Outline)
                                TransactionListItem(
                                    row = row,
                                    showDate = false,
                                    // Adjustments are not editable in the Add screen, only deletable.
                                    onClick = if (row.type == TransactionType.ADJUSTMENT) {
                                        { adjustmentToDelete = row.id }
                                    } else {
                                        { onTransactionClick(row.id) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    adjustmentToDelete?.let { id ->
        PikConfirmDialog(
            title = stringResource(R.string.delete_adjustment_title),
            message = stringResource(R.string.delete_adjustment_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                adjustmentToDelete = null
                onDeleteAdjustment(id)
            },
            onDismiss = { adjustmentToDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun CategoryOption.label(): String = when {
    name != null -> name
    filter == CategoryFilter.DebtPayments -> stringResource(R.string.filter_debt_payments)
    filter == CategoryFilter.Uncategorised -> stringResource(R.string.transaction_uncategorized)
    else -> stringResource(R.string.filter_category_selected)
}

private fun TypeFilter.labelRes(): Int = when (this) {
    TypeFilter.ALL -> R.string.filter_all
    TypeFilter.EXPENSE -> R.string.filter_expenses
    TypeFilter.INCOME -> R.string.filter_income
    TypeFilter.TRANSFER -> R.string.filter_transfers
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (DateFormats.relativeDay(date, today)) {
    RelativeDay.TODAY -> stringResource(R.string.date_today)
    RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
    RelativeDay.OTHER -> DateFormats.dayHeader(date)
}
