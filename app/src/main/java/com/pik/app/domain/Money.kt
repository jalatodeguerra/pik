package com.pik.app.domain

/**
 * Money helpers. Money is always a Long number of euro cents.
 * Formatting is done by hand (Italian style) and never depends on the device locale:
 * thousands '.', decimals ',', then a non-breaking space and the euro sign.
 */
object Money {
    const val NBSP: Char = ' '
    const val CURRENCY_SYMBOL: String = "€"

    private const val MAX_PARSE_INTEGER_DIGITS = 12

    /** "1.234,56 €" (with U+00A0 before the euro sign). */
    fun format(cents: Long, showPlusSign: Boolean = false): String {
        val sign = when {
            cents < 0 -> "-"
            showPlusSign && cents > 0 -> "+"
            else -> ""
        }
        return sign + formatPlain(if (cents < 0) -cents else cents) + NBSP + CURRENCY_SYMBOL
    }

    /** Unsigned-looking number part for a non-negative amount, e.g. "1.234,56". Negative values keep a '-'. */
    fun formatPlain(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val euros = abs / 100
        val frac = (abs % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + groupThousands(euros.toString()) + "," + frac
    }

    /** Inserts '.' every three digits from the right. Input must be digits only. */
    fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val first = digits.length % 3
        if (first > 0) sb.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /**
     * Parses user input such as "1.234,56", "12,5", "12", "-5,00", "12.5" into cents.
     * '.' is a thousands separator, except a single '.' followed by 1-2 digits
     * and no ',' which is read as a decimal separator (so "12.5" is 12,50).
     * Returns null when the text is not a valid amount (more than 2 decimals, garbage, ...).
     */
    fun parse(input: String): Long? {
        var s = input.filterNot { it.isWhitespace() || it == NBSP || it == '€' }
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("-")) {
            negative = true
            s = s.substring(1)
        } else if (s.startsWith("+")) {
            s = s.substring(1)
        }
        if (s.isEmpty()) return null

        val intPart: String
        val fracPart: String
        val comma = s.indexOf(',')
        if (comma >= 0) {
            if (s.indexOf(',', comma + 1) >= 0) return null
            intPart = s.substring(0, comma)
            fracPart = s.substring(comma + 1)
        } else {
            val dot = s.lastIndexOf('.')
            val decimalDot = dot >= 0 && s.indexOf('.') == dot && (s.length - dot - 1) in 1..2
            if (decimalDot) {
                intPart = s.substring(0, dot)
                fracPart = s.substring(dot + 1)
            } else {
                intPart = s
                fracPart = ""
            }
        }

        val intDigits = stripGrouping(intPart) ?: return null
        if (fracPart.length > 2 || !fracPart.all { it in '0'..'9' }) return null
        if (intDigits.isEmpty() && fracPart.isEmpty()) return null
        if (intDigits.length > MAX_PARSE_INTEGER_DIGITS) return null

        val euros = if (intDigits.isEmpty()) 0L else intDigits.toLong()
        val frac = if (fracPart.isEmpty()) 0L else fracPart.padEnd(2, '0').toLong()
        val cents = euros * 100 + frac
        return if (negative) -cents else cents
    }

    /** Validates Italian thousands grouping and returns the digits, or null when invalid. */
    private fun stripGrouping(intPart: String): String? {
        if (!intPart.contains('.')) {
            return if (intPart.all { it in '0'..'9' }) intPart else null
        }
        val groups = intPart.split('.')
        val head = groups.first()
        if (head.isEmpty() || head.length > 3 || !head.all { it in '0'..'9' }) return null
        for (g in groups.drop(1)) {
            if (g.length != 3 || !g.all { it in '0'..'9' }) return null
        }
        return groups.joinToString("")
    }
}
