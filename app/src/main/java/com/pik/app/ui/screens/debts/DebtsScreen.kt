package com.pik.app.ui.screens.debts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.repo.DebtItem
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.PayoffResult
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikProgressBar
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.components.durationText
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import kotlin.math.roundToInt

private val payoffOrders = listOf(PayoffOrder.HIGHEST_INTEREST, PayoffOrder.SMALLEST_BALANCE)

@Composable
fun DebtsScreen(
    onAddDebt: () -> Unit,
    onDebtClick: (Long) -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DebtsContent(
        state = state,
        onAddDebt = onAddDebt,
        onDebtClick = onDebtClick,
        onOrderChange = viewModel::setOrder,
        onWhatIfChange = viewModel::setWhatIfExtra,
        onUseWhatIf = viewModel::useWhatIf,
    )
}

@Composable
private fun DebtsContent(
    state: DebtsUiState,
    onAddDebt: () -> Unit,
    onDebtClick: (Long) -> Unit,
    onOrderChange: (PayoffOrder) -> Unit,
    onWhatIfChange: (Long) -> Unit,
    onUseWhatIf: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.debts_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PikColors.TextPrimary,
                )
                if (state.hasDebts) {
                    TextButton(
                        onClick = onAddDebt,
                        colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = PikColors.Lime,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.debts_add),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        if (state.isLoading) return@LazyColumn

        if (!state.hasDebts) {
            item { EmptyDebtsCard(onAddDebt) }
            return@LazyColumn
        }

        item { SummaryCard(state) }

        if (state.hasOpenDebts) {
            item { OrderCard(state, onOrderChange) }
            item { WhatIfCard(state, onWhatIfChange, onUseWhatIf) }
        }

        if (state.rows.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.debts_your_debts)) }
            item {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = PikColors.Outline)
                        DebtRow(row, onClick = { onDebtClick(row.item.debt.id) })
                    }
                }
            }
        }

        if (state.archived.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.debts_archived)) }
            item {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    state.archived.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = PikColors.Outline)
                        ArchivedRow(item, onClick = { onDebtClick(item.debt.id) })
                    }
                }
            }
        }

        item {
            PikPrimaryButton(
                text = stringResource(R.string.debts_add),
                onClick = onAddDebt,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyDebtsCard(onAddDebt: () -> Unit) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconCircle(Icons.Filled.AccountBalanceWallet, PikColors.Blue, null, size = 64)
            Text(
                text = stringResource(R.string.debts_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = PikColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.debts_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            PikPrimaryButton(
                text = stringResource(R.string.debts_empty_button),
                onClick = onAddDebt,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Paid-off ring, total still owed, debt-free date and interest to pay (or the "never" warning). */
@Composable
private fun SummaryCard(state: DebtsUiState) {
    val plan = state.plan
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ProgressRing(fraction = state.overall.fraction)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debts_total_remaining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                    )
                    Text(
                        text = Money.format(state.totalRemainingCents),
                        style = PikNumberStyles.Hero,
                        color = PikColors.Blue,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(
                            R.string.debts_paid_off_percent,
                            (state.overall.fraction * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                    )
                }
            }
            when {
                plan == null || plan.isDebtFreeNow -> Text(
                    text = stringResource(R.string.debts_debt_free_now),
                    style = MaterialTheme.typography.titleMedium,
                    color = PikColors.Lime,
                )
                plan.neverPaysOff -> Text(
                    text = stringResource(R.string.debts_never_pays_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.Coral,
                )
                else -> {
                    Text(
                        text = stringResource(
                            R.string.debts_debt_free_by,
                            plan.debtFreeMonth?.let { DateFormats.monthYearInline(it) }.orEmpty(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = PikColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.debts_interest_to_pay, Money.format(plan.totalInterestCents)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                    )
                }
            }
            if (plan != null && !plan.isDebtFreeNow) {
                Text(
                    text = stringResource(R.string.debts_plan_assumption, Money.format(state.savedExtraCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, ringSize: Dp = 104.dp, strokeWidth: Dp = 12.dp) {
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = PikColors.Outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (fraction > 0f) {
                drawArc(
                    color = PikColors.Blue,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = PikNumberStyles.Large,
            color = PikColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun OrderCard(state: DebtsUiState, onOrderChange: (PayoffOrder) -> Unit) {
    val comparison = state.comparison
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.debts_order_title))
            PikSegmented(
                options = listOf(
                    stringResource(R.string.debts_order_highest_interest),
                    stringResource(R.string.debts_order_smallest_balance),
                ),
                selectedIndex = payoffOrders.indexOf(state.order).coerceAtLeast(0),
                onSelect = { onOrderChange(payoffOrders[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (comparison != null) {
                payoffOrders.forEach { order ->
                    val label = stringResource(
                        if (order == PayoffOrder.HIGHEST_INTEREST) {
                            R.string.debts_order_highest_interest
                        } else {
                            R.string.debts_order_smallest_balance
                        },
                    )
                    OrderResultRow(
                        label = label,
                        result = comparison.of(order),
                        selected = order == state.order,
                    )
                }
            }
            Text(
                text = stringResource(R.string.debts_order_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun OrderResultRow(label: String, result: PayoffResult, selected: Boolean) {
    val color = if (selected) PikColors.TextPrimary else PikColors.TextSecondary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (result.neverPaysOff) {
                stringResource(R.string.debts_order_never)
            } else {
                stringResource(
                    R.string.debts_order_result,
                    durationText(result.months),
                    Money.format(result.totalInterestCents),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.neverPaysOff) PikColors.Coral else color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun WhatIfCard(state: DebtsUiState, onChange: (Long) -> Unit, onUse: () -> Unit) {
    val whatIf = state.whatIf
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.debts_whatif_title))
            Text(
                text = stringResource(R.string.debts_whatif_extra, Money.format(state.whatIfExtraCents)),
                style = PikNumberStyles.Medium,
                color = PikColors.Blue,
            )
            val maxEuros = state.sliderMaxCents / 100f
            Slider(
                value = (state.whatIfExtraCents / 100f).coerceIn(0f, maxEuros),
                onValueChange = { euros ->
                    onChange((euros / 5f).roundToInt() * DebtsUiState.SLIDER_STEP_CENTS)
                },
                valueRange = 0f..maxEuros,
                steps = (state.sliderMaxCents / DebtsUiState.SLIDER_STEP_CENTS).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = PikColors.Lime,
                    activeTrackColor = PikColors.Lime,
                    inactiveTrackColor = PikColors.Outline,
                    activeTickColor = PikColors.OnLime,
                    inactiveTickColor = PikColors.TextSecondary,
                ),
            )
            Text(
                text = stringResource(R.string.debts_whatif_saved, Money.format(state.savedExtraCents)),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            if (whatIf != null) {
                val result = whatIf.result
                when {
                    result.isDebtFreeNow -> Unit
                    result.neverPaysOff -> Text(
                        text = stringResource(R.string.debts_whatif_never),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.Coral,
                    )
                    else -> {
                        Text(
                            text = stringResource(
                                R.string.debts_debt_free_by,
                                result.debtFreeMonth?.let { DateFormats.monthYearInline(it) }.orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = PikColors.TextPrimary,
                        )
                        val sooner = whatIf.monthsSooner
                        val saved = whatIf.interestSavedCents
                        when {
                            whatIf.makesItPossible -> Text(
                                text = stringResource(R.string.debts_whatif_possible),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PikColors.Lime,
                            )
                            sooner != null && saved != null && (sooner > 0 || saved > 0) -> Text(
                                text = stringResource(
                                    R.string.debts_whatif_sooner,
                                    durationText(sooner),
                                    Money.format(saved),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PikColors.Lime,
                            )
                            else -> Text(
                                text = stringResource(R.string.debts_whatif_no_extra),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PikColors.TextSecondary,
                            )
                        }
                    }
                }
            }
            PikPrimaryButton(
                text = stringResource(R.string.debts_whatif_use),
                onClick = onUse,
                enabled = state.whatIfChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DebtRow(row: DebtRowUi, onClick: () -> Unit) {
    val item = row.item
    val debt = item.debt
    val paidOff = item.progress.paidOff || item.remainingCents == 0L
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrderBadge(row.orderNumber)
            Icon(
                imageVector = debt.type.icon(),
                contentDescription = stringResource(debt.type.labelRes()),
                tint = PikColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = debt.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (debt.type.hasInstalments) R.string.debts_row_details_rata else R.string.debts_row_details,
                        ratesText(debt),
                        Money.format(debt.minPaymentCents),
                        debt.dueDay,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Money.format(item.remainingCents),
                    style = PikNumberStyles.Medium,
                    color = PikColors.Blue,
                    maxLines = 1,
                )
                if (paidOff) {
                    Text(
                        text = stringResource(R.string.debts_paid_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.Lime,
                    )
                }
            }
        }
        PikProgressBar(
            fraction = item.progress.fraction,
            color = PikColors.Blue,
            modifier = Modifier.padding(top = 10.dp),
        )
        val installments = item.installments()
        if (installments != null && debt.type.hasInstalments) {
            Text(
                text = installmentsText(installments),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            PikProgressBar(
                fraction = installments.fraction,
                color = PikColors.Lime,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The place of a debt in the payoff order; a check mark once it is paid off. */
@Composable
private fun OrderBadge(orderNumber: Int?) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(PikColors.SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (orderNumber != null) {
            Text(
                text = orderNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PikColors.TextPrimary,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.debts_paid_off),
                tint = PikColors.Lime,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ArchivedRow(item: DebtItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(Icons.Filled.AccountBalanceWallet, PikColors.TextSecondary, null)
        Text(
            text = item.debt.name,
            style = MaterialTheme.typography.bodyLarge,
            color = PikColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(item.remainingCents),
            style = PikNumberStyles.Medium,
            color = PikColors.TextSecondary,
            maxLines = 1,
        )
    }
}
