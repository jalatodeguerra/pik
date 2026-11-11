package com.pik.app.ui.screens.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.theme.PikColors

@Composable
private fun ComingSoon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconCircle(icon = icon, tint = tint, contentDescription = null, size = 72)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = PikColors.TextPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = PikColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun DebtsScreen() {
    ComingSoon(
        icon = Icons.Filled.AccountBalanceWallet,
        tint = PikColors.Blue,
        title = stringResource(R.string.debts_title),
        message = stringResource(R.string.debts_coming_soon),
    )
}
