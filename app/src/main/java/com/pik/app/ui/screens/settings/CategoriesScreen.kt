package com.pik.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.CategoryWithUsage
import com.pik.app.domain.CategoryKind
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors

private const val MAX_NAME_LENGTH = 30

private val kinds = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME)

/** Which category the editor works on: null item means "new category". */
private data class CategoryEditorTarget(val item: CategoryWithUsage?)

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            when (it) {
                CategoriesMessage.CANNOT_ARCHIVE_LAST -> R.string.category_cannot_archive_last
                CategoriesMessage.IN_USE -> R.string.category_in_use
            },
        )
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    var editor by remember { mutableStateOf<CategoryEditorTarget?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PikBackTopBar(title = stringResource(R.string.categories_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PikSegmented(
                    options = listOf(
                        stringResource(R.string.type_expense),
                        stringResource(R.string.type_income),
                    ),
                    selectedIndex = kinds.indexOf(state.kind).coerceAtLeast(0),
                    onSelect = { viewModel.selectKind(kinds[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    state.items.forEach { item ->
                        CategoryRow(item = item, onClick = { editor = CategoryEditorTarget(item) })
                        HorizontalDivider(color = PikColors.Outline)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editor = CategoryEditorTarget(null) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = PikColors.Lime)
                        Text(
                            text = stringResource(R.string.category_add),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PikColors.Lime,
                        )
                    }
                }
                Box(modifier = Modifier.padding(bottom = 32.dp))
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
        CategoryEditorDialog(
            item = target.item,
            activeCount = state.activeCount,
            onDismiss = { editor = null },
            onSave = { name, icon, color ->
                val existing = target.item
                if (existing == null) {
                    viewModel.add(name, icon, color)
                } else {
                    viewModel.update(existing.category.id, name, icon, color)
                }
                editor = null
            },
            onSetArchived = { archived ->
                target.item?.let { viewModel.setArchived(it.category.id, archived) }
                editor = null
            },
            onDelete = {
                target.item?.let { viewModel.delete(it.category.id) }
                editor = null
            },
        )
    }
}

@Composable
private fun CategoryRow(item: CategoryWithUsage, onClick: () -> Unit) {
    val archived = item.category.archived
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(
            icon = CategoryStyle.icon(item.category.iconKey),
            tint = if (archived) PikColors.TextSecondary else CategoryStyle.color(item.category.colorKey),
            contentDescription = null,
        )
        Text(
            text = item.category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (archived) PikColors.TextSecondary else PikColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (archived) {
            Text(
                text = stringResource(R.string.account_archived),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    item: CategoryWithUsage?,
    activeCount: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, colorKey: String) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item?.category?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(item?.category?.iconKey ?: CategoryStyle.iconKeys.first()) }
    var colorKey by remember { mutableStateOf(item?.category?.colorKey ?: CategoryStyle.colorKeys.first()) }
    var confirmDelete by remember { mutableStateOf(false) }

    PikDialog(
        title = stringResource(if (item == null) R.string.category_new_title else R.string.category_edit_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name.trim(), iconKey, colorKey) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.category_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Text(
            text = stringResource(R.string.category_icon),
            style = MaterialTheme.typography.labelLarge,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryStyle.iconKeys.forEach { key ->
                val selected = key == iconKey
                // 48dp touch target around the 44dp circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { iconKey = key },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) PikColors.Lime else PikColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CategoryStyle.icon(key),
                            contentDescription = null,
                            tint = if (selected) PikColors.OnLime else PikColors.TextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.category_color),
            style = MaterialTheme.typography.labelLarge,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryStyle.colorKeys.forEach { key ->
                val selected = key == colorKey
                // 48dp touch target around the 36dp colour circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { colorKey = key },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CategoryStyle.color(key))
                            .then(if (selected) Modifier.border(3.dp, PikColors.TextPrimary, CircleShape) else Modifier),
                    )
                }
            }
        }
        if (item != null) {
            val archived = item.category.archived
            val isLastActive = !archived && activeCount <= 1
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
                    if (isLastActive) R.string.category_cannot_archive_last else R.string.category_archive_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            if (item.usageCount == 0) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Coral),
                ) { Text(stringResource(R.string.action_delete)) }
            } else {
                Text(
                    text = stringResource(R.string.category_delete_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
        }
    }

    if (confirmDelete) {
        PikConfirmDialog(
            title = stringResource(R.string.category_delete_title),
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
