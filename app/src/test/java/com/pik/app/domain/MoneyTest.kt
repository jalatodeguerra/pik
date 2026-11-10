package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    private val nb = " "

    @Test fun formatZero() = assertEquals("0,00${nb}€", Money.format(0))

    @Test fun formatThousands() = assertEquals("1.234,56${nb}€", Money.format(123456))

    @Test fun formatSmallNegative() = assertEquals("-0,05${nb}€", Money.format(-5))

    @Test fun formatMillion() = assertEquals("1.000.000,00${nb}€", Money.format(100000000))

    @Test fun formatNegativeThousands() = assertEquals("-1.234,56${nb}€", Money.format(-123456))

    @Test fun formatHundredEuros() = assertEquals("100,00${nb}€", Money.format(10000))

    @Test fun formatPlusSign() {
        assertEquals("+12,00${nb}€", Money.format(1200, showPlusSign = true))
        assertEquals("0,00${nb}€", Money.format(0, showPlusSign = true))
        assertEquals("-12,00${nb}€", Money.format(-1200, showPlusSign = true))
    }

    @Test fun formatPlain() = assertEquals("1.234,50", Money.formatPlain(123450))

    @Test fun parseItalian() {
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1200L, Money.parse("12"))
        assertEquals(5L, Money.parse("0,05"))
        assertEquals(5L, Money.parse(",05"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(100000000L, Money.parse("1.000.000,00"))
        assertEquals(-500L, Money.parse("-5,00"))
        assertEquals(123456L, Money.parse("1.234,56${nb}€"))
        assertEquals(1200L, Money.parse("12,"))
    }

    @Test fun parseDotAsDecimalWhenUnambiguous() {
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(1250L, Money.parse("12.50"))
    }

    @Test fun parseInvalid() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-"))
        assertNull(Money.parse(","))
        assertNull(Money.parse("1,234,5"))
        assertNull(Money.parse("12,345"))
        assertNull(Money.parse("1.2345"))
        assertNull(Money.parse("12.34.5"))
    }

    @Test fun roundTrip() {
        listOf(0L, 1L, 99L, 100L, 123456L, 99999999999L, -2550L).forEach {
            assertEquals(it, Money.parse(Money.formatPlain(it)))
        }
    }
}
