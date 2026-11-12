package com.pik.app.domain.importing

/** Reads CSV text from banks and apps: ';' ',' or tab separated, quoted fields, BOM, CR/LF line ends. */
object CsvReader {
    private val CANDIDATES = charArrayOf(';', ',', '\t')
    private const val SAMPLE_LINES = 40

    /** Rows of trimmed fields; completely empty rows are left out. */
    fun read(text: String): List<List<String>> = read(text, detectDelimiter(text))

    fun read(text: String, delimiter: Char): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false

        fun endRow() {
            row.add(field.toString().trim())
            field.setLength(0)
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = ArrayList()
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else if (c == '"' && field.isEmpty()) {
                inQuotes = true
            } else if (c == delimiter) {
                row.add(field.toString().trim())
                field.setLength(0)
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                endRow()
            } else {
                field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * The separator used by most lines: for each of ';' ',' and tab, how many of the first lines have the
     * same number of them (outside quotes), times that number. Ties go to ';' then ','.
     */
    fun detectDelimiter(text: String): Char {
        val lines = countOutsideQuotes(text.removePrefix("\uFEFF"))
        var best = ';'
        var bestScore = -1
        for ((index, candidate) in CANDIDATES.withIndex()) {
            val counts = lines.map { it[index] }.filter { it > 0 }
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }
            val score = if (mode == null) 0 else mode.value * mode.key
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /** For each of the first lines: the counts of ';' ',' and tab outside quotes. */
    private fun countOutsideQuotes(s: String): List<IntArray> {
        val result = ArrayList<IntArray>()
        var counts = IntArray(CANDIDATES.size)
        var inQuotes = false
        var any = false
        for (c in s) {
            if (c == '"') {
                inQuotes = !inQuotes
                any = true
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (any) result.add(counts)
                counts = IntArray(CANDIDATES.size)
                any = false
                if (result.size >= SAMPLE_LINES) return result
            } else if (!inQuotes) {
                val idx = CANDIDATES.indexOf(c)
                if (idx >= 0) counts[idx]++
                if (!c.isWhitespace()) any = true
            }
        }
        if (any) result.add(counts)
        return result
    }
}
