package com.pik.app.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.CategoryEntity
import com.pik.app.domain.CategoryFilter
import com.pik.app.domain.DateFormats
import com.pik.app.domain.InsightsSummary
import com.pik.app.domain.Money
import com.pik.app.domain.SliceKind
import com.pik.app.domain.SpendingSlice
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.screens.activity.ActivityFocusRequest
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import java.time.YearMonth

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onShowInActivity: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        PikBackTopBar(title = stringResource(R.string.insights_title), onBack = onBack)
        val summary = state.summary
        when {
            state.isLoading || summary == null -> Box(Modifier.fillMaxSize())
            !summary.hasData -> EmptyInsights(onAddTransaction)
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BarsCard(summary)
                WhereItWentCard(
                    summary = summary,
                    categories = state.categories,
                    canGoPrevious = state.canGoPrevious,
                    canGoNext = state.canGoNext,
                    onPrevious = viewModel::previousPeriod,
                    onNext = viewModel::nextPeriod,
                    onSliceClick = { slice ->
                        val filter = slice.toFilter()
                        if (filter != null) {
                            viewModel.showInActivity(ActivityFocusRequest(filter, summary.selected))
                            onShowInActivity()
                        }
                    },
                )
                StatTiles(summary, state.categories)
                Box(Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

private fun SpendingSlice.toFilter(): CategoryFilter? = when (kind) {
    SliceKind.CATEGORY -> categoryId?.let { CategoryFilter.Category(it) }
    SliceKind.UNCATEGORISED -> CategoryFilter.Uncategorised
    SliceKind.DEBT -> CategoryFilter.DebtPayments
    SliceKind.OTHER -> null
}

@Composable
private fun EmptyInsights(onAdd: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) {
        PikCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconCircle(Icons.Filled.BarChart, PikColors.Lime, null, size = 64)
                Text(
                    text = stringResource(R.string.insights_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = PikColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.insights_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                PikPrimaryButton(
                    text = stringResource(R.string.home_empty_button),
                    onClick = onAdd,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = PikColors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarsCard(summary: InsightsSummary) {
    val bars = summary.bars
    var picked by rememberSaveable { mutableStateOf(-1) }
    val selected = if (picked in bars.indices) picked else bars.lastIndex
    val labels = bars.map { DateFormats.monthShort(YearMonth.from(it.period.start)) }

    val context = LocalContext.current
    // joinToString is not inline, so the strings are read with the context instead of stringResource.
    val cdItems = bars.joinToString(" ") { bar ->
        context.getString(
            R.string.insights_bars_cd_item,
            bar.period.label(),
            Money.format(bar.incomeCents),
            Money.format(bar.spendingCents),
            Money.format(bar.debtCents),
        )
    }
    val chartDescription = stringResource(R.string.insights_bars_cd, bars.size, cdItems)

    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.insights_bars_title))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegendDot(PikColors.Lime, stringResource(R.string.insights_legend_income))
                LegendDot(PikColors.Coral, stringResource(R.string.insights_legend_spending))
                LegendDot(PikColors.Blue, stringResource(R.string.insights_legend_debt))
            }
            val bar = bars[selected]
            Text(
                text = if (bar.debtCents > 0L) {
                    stringResource(
                        R.string.insights_bar_detail_debt,
                        bar.period.label(),
                        Money.format(bar.incomeCents),
                        Money.format(bar.spendingCents),
                        Money.format(bar.debtCents),
                    )
                } else {
                    stringResource(
                        R.string.insights_bar_detail,
                        bar.period.label(),
                        Money.format(bar.incomeCents),
                        Money.format(bar.spendingCents),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextPrimary,
            )
            IncomeSpendingBars(
                bars = bars,
                labels = labels,
                selectedIndex = selected,
                onSelect = { picked = it },
                contentDescription = chartDescription,
            )
            Text(
                text = stringResource(R.string.insights_bars_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun sliceName(slice: SpendingSlice, categories: Map<Long, CategoryEntity>): String = when (slice.kind) {
    SliceKind.CATEGORY -> categories[slice.categoryId]?.name ?: stringResource(R.string.transaction_uncategorized)
    SliceKind.UNCATEGORISED -> stringResource(R.string.transaction_uncategorized)
    SliceKind.OTHER -> stringResource(R.string.insights_other)
    SliceKind.DEBT -> stringResource(R.string.insights_debt_payments)
}

private fun sliceColor(slice: SpendingSlice, categories: Map<Long, CategoryEntity>): Color = when (slice.kind) {
    SliceKind.CATEGORY -> CategoryStyle.color(categories[slice.categoryId]?.colorKey)
    SliceKind.UNCATEGORISED -> PikColors.Amber
    SliceKind.OTHER -> PikColors.TextSecondary
    SliceKind.DEBT -> PikColors.Blue
}

@Composable
private fun WhereItWentCard(
    summary: InsightsSummary,
    categories: Map<Long, CategoryEntity>,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSliceClick: (SpendingSlice) -> Unit,
) {
    val slices = summary.slices
    val periodLabel = summary.selected.label()
    val names = slices.map { sliceName(it, categories) }
    val context = LocalContext.current
    val description = if (slices.isEmpty()) {
        stringResource(R.string.insights_donut_cd_empty, periodLabel)
    } else {
        stringResource(
            R.string.insights_donut_cd,
            periodLabel,
            slices.indices.joinToString(" ") { i ->
                context.getString(
                    R.string.insights_donut_cd_item,
                    names[i],
                    Money.format(slices[i].cents),
                    slices[i].percent,
                )
            },
        )
    }

    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                text = stringResource(R.string.insights_where_title),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_period),
                        tint = if (canGoPrevious) PikColors.TextPrimary else PikColors.Outline,
                    )
                }
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = PikColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_period),
                        tint = if (canGoNext) PikColors.TextPrimary else PikColors.Outline,
                    )
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = slices.map { DonutSlice(it.cents, sliceColor(it, categories)) },
                    onSliceClick = { index -> onSliceClick(slices[index]) },
                    contentDescription = description,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.insights_spent),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.TextSecondary,
                        )
                        Text(
                            text = Money.format(summary.spentCents),
                            style = PikNumberStyles.Medium,
                            color = PikColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (slices.isEmpty()) {
                Text(
                    text = stringResource(R.string.insights_no_spending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            } else {
                Column {
                    slices.forEachIndexed { i, slice ->
                        LegendRow(
                            name = names[i],
                            color = sliceColor(slice, categories),
                            amount = Money.format(slice.cents),
                            percent = slice.percent,
                            enabled = slice.kind != SliceKind.OTHER,
                            onClick = { onSliceClick(slice) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.insights_where_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(
    name: String,
    color: Color,
    amount: String,
    percent: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = PikColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = PikNumberStyles.Medium,
            color = PikColors.TextPrimary,
            maxLines = 1,
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp),
        )
    }
}

@Composable
private fun StatTiles(summary: InsightsSummary, categories: Map<Long, CategoryEntity>) {
    val change = summary.changePercent
    val average = summary.previousAverageCents
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = stringResource(R.string.insights_stat_spent),
                value = Money.format(summary.spentCents),
                valueColor = PikColors.Coral,
                detail = when {
                    change != null && average != null -> stringResource(
                        R.string.insights_stat_vs_average,
                        signedPercent(change),
                        Money.format(average),
                    )
                    else -> stringResource(R.string.insights_stat_no_average)
                },
                detailColor = when {
                    change == null -> PikColors.TextSecondary
                    change > 0 -> PikColors.Coral
                    change < 0 -> PikColors.Lime
                    else -> PikColors.TextSecondary
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            StatTile(
                label = stringResource(R.string.insights_stat_kept),
                value = Money.format(summary.keptCents, showPlusSign = true),
                valueColor = if (summary.keptCents >= 0L) PikColors.Lime else PikColors.Coral,
                detail = stringResource(R.string.insights_stat_kept_detail),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = stringResource(R.string.insights_stat_debt),
                value = Money.format(summary.debtPaidCents),
                valueColor = PikColors.Blue,
                detail = stringResource(R.string.insights_stat_debt_detail),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            val biggest = summary.biggest
            StatTile(
                label = stringResource(R.string.insights_stat_biggest),
                value = biggest?.let { Money.format(it.amountCents) } ?: "\u2013",
                valueColor = PikColors.Coral,
                detail = biggest?.let {
                    val category = categories[it.categoryId]?.name ?: stringResource(R.string.transaction_uncategorized)
                    "$category \u00B7 ${DateFormats.shortDate(it.date)}"
                } ?: stringResource(R.string.insights_stat_biggest_none),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

private fun signedPercent(percent: Int): String = when {
    percent > 0 -> "+$percent%"
    else -> "$percent%"
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueColor: Color,
    detail: String,
    modifier: Modifier = Modifier,
    detailColor: Color = PikColors.TextSecondary,
) {
    PikCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 2,
            )
            Text(
                text = value,
                style = PikNumberStyles.Medium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
