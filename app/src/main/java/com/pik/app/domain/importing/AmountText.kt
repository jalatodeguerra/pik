package com.pik.app.domain.importing

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Reads euro amounts out of free text ("12,50 €", "€1,234.56", "EUR 12,50") and out of statement cells
 * ("-12.50", "1234,5"). Money is always a Long number of cents. No Android types, so it runs in unit tests.
 */
object AmountText {

    /** An amount found in a text: [sign] is '+', '-' or null; [start] until [end] is its place in the text. */
    data class Found(val cents: Long, val sign: Char?, val start: Int, val end: Int)

    private const val MAX_INTEGER_DIGITS = 12
    private const val NUM = """\d[\d.,]*\d|\d"""
    private const val CUR = """(?:€|(?<![A-Za-z])EUR(?![A-Za-z]))"""

    // Group 1-3: currency first (sign before or after it, number). Group 4-5: number first, then currency.
    private val AMOUNT = Regex(
        """(?:([-+])\s*)?$CUR\s*(?:([-+])\s*)?($NUM)|(?:([-+])\s*)?($NUM)\s*$CUR""",
        RegexOption.IGNORE_CASE,
    )

    /** Every amount that has a euro sign or "EUR" next to it, in text order. */
    fun findAll(text: String): List<Found> =
        AMOUNT.findAll(text).mapNotNull { m ->
            val number = m.groups[3]?.value ?: m.groups[5]?.value ?: return@mapNotNull null
            val cents = parseNumber(number) ?: return@mapNotNull null
            val sign = m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[4]?.value
            Found(cents, sign?.firstOrNull(), m.range.first, m.range.last + 1)
        }.toList()

    /**
     * Unsigned number made of digits, '.' and ','. The decimal separator is the last separator when
     * it is followed by 1 or 2 digits; when it is followed by exactly 3 digits (and is the only kind)
     * it is a thousands separator. "1.234,56", "1,234.56", "12.50", "12,5", "1.234" (= 1234), "1234".
     * Returns null for anything else ("12,345,6", "1.2345").
     */
    fun parseNumber(token: String): Long? {
        if (token.isEmpty() || token.first() !in '0'..'9' || token.last() !in '0'..'9') return null
        if (!token.all { it in '0'..'9' || it == '.' || it == ',' }) return null
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        val lastSep = maxOf(lastDot, lastComma)
        if (lastSep < 0) return toCents(token, "")
        val sep = token[lastSep]
        val other = if (sep == '.') ',' else '.'
        val fracLen = token.length - lastSep - 1
        val decimal: Boolean = when {
            token.indexOf(other) >= 0 -> {
                // both kinds: the last one is the decimal separator and must be used only once
                if (token.count { it == sep } != 1 || fracLen !in 1..2) return null
                true
            }
            token.count { it == sep } > 1 -> false
            fracLen == 3 -> false
            fracLen in 1..2 -> true
            else -> return null
        }
        val intPart = if (decimal) token.substring(0, lastSep) else token
        val frac = if (decimal) token.substring(lastSep + 1) else ""
        val groups = intPart.split('.', ',')
        val digits: String
        if (groups.size == 1) {
            digits = groups[0]
        } else {
            if (groups[0].length !in 1..3 || groups.drop(1).any { it.length != 3 }) return null
            // the grouping separators must all be the same character
            val seps = intPart.filter { it == '.' || it == ',' }.toSet()
            if (seps.size != 1) return null
            digits = groups.joinToString("")
        }
        return toCents(digits, frac)
    }

    private fun toCents(intDigits: String, frac: String): Long? {
        if (intDigits.isEmpty() || intDigits.length > MAX_INTEGER_DIGITS) return null
        val euros = intDigits.toLongOrNull() ?: return null
        val cents = if (frac.isEmpty()) 0L else frac.padEnd(2, '0').toLong()
        return euros * 100 + cents
    }

    /**
     * An amount in a statement cell: optional sign (also a trailing one or round brackets), euro sign,
     * "EUR" and spaces are ignored. "-12.50", "1.234,56", "1234,5", "(12,50)", "12,50-", "€ 12,50".
     * Plain decimals with more digits ("12.499999" from a spreadsheet) are rounded to cents.
     * Returns the signed cents, or null when the cell is not an amount.
     */
    fun parseLoose(input: String): Long? {
        var s = input.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        s = s.replace("EUR", "", ignoreCase = true)
        s = s.filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it == '€' }
        s = s.replace('−', '-')
        if (s.startsWith("-")) {
            negative = !negative
            s = s.substring(1)
        } else if (s.startsWith("+")) {
            s = s.substring(1)
        }
        if (s.endsWith("-")) {
            negative = !negative
            s = s.substring(0, s.length - 1)
        }
        if (s.isEmpty()) return null
        val cents = parseNumber(s) ?: roundedDecimal(s) ?: return null
        return if (negative) -cents else cents
    }

    /** "12.499999999999998" or "1.2E3" as written by spreadsheets: a plain Java decimal rounded to cents. */
    private fun roundedDecimal(s: String): Long? {
        if (!s.all { it in '0'..'9' || it == '.' || it == 'E' || it == 'e' || it == '-' || it == '+' }) return null
        val value = s.toBigDecimalOrNull() ?: return null
        if (value.abs() > BigDecimal("999999999999")) return null
        return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong().let { if (it < 0) -it else it }
    }
}
