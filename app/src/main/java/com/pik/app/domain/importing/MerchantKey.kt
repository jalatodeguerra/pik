package com.pik.app.domain.importing

import java.util.Locale

/** Normalised merchant names, used to learn "this shop is always that category". */
object MerchantKey {
    private val WALLET_PREFIX = Regex("""^(?:PAYPAL|PP)\s*\*\s*""")
    private val NOT_LETTERS = Regex("""[^\p{L}]+""")

    /**
     * Upper case, without a "PAYPAL *" prefix, digits and punctuation, single spaces:
     * "PAYPAL *AMAZON EU 123" and "Amazon EU" both become "AMAZON EU". Empty when nothing is left.
     */
    fun normalise(merchant: String): String {
        val upper = merchant.uppercase(Locale.ROOT).trim()
        return NOT_LETTERS.replace(WALLET_PREFIX.replace(upper, ""), " ").trim()
    }

    /** Upper case letters only (keeps a "PAYPAL" prefix): for checks like "does this text mention PayPal". */
    fun letters(text: String): String = NOT_LETTERS.replace(text.uppercase(Locale.ROOT), " ").trim()
}

/** Words that say money was moved into a wallet (PayPal, Revolut) from the user's own bank. */
object FundingHints {
    private val MARKERS = listOf(
        "money added",
        "ricarica",
        "top-up",
        "top up",
        "topup",
        "topped up",
        "from bank",
        "general card deposit",
        "bank deposit to pp account",
        "bonifico bancario sul conto paypal",
        "trasferimento generale con carta",
    )

    fun isFunding(text: String): Boolean {
        val lower = text.lowercase()
        return MARKERS.any { lower.contains(it) }
    }
}
