package com.pik.app.domain.importing

import java.time.DateTimeException
import java.time.LocalDate

enum class StatementFormat { REVOLUT, PAYPAL, GENERIC }

/** One payment line of a statement file, ready to become an inbox item. */
data class StatementRow(
    val date: LocalDate,
    val direction: ImportDirection,
    val amountCents: Long,
    /** Description or merchant name as written in the file. */
    val merchant: String,
    /** Description plus the type column (e.g. "TOPUP"), kept for the funding check and the Inbox. */
    val rawText: String,
    /** 0 for the first row with these exact values in the file, 1 for the second identical one, ... */
    val occurrence: Int,
)

data class StatementResult(
    val format: StatementFormat,
    val rows: List<StatementRow>,
    /** Rows that were left out: not completed, other currency, no date or no amount. */
    val skipped: Int,
)

/**
 * Turns the rows of a bank, Revolut or PayPal export into [StatementRow]s. The header row is the first row
 * that has a date-like and an amount-like column name (an export can start with lines about the account).
 */
object StatementParser {
    private const val MAX_HEADER_SEARCH = 60
    private const val MAX_MERCHANT_LENGTH = 80

    private val DATE_NAMES = listOf(
        "completed date", "data di completamento", "data contabile", "data operazione", "date", "data",
        "started date", "data di inizio", "data valuta",
    )
    private val DESCRIPTION_NAMES = listOf(
        "descrizione operazione", "descrizione", "causale", "description", "dettagli", "details", "name", "nome",
        "beneficiario",
    )
    private val TYPE_NAMES = listOf("type", "tipo")
    private val AMOUNT_NAMES = listOf("importo", "amount", "lordo", "gross", "netto", "net")
    private val DEBIT_NAMES = listOf("dare", "addebiti", "addebito", "uscite", "debit")
    private val CREDIT_NAMES = listOf("avere", "accrediti", "accredito", "entrate", "credit")
    private val CURRENCY_NAMES = listOf("currency", "valuta", "divisa")
    private val STATE_NAMES = listOf("state", "stato", "status")
    private val FEE_NAMES = listOf("fee", "commissione", "commissioni")

    private class Columns(
        val dates: List<Int>,
        val descriptions: List<Int>,
        val type: Int?,
        val amount: Int?,
        val debit: Int?,
        val credit: Int?,
        val currency: Int?,
        val state: Int?,
        val fee: Int?,
        val format: StatementFormat,
    )

    /** null when no header row with a date and an amount column was found. */
    fun parse(table: List<List<String>>): StatementResult? {
        var headerIndex = -1
        var columns: Columns? = null
        for (i in 0 until minOf(table.size, MAX_HEADER_SEARCH)) {
            val c = detectColumns(table[i])
            if (c != null) {
                headerIndex = i
                columns = c
                break
            }
        }
        val cols = columns ?: return null

        val rows = ArrayList<StatementRow>()
        var skipped = 0
        val strict = cols.format != StatementFormat.GENERIC
        for (r in headerIndex + 1 until table.size) {
            val line = table[r]
            if (line.all { it.isBlank() }) continue
            fun cell(index: Int?): String = if (index == null) "" else line.getOrNull(index)?.trim().orEmpty()

            if (strict) {
                val state = cell(cols.state).lowercase()
                if (state.isNotEmpty() && !state.startsWith("complet")) {
                    skipped++
                    continue
                }
            }
            val currency = cell(cols.currency)
            if (currency.isNotEmpty() && !currency.equals("EUR", ignoreCase = true)) {
                skipped++
                continue
            }
            val date = cols.dates.firstNotNullOfOrNull { parseDate(cell(it)) }
            if (date == null) {
                skipped++
                continue
            }
            val signed: Long? = signedAmount(cols, ::cell)
            if (signed == null || signed == 0L) {
                skipped++
                continue
            }
            val description = cols.descriptions
                .map { cell(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" - ")
            val type = cell(cols.type)
            val merchant = description.ifEmpty { type }.take(MAX_MERCHANT_LENGTH)
            val raw = listOf(description, type).filter { it.isNotEmpty() }.distinct().joinToString(" | ")
            rows.add(
                StatementRow(
                    date = date,
                    direction = if (signed < 0) ImportDirection.OUT else ImportDirection.IN,
                    amountCents = if (signed < 0) -signed else signed,
                    merchant = merchant,
                    rawText = raw,
                    occurrence = 0,
                ),
            )
        }
        return StatementResult(cols.format, withOccurrences(rows), skipped)
    }

    private fun signedAmount(columns: Columns, cell: (Int?) -> String): Long? {
        if (columns.amount != null) {
            val amount = parseAmountCell(cell(columns.amount)) ?: return null
            if (columns.format == StatementFormat.REVOLUT) {
                val fee = parseAmountCell(cell(columns.fee)) ?: 0L
                return amount - (if (fee < 0) -fee else fee)
            }
            return amount
        }
        val debit = parseAmountCell(cell(columns.debit))?.let { if (it < 0) -it else it } ?: 0L
        val credit = parseAmountCell(cell(columns.credit))?.let { if (it < 0) -it else it } ?: 0L
        return when {
            debit > 0L -> -debit
            credit > 0L -> credit
            else -> 0L
        }
    }

    private fun parseAmountCell(text: String): Long? = if (text.isBlank()) null else AmountText.parseLoose(text)

    private fun detectColumns(header: List<String>): Columns? {
        val names = header.map { normaliseHeader(it) }
        fun find(candidates: List<String>): Int? {
            for (name in candidates) {
                val index = names.indexOf(name)
                if (index >= 0) return index
            }
            return null
        }
        val dates = DATE_NAMES.mapNotNull { name -> names.indexOf(name).takeIf { it >= 0 } }
        if (dates.isEmpty()) return null
        val amount = find(AMOUNT_NAMES)
        val debit = find(DEBIT_NAMES)
        val credit = find(CREDIT_NAMES)
        if (amount == null && debit == null && credit == null) return null
        val state = find(STATE_NAMES)
        val format = when {
            ("started date" in names || "data di inizio" in names) && state != null -> StatementFormat.REVOLUT
            ("gross" in names || "lordo" in names) && ("net" in names || "netto" in names) && state != null ->
                StatementFormat.PAYPAL
            else -> StatementFormat.GENERIC
        }
        return Columns(
            dates = dates,
            descriptions = DESCRIPTION_NAMES.mapNotNull { name -> names.indexOf(name).takeIf { it >= 0 } },
            type = find(TYPE_NAMES),
            amount = amount,
            debit = if (amount == null) debit else null,
            credit = if (amount == null) credit else null,
            currency = find(CURRENCY_NAMES),
            state = state,
            fee = find(FEE_NAMES),
            format = format,
        )
    }

    private fun normaliseHeader(text: String): String =
        text.removePrefix("\uFEFF").trim().trim('"').trim().lowercase().replace(Regex("\\s+"), " ")

    private fun withOccurrences(rows: List<StatementRow>): List<StatementRow> {
        val seen = HashMap<String, Int>()
        return rows.map { row ->
            val key = "${row.date}|${row.direction}|${row.amountCents}|${row.merchant}|${row.rawText}"
            val n = seen.getOrDefault(key, 0)
            seen[key] = n + 1
            row.copy(occurrence = n)
        }
    }

    // ---- dates ----

    private val ISO_DATE = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""")
    private val DAY_FIRST_DATE = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2}|\d{4})$""")
    private val EXCEL_SERIAL = Regex("""^(\d{5})(?:\.\d+)?$""")
    private const val MIN_SERIAL = 20_000L
    private const val MAX_SERIAL = 80_000L

    /**
     * dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, d/M/yy, yyyy-MM-dd (with or without a time after it) or an
     * Excel serial number. Null when it is none of these or not a real date.
     */
    fun parseDate(text: String): LocalDate? {
        var s = text.trim()
        if (s.isEmpty()) return null
        if (s.length > 10) {
            val cut = s.indexOfFirst { it == ' ' || it == 'T' }
            if (cut > 0) s = s.substring(0, cut)
        } else if (s.contains(' ')) {
            s = s.substringBefore(' ')
        }
        try {
            ISO_DATE.matchEntire(s)?.let {
                return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
            DAY_FIRST_DATE.matchEntire(s)?.let {
                var year = it.groupValues[3].toInt()
                if (it.groupValues[3].length == 2) year += 2000
                return LocalDate.of(year, it.groupValues[2].toInt(), it.groupValues[1].toInt())
            }
            EXCEL_SERIAL.matchEntire(s)?.let {
                val serial = it.groupValues[1].toLong()
                if (serial in MIN_SERIAL..MAX_SERIAL) return LocalDate.of(1899, 12, 30).plusDays(serial)
            }
        } catch (e: DateTimeException) {
            return null
        }
        return null
    }
}
