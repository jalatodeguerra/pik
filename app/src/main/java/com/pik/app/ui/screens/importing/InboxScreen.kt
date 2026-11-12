package com.pik.app.ui.screens.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.importing.ImportDirection
import com.pik.app.domain.importing.ImportSuggestion
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

/** Which question is open: a category for an item (and add it afterwards), or an account. */
private sealed interface Picker {
    val itemId: Long

    data class Category(override val itemId: Long, val addAfter: Boolean, val accountId: Long?) : Picker

    data class Account(override val itemId: Long) : Picker
}

@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
    fileViewModel: ImportFileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val openFilePicker = rememberImportFileLauncher(fileViewModel)
    ImportFileDialogs(fileViewModel, snackbarHostState)

    val addedAll = state.addedAll
    val addedAllText = addedAll?.let { stringResource(R.string.inbox_added_all, it) }
    LaunchedEffect(addedAll) {
        if (addedAllText != null) {
            snackbarHostState.showSnackbar(addedAllText)
            viewModel.consumeAddedAll()
        }
    }

    var picker by remember { mutableStateOf<Picker?>(null) }
    var duplicatesOpen by remember { mutableStateOf(false) }

    fun onAdd(ui: InboxItemUi) {
        val item = ui.item
        when {
            item.accountId == null -> picker = Picker.Account(item.id)
            !ui.isTransfer && item.suggestedCategoryId == null ->
                picker = Picker.Category(item.id, addAfter = true, accountId = null)
            else -> viewModel.add(item.id)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PikBackTopBar(title = stringResource(R.string.inbox_title), onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.addAllCount >= 2) {
                    item {
                        PikPrimaryButton(
                            text = stringResource(R.string.inbox_add_all, state.addAllCount),
                            onClick = viewModel::addAll,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (!state.isLoading && state.pending.isEmpty()) {
                    item {
                        PikCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.inbox_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PikColors.TextPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.inbox_empty_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PikColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
                items(state.pending, key = { "p${it.item.id}" }) { ui ->
                    InboxCard(
                        ui = ui,
                        onCategoryClick = { picker = Picker.Category(ui.item.id, addAfter = false, accountId = null) },
                        onAdd = { onAdd(ui) },
                        onEdit = { onEdit(ui.item.id) },
                        onDismiss = { viewModel.dismiss(ui.item.id) },
                    )
                }
                if (state.duplicates.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { duplicatesOpen = !duplicatesOpen }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.inbox_already_title, state.duplicates.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = PikColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (duplicatesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = PikColors.TextSecondary,
                            )
                        }
                    }
                    if (duplicatesOpen) {
                        item {
                            Text(
                                text = stringResource(R.string.inbox_already_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = PikColors.TextSecondary,
                            )
                        }
                        items(state.duplicates, key = { "d${it.item.id}" }) { ui ->
                            DuplicateRow(ui = ui, onRestore = { viewModel.restore(ui.item.id) })
                        }
                    }
                }
                item {
                    PikSecondaryButton(
                        text = stringResource(R.string.import_file_button),
                        onClick = openFilePicker,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

    when (val open = picker) {
        is Picker.Category -> {
            val ui = state.pending.firstOrNull { it.item.id == open.itemId }
            if (ui == null) {
                picker = null
            } else {
                val categories = if (ui.item.suggestion == ImportSuggestion.INCOME) {
                    state.incomeCategories
                } else {
                    state.expenseCategories
                }
                OptionPickerDialog(
                    title = stringResource(R.string.inbox_pick_category),
                    options = categories.map { it.id to it.name },
                    onPick = { categoryId ->
                        if (open.addAfter) {
                            viewModel.add(open.itemId, accountId = open.accountId, categoryId = categoryId)
                        } else {
                            viewModel.setCategory(open.itemId, categoryId)
                        }
                        picker = null
                    },
                    onDismiss = { picker = null },
                )
            }
        }
        is Picker.Account -> {
            val ui = state.pending.firstOrNull { it.item.id == open.itemId }
            if (ui == null) {
                picker = null
            } else {
                OptionPickerDialog(
                    title = stringResource(R.string.inbox_pick_account),
                    options = state.accounts.map { it.id to it.name },
                    onPick = { accountId ->
                        if (!ui.isTransfer && ui.item.suggestedCategoryId == null) {
                            picker = Picker.Category(open.itemId, addAfter = true, accountId = accountId)
                        } else {
                            viewModel.add(open.itemId, accountId = accountId)
                            picker = null
                        }
                    },
                    onDismiss = { picker = null },
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun InboxCard(
    ui: InboxItemUi,
    onCategoryClick: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val item = ui.item
    val amountText: String
    val amountColor = when {
        ui.isTransfer -> PikColors.TextSecondary
        item.direction == ImportDirection.OUT -> PikColors.Expense
        else -> PikColors.Income
    }
    amountText = when {
        ui.isTransfer -> Money.format(item.amountCents)
        item.direction == ImportDirection.OUT -> Money.format(-item.amountCents)
        else -> Money.format(item.amountCents, showPlusSign = true)
    }
    val title = item.merchant.ifBlank { stringResource(R.string.inbox_unknown_merchant) }
    val subtitle = listOf(
        DateFormats.shortDate(item.date),
        ui.accountName ?: stringResource(R.string.inbox_no_account),
        importSourceLabel(item.source),
    ).joinToString(" · ")

    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconCircle(importSourceIcon(item.source), PikColors.Lime, null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PikColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = amountText,
                    style = PikNumberStyles.Medium,
                    color = amountColor,
                    maxLines = 1,
                )
            }
            if (ui.isTransfer) {
                val from: String
                val to: String
                if (item.suggestion == ImportSuggestion.TRANSFER_OUT) {
                    from = ui.accountName ?: "?"
                    to = ui.transferAccountName ?: "?"
                } else {
                    from = ui.transferAccountName ?: "?"
                    to = ui.accountName ?: "?"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = PikColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.transaction_transfer) + ": " +
                            stringResource(R.string.transfer_route, from, to),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextPrimary,
                    )
                }
            } else {
                CategoryChip(ui = ui, onClick = onCategoryClick)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PikPrimaryButton(
                    text = stringResource(R.string.inbox_action_add),
                    onClick = onAdd,
                    modifier = Modifier.weight(1f),
                )
                PikSecondaryButton(
                    text = stringResource(R.string.inbox_action_edit),
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
                PikSecondaryButton(
                    text = stringResource(R.string.inbox_action_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The suggested category; tapping it opens the category picker. */
@Composable
private fun CategoryChip(ui: InboxItemUi, onClick: () -> Unit) {
    val category = ui.category
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PikColors.SurfaceRaised,
        contentColor = PikColors.TextPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (category != null) {
                Icon(
                    imageVector = CategoryStyle.icon(category.iconKey),
                    contentDescription = null,
                    tint = CategoryStyle.color(category.colorKey),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = category?.name ?: stringResource(R.string.inbox_choose_category),
                style = MaterialTheme.typography.labelLarge,
                color = if (category != null) PikColors.TextPrimary else PikColors.Amber,
            )
        }
    }
}

@Composable
private fun DuplicateRow(ui: InboxItemUi, onRestore: () -> Unit) {
    val item = ui.item
    val sign = if (item.direction == ImportDirection.OUT) -item.amountCents else item.amountCents
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.merchant.ifBlank { stringResource(R.string.inbox_unknown_merchant) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateFormats.shortDate(item.date) + " · " + Money.format(sign),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = onRestore,
                colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
            ) { Text(stringResource(R.string.inbox_restore)) }
        }
    }
}

/** A dialog with one chip per option (a category or an account). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<Pair<Long, String>>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PikColors.SurfaceRaised,
        titleContentColor = PikColors.TextPrimary,
        textContentColor = PikColors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { (id, label) ->
                        PikFilterChip(label = label, selected = false, onClick = { onPick(id) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = PikColors.TextPrimary),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
