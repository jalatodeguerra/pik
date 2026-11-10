package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    /** Types a sequence: digits, ',' for comma, '<' for backspace. */
    private fun type(keys: String): AmountInput =
        keys.fold(AmountInput()) { acc, c ->
            when (c) {
                ',' -> acc.typeComma()
                '<' -> acc.backspace()
                else -> acc.typeDigit(c)
            }
        }

    @Test fun emptyIsZero() {
        val a = AmountInput()
        assertEquals(0L, a.toCents())
        assertEquals("0", a.display())
        assertTrue(a.isZero)
    }

    @Test fun simpleDigits() {
        assertEquals(1200L, type("12").toCents())
        assertEquals("12", type("12").display())
    }

    @Test fun decimals() {
        assertEquals(1250L, type("12,5").toCents())
        assertEquals(1205L, type("12,05").toCents())
        assertEquals("12,5", type("12,5").display())
    }

    @Test fun maxTwoDecimals() {
        val a = type("1,234")
        assertEquals("1,23", a.raw)
        assertEquals(123L, a.toCents())
    }

    @Test fun leadingCommaBecomesZeroComma() {
        assertEquals("0,", type(",").raw)
        assertEquals(50L, type(",5").toCents())
    }

    @Test fun leadingZerosCollapse() {
        assertEquals("0", type("000").raw)
        assertEquals("5", type("05").raw)
        assertEquals("7", type("007").raw)
    }

    @Test fun onlyOneComma() {
        assertEquals("1,2", type("1,,2").raw)
        assertEquals("1,23", type("1,2,3").raw)
    }

    @Test fun backspace() {
        assertEquals("1", type("12<").raw)
        assertEquals("12", type("12,<").raw)
        assertEquals("", type("1<").raw)
        assertEquals("", type("<<").raw)
        assertEquals("0", type("0,<").raw)
    }

    @Test fun maxIsNineMillions() {
        val a = type("9999999,99")
        assertEquals(999_999_999L, a.toCents())
        assertEquals("9.999.999,99", a.display())
        // An eighth integer digit and a third decimal are ignored.
        assertEquals("9999999", type("99999999").raw)
        assertEquals("9999999,99", type("9999999,999").raw)
    }

    @Test fun displayGroupsThousands() {
        assertEquals("1.234", type("1234").display())
        assertEquals("1.234,5", type("1234,5").display())
        assertEquals("0,", type(",").display())
    }

    @Test fun fromCents() {
        assertEquals("", AmountInput.fromCents(0).raw)
        assertEquals("12", AmountInput.fromCents(1200).raw)
        assertEquals("12,5", AmountInput.fromCents(1250).raw)
        assertEquals("12,05", AmountInput.fromCents(1205).raw)
        assertEquals("0,05", AmountInput.fromCents(5).raw)
        assertEquals(123456L, AmountInput.fromCents(123456).toCents())
    }

    @Test fun fromCentsRoundTrips() {
        listOf(1L, 9L, 10L, 99L, 100L, 101L, 110L, 99_999_999L, 999_999_999L).forEach {
            assertEquals(it, AmountInput.fromCents(it).toCents())
        }
    }

    @Test fun isZeroWhenTypedZero() {
        assertTrue(type("0").isZero)
        assertTrue(type("0,00").isZero)
        assertFalse(type("0,01").isZero)
    }
}
