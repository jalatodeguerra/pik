package com.pik.app.domain

/** Transaction type filter of the Activity screen. */
enum class TypeFilter { ALL, EXPENSE, INCOME, TRANSFER }

/** Category filter of the Activity screen (it also reads "debt payments" as a group of its own). */
sealed interface CategoryFilter {
    data object All : CategoryFilter
    data class Category(val id: Long) : CategoryFilter
    data object DebtPayments : CategoryFilter

    /** Expenses without a category that are not debt payments. */
    data object Uncategorised : CategoryFilter
}

/** Pure matching rules for Activity search and filters. */
object ActivityFilter {

    /**
     * Case-insensitive "contains" search over the given texts (note, category name, account
     * names). A blank query matches everything.
     */
    fun matchesQuery(query: String, vararg texts: String?): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return texts.any { it != null && it.lowercase().contains(q) }
    }

    /** Balance adjustments only show under [TypeFilter.ALL]. */
    fun matchesType(filter: TypeFilter, type: TransactionType): Boolean = when (filter) {
        TypeFilter.ALL -> true
        TypeFilter.EXPENSE -> type == TransactionType.EXPENSE
        TypeFilter.INCOME -> type == TransactionType.INCOME
        TypeFilter.TRANSFER -> type == TransactionType.TRANSFER
    }

    /** A null [accountId] means all accounts. A transfer matches both of its accounts. */
    fun matchesAccount(accountId: Long?, fromAccountId: Long, toAccountId: Long?): Boolean =
        accountId == null || accountId == fromAccountId || accountId == toAccountId

    /**
     * [CategoryFilter.Category] matches transactions of that category (debt payments have none).
     * [CategoryFilter.DebtPayments] matches expenses that pay a debt.
     */
    fun matchesCategory(filter: CategoryFilter, type: TransactionType, categoryId: Long?, debtId: Long?): Boolean =
        when (filter) {
            CategoryFilter.All -> true
            is CategoryFilter.Category -> categoryId == filter.id
            CategoryFilter.DebtPayments -> type == TransactionType.EXPENSE && debtId != null
            CategoryFilter.Uncategorised -> type == TransactionType.EXPENSE && categoryId == null && debtId == null
        }
}
