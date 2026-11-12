package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvReaderTest {

    @Test fun commaSeparated() {
        val rows = CsvReader.read("a,b,c\n1,2,3\n")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), rows)
    }

    @Test fun semicolonSeparatedWithDecimalCommas() {
        val text = "Data;Dare;Avere\n01/11/2026;12,50;\n02/11/2026;;100,00\n"
        assertEquals(';', CsvReader.detectDelimiter(text))
        assertEquals(
            listOf(listOf("Data", "Dare", "Avere"), listOf("01/11/2026", "12,50", ""), listOf("02/11/2026", "", "100,00")),
            CsvReader.read(text),
        )
    }

    @Test fun tabSeparated() {
        val text = "a\tb\n1\t2\n"
        assertEquals('\t', CsvReader.detectDelimiter(text))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvReader.read(text))
    }

    @Test fun quotedFieldsKeepDelimitersAndLineBreaks() {
        val text = "\"Name\",\"Gross\"\n\"Rossi, Mario\",\"-20,00\"\n\"two\nlines\",\"1\"\n"
        assertEquals(
            listOf(listOf("Name", "Gross"), listOf("Rossi, Mario", "-20,00"), listOf("two\nlines", "1")),
            CsvReader.read(text),
        )
    }

    @Test fun escapedQuotes() {
        assertEquals(listOf(listOf("say \"hi\"", "x")), CsvReader.read("\"say \"\"hi\"\"\",x"))
    }

    @Test fun byteOrderMarkIsStripped() {
        val rows = CsvReader.read("﻿Data;Importo\n01/11/2026;5,00\n")
        assertEquals("Data", rows[0][0])
    }

    @Test fun windowsLineEndsAndBlankLines() {
        val rows = CsvReader.read("a;b\r\n\r\n1;2\r\n;\r\n3;4")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")), rows)
    }

    @Test fun missingFinalNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvReader.read("a,b\n1,2"))
    }

    @Test fun fieldsAreTrimmed() {
        assertEquals(listOf(listOf("a", "b")), CsvReader.read(" a ; b "))
    }

    @Test fun emptyTextHasNoRows() {
        assertEquals(emptyList<List<String>>(), CsvReader.read(""))
    }

    @Test fun preambleLinesDoNotConfuseTheDelimiter() {
        val text = "Estratto conto;\nConto: IT00;\nData contabile;Data valuta;Dare;Avere;Descrizione operazione\n" +
            "01/11/2026;01/11/2026;12,50;;PAGAMENTO POS\n"
        assertEquals(';', CsvReader.detectDelimiter(text))
    }
}
