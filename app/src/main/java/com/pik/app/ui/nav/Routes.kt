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

    val tabs: Set<String> = setOf(HOME, ACTIVITY, PLAN, DEBTS)
}
