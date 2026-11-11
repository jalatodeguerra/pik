package com.pik.app.ui.nav

object Routes {
    const val FIRST_RUN = "firstRun"
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val PLAN = "plan"
    const val DEBTS = "debts"
    const val BUDGETS = "plan/budgets"
    const val SETTINGS = "settings"
    const val CATEGORIES = "settings/categories"

    const val ARG_TRANSACTION_ID = "transactionId"
    const val ADD = "add?$ARG_TRANSACTION_ID={$ARG_TRANSACTION_ID}"

    fun add(): String = "add"
    fun edit(transactionId: Long): String = "add?$ARG_TRANSACTION_ID=$transactionId"

    const val ARG_DEBT_ID = "debtId"
    const val DEBT_DETAIL = "debt/{$ARG_DEBT_ID}"
    const val DEBT_EDIT = "debt-edit?$ARG_DEBT_ID={$ARG_DEBT_ID}"

    fun debtDetail(debtId: Long): String = "debt/$debtId"
    fun debtAdd(): String = "debt-edit"
    fun debtEdit(debtId: Long): String = "debt-edit?$ARG_DEBT_ID=$debtId"

    val tabs: Set<String> = setOf(HOME, ACTIVITY, PLAN, DEBTS)
}
