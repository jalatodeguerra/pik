package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReaderTest {

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    @Test fun sharedStringsNumbersAndInlineStrings() {
        val shared = header + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"3\">" +
            "<si><t>Data</t></si><si><t>Importo</t></si><si><r><t>Caff</t></r><r><t xml:space=\"preserve\">è &amp; co</t></r></si></sst>"
        val sheet = header + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" +
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\"><v>46338</v></c><c r=\"B2\" s=\"3\"><v>-12.5</v></c></row>" +
            "<row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t>12/11/2026</t></is></c><c r=\"B3\" t=\"s\"><v>2</v></c></row>" +
            "</sheetData></worksheet>"
        val rows = XlsxReader.read(zip(mapOf("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet)))
        assertEquals(
            listOf(
                listOf("Data", "Importo"),
                listOf("46338", "-12.5"),
                listOf("12/11/2026", "Caffè & co"),
            ),
            rows,
        )
    }

    @Test fun skippedColumnsAndEmptyRows() {
        val sheet = header + "<worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>a</t></is></c><c r=\"C1\" t=\"inlineStr\"><is><t>c</t></is></c></row>" +
            "<row r=\"2\"/>" +
            "<row r=\"3\"><c r=\"B3\"><v>7</v></c><c r=\"C3\"/></row>" +
            "</sheetData></worksheet>"
        val rows = XlsxReader.read(zip(mapOf("xl/worksheets/sheet1.xml" to sheet)))
        assertEquals(listOf(listOf("a", "", "c"), listOf("", "7", "")), rows)
    }

    @Test fun booleansAndErrors() {
        val sheet = header + "<worksheet><sheetData><row r=\"1\">" +
            "<c r=\"A1\" t=\"b\"><v>1</v></c><c r=\"B1\" t=\"e\"><v>#DIV/0!</v></c><c r=\"C1\" t=\"str\"><v>text</v></c>" +
            "</row></sheetData></worksheet>"
        assertEquals(listOf(listOf("TRUE", "", "text")), XlsxReader.read(zip(mapOf("xl/worksheets/sheet1.xml" to sheet))))
    }

    @Test fun theLowestNumberedSheetIsRead() {
        val one = header + "<worksheet><sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData></worksheet>"
        val two = header + "<worksheet><sheetData><row r=\"1\"><c r=\"A1\"><v>2</v></c></row></sheetData></worksheet>"
        val rows = XlsxReader.read(zip(mapOf("xl/worksheets/sheet2.xml" to two, "xl/worksheets/sheet1.xml" to one)))
        assertEquals(listOf(listOf("1")), rows)
    }

    @Test fun phoneticTextInSharedStringsIsIgnored() {
        val shared = header + "<sst><si><t>Roma</t><rPh sb=\"0\" eb=\"1\"><t>ROMA</t></rPh></si></sst>"
        val sheet = header + "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row></sheetData></worksheet>"
        val rows = XlsxReader.read(zip(mapOf("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet)))
        assertEquals(listOf(listOf("Roma")), rows)
    }

    @Test fun notAWorkbookIsNull() {
        assertNull(XlsxReader.read("hello".toByteArray()))
        assertNull(XlsxReader.read(zip(mapOf("something.txt" to "x"))))
        assertNull(XlsxReader.read(ByteArray(0)))
    }
}
