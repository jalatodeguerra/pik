package com.pik.app.data.repo

import com.pik.app.data.db.CategoryEntity
import com.pik.app.domain.CategoryKind
import java.util.Locale

/**
 * The categories a new install starts with. They are seeded once, in the device language at that
 * moment (Italian names when the language is Italian, English otherwise). Existing categories are
 * never renamed when the language changes: they belong to the user.
 */
object DefaultCategories {
    private class Def(val en: String, val it: String, val icon: String, val color: String)

    private val expenses = listOf(
        Def("Groceries", "Spesa", "cart", "mint"),
        Def("Eating out", "Ristoranti", "restaurant", "orange"),
        Def("Coffee", "Caff\u00E8", "coffee", "amber"),
        Def("Transport", "Trasporti", "bus", "blue"),
        Def("Rent", "Affitto", "home", "violet"),
        Def("Bills & utilities", "Bollette e utenze", "bills", "teal"),
        Def("Shopping", "Shopping", "bag", "pink"),
        Def("Health", "Salute", "health", "coral"),
        Def("Entertainment", "Svago", "movie", "violet"),
        Def("Subscriptions", "Abbonamenti", "subscriptions", "blue"),
        Def("Gifts", "Regali", "gift", "pink"),
        Def("Other", "Altro", "other", "slate"),
    )

    private val incomes = listOf(
        Def("Salary", "Stipendio", "salary", "lime"),
        Def("Refund", "Rimborso", "refund", "teal"),
        Def("Gift received", "Regalo ricevuto", "redeem", "pink"),
        Def("Other income", "Altre entrate", "income", "slate"),
    )

    /** The default categories with names in the language of [locale] (Italian or English). */
    fun forLocale(locale: Locale = Locale.getDefault()): List<CategoryEntity> {
        val italian = locale.language == "it"
        fun build(defs: List<Def>, kind: CategoryKind) = defs.mapIndexed { order, d ->
            CategoryEntity(
                name = if (italian) d.it else d.en,
                kind = kind,
                colorKey = d.color,
                iconKey = d.icon,
                sortOrder = order,
            )
        }
        return build(expenses, CategoryKind.EXPENSE) + build(incomes, CategoryKind.INCOME)
    }
}
