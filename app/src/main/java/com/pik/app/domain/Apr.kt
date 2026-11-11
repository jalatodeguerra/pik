package com.pik.app.domain

/**
 * Annual percentage rate as stored: integer basis points (1250 = 12,50 %). Parsed and shown
 * Italian style with a decimal comma, independent of the device locale.
 */
object Apr {
    const val MAX_BASIS_POINTS = 10_000

    /**
     * Parses "12,5", "12.5", "12", "12,50 %" into basis points. Returns null for garbage,
     * negative numbers, more than 2 decimals or a rate above 100 %.
     */
    fun parse(input: String): Int? {
        val s = input.filterNot { it.isWhitespace() || it == '%' || it == Money.NBSP }
        if (s.isEmpty()) return null
        val separators = s.count { it == ',' || it == '.' }
        if (separators > 1) return null
        val sep = s.indexOfFirst { it == ',' || it == '.' }
        val whole = if (sep >= 0) s.substring(0, sep) else s
        val frac = if (sep >= 0) s.substring(sep + 1) else ""
        if (whole.isEmpty() && frac.isEmpty()) return null
        if (!whole.all { it in '0'..'9' } || !frac.all { it in '0'..'9' }) return null
        if (whole.length > 3 || frac.length > 2) return null
        val wholeValue = if (whole.isEmpty()) 0 else whole.toInt()
        val fracValue = if (frac.isEmpty()) 0 else frac.padEnd(2, '0').toInt()
        val bp = wholeValue * 100 + fracValue
        return if (bp in 0..MAX_BASIS_POINTS) bp else null
    }

    /** "12", "12,5" or "12,25": the shortest exact form, without the percent sign. */
    fun formatPlain(basisPoints: Int): String {
        val whole = basisPoints / 100
        val frac = basisPoints % 100
        return when {
            frac == 0 -> "$whole"
            frac % 10 == 0 -> "$whole,${frac / 10}"
            else -> "$whole,${frac.toString().padStart(2, '0')}"
        }
    }

    /** "12,5%" */
    fun format(basisPoints: Int): String = formatPlain(basisPoints) + "%"
}
