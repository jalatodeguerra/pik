package com.pik.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementParserTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun parse(csv: String): StatementResult {
        val result = StatementParser.parse(CsvReader.read(csv))
        assertNotNull("no header found", result)
        return result!!
    }

    // ---- Revolut ----

    private val revolutCsv = """
        Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
        CARD_PAYMENT,Current,2026-11-10 09:12:44,2026-11-11 03:00:01,Lidl,-12.50,0.00,EUR,COMPLETED,100.00
        TOPUP,Current,2026-11-09 18:00:00,2026-11-09 18:00:05,Top-Up by *1234,50.00,0.00,EUR,COMPLETED,112.50
        CARD_PAYMENT,Current,2026-11-08 12:00:00,,Pending Shop,-5.00,0.00,EUR,PENDING,
        TRANSFER,Current,2026-11-07 10:00:00,2026-11-07 10:00:02,To Mario,-20.00,0.50,EUR,COMPLETED,60.00
        CARD_PAYMENT,Current,2026-11-06 10:00:00,2026-11-06 10:00:02,Shop USD,-9.00,0.00,USD,COMPLETED,60.00
        CARD_PAYMENT,Current,2026-11-05 10:00:00,2026-11-05 10:00:02,Reverted Shop,-9.00,0.00,EUR,REVERTED,60.00
    """.trimIndent()

    @Test fun revolutRows() {
        val result = parse(revolutCsv)
        assertEquals(StatementFormat.REVOLUT, result.format)
        assertEquals(3, result.rows.size)
        assertEquals(3, result.skipped)
        val lidl = result.rows[0]
        assertEquals(d(2026, 11, 11), lidl.date) // the completed date is preferred
        assertEquals(ImportDirection.OUT, lidl.direction)
        assertEquals(1250L, lidl.amountCents)
        assertEquals("Lidl", lidl.merchant)
        val topUp = result.rows[1]
        assertEquals(ImportDirection.IN, topUp.direction)
        assertEquals(5000L, topUp.amountCents)
        assertTrue(FundingHints.isFunding(topUp.rawText))
    }

    @Test fun revolutFeeIsAddedToWhatWentOut() {
        val transfer = parse(revolutCsv).rows[2]
        assertEquals(ImportDirection.OUT, transfer.direction)
        assertEquals(2050L, transfer.amountCents)
        assertEquals(d(2026, 11, 7), transfer.date)
    }

    @Test fun revolutInItalian() {
        val csv = """
            Tipo,Prodotto,Data di inizio,Data di completamento,Descrizione,Importo,Commissione,Valuta,Stato,Saldo
            PAGAMENTO CON CARTA,Attuale,2026-11-10 09:12:44,2026-11-10 09:12:50,Conad,"-12,50",0,EUR,COMPLETATO,100
        """.trimIndent()
        val result = parse(csv)
        assertEquals(StatementFormat.REVOLUT, result.format)
        assertEquals(1250L, result.rows.single().amountCents)
    }

    // ---- PayPal ----

    private val paypalCsv = """
        "Date","Time","TimeZone","Name","Type","Status","Currency","Gross","Fee","Net","From Email Address"
        "11/11/2026","10:00:00","CET","Amazon EU","Express Checkout Payment","Completed","EUR","-20,00","0,00","-20,00","a@b.it"
        "11/11/2026","10:00:00","CET","","General Card Deposit","Completed","EUR","20,00","0,00","20,00",""
        "10/11/2026","09:00:00","CET","Luca","Payment Received","Completed","EUR","1.015,00","-0,50","1.014,50","l@b.it"
        "09/11/2026","09:00:00","CET","Shop","Express Checkout Payment","Pending","EUR","-5,00","0,00","-5,00",""
        "08/11/2026","09:00:00","CET","US Shop","Express Checkout Payment","Completed","USD","-5,00","0,00","-5,00",""
    """.trimIndent()

    @Test fun paypalEnglish() {
        val result = parse(paypalCsv)
        assertEquals(StatementFormat.PAYPAL, result.format)
        assertEquals(3, result.rows.size)
        assertEquals(2, result.skipped)
        val amazon = result.rows[0]
        assertEquals(d(2026, 11, 11), amazon.date)
        assertEquals(ImportDirection.OUT, amazon.direction)
        assertEquals(2000L, amazon.amountCents)
        assertEquals("Amazon EU", amazon.merchant)
        val received = result.rows[2]
        assertEquals(ImportDirection.IN, received.direction)
        assertEquals(101500L, received.amountCents) // gross, not net
    }

    @Test fun paypalFundingRowKeepsItsTypeSoItCanBecomeATransfer() {
        val funding = parse(paypalCsv).rows[1]
        assertEquals(ImportDirection.IN, funding.direction)
        assertEquals("General Card Deposit", funding.merchant)
        assertTrue(FundingHints.isFunding(funding.rawText))
    }

    @Test fun paypalItalian() {
        val csv = """
            "Data","Ora","Fuso orario","Nome","Tipo","Stato","Valuta","Lordo","Tariffa","Netto"
            "11/11/2026","10:00:00","CET","Zalando","Pagamento Express Checkout","Completato","EUR","-45,90","0,00","-45,90"
            "10/11/2026","10:00:00","CET","","Bonifico bancario sul conto PayPal","Completato","EUR","100,00","0,00","100,00"
            "09/11/2026","10:00:00","CET","Rimborsato","Pagamento","In sospeso","EUR","-1,00","0,00","-1,00"
        """.trimIndent()
        val result = parse(csv)
        assertEquals(StatementFormat.PAYPAL, result.format)
        assertEquals(2, result.rows.size)
        assertEquals(1, result.skipped)
        assertEquals(4590L, result.rows[0].amountCents)
        assertEquals(ImportDirection.OUT, result.rows[0].direction)
        assertTrue(FundingHints.isFunding(result.rows[1].rawText))
    }

    // ---- bank (BCC style) ----

    private val bccCsv = "Estratto conto al 12/11/2026;;;;\n" +
        "Conto corrente;IT00X0000000000000000000000;;;\n" +
        ";;;;\n" +
        "Data contabile;Data valuta;Dare;Avere;Descrizione operazione\n" +
        "12/11/2026;13/11/2026;12,50;;PAGAMENTO POS CONAD\n" +
        "11/11/2026;11/11/2026;;1.500,00;ACCREDITO STIPENDIO\n" +
        "10/11/2026;10/11/2026;1.234,56;;BONIFICO A ROSSI\n" +
        "Totale;;1.247,06;1.500,00;\n"

    @Test fun bccWithPreambleAndSplitColumns() {
        val result = parse(bccCsv)
        assertEquals(StatementFormat.GENERIC, result.format)
        assertEquals(3, result.rows.size)
        assertEquals(1, result.skipped) // the totals line has no date
        assertEquals(d(2026, 11, 12), result.rows[0].date) // data contabile, not data valuta
        assertEquals(ImportDirection.OUT, result.rows[0].direction)
        assertEquals(1250L, result.rows[0].amountCents)
        assertEquals("PAGAMENTO POS CONAD", result.rows[0].merchant)
        assertEquals(ImportDirection.IN, result.rows[1].direction)
        assertEquals(150000L, result.rows[1].amountCents)
        assertEquals(123456L, result.rows[2].amountCents)
    }

    @Test fun singleSignedAmountColumn() {
        val csv = "Data;Descrizione;Importo\n01/11/2026;COOP;-23,40\n02/11/2026;STIPENDIO;1.800,00\n"
        val rows = parse(csv).rows
        assertEquals(ImportDirection.OUT, rows[0].direction)
        assertEquals(2340L, rows[0].amountCents)
        assertEquals(ImportDirection.IN, rows[1].direction)
        assertEquals(180000L, rows[1].amountCents)
    }

    @Test fun englishNamesWork() {
        val rows = parse("Date,Description,Amount\n2026-11-01,Coffee,-1.20\n").rows
        assertEquals(120L, rows.single().amountCents)
        assertEquals(d(2026, 11, 1), rows.single().date)
    }

    @Test fun descriptionAndCausaleAreJoined() {
        val rows = parse("Data;Causale;Descrizione;Importo\n01/11/2026;POS;COOP MILANO;-5,00\n").rows
        assertEquals("COOP MILANO - POS", rows.single().merchant)
    }

    @Test fun rowsWithoutADateOrAnAmountAreSkipped() {
        val result = parse("Data;Descrizione;Importo\nnon una data;X;1,00\n01/11/2026;Y;\n01/11/2026;Z;0,00\n01/11/2026;OK;2,00\n")
        assertEquals(1, result.rows.size)
        assertEquals(3, result.skipped)
    }

    // ---- headers, occurrences ----

    @Test fun noHeaderMeansNull() {
        assertNull(StatementParser.parse(CsvReader.read("foo;bar\n1;2\n")))
        assertNull(StatementParser.parse(CsvReader.read("Data;Descrizione\n01/11/2026;X\n")))
        assertNull(StatementParser.parse(emptyList()))
    }

    @Test fun identicalRowsGetOccurrenceNumbers() {
        val rows = parse("Data;Descrizione;Importo\n01/11/2026;CAFFE;-1,20\n01/11/2026;CAFFE;-1,20\n01/11/2026;CAFFE;-1,30\n").rows
        assertEquals(listOf(0, 1, 0), rows.map { it.occurrence })
        assertTrue(ImportKeys.fileRowKey(1, rows[0]) != ImportKeys.fileRowKey(1, rows[1]))
        assertTrue(ImportKeys.fileRowKey(1, rows[0]) != ImportKeys.fileRowKey(2, rows[0]))
    }

    @Test fun sameFileTwiceGivesTheSameKeys() {
        val csv = "Data;Descrizione;Importo\n01/11/2026;CAFFE;-1,20\n01/11/2026;CAFFE;-1,20\n"
        val a = parse(csv).rows.map { ImportKeys.fileRowKey(7, it) }
        val b = parse(csv).rows.map { ImportKeys.fileRowKey(7, it) }
        assertEquals(a, b)
    }

    @Test fun headerWithBomAndQuotes() {
        val rows = parse("﻿\"Data\";\"Descrizione\";\"Importo\"\n\"01/11/2026\";\"X\";\"-1,00\"\n").rows
        assertEquals(100L, rows.single().amountCents)
    }

    // ---- dates ----

    @Test fun dateFormats() {
        val expected = d(2026, 11, 3)
        assertEquals(expected, StatementParser.parseDate("03/11/2026"))
        assertEquals(expected, StatementParser.parseDate("3/11/2026"))
        assertEquals(expected, StatementParser.parseDate("03-11-2026"))
        assertEquals(expected, StatementParser.parseDate("03.11.2026"))
        assertEquals(expected, StatementParser.parseDate("2026-11-03"))
        assertEquals(expected, StatementParser.parseDate("2026-11-03 14:03:22"))
        assertEquals(expected, StatementParser.parseDate("2026-11-03T14:03:22"))
        assertEquals(expected, StatementParser.parseDate("03/11/2026 14:03"))
        assertEquals(expected, StatementParser.parseDate("3/11/26"))
        assertEquals(expected, StatementParser.parseDate("46329")) // Excel serial number
        assertEquals(expected, StatementParser.parseDate("46329.5"))
    }

    @Test fun badDates() {
        assertNull(StatementParser.parseDate(""))
        assertNull(StatementParser.parseDate("31/02/2026"))
        assertNull(StatementParser.parseDate("hello"))
        assertNull(StatementParser.parseDate("12345678"))
        assertNull(StatementParser.parseDate("100"))
    }
}
