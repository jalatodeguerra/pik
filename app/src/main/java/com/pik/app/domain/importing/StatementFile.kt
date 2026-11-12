package com.pik.app.domain.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

sealed interface FileParseOutcome {
    data class Parsed(val result: StatementResult) : FileParseOutcome

    /** An old binary .xls file: the user has to export it again as CSV or XLSX. */
    object OldExcel : FileParseOutcome

    /** The bytes are not a CSV text file or an .xlsx workbook. */
    object Unreadable : FileParseOutcome

    /** A readable file, but no table with a date and an amount column, or no payment rows in it. */
    object NoTransactions : FileParseOutcome
}

/** From the bytes of a chosen file to payment rows: CSV or text, or a .xlsx workbook. */
object StatementFile {
    private const val MAX_BYTES = 30 * 1024 * 1024

    fun parse(bytes: ByteArray): FileParseOutcome {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return FileParseOutcome.Unreadable
        val table: List<List<String>> = when {
            startsWith(bytes, 0x50, 0x4B, 0x03, 0x04) -> XlsxReader.read(bytes) ?: return FileParseOutcome.Unreadable
            startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0) -> return FileParseOutcome.OldExcel
            else -> CsvReader.read(decodeText(bytes))
        }
        val result = StatementParser.parse(table) ?: return FileParseOutcome.NoTransactions
        if (result.rows.isEmpty()) return FileParseOutcome.NoTransactions
        return FileParseOutcome.Parsed(result)
    }

    private fun startsWith(bytes: ByteArray, vararg prefix: Int): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) if ((bytes[i].toInt() and 0xFF) != prefix[i]) return false
        return true
    }

    /** UTF-16 with a byte order mark, strict UTF-8, or else Windows-1252 (what Excel writes for CSV in Italy). */
    fun decodeText(bytes: ByteArray): String {
        if (startsWith(bytes, 0xFF, 0xFE) || startsWith(bytes, 0xFE, 0xFF)) {
            return String(bytes, Charsets.UTF_16)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
