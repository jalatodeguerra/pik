package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountTextTest {

    private fun first(text: String): Long? = AmountText.findAll(text).firstOrNull()?.cents

    @Test fun italianWithEuroAfter() = assertEquals(1250L, first("Pagamento di 12,50 €"))

    @Test fun euroSignBefore() = assertEquals(1250L, first("€ 12,50"))

    @Test fun euroSignGluedAndDecimalDot() = assertEquals(1250L, first("€12.50 at Lidl"))

    @Test fun eurCodeBefore() = assertEquals(1250L, first("EUR 12,50"))

    @Test fun eurCodeAfter() = assertEquals(1250L, first("12,50 EUR"))

    @Test fun thousandsItalian() = assertEquals(123456L, first("1.234,56 €"))

    @Test fun thousandsEnglish() = assertEquals(123456L, first("€1,234.56"))

    @Test fun wholeEuros() = assertEquals(5000L, first("Money added: €50"))

    @Test fun negativeSign() {
        val found = AmountText.findAll("-12,50 €").single()
        assertEquals(1250L, found.cents)
        assertEquals('-', found.sign)
    }

    @Test fun plusSignBeforeEuro() {
        val found = AmountText.findAll("+€50 from Mario").single()
        assertEquals(5000L, found.cents)
        assertEquals('+', found.sign)
    }

    @Test fun noSign() = assertNull(AmountText.findAll("12,50 €").single().sign)

    @Test fun numbersWithoutCurrencyAreIgnored() {
        assertEquals(emptyList<AmountText.Found>(), AmountText.findAll("Carta ****1234 il 12/11"))
    }

    @Test fun wordsStartingWithEurAreNotCurrency() {
        assertEquals(emptyList<AmountText.Found>(), AmountText.findAll("EUROPA 12"))
    }

    @Test fun severalAmountsInOrder() {
        val all = AmountText.findAll("Pagamento di 12,50 EUR. Saldo disponibile 100,00 EUR")
        assertEquals(listOf(1250L, 10000L), all.map { it.cents })
    }

    @Test fun positionsPointIntoTheText() {
        val text = "Hai pagato 12,50 € a Lidl"
        val found = AmountText.findAll(text).single()
        assertEquals("12,50 €", text.substring(found.start, found.end))
    }

    @Test fun parseNumberDecimals() {
        assertEquals(1250L, AmountText.parseNumber("12,5"))
        assertEquals(1250L, AmountText.parseNumber("12.50"))
        assertEquals(123450L, AmountText.parseNumber("1234,5"))
        assertEquals(5L, AmountText.parseNumber("0,05"))
    }

    @Test fun parseNumberThousandsWithThreeDigits() {
        assertEquals(123400L, AmountText.parseNumber("1.234"))
        assertEquals(123400L, AmountText.parseNumber("1,234"))
        assertEquals(123456700L, AmountText.parseNumber("1.234.567"))
        assertEquals(123456789L, AmountText.parseNumber("1.234.567,89"))
        assertEquals(123456789L, AmountText.parseNumber("1,234,567.89"))
    }

    @Test fun parseNumberRejectsGarbage() {
        assertNull(AmountText.parseNumber(""))
        assertNull(AmountText.parseNumber("1.2345"))
        assertNull(AmountText.parseNumber("12,345,6"))
        assertNull(AmountText.parseNumber("1,23.456"))
        assertNull(AmountText.parseNumber("1.23,45,67"))
        assertNull(AmountText.parseNumber("abc"))
        assertNull(AmountText.parseNumber("12,"))
    }

    @Test fun parseLooseSignsAndSymbols() {
        assertEquals(-1250L, AmountText.parseLoose("-12.50"))
        assertEquals(-1250L, AmountText.parseLoose("-12,50"))
        assertEquals(123450L, AmountText.parseLoose("1234,5"))
        assertEquals(123456L, AmountText.parseLoose("1.234,56"))
        assertEquals(-123456L, AmountText.parseLoose("-1,234.56"))
        assertEquals(1250L, AmountText.parseLoose("+12,50"))
        assertEquals(-1250L, AmountText.parseLoose("(12,50)"))
        assertEquals(-1250L, AmountText.parseLoose("12,50-"))
        assertEquals(-1250L, AmountText.parseLoose("−12,50"))
        assertEquals(1250L, AmountText.parseLoose("€ 12,50"))
        assertEquals(1250L, AmountText.parseLoose("12,50 EUR"))
        assertEquals(0L, AmountText.parseLoose("0,00"))
    }

    @Test fun parseLooseRoundsSpreadsheetNoise() {
        assertEquals(1250L, AmountText.parseLoose("12.499999999999998"))
        assertEquals(-1250L, AmountText.parseLoose("-12.5000000001"))
        assertEquals(120000L, AmountText.parseLoose("1.2E3"))
    }

    @Test fun parseLooseRejectsText() {
        assertNull(AmountText.parseLoose(""))
        assertNull(AmountText.parseLoose("abc"))
        assertNull(AmountText.parseLoose("-"))
        assertNull(AmountText.parseLoose("12 apples"))
    }
}
