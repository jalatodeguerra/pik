package com.pik.app.ui.screens.recurring

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.data.repo.RecurringInput
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.reminders.ReminderPermissions
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikDatePickerDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikMoneyField
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSwitchRow
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

private const val MAX_NAME_LENGTH = 40
private const val MAX_NOTE_LENGTH = 120
private const val MAX_INTERVAL = 120
private const val MAX_TIMES = 1200

/** How a rule ends. */
private enum class EndMode { NEVER, ON_DATE, AFTER_TIMES }

/** The kinds a user can pick; DEBT_PAYMENT rules are created from a debt. */
private val pickableKinds = listOf(RecurringKind.BILL, RecurringKind.SUBSCRIPTION, RecurringKind.INCOME)

/** Add or edit a bill, subscription or recurring income (and, limited, the payment rule of a debt). */
@Composable
fun RecurringEditScreen(
    onBack: () -> Unit,
    viewModel: RecurringEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isEditing = state.existing != null || state.isLoading
    Column(modifier = Modifier.fillMaxSize()) {
        PikBackTopBar(
            title = stringResource(if (isEditing) R.string.recurring_edit_title else R.string.recurring_new_title),
            onBack = onBack,
        )
        when {
            state.isLoading -> Box(Modifier.weight(1f))
            state.notFound -> Box(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.recurring_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            else -> RecurringEditForm(
                existing = state.existing,
                accounts = state.accounts,
                defaultAccountId = state.defaultAccountId,
                categories = state.categories,
                onSave = { input -> viewModel.save(input, onDone = onBack) },
                onDelete = { viewModel.delete(onDone = onBack) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditForm(
    existing: RecurringRuleEntity?,
    accounts: List<AccountEntity>,
    defaultAccountId: Long?,
    categories: List<CategoryEntity>,
    onSave: (RecurringInput) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownedByDebt = existing?.debtId != null
    val today = remember { LocalDate.now() }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: RecurringKind.BILL) }
    var amountText by remember { mutableStateOf(existing?.let { Money.formatPlain(it.amountCents) }.orEmpty()) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: defaultAccountId) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: today) }
    var everyText by remember { mutableStateOf((existing?.intervalCount ?: 1).toString()) }
    var unit by remember { mutableStateOf(existing?.intervalUnit ?: RecurrenceUnit.MONTH) }
    var dayOfMonth by remember { mutableStateOf(existing?.dayOfMonth ?: today.dayOfMonth) }
    var dayChosen by remember { mutableStateOf(existing?.dayOfMonth != null) }
    var endMode by remember {
        mutableStateOf(
            when {
                existing?.endDate != null -> EndMode.ON_DATE
                existing?.maxOccurrences != null -> EndMode.AFTER_TIMES
                else -> EndMode.NEVER
            },
        )
    }
    var endDate by remember { mutableStateOf(existing?.endDate ?: today.plusYears(1)) }
    var timesText by remember { mutableStateOf(existing?.maxOccurrences?.toString() ?: "12") }
    var remind by remember { mutableStateOf(existing?.remindDayBefore ?: false) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    // The notification permission is requested only when the user first turns a reminder on.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    val context = LocalContext.current

    val amount = Money.parse(amountText)?.takeIf { it > 0 }
    val every = everyText.toIntOrNull()?.takeIf { it in 1..MAX_INTERVAL }
    val times = timesText.toIntOrNull()?.takeIf { it in 1..MAX_TIMES }
    val endOk = when (endMode) {
        EndMode.NEVER -> true
        EndMode.ON_DATE -> endDate >= startDate
        EndMode.AFTER_TIMES -> times != null
    }
    val valid = if (ownedByDebt) {
        accountId != null
    } else {
        name.isNotBlank() && amount != null && accountId != null && every != null && endOk
    }
    val categoryKind = if (kind == RecurringKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val shownCategories = categories.filter { it.kind == categoryKind }

    Column(modifier = modifier.imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ownedByDebt && existing != null) {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = existing.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = PikColors.TextPrimary,
                        )
                        Text(
                            text = frequencyText(existing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PikColors.TextSecondary,
                        )
                        Text(
                            text = stringResource(R.string.recurring_debt_owned, Money.format(existing.amountCents)),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.Blue,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.recurring_name)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = pikTextFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (option in pickableKinds) {
                                PikFilterChip(
                                    label = stringResource(option.labelRes()),
                                    selected = option == kind,
                                    onClick = {
                                        // expense and income categories are different lists
                                        if ((option == RecurringKind.INCOME) != (kind == RecurringKind.INCOME)) {
                                            categoryId = null
                                        }
                                        kind = option
                                    },
                                )
                            }
                        }
                        PikMoneyField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = stringResource(R.string.recurring_amount),
                            isError = amountText.isNotBlank() && amount == null,
                        )
                    }
                }
            }

            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LabelText(stringResource(R.string.add_account))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { account ->
                            PikFilterChip(
                                label = account.name,
                                selected = account.id == accountId,
                                onClick = { accountId = account.id },
                            )
                        }
                    }
                    if (!ownedByDebt && shownCategories.isNotEmpty()) {
                        LabelText(stringResource(R.string.add_category))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            shownCategories.forEach { category ->
                                PikFilterChip(
                                    label = category.name,
                                    selected = category.id == categoryId,
                                    // tapping the chosen category again clears it
                                    onClick = { categoryId = if (category.id == categoryId) null else category.id },
                                )
                            }
                        }
                    }
                }
            }

            if (!ownedByDebt) {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DateRow(
                            label = stringResource(R.string.recurring_start_date),
                            date = startDate,
                            onClick = { pickingStart = true },
                        )
                        LabelText(stringResource(R.string.recurring_repeat))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.recurring_every),
                                style = MaterialTheme.typography.bodyLarge,
                                color = PikColors.TextPrimary,
                            )
                            OutlinedTextField(
                                value = everyText,
                                onValueChange = { everyText = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.weight(1f),
                                isError = every == null,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = pikTextFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (option in RecurrenceUnit.entries) {
                                PikFilterChip(
                                    label = stringResource(option.labelRes()),
                                    selected = option == unit,
                                    onClick = { unit = option },
                                )
                            }
                        }
                        if (unit != RecurrenceUnit.WEEK) {
                            PikCard(
                                modifier = Modifier.fillMaxWidth(),
                                color = PikColors.Surface,
                                onClick = { pickingDay = true },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.recurring_day_of_month),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PikColors.TextSecondary,
                                    )
                                    Text(
                                        text = dayOfMonth.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PikColors.TextPrimary,
                                    )
                                }
                            }
                            if (dayOfMonth > 28) {
                                Text(
                                    text = stringResource(R.string.recurring_day_clamp_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PikColors.TextSecondary,
                                )
                            }
                        }
                    }
                }

                PikCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LabelText(stringResource(R.string.recurring_ends))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PikFilterChip(
                                label = stringResource(R.string.recurring_ends_never),
                                selected = endMode == EndMode.NEVER,
                                onClick = { endMode = EndMode.NEVER },
                            )
                            PikFilterChip(
                                label = stringResource(R.string.recurring_ends_on_date),
                                selected = endMode == EndMode.ON_DATE,
                                onClick = { endMode = EndMode.ON_DATE },
                            )
                            PikFilterChip(
                                label = stringResource(R.string.recurring_ends_after),
                                selected = endMode == EndMode.AFTER_TIMES,
                                onClick = { endMode = EndMode.AFTER_TIMES },
                            )
                        }
                        when (endMode) {
                            EndMode.NEVER -> Unit
                            EndMode.ON_DATE -> {
                                DateRow(
                                    label = stringResource(R.string.recurring_end_date),
                                    date = endDate,
                                    onClick = { pickingEnd = true },
                                )
                                if (endDate < startDate) {
                                    Text(
                                        text = stringResource(R.string.recurring_end_before_start),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PikColors.Coral,
                                    )
                                }
                            }
                            EndMode.AFTER_TIMES -> OutlinedTextField(
                                value = timesText,
                                onValueChange = { timesText = it.filter(Char::isDigit).take(4) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.recurring_times)) },
                                supportingText = { Text(stringResource(R.string.recurring_times_hint)) },
                                isError = times == null,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = pikTextFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                }
            }

            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PikSwitchRow(
                        label = stringResource(R.string.recurring_remind),
                        supporting = stringResource(R.string.recurring_remind_hint),
                        checked = remind,
                        accent = PikColors.Amber,
                        onCheckedChange = { on ->
                            remind = on
                            if (on) {
                                permissionDenied = false
                                if (ReminderPermissions.needsRuntimePermission && !ReminderPermissions.canNotify(context)) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                    )
                    if (remind && permissionDenied) {
                        Text(
                            text = stringResource(R.string.recurring_remind_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.Amber,
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_note)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = pikTextFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            if (existing != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Coral),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_delete)) }
                Text(
                    text = stringResource(R.string.recurring_delete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
            Box(Modifier.padding(bottom = 8.dp))
        }
        PikPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                val account = accountId
                if (valid && account != null) {
                    onSave(
                        RecurringInput(
                            name = if (ownedByDebt && existing != null) existing.name else name.trim(),
                            kind = if (ownedByDebt && existing != null) existing.kind else kind,
                            amountCents = if (ownedByDebt && existing != null) existing.amountCents else amount ?: 0L,
                            accountId = account,
                            categoryId = if (ownedByDebt) null else categoryId?.takeIf { id -> shownCategories.any { it.id == id } },
                            note = note.trim(),
                            startDate = startDate,
                            intervalCount = every ?: 1,
                            unit = unit,
                            dayOfMonth = if (unit == RecurrenceUnit.WEEK) null else dayOfMonth,
                            endDate = if (endMode == EndMode.ON_DATE) endDate else null,
                            maxOccurrences = if (endMode == EndMode.AFTER_TIMES) times else null,
                            remindDayBefore = remind,
                        ),
                    )
                }
            },
            enabled = valid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (pickingStart) {
        PikDatePickerDialog(
            initial = startDate,
            onDismiss = { pickingStart = false },
            onConfirm = {
                startDate = it
                // the day of the month follows the start date until the user picks one
                if (!dayChosen) dayOfMonth = it.dayOfMonth
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        PikDatePickerDialog(
            initial = endDate,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                endDate = it
                pickingEnd = false
            },
        )
    }
    if (pickingDay) {
        DayOfMonthDialog(
            current = dayOfMonth,
            onDismiss = { pickingDay = false },
            onSave = {
                dayOfMonth = it
                dayChosen = true
                pickingDay = false
            },
        )
    }
    if (confirmDelete) {
        PikConfirmDialog(
            title = stringResource(R.string.recurring_delete_title),
            message = stringResource(R.string.recurring_delete_message),
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

@Composable
private fun LabelText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = PikColors.TextSecondary)
}

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    PikCard(modifier = Modifier.fillMaxWidth(), color = PikColors.Surface, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.CalendarToday, contentDescription = null, tint = PikColors.Amber)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = DateFormats.shortDate(date),
                style = MaterialTheme.typography.titleMedium,
                color = PikColors.TextPrimary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfMonthDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    PikDialog(
        title = stringResource(R.string.recurring_day_of_month),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = { onSave(selected) },
    ) {
        Text(
            text = stringResource(R.string.recurring_day_of_month_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (day in 1..31) {
                PikFilterChip(label = day.toString(), selected = day == selected, onClick = { selected = day })
            }
        }
    }
}
