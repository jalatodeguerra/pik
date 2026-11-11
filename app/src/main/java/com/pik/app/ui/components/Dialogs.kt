package com.pik.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.domain.BalanceCheck
import com.pik.app.domain.Money
import com.pik.app.ui.theme.PikColors

/** AlertDialog with explicit colors on every part. [content] is scrollable. */
@Composable
fun PikDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmColor: Color = PikColors.Lime,
    dismissLabel: String = stringResource(R.string.action_cancel),
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PikColors.SurfaceRaised,
        titleContentColor = PikColors.TextPrimary,
        textContentColor = PikColors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = confirmColor,
                    disabledContentColor = PikColors.TextSecondary,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = PikColors.TextPrimary),
            ) { Text(dismissLabel) }
        },
    )
}

/** Simple message + confirm/cancel dialog. */
@Composable
fun PikConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    PikDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmColor = if (destructive) PikColors.Coral else PikColors.Lime,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = PikColors.TextSecondary)
    }
}

/**
 * "Type what your bank app shows". Shows Pik's calculated balance, accepts Italian input
 * ("1.234,56", "-50", "12,5") and reports whether an adjustment will be added.
 * [onConfirm] gets the real balance in cents and the note for the adjustment; it is only called
 * when the amounts differ.
 */
@Composable
fun CheckBalanceDialog(
    accountName: String,
    currentCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (realCents: Long, note: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val real: Long? = Money.parse(text)
    val adjustment: Long? = real?.let { BalanceCheck.adjustmentCents(it, currentCents) }
    val note = stringResource(R.string.balance_check_note)

    PikDialog(
        title = stringResource(R.string.check_balance_title, accountName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(
            if (adjustment != null && adjustment != 0L) R.string.check_balance_adjust else R.string.action_ok,
        ),
        confirmEnabled = real != null,
        onConfirm = {
            if (real != null && adjustment != null && adjustment != 0L) onConfirm(real, note) else onDismiss()
        },
    ) {
        Text(
            text = stringResource(R.string.check_balance_pik_says, Money.format(currentCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextPrimary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.check_balance_field)) },
            placeholder = { Text("0,00") },
            suffix = { Text(Money.CURRENCY_SYMBOL) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = pikTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        val (message, color) = when {
            text.isBlank() -> stringResource(R.string.check_balance_hint) to PikColors.TextSecondary
            real == null -> stringResource(R.string.check_balance_invalid) to PikColors.Coral
            adjustment == 0L -> stringResource(R.string.check_balance_matches) to PikColors.Lime
            else -> stringResource(
                R.string.check_balance_difference,
                Money.format(adjustment ?: 0L, showPlusSign = true),
            ) to PikColors.TextPrimary
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
