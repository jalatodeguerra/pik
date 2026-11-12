package com.pik.app.domain.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Minimal reader for .xlsx files (a zip of XML files): the first worksheet as rows of text. Handles shared
 * strings, inline strings, booleans and numbers (numbers are returned as written, so a date is an Excel
 * serial number). Pure Java, no Android XML classes, so it runs in unit tests.
 */
object XlsxReader {
    private const val MAX_ENTRY_BYTES = 40 * 1024 * 1024
    private val SHEET_NAME = Regex("""xl/worksheets/sheet(\d+)\.xml""")

    private sealed interface Event
    private class Start(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Event
    private class End(val name: String) : Event
    private class Text(val value: String) : Event

    /** Rows of the first sheet, or null when the bytes are not a readable workbook. */
    fun read(bytes: ByteArray): List<List<String>>? {
        return try {
            var shared: ByteArray? = null
            var sheetNumber = Int.MAX_VALUE
            var sheet: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml") {
                        shared = readLimited(zip)
                    } else {
                        val number = SHEET_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
                        if (number != null && number < sheetNumber) {
                            sheetNumber = number
                            sheet = readLimited(zip)
                        }
                    }
                }
            }
            val sheetBytes = sheet ?: return null
            val strings = shared?.let { parseSharedStrings(String(it, Charsets.UTF_8)) } ?: emptyList()
            parseSheet(String(sheetBytes, Charsets.UTF_8), strings)
        } catch (e: Exception) {
            null
        }
    }

    private fun readLimited(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalStateException("entry too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val result = ArrayList<String>()
        var current: StringBuilder? = null
        var inText = false
        var phoneticDepth = 0
        for (event in tokenize(xml)) {
            when (event) {
                is Start -> when (event.name) {
                    "si" -> {
                        if (event.selfClosing) {
                            result.add("")
                        } else {
                            current = StringBuilder()
                        }
                    }
                    "rPh" -> if (!event.selfClosing) phoneticDepth++
                    "t" -> inText = !event.selfClosing
                }
                is End -> when (event.name) {
                    "si" -> {
                        result.add(current?.toString() ?: "")
                        current = null
                    }
                    "rPh" -> if (phoneticDepth > 0) phoneticDepth--
                    "t" -> inText = false
                }
                is Text -> if (inText && phoneticDepth == 0) current?.append(event.value)
            }
        }
        return result
    }

    private fun parseSheet(xml: String, strings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var cells = ArrayList<String>()
        var column = 0
        var cellType = ""
        var inRow = false
        var inValue = false
        var inInlineText = false
        var inInline = false
        val value = StringBuilder()

        fun endCell() {
            val text = when (cellType) {
                "s" -> value.toString().trim().toIntOrNull()?.let { strings.getOrNull(it) } ?: ""
                "b" -> if (value.toString().trim() == "1") "TRUE" else "FALSE"
                "e" -> ""
                else -> value.toString()
            }
            while (cells.size < column) cells.add("")
            cells.add(text)
            value.setLength(0)
        }

        for (event in tokenize(xml)) {
            when (event) {
                is Start -> when (event.name) {
                    "row" -> {
                        inRow = true
                        cells = ArrayList()
                    }
                    "c" -> if (inRow) {
                        column = columnIndex(event.attrs["r"], cells.size)
                        cellType = event.attrs["t"] ?: ""
                        value.setLength(0)
                        if (event.selfClosing) endCell()
                    }
                    "v" -> inValue = !event.selfClosing
                    "is" -> inInline = !event.selfClosing
                    "t" -> inInlineText = inInline && !event.selfClosing
                }
                is End -> when (event.name) {
                    "row" -> {
                        inRow = false
                        if (cells.any { it.isNotBlank() }) rows.add(cells.map { it.trim() })
                    }
                    "c" -> if (inRow) endCell()
                    "v" -> inValue = false
                    "is" -> inInline = false
                    "t" -> inInlineText = false
                }
                is Text -> if (inValue || inInlineText) value.append(event.value)
            }
        }
        return rows
    }

    /** "B3" -> 1, "AA10" -> 26. Without a usable reference the cell goes right after the previous one. */
    private fun columnIndex(ref: String?, fallback: Int): Int {
        if (ref == null) return fallback
        var n = 0
        var letters = 0
        for (c in ref) {
            if (c in 'A'..'Z') {
                n = n * 26 + (c - 'A' + 1)
                letters++
            } else if (c in 'a'..'z') {
                n = n * 26 + (c - 'a' + 1)
                letters++
            } else {
                break
            }
        }
        return if (letters == 0) fallback else n - 1
    }

    // ---- a very small XML tokenizer (names without namespace prefix, entities decoded) ----

    private fun tokenize(xml: String): List<Event> {
        val events = ArrayList<Event>()
        var i = 0
        val n = xml.length
        while (i < n) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) {
                addText(events, xml.substring(i))
                break
            }
            if (lt > i) addText(events, xml.substring(i, lt))
            when {
                xml.startsWith("<!--", lt) -> {
                    val end = xml.indexOf("-->", lt + 4)
                    i = if (end < 0) n else end + 3
                }
                xml.startsWith("<![CDATA[", lt) -> {
                    val end = xml.indexOf("]]>", lt + 9)
                    val stop = if (end < 0) n else end
                    events.add(Text(xml.substring(lt + 9, stop)))
                    i = if (end < 0) n else end + 3
                }
                xml.startsWith("<?", lt) -> {
                    val end = xml.indexOf("?>", lt + 2)
                    i = if (end < 0) n else end + 2
                }
                xml.startsWith("<!", lt) -> {
                    val end = xml.indexOf('>', lt + 2)
                    i = if (end < 0) n else end + 1
                }
                xml.startsWith("</", lt) -> {
                    val end = xml.indexOf('>', lt + 2)
                    if (end < 0) {
                        i = n
                    } else {
                        events.add(End(localName(xml.substring(lt + 2, end).trim())))
                        i = end + 1
                    }
                }
                else -> {
                    val end = tagEnd(xml, lt + 1)
                    if (end < 0) {
                        i = n
                    } else {
                        var body = xml.substring(lt + 1, end)
                        val selfClosing = body.endsWith("/")
                        if (selfClosing) body = body.dropLast(1)
                        events.add(startTag(body, selfClosing))
                        i = end + 1
                    }
                }
            }
        }
        return events
    }

    /** Index of the '>' that closes a start tag, skipping '>' inside attribute values. */
    private fun tagEnd(xml: String, from: Int): Int {
        var quote = '\u0000'
        var i = from
        while (i < xml.length) {
            val c = xml[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return -1
    }

    private fun addText(events: MutableList<Event>, raw: String) {
        if (raw.isNotEmpty()) events.add(Text(decodeEntities(raw)))
    }

    private fun localName(name: String): String = name.substringAfter(':')

    private fun startTag(body: String, selfClosing: Boolean): Start {
        var i = 0
        while (i < body.length && !body[i].isWhitespace()) i++
        val name = localName(body.substring(0, i))
        val attrs = HashMap<String, String>()
        while (i < body.length) {
            while (i < body.length && body[i].isWhitespace()) i++
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val key = localName(body.substring(i, eq).trim())
            var j = eq + 1
            while (j < body.length && body[j].isWhitespace()) j++
            if (j >= body.length) break
            val quote = body[j]
            if (quote != '"' && quote != '\'') break
            val close = body.indexOf(quote, j + 1)
            if (close < 0) break
            attrs[key] = decodeEntities(body.substring(j + 1, close))
            i = close + 1
        }
        return Start(name, attrs, selfClosing)
    }

    private fun decodeEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi > i && semi - i <= 10) {
                    val entity = text.substring(i + 1, semi)
                    val decoded: String? = when {
                        entity == "amp" -> "&"
                        entity == "lt" -> "<"
                        entity == "gt" -> ">"
                        entity == "quot" -> "\""
                        entity == "apos" -> "'"
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.substring(2).toIntOrNull(16)?.let { codePoint(it) }
                        entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let { codePoint(it) }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun codePoint(value: Int): String? =
        if (value in 0..0x10FFFF && !(value in 0xD800..0xDFFF)) String(Character.toChars(value)) else null
}
