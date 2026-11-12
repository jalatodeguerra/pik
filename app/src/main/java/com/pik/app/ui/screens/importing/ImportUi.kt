package com.pik.app.ui.screens.importing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pik.app.R
import com.pik.app.domain.importing.ImportSource

/** The name of where an inbox item came from. */
@Composable
fun importSourceLabel(source: ImportSource): String = stringResource(
    when (source) {
        ImportSource.RELAX -> R.string.import_source_relax
        ImportSource.NEXI -> R.string.import_source_nexi
        ImportSource.REVOLUT -> R.string.import_source_revolut
        ImportSource.PAYPAL -> R.string.import_source_paypal
        ImportSource.OTHER_APP -> R.string.import_source_other
        ImportSource.FILE -> R.string.import_source_file
    },
)

fun importSourceIcon(source: ImportSource): ImageVector = when (source) {
    ImportSource.RELAX -> Icons.Filled.AccountBalance
    ImportSource.NEXI -> Icons.Filled.CreditCard
    ImportSource.REVOLUT -> Icons.Filled.Payments
    ImportSource.PAYPAL -> Icons.Filled.AccountBalanceWallet
    ImportSource.OTHER_APP -> Icons.Filled.Notifications
    ImportSource.FILE -> Icons.Filled.Description
}
