package com.pik.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFilterTest {
    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(ActivityFilter.matchesQuery("", "x"))
        assertTrue(ActivityFilter.matchesQuery("   ", null, null))
    }

    @Test
    fun matchesNoteCategoryOrAccountCaseInsensitive() {
        assertTrue(ActivityFilter.matchesQuery("PIZZA", "pizza with friends", "Eating out", "Cash"))
        assertTrue(ActivityFilter.matchesQuery("eating", "pizza", "Eating out", "Cash"))
        assertTrue(ActivityFilter.matchesQuery("cash", "pizza", "Eating out", "Cash"))
    }

    @Test
    fun matchesPartOfWordAndTrimsQuery() {
        assertTrue(ActivityFilter.matchesQuery("  gro ", "", "Groceries", "Bank"))
    }

    @Test
    fun noMatchWhenNothingContainsQuery() {
        assertFalse(ActivityFilter.matchesQuery("rent", "pizza", "Eating out", "Cash", null))
    }

    @Test
    fun handlesAccentedLetters() {
        assertTrue(ActivityFilter.matchesQuery("caffè", "Caffè al bar", null, "Cash"))
        assertTrue(ActivityFilter.matchesQuery("CAFFÈ", "caffè al bar", null, "Cash"))
    }

    @Test
    fun typeFilter() {
        assertTrue(ActivityFilter.matchesType(TypeFilter.ALL, TransactionType.ADJUSTMENT))
        assertTrue(ActivityFilter.matchesType(TypeFilter.EXPENSE, TransactionType.EXPENSE))
        assertFalse(ActivityFilter.matchesType(TypeFilter.EXPENSE, TransactionType.INCOME))
        assertTrue(ActivityFilter.matchesType(TypeFilter.INCOME, TransactionType.INCOME))
        assertTrue(ActivityFilter.matchesType(TypeFilter.TRANSFER, TransactionType.TRANSFER))
        assertFalse(ActivityFilter.matchesType(TypeFilter.TRANSFER, TransactionType.ADJUSTMENT))
    }

    @Test
    fun accountFilterMatchesBothSidesOfTransfer() {
        assertTrue(ActivityFilter.matchesAccount(null, 1, 2))
        assertTrue(ActivityFilter.matchesAccount(1, 1, null))
        assertTrue(ActivityFilter.matchesAccount(2, 1, 2))
        assertFalse(ActivityFilter.matchesAccount(3, 1, 2))
    }

    @Test
    fun categoryFilterMatchesCategoryDebtPaymentsAndUncategorised() {
        val exp = TransactionType.EXPENSE
        assertTrue(ActivityFilter.matchesCategory(CategoryFilter.All, exp, null, null))
        assertTrue(ActivityFilter.matchesCategory(CategoryFilter.Category(4), exp, 4, null))
        assertFalse(ActivityFilter.matchesCategory(CategoryFilter.Category(4), exp, 5, null))
        assertFalse(ActivityFilter.matchesCategory(CategoryFilter.Category(4), exp, null, 9))
        assertTrue(ActivityFilter.matchesCategory(CategoryFilter.DebtPayments, exp, null, 9))
        assertFalse(ActivityFilter.matchesCategory(CategoryFilter.DebtPayments, exp, 4, null))
        assertTrue(ActivityFilter.matchesCategory(CategoryFilter.Uncategorised, exp, null, null))
        assertFalse(ActivityFilter.matchesCategory(CategoryFilter.Uncategorised, exp, null, 9))
        assertFalse(ActivityFilter.matchesCategory(CategoryFilter.Uncategorised, TransactionType.TRANSFER, null, null))
    }
}
