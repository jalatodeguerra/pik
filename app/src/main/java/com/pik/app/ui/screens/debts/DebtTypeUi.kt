package com.pik.app.ui.screens.debts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pik.app.R
import com.pik.app.domain.Apr
import com.pik.app.domain.DebtType
import com.pik.app.data.db.DebtEntity
import com.pik.app.domain.InstallmentStatus
import com.pik.app.domain.DateFormats

fun DebtType.labelRes(): Int = when (this) {
    DebtType.CAR_LOAN -> R.string.debt_type_car_loan
    DebtType.PERSONAL_LOAN -> R.string.debt_type_personal_loan
    DebtType.MORTGAGE -> R.string.debt_type_mortgage
    DebtType.CONSUMER_CREDIT -> R.string.debt_type_consumer_credit
    DebtType.STUDENT_LOAN -> R.string.debt_type_student_loan
    DebtType.FAMILY_FRIENDS -> R.string.debt_type_family_friends
    DebtType.OTHER -> R.string.debt_type_other
}

fun DebtType.icon(): ImageVector = when (this) {
    DebtType.CAR_LOAN -> Icons.Filled.DirectionsCar
    DebtType.PERSONAL_LOAN -> Icons.Filled.Payments
    DebtType.MORTGAGE -> Icons.Filled.Home
    DebtType.CONSUMER_CREDIT -> Icons.Filled.ShoppingBag
    DebtType.STUDENT_LOAN -> Icons.Filled.School
    DebtType.FAMILY_FRIENDS -> Icons.Filled.People
    DebtType.OTHER -> Icons.Filled.MoreHoriz
}

/** "TAN 7,50% · TAEG 9,12%", or just "TAN 7,50%" when there is no TAEG. */
@Composable
fun ratesText(debt: DebtEntity): String {
    val tan = Apr.format(debt.aprBasisPoints)
    val taeg = debt.taegBasisPoints
    return if (taeg != null) {
        stringResource(R.string.debt_rates_tan_taeg, tan, Apr.format(taeg))
    } else {
        stringResource(R.string.debt_rates_tan, tan)
    }
}

/** "Instalment 18 of 60 · 42 left · ends March 2030". */
@Composable
fun installmentsText(status: InstallmentStatus): String {
    val end = status.endMonth
    if (end == null) return stringResource(R.string.debt_installments_done, status.paid, status.total)
    val left = stringResource(
        if (status.left == 1) R.string.debt_installments_left_one else R.string.debt_installments_left_other,
        status.left,
    )
    return stringResource(
        R.string.debt_installments_status,
        status.paid,
        status.total,
        left,
        DateFormats.monthYearInline(end),
    )
}
