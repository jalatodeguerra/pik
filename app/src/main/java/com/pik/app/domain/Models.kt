package com.pik.app.domain

/** Pure domain enums. Stored in Room by their name. */
enum class AccountType { CASH, BANK }

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { EXPENSE, INCOME, TRANSFER, ADJUSTMENT }
