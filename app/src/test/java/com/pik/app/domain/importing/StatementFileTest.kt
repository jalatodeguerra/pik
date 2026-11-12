package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StatementFileTest {

    private fun parse(bytes: ByteArray) = StatementFile.parse(bytes)

    @Test fun utf8CsvIsRead() {
        val outcome = parse("Data;Descrizione;Importo\n01/11/2026;Caffè;-1,20\n".toByteArray(Charsets.UTF_8))
        val parsed = outcome as FileParseOutcome.Parsed
        assertEquals("Caffè", parsed.result.rows.single().merchant)
    }

    @Test fun windows1252CsvIsRead() {
        val bytes = "Data;Descrizione;Importo\n01/11/2026;Caffè;-1,20\n".toByteArray(charset("windows-1252"))
        val parsed = parse(bytes) as FileParseOutcome.Parsed
        assertEquals("Caffè", parsed.result.rows.single().merchant)
    }

    @Test fun utf16WithBomIsRead() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Data;Descrizione;Importo\n01/11/2026;X;-1,20\n".toByteArray(Charsets.UTF_16LE)
        assertTrue(parse(bytes) is FileParseOutcome.Parsed)
    }

    @Test fun utf8ByteOrderMarkIsRead() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Data;Descrizione;Importo\n01/11/2026;X;-1,20\n".toByteArray(Charsets.UTF_8)
        assertTrue(parse(bytes) is FileParseOutcome.Parsed)
    }

    @Test fun xlsxIsRead() {
        val sheet = "<worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Data</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>Descrizione</t></is></c>" +
            "<c r=\"C1\" t=\"inlineStr\"><is><t>Importo</t></is></c></row>" +
            "<row r=\"2\"><c r=\"A2\"><v>46329</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>COOP</t></is></c><c r=\"C2\"><v>-12.5</v></c></row>" +
            "</sheetData></worksheet>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            it.write(sheet.toByteArray())
            it.closeEntry()
        }
        val parsed = parse(out.toByteArray()) as FileParseOutcome.Parsed
        val row = parsed.result.rows.single()
        assertEquals(java.time.LocalDate.of(2026, 11, 3), row.date)
        assertEquals(1250L, row.amountCents)
        assertEquals(ImportDirection.OUT, row.direction)
    }

    @Test fun oldBinaryExcelAsksForAnotherExport() {
        val bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        assertTrue(parse(bytes) === FileParseOutcome.OldExcel)
    }

    @Test fun brokenZipIsUnreadable() {
        assertTrue(parse(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)) === FileParseOutcome.Unreadable)
    }

    @Test fun emptyFileIsUnreadable() {
        assertTrue(parse(ByteArray(0)) === FileParseOutcome.Unreadable)
    }

    @Test fun textWithoutATableHasNoTransactions() {
        assertTrue(parse("hello world".toByteArray()) === FileParseOutcome.NoTransactions)
    }

    @Test fun headerOnlyHasNoTransactions() {
        assertTrue(parse("Data;Descrizione;Importo\n".toByteArray()) === FileParseOutcome.NoTransactions)
    }
}
