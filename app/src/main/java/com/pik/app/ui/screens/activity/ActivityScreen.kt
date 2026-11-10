package com.pik.app.ui.screens.activity

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
    ActivityContent(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onTransactionClick = onTransactionClick,
    )
}

@Composable
private fun ActivityContent(
    state: ActivityUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.activity_title),
            style = MaterialTheme.typography.headlineLarge,
            color = PikColors.TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
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

        if (!state.isLoading && state.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.activity_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
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
                                    // Adjustments cannot be edited from the Add screen in this milestone.
                                    onClick = if (row.type == TransactionType.ADJUSTMENT) null else {
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
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (DateFormats.relativeDay(date, today)) {
    RelativeDay.TODAY -> stringResource(R.string.date_today)
    RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
    RelativeDay.OTHER -> DateFormats.dayHeader(date)
}
