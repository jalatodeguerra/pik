package com.pik.app.domain.importing

import java.time.LocalDate

/**
 * Finds a card or wallet payment in the text of a notification. There are no sample notifications
 * yet, so it is tolerant and table driven: amounts with a euro sign, words that mean "paid" or
 * "received", words that mean "this is a security message", and the words that come before the
 * merchant name. Returns null when the notification is not clearly a payment.
 */
object NotificationParser {

    /** A notification with one of these is about logging in, limits or a refused payment: never a payment. */
    private val IGNORE: List<Regex> = listOf(
        "\\bconferma\\b", "\\bconfermi\\b", "\\bconfermare\\b",
        "\\bautorizza\\b", "\\bautorizzare\\b", "\\bautorizzi\\b",
        "\\bcodice\\b", "\\botp\\b",
        "\\bapprove\\b", "\\bconfirm\\b", "\\bverify\\b", "\\bverifica\\b", "\\bverificare\\b",
        "\\baccedi\\b", "\\blogin\\b", "\\blog in\\b",
        "\\blimite\\b", "\\blimit\\b",
        "rifiutat", "\\bdeclined\\b", "non riuscit", "\\bfailed\\b",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** A trailing * means "any ending" (accredit* = accredito, accreditato); the others must be whole words. */
    private fun wordRegex(words: List<String>): Regex = Regex(
        "(?<![\\p{L}\\p{N}])(?:" + words.joinToString("|") { w ->
            if (w.endsWith("*")) Regex.escape(w.dropLast(1)) + "\\p{L}*" else Regex.escape(w) + "(?![\\p{L}\\p{N}])"
        } + ")",
        RegexOption.IGNORE_CASE,
    )

    private val IN_REGEX = wordRegex(
        listOf(
            "hai ricevuto", "ricevuto", "accredit*", "bonifico in entrata", "rimbors*",
            "received", "refund*", "money added", "sent you", "top-up", "topup", "topped up",
        ),
    )

    private val OUT_REGEX = wordRegex(
        listOf(
            "hai pagato", "pagament*", "pagat*", "addebit*", "prelievo", "acquist*", "spesa", "autorizzazione di",
            "paid", "spent", "payment*", "hai inviato", "sent", "withdraw*",
        ),
    )

    /** "at " is only a payment word as a whole word ("paid at", "💳 €12.50 at Lidl"). */
    private val AT_WORD = Regex("(?<![\\p{L}\\p{N}])at\\s", RegexOption.IGNORE_CASE)

    private val MARKER = Regex(
        "(?<![\\p{L}\\p{N}])(presso|da|a|at|to|from|su)(?![\\p{L}\\p{N}])\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val MERCHANT_END = Regex("\\n|\\.(?:\\s|$)|\\s+(?:il|con|del)\\s", RegexOption.IGNORE_CASE)

    private const val MAX_MERCHANT_LENGTH = 60
    private const val KEYWORD_WINDOW = 30

    /** Titles that say nothing about the merchant. */
    private val GENERIC_TITLES = setOf(
        "pagamento", "payment", "notifica", "notification", "movimento", "transazione", "transaction",
        "carta", "card", "card payment", "paid", "nuovo pagamento", "new payment", "operazione",
    )

    @Suppress("UNUSED_PARAMETER")
    fun parse(
        source: ImportSource,
        title: String,
        text: String,
        postedAt: LocalDate,
        appLabel: String = "",
    ): ParsedPayment? {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        val full = if (cleanTitle.isEmpty()) cleanText else if (cleanText.isEmpty()) cleanTitle else "$cleanTitle\n$cleanText"
        if (full.isBlank()) return null
        val lower = full.lowercase()

        if (IGNORE.any { it.containsMatchIn(lower) }) return null

        val amounts = AmountText.findAll(full).filter { it.cents > 0L }
        if (amounts.isEmpty()) return null
        val first = amounts.first()

        val inWord = IN_REGEX.containsMatchIn(lower)
        val outWord = OUT_REGEX.containsMatchIn(lower) || AT_WORD.containsMatchIn(lower)
        val hasKeyword = inWord || outWord

        // Only a balance notice ("saldo disponibile") and no payment word.
        if (!hasKeyword && (lower.contains("saldo") || lower.contains("balance"))) return null

        // Several different amounts: take the first one only when a payment word is next to it.
        if (amounts.map { it.cents }.distinct().size > 1) {
            val from = (first.start - KEYWORD_WINDOW).coerceAtLeast(0)
            val to = (first.end + KEYWORD_WINDOW).coerceAtMost(lower.length)
            val near = lower.substring(from, to)
            val nextToIt = IN_REGEX.containsMatchIn(near) || OUT_REGEX.containsMatchIn(near) ||
                AT_WORD.containsMatchIn(near)
            if (!nextToIt) return null
        }

        val direction: ImportDirection = when {
            inWord -> ImportDirection.IN
            outWord -> ImportDirection.OUT
            !KnownApps.isFinance(source) -> return null
            first.sign == '+' -> ImportDirection.IN
            else -> ImportDirection.OUT
        }

        val merchant = findMerchant(full, first.end, direction, source)
            ?: titleMerchant(cleanTitle, source, appLabel)
            ?: ""
        return ParsedPayment(first.cents, direction, merchant)
    }

    private fun allowedMarkers(direction: ImportDirection, source: ImportSource): Set<String> =
        if (direction == ImportDirection.IN) {
            setOf("from", "da")
        } else {
            val set = mutableSetOf("presso", "at", "to", "su")
            when (source) {
                ImportSource.RELAX, ImportSource.NEXI -> set.add("da")
                else -> set.add("a")
            }
            set
        }

    /** The text after "presso", "at", "to", ... that follows the amount, up to the end of the sentence. */
    private fun findMerchant(full: String, afterAmount: Int, direction: ImportDirection, source: ImportSource): String? {
        val allowed = allowedMarkers(direction, source)
        val rest = full.substring(afterAmount.coerceIn(0, full.length))
        var search = 0
        while (true) {
            val m = MARKER.find(rest, search) ?: return null
            if (m.groupValues[1].lowercase() in allowed) {
                val tail = rest.substring(m.range.last + 1)
                val end = MERCHANT_END.find(tail)?.range?.first ?: tail.length
                val cleaned = cleanMerchant(tail.substring(0, end))
                if (cleaned.isNotEmpty()) return cleaned
            }
            search = m.range.last + 1
            if (search >= rest.length) return null
        }
    }

    private fun cleanMerchant(raw: String): String =
        raw.replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ',', ';', ':', '!', '"', '\'', ' ')
            .take(MAX_MERCHANT_LENGTH)
            .trim()

    /** The title is the merchant unless it is just the app name, a payment word or an amount. */
    private fun titleMerchant(title: String, source: ImportSource, appLabel: String): String? {
        if (title.isBlank()) return null
        val lower = title.lowercase()
        if (lower in GENERIC_TITLES) return null
        val label = appLabel.trim().lowercase()
        if (label.isNotEmpty() && lower == label) return null
        val brand = when (source) {
            ImportSource.RELAX -> "relax"
            ImportSource.NEXI -> "nexi"
            ImportSource.REVOLUT -> "revolut"
            ImportSource.PAYPAL -> "paypal"
            else -> null
        }
        if (brand != null && lower.contains(brand)) return null
        if (AmountText.findAll(title).isNotEmpty()) return null
        if (IN_REGEX.containsMatchIn(lower) || OUT_REGEX.containsMatchIn(lower) || AT_WORD.containsMatchIn(lower)) {
            return null
        }
        return cleanMerchant(title).ifEmpty { null }
    }
}
