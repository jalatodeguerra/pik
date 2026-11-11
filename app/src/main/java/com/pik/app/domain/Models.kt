package com.pik.app.domain

/** Pure domain enums. Stored in Room by their name. */
enum class AccountType { CASH, BANK }

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { EXPENSE, INCOME, TRANSFER, ADJUSTMENT }

/**
 * Kind of a debt entry. The amount of an entry is signed: positive raises what you owe,
 * negative lowers it. INTEREST and FEE are normally positive, ADJUSTMENT can be either
 * (it comes from "Check balance").
 */
enum class DebtEntryType { INTEREST, FEE, ADJUSTMENT }

/** Which debt gets the extra money first in the payoff plan. */
enum class PayoffOrder { HIGHEST_INTEREST, SMALLEST_BALANCE }

/**
 * Kind of loan. Stored in Room by its name (default OTHER). Types with a fixed monthly instalment
 * (rata) are every type except [FAMILY_FRIENDS] and [OTHER].
 */
enum class DebtType {
    CAR_LOAN, PERSONAL_LOAN, MORTGAGE, CONSUMER_CREDIT, STUDENT_LOAN, FAMILY_FRIENDS, OTHER;

    val hasInstalments: Boolean get() = this != FAMILY_FRIENDS && this != OTHER
}
