package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {

    private fun row(
        type: TransactionType = TransactionType.EXPENSE,
        cents: Long = 1250,
        account: String = "Cash",
        to: String? = null,
        category: String? = "Groceries",
        debt: String? = null,
        note: String = "",
    ) = CsvTransaction(LocalDate.of(2026, 11, 3), type, cents, account, to, category, debt, note)

    @Test
    fun amountUsesDecimalCommaAndSigns() {
        assertEquals("-12,50", CsvExport.amount(TransactionType.EXPENSE, 1250))
        assertEquals("12,50", CsvExport.amount(TransactionType.INCOME, 1250))
        assertEquals("100,00", CsvExport.amount(TransactionType.TRANSFER, 10000))
        assertEquals("-3,05", CsvExport.amount(TransactionType.ADJUSTMENT, -305))
        assertEquals("3,05", CsvExport.amount(TransactionType.ADJUSTMENT, 305))
        assertEquals("-0,05", CsvExport.amount(TransactionType.EXPENSE, 5))
        assertEquals("1234567,89", CsvExport.amount(TransactionType.INCOME, 123456789))
        assertEquals("0,00", CsvExport.amount(TransactionType.INCOME, 0))
    }

    @Test
    fun escapePlainTextIsUntouched() {
        assertEquals("Groceries", CsvExport.escape("Groceries"))
        assertEquals("", CsvExport.escape(""))
        assertEquals("caffè €", CsvExport.escape("caffè €"))
    }

    @Test
    fun escapeQuotesSemicolonsAndNewlines() {
        assertEquals("\"a;b\"", CsvExport.escape("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.escape("line1\nline2"))
        assertEquals("\"a\r\nb\"", CsvExport.escape("a\r\nb"))
        // a comma is not a separator here (Italian Excel uses semicolons)
        assertEquals("a,b", CsvExport.escape("a,b"))
    }

    @Test
    fun escapeStopsSpreadsheetFormulas() {
        assertEquals("'=SUM(A1)", CsvExport.escape("=SUM(A1)"))
        assertEquals("'+39 333", CsvExport.escape("+39 333"))
        assertEquals("'-5 refund", CsvExport.escape("-5 refund"))
        assertEquals("'@home", CsvExport.escape("@home"))
        assertEquals("\"'=1;2\"", CsvExport.escape("=1;2"))
        assertEquals("a=b", CsvExport.escape("a=b"))
    }

    @Test
    fun startsWithByteOrderMarkAndHeader() {
        val text = CsvExport.build(emptyList())
        assertEquals('\uFEFF', text[0])
        assertEquals("\uFEFFdate;type;amount;account;to account;category;debt;note\r\n", text)
    }

    @Test
    fun buildsRowsInOrder() {
        val text = CsvExport.build(
            listOf(
                row(),
                row(type = TransactionType.INCOME, cents = 150000, category = "Salary", note = "Nov; bonus"),
                row(type = TransactionType.TRANSFER, cents = 5000, account = "Bank", to = "Cash", category = null),
                row(cents = 20000, category = null, debt = "Car loan"),
            ),
        )
        val lines = text.removePrefix(CsvExport.BOM).split("\r\n")
        assertEquals(6, lines.size) // header + 4 rows + the empty piece after the last line end
        assertEquals("", lines.last())
        assertEquals("2026-11-03;expense;-12,50;Cash;;Groceries;;", lines[1])
        assertEquals("2026-11-03;income;1500,00;Cash;;Salary;;\"Nov; bonus\"", lines[2])
        assertEquals("2026-11-03;transfer;50,00;Bank;Cash;;;", lines[3])
        assertEquals("2026-11-03;expense;-200,00;Cash;;;Car loan;", lines[4])
        assertTrue(lines.drop(1).dropLast(1).all { it.count { c -> c == ';' } >= 7 })
    }

    @Test
    fun noteWithNewlineStaysOneRecord() {
        val text = CsvExport.build(listOf(row(note = "first\nsecond")))
        assertTrue(text.contains("\"first\nsecond\"\r\n"))
    }
}
