package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Backup file format (formatVersion 1): one JSON object with the header fields and three arrays
 * holding every column of every row. Dates are ISO yyyy-MM-dd, money is integer cents.
 */
object BackupJson {
    const val FORMAT_VERSION = 1

    fun serialize(file: BackupFile): String {
        val root = JSONObject()
        root.put("formatVersion", file.formatVersion)
        root.put("appVersion", file.appVersion)
        root.put("exportedAt", file.exportedAt)
        root.put("accounts", JSONArray().also { arr -> file.accounts.forEach { arr.put(accountToJson(it)) } })
        root.put("categories", JSONArray().also { arr -> file.categories.forEach { arr.put(categoryToJson(it)) } })
        root.put(
            "transactions",
            JSONArray().also { arr -> file.transactions.forEach { arr.put(transactionToJson(it)) } },
        )
        return root.toString(2)
    }

    /** Parses and validates. Throws [BackupFormatException] when the text is not a valid Pik backup. */
    fun parse(text: String): BackupFile {
        val root = try {
            JSONObject(text.trim().removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw BackupFormatException(BackupError.NOT_A_BACKUP)
        }
        if (!root.has("formatVersion") || !root.has("accounts") || !root.has("transactions")) {
            throw BackupFormatException(BackupError.NOT_A_BACKUP)
        }
        val version = try {
            root.getInt("formatVersion")
        } catch (e: Exception) {
            throw BackupFormatException(BackupError.NOT_A_BACKUP)
        }
        if (version != FORMAT_VERSION) throw BackupFormatException(BackupError.UNSUPPORTED_VERSION)

        val file = try {
            BackupFile(
                formatVersion = version,
                appVersion = root.optString("appVersion", ""),
                exportedAt = root.optString("exportedAt", ""),
                accounts = root.getJSONArray("accounts").mapObjects(::accountFromJson),
                categories = root.getJSONArray("categories").mapObjects(::categoryFromJson),
                transactions = root.getJSONArray("transactions").mapObjects(::transactionFromJson),
            )
        } catch (e: Exception) {
            throw BackupFormatException(BackupError.INVALID_DATA)
        }
        validate(file)
        return file
    }

    /** Checks ids and references so a restore can never violate a foreign key. */
    fun validate(file: BackupFile) {
        fun invalid(): Nothing = throw BackupFormatException(BackupError.INVALID_DATA)

        if (file.formatVersion != FORMAT_VERSION) throw BackupFormatException(BackupError.UNSUPPORTED_VERSION)
        if (file.accounts.isEmpty()) invalid()
        val accountIds = file.accounts.map { it.id }
        val categoryIds = file.categories.map { it.id }
        val transactionIds = file.transactions.map { it.id }
        if (accountIds.any { it <= 0 } || categoryIds.any { it <= 0 } || transactionIds.any { it <= 0 }) invalid()
        if (accountIds.toSet().size != accountIds.size) invalid()
        if (categoryIds.toSet().size != categoryIds.size) invalid()
        if (transactionIds.toSet().size != transactionIds.size) invalid()
        val accountSet = accountIds.toSet()
        val categorySet = categoryIds.toSet()
        for (tx in file.transactions) {
            if (tx.accountId !in accountSet) invalid()
            if (tx.toAccountId != null && tx.toAccountId !in accountSet) invalid()
            if (tx.categoryId != null && tx.categoryId !in categorySet) invalid()
            if (tx.type == TransactionType.TRANSFER && (tx.toAccountId == null || tx.toAccountId == tx.accountId)) {
                invalid()
            }
            if (tx.type != TransactionType.ADJUSTMENT && tx.amountCents < 0) invalid()
        }
    }

    // ---- to JSON ----

    private fun JSONObject.putNullable(key: String, value: Long?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun accountToJson(a: BackupAccount) = JSONObject().apply {
        put("id", a.id)
        put("name", a.name)
        put("type", a.type.name)
        put("startingBalanceCents", a.startingBalanceCents)
        put("archived", a.archived)
        put("sortOrder", a.sortOrder)
        put("createdAt", a.createdAt)
    }

    private fun categoryToJson(c: BackupCategory) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("kind", c.kind.name)
        put("colorKey", c.colorKey)
        put("iconKey", c.iconKey)
        put("archived", c.archived)
        put("sortOrder", c.sortOrder)
    }

    private fun transactionToJson(t: BackupTransaction) = JSONObject().apply {
        put("id", t.id)
        put("type", t.type.name)
        put("amountCents", t.amountCents)
        put("date", t.date.toString())
        put("accountId", t.accountId)
        putNullable("toAccountId", t.toAccountId)
        putNullable("categoryId", t.categoryId)
        put("note", t.note)
        putNullable("debtId", t.debtId)
        putNullable("recurringId", t.recurringId)
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
    }

    // ---- from JSON ----

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun accountFromJson(o: JSONObject) = BackupAccount(
        id = o.getLong("id"),
        name = o.getString("name"),
        type = AccountType.valueOf(o.getString("type")),
        startingBalanceCents = o.getLong("startingBalanceCents"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        createdAt = o.getLong("createdAt"),
    )

    private fun categoryFromJson(o: JSONObject) = BackupCategory(
        id = o.getLong("id"),
        name = o.getString("name"),
        kind = CategoryKind.valueOf(o.getString("kind")),
        colorKey = o.getString("colorKey"),
        iconKey = o.getString("iconKey"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun transactionFromJson(o: JSONObject) = BackupTransaction(
        id = o.getLong("id"),
        type = TransactionType.valueOf(o.getString("type")),
        amountCents = o.getLong("amountCents"),
        date = LocalDate.parse(o.getString("date")),
        accountId = o.getLong("accountId"),
        toAccountId = o.nullableLong("toAccountId"),
        categoryId = o.nullableLong("categoryId"),
        note = o.getString("note"),
        debtId = o.nullableLong("debtId"),
        recurringId = o.nullableLong("recurringId"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
    )
}
