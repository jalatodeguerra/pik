package com.pik.app.domain.importing

import java.security.MessageDigest

/** The dedupe keys of inbox items: the same payment read twice gets the same key. */
object ImportKeys {
    private const val SEP = "\u001F"
    private const val MINUTE_MILLIS = 60_000L

    fun sha256Hex(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"

    /** sha-256 of the app, the notification key, the post time rounded to the minute, and the text. */
    fun notificationKey(packageName: String, notificationKey: String, postTimeMillis: Long, text: String): String =
        sha256Hex(listOf(packageName, notificationKey, (postTimeMillis / MINUTE_MILLIS).toString(), text).joinToString(SEP))

    /** sha-256 of the account, the day, the amount, the description and the occurrence of identical rows in the file. */
    fun fileRowKey(accountId: Long, row: StatementRow): String =
        sha256Hex(
            listOf(
                accountId.toString(),
                row.date.toString(),
                row.direction.name + row.amountCents,
                row.merchant,
                row.rawText,
                row.occurrence.toString(),
            ).joinToString(SEP),
        )
}
