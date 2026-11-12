package com.pik.app.domain

import java.time.LocalDate

/** One row of the CSV export, with the names already looked up. */
data class CsvTransaction(
    val date: LocalDate,
    val type: TransactionType,
    val amountCents: Long,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val debtName: String?,
    val note: String,
)

/**
 * Builds the spreadsheet export. Italian Excel style: fields separated by semicolons, decimal comma,
 * UTF-8 with a byte order mark (so Excel reads accents and the euro sign), CRLF line ends.
 * This file is for spreadsheets only; the JSON backup is what restores.
 */
object CsvExport {
    /** U+FEFF: written first so Excel detects UTF-8. */
    const val BOM: String = "\uFEFF"
    const val SEPARATOR: Char = ';'
    const val LINE_END: String = "\r\n"

    /** Fixed (not translated) so the file looks the same whatever language the app is in. */
    val HEADER: List<String> = listOf("date", "type", "amount", "account", "to account", "category", "debt", "note")

    fun typeCode(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> "expense"
        TransactionType.INCOME -> "income"
        TransactionType.TRANSFER -> "transfer"
        TransactionType.ADJUSTMENT -> "adjustment"
    }

    /**
     * "-12,50": expenses are negative, income positive, a transfer is the plain amount, an adjustment
     * keeps its own sign. No thousands separator, so a spreadsheet reads it as a number.
     */
    fun amount(type: TransactionType, amountCents: Long): String {
        val signed = if (type == TransactionType.EXPENSE) -amountCents else amountCents
        val abs = if (signed < 0) -signed else signed
        val frac = (abs % 100).toString().padStart(2, '0')
        return (if (signed < 0) "-" else "") + (abs / 100).toString() + "," + frac
    }

    /**
     * Quotes a field when it holds a separator, a quote, or a line break (quotes are doubled).
     * Text that starts with = + - @ (or a tab / carriage return) gets a leading apostrophe so a
     * spreadsheet never runs it as a formula.
     */
    fun escape(field: String): String {
        val safe = if (field.isNotEmpty() && field[0] in FORMULA_STARTS) "'$field" else field
        val needsQuotes = safe.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** One line, without the line end. */
    fun line(fields: List<String>): String = fields.joinToString(SEPARATOR.toString())

    fun build(rows: List<CsvTransaction>): String {
        val sb = StringBuilder(BOM)
        sb.append(line(HEADER)).append(LINE_END)
        for (row in rows) {
            sb.append(
                line(
                    listOf(
                        row.date.toString(),
                        typeCode(row.type),
                        amount(row.type, row.amountCents),
                        escape(row.accountName),
                        escape(row.toAccountName.orEmpty()),
                        escape(row.categoryName.orEmpty()),
                        escape(row.debtName.orEmpty()),
                        escape(row.note),
                    ),
                ),
            ).append(LINE_END)
        }
        return sb.toString()
    }

    private val FORMULA_STARTS = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
