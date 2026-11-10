package com.pik.app.data.repo

import com.pik.app.data.db.CategoryEntity
import com.pik.app.domain.CategoryKind

object DefaultCategories {
    private fun expense(order: Int, name: String, icon: String, color: String) =
        CategoryEntity(name = name, kind = CategoryKind.EXPENSE, colorKey = color, iconKey = icon, sortOrder = order)

    private fun income(order: Int, name: String, icon: String, color: String) =
        CategoryEntity(name = name, kind = CategoryKind.INCOME, colorKey = color, iconKey = icon, sortOrder = order)

    val all: List<CategoryEntity> = listOf(
        expense(0, "Groceries", "cart", "mint"),
        expense(1, "Eating out", "restaurant", "orange"),
        expense(2, "Coffee", "coffee", "amber"),
        expense(3, "Transport", "bus", "blue"),
        expense(4, "Rent", "home", "violet"),
        expense(5, "Bills & utilities", "bills", "teal"),
        expense(6, "Shopping", "bag", "pink"),
        expense(7, "Health", "health", "coral"),
        expense(8, "Entertainment", "movie", "violet"),
        expense(9, "Subscriptions", "subscriptions", "blue"),
        expense(10, "Gifts", "gift", "pink"),
        expense(11, "Other", "other", "slate"),
        income(0, "Salary", "salary", "lime"),
        income(1, "Refund", "refund", "teal"),
        income(2, "Gift received", "redeem", "pink"),
        income(3, "Other income", "income", "slate"),
    )
}
