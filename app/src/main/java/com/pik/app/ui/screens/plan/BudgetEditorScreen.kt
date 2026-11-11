package com.pik.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.Money
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors

/** Blank means "no budget" (0). Returns null when the text is not a valid non-negative amount. */
private fun parseLimit(text: String): Long? {
    if (text.isBlank()) return 0L
    val cents = Money.parse(text) ?: return null
    return if (cents < 0) null else cents
}

@Composable
fun BudgetEditorScreen(
    onBack: () -> Unit,
    viewModel: BudgetEditorViewModel = hiltViewModel(),
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        PikBackTopBar(title = stringResource(R.string.budgets_title), onBack = onBack)
        val ui = loaded
        if (ui == null) {
            Box(Modifier.weight(1f))
        } else {
            BudgetEditorForm(
                ui = ui,
                onSave = { overall, categories -> viewModel.save(overall, categories, onDone = onBack) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BudgetEditorForm(
    ui: BudgetEditorUiState,
    onSave: (overallCents: Long, categoryCents: Map<Long, Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overallText by remember { mutableStateOf(ui.overallInitialText) }
    val categoryTexts = remember {
        mutableStateMapOf<Long, String>().also { map -> ui.categories.forEach { map[it.id] = it.initialText } }
    }

    val overall = parseLimit(overallText)
    val parsed = ui.categories.associate { it.id to parseLimit(categoryTexts[it.id].orEmpty()) }
    val allValid = overall != null && parsed.values.all { it != null }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.budgets_applies_from, ui.period.label()),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
            )
            Text(
                text = stringResource(R.string.budgets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )

            SectionTitle(stringResource(R.string.budgets_overall))
            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    LimitField(
                        value = overallText,
                        onValueChange = { overallText = it },
                        label = stringResource(R.string.budgets_overall_label),
                        isError = overall == null,
                    )
                }
            }

            SectionTitle(stringResource(R.string.budgets_categories))
            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconCircle(
                                icon = CategoryStyle.icon(category.iconKey),
                                tint = CategoryStyle.color(category.colorKey),
                                contentDescription = null,
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PikColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            LimitField(
                                value = categoryTexts[category.id].orEmpty(),
                                onValueChange = { categoryTexts[category.id] = it },
                                label = null,
                                isError = parsed[category.id] == null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (!allValid) {
                Text(
                    text = stringResource(R.string.budgets_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.Coral,
                )
            }
            Box(Modifier.padding(bottom = 8.dp))
        }
        PikPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                onSave(
                    overall ?: 0L,
                    parsed.mapValues { it.value ?: 0L },
                )
            },
            enabled = allValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LimitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(15)) },
        modifier = modifier.fillMaxWidth(),
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = { Text("0,00") },
        suffix = { Text(Money.CURRENCY_SYMBOL) },
        isError = isError,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = pikTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )
}
