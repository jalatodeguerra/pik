package com.pik.app.domain.importing

/** Where an inbox item came from. Stored in Room by its name. */
enum class ImportSource { RELAX, NEXI, REVOLUT, PAYPAL, OTHER_APP, FILE }

/** Money going out of the account (OUT) or coming in (IN). The amount of an item is always positive. */
enum class ImportDirection { OUT, IN }

/** PENDING waits for the user, DUPLICATE is probably already in Pik (shown collapsed, can be restored). */
enum class ImportStatus { PENDING, ADDED, DISMISSED, DUPLICATE }

/** What the item would become when added. The two TRANSFER kinds move money between two Pik accounts. */
enum class ImportSuggestion { EXPENSE, INCOME, TRANSFER_OUT, TRANSFER_IN }

/** A payment found in a notification. [merchant] is empty when none could be found. */
data class ParsedPayment(
    val amountCents: Long,
    val direction: ImportDirection,
    val merchant: String,
)

/** A payment app the user chose to read, and the Pik account its payments belong to. */
data class ImportAppConfig(
    val packageName: String,
    val source: ImportSource,
    val accountId: Long?,
) {
    /** "package|SOURCE|accountId" (empty accountId when none). Package names never contain '|'. */
    fun encode(): String = "$packageName|${source.name}|${accountId ?: ""}"

    companion object {
        fun decode(text: String): ImportAppConfig? {
            val parts = text.split('|')
            if (parts.size != 3 || parts[0].isBlank()) return null
            val source = ImportSource.entries.firstOrNull { it.name == parts[1] } ?: return null
            val accountId = if (parts[2].isEmpty()) null else (parts[2].toLongOrNull() ?: return null)
            return ImportAppConfig(parts[0], source, accountId)
        }
    }
}

/** Finds the payment apps by name, because the package ids are not certain. */
object KnownApps {
    private val PACKAGES: Map<String, ImportSource> = mapOf(
        "com.revolut.revolut" to ImportSource.REVOLUT,
        "com.paypal.android.p2pmobile" to ImportSource.PAYPAL,
    )

    private val LABELS: List<Pair<String, ImportSource>> = listOf(
        "relax" to ImportSource.RELAX,
        "nexi" to ImportSource.NEXI,
        "revolut" to ImportSource.REVOLUT,
        "paypal" to ImportSource.PAYPAL,
    )

    /** The source of a known finance app, found by package id first and then by label; null for any other app. */
    fun sourceFor(packageName: String, label: String): ImportSource? {
        PACKAGES[packageName]?.let { return it }
        val lower = label.lowercase()
        return LABELS.firstOrNull { lower.contains(it.first) }?.second
    }

    /** True for the sources that are payment apps (a notification of one of them is a payment even without keywords). */
    fun isFinance(source: ImportSource): Boolean = source != ImportSource.OTHER_APP && source != ImportSource.FILE
}
