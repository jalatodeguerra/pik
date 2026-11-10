package com.pik.app.domain

/**
 * State of the on-screen keypad. [raw] holds what the user typed using plain digits and ','
 * (for example "1234,5"). Immutable: every key press returns a new instance.
 * Rules: up to 7 integer digits and 2 decimals (max 9.999.999,99), leading zeros collapse.
 */
data class AmountInput(val raw: String = "") {

    val hasComma: Boolean get() = raw.contains(',')
    val integerPart: String get() = if (hasComma) raw.substringBefore(',') else raw
    val decimalPart: String get() = if (hasComma) raw.substringAfter(',') else ""

    fun typeDigit(digit: Char): AmountInput {
        if (digit !in '0'..'9') return this
        if (!hasComma) {
            val newInt = if (integerPart == "0") digit.toString() else integerPart + digit
            if (newInt.length > MAX_INTEGER_DIGITS) return this
            return AmountInput(newInt)
        }
        if (decimalPart.length >= MAX_DECIMALS) return this
        return AmountInput(raw + digit)
    }

    fun typeComma(): AmountInput {
        if (hasComma) return this
        return if (raw.isEmpty()) AmountInput("0,") else AmountInput("$raw,")
    }

    fun backspace(): AmountInput = if (raw.isEmpty()) this else AmountInput(raw.dropLast(1))

    fun clear(): AmountInput = AmountInput()

    fun toCents(): Long {
        val euros = if (integerPart.isEmpty()) 0L else integerPart.toLong()
        val frac = decimalPart.padEnd(MAX_DECIMALS, '0').toLong()
        return euros * 100 + frac
    }

    val isZero: Boolean get() = toCents() == 0L

    /** Text for the big amount display without the euro sign, e.g. "1.234,5". Empty input shows "0". */
    fun display(): String {
        if (raw.isEmpty()) return "0"
        val grouped = Money.groupThousands(integerPart.ifEmpty { "0" })
        return if (hasComma) grouped + "," + decimalPart else grouped
    }

    companion object {
        const val MAX_INTEGER_DIGITS = 7
        const val MAX_DECIMALS = 2
        const val MAX_CENTS = 999_999_999L

        /** Builds the input for editing an existing amount. Zero gives empty input. */
        fun fromCents(cents: Long): AmountInput {
            val abs = minOf(if (cents < 0) -cents else cents, MAX_CENTS)
            if (abs == 0L) return AmountInput()
            val euros = abs / 100
            val frac = abs % 100
            return when {
                frac == 0L -> AmountInput(euros.toString())
                frac % 10 == 0L -> AmountInput("$euros,${frac / 10}")
                else -> AmountInput("$euros,${frac.toString().padStart(2, '0')}")
            }
        }
    }
}
