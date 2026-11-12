package com.pik.app.ui.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

/** On-screen number keypad: 1-9, ",", 0, backspace. Not the system keyboard. */
@Composable
fun AmountKeypad(
    onDigit: (Char) -> Unit,
    onComma: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKeys.forEach { c ->
                    KeypadKey(onClick = { onDigit(c) }, modifier = Modifier.weight(1f)) {
                        Text(text = c.toString(), style = PikNumberStyles.Keypad, color = PikColors.TextPrimary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadKey(onClick = onComma, modifier = Modifier.weight(1f)) {
                Text(text = ",", style = PikNumberStyles.Keypad, color = PikColors.TextPrimary)
            }
            KeypadKey(onClick = { onDigit('0') }, modifier = Modifier.weight(1f)) {
                Text(text = "0", style = PikNumberStyles.Keypad, color = PikColors.TextPrimary)
            }
            KeypadKey(onClick = onBackspace, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.keypad_backspace),
                    tint = PikColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = PikColors.Surface,
        contentColor = PikColors.TextPrimary,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
