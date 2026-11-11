package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.Apr
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.Period
import com.pik.app.domain.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Backup file format: one JSON object with the header fields and one array per table holding
 * every column of every row. Dates are ISO yyyy-MM-dd, money is integer cents.
 * formatVersion 2 added the "budgets" array; formatVersion 3 added the "debts" and "debtEntries"
 * arrays and the optional "settings" object. Version 1 and 2 files can still be read.
 */
object BackupJson {
    const val FORMAT_VERSION = 3
    private const val OLDEST_SUPPORTED_VERSION = 1
    private val PERIOD_KEY = Regex("""\d{4}-(0[1-9]|1[0-2])""")

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
        if (file.formatVersion >= 2) {
            root.put("budgets", JSONArray().also { arr -> file.budgets.forEach { arr.put(budgetToJson(it)) } })
        }
        if (file.formatVersion >= 3) {
            root.put("debts", JSONArray().also { arr -> file.debts.forEach { arr.put(debtToJson(it)) } })
            root.put(
                "debtEntries",
                JSONArray().also { arr -> file.debtEntries.forEach { arr.put(debtEntryToJson(it)) } },
            )
            file.settings?.let { root.put("settings", settingsToJson(it)) }
        }
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
        if (version !in OLDEST_SUPPORTED_VERSION..FORMAT_VERSION) {
            throw BackupFormatException(BackupError.UNSUPPORTED_VERSION)
        }

        val file = try {
            BackupFile(
                formatVersion = version,
                appVersion = root.optString("appVersion", ""),
                exportedAt = root.optString("exportedAt", ""),
                accounts = root.getJSONArray("accounts").mapObjects(::accountFromJson),
                categories = root.getJSONArray("categories").mapObjects(::categoryFromJson),
                transactions = root.getJSONArray("transactions").mapObjects(::transactionFromJson),
                budgets = if (version >= 2) root.getJSONArray("budgets").mapObjects(::budgetFromJson) else emptyList(),
                debts = if (version >= 3) root.getJSONArray("debts").mapObjects(::debtFromJson) else emptyList(),
                debtEntries = if (version >= 3) {
                    root.getJSONArray("debtEntries").mapObjects(::debtEntryFromJson)
                } else {
                    emptyList()
                },
                settings = if (version >= 3 && root.has("settings") && !root.isNull("settings")) {
                    settingsFromJson(root.getJSONObject("settings"))
                } else {
                    null
                },
            )
        } catch (e: Exception) {
            throw BackupFormatException(BackupError.INVALID_DATA)
        }
        // Before version 3 nothing could point to a debt (there were no debts), so a stray debtId
        // in an old file is dropped instead of leaving a dangling reference.
        val clean = if (version < 3) {
            file.copy(transactions = file.transactions.map { if (it.debtId != null) it.copy(debtId = null) else it })
        } else {
            file
        }
        validate(clean)
        return clean
    }

    /** Checks ids and references so a restore can never violate a foreign key. */
    fun validate(file: BackupFile) {
        fun invalid(): Nothing = throw BackupFormatException(BackupError.INVALID_DATA)

        if (file.formatVersion !in OLDEST_SUPPORTED_VERSION..FORMAT_VERSION) {
            throw BackupFormatException(BackupError.UNSUPPORTED_VERSION)
        }
        if (file.formatVersion < 2 && file.budgets.isNotEmpty()) invalid()
        if (file.formatVersion < 3 && (file.debts.isNotEmpty() || file.debtEntries.isNotEmpty() || file.settings != null)) {
            invalid()
        }
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
        val debtIds = file.debts.map { it.id }
        if (debtIds.any { it <= 0 } || debtIds.toSet().size != debtIds.size) invalid()
        val debtSet = debtIds.toSet()
        for (debt in file.debts) {
            if (debt.startingBalanceCents < 0) invalid()
            if (debt.originalAmountCents != null && debt.originalAmountCents < 0) invalid()
            if (debt.aprBasisPoints !in 0..Apr.MAX_BASIS_POINTS) invalid()
            if (debt.minPaymentCents < 0) invalid()
            if (debt.dueDay !in 1..28) invalid()
        }
        val entryIds = file.debtEntries.map { it.id }
        if (entryIds.any { it <= 0 } || entryIds.toSet().size != entryIds.size) invalid()
        if (file.debtEntries.any { it.debtId !in debtSet }) invalid()
        file.settings?.let { st ->
            if (st.monthStartDay !in Period.MIN_START_DAY..Period.MAX_START_DAY) invalid()
            if (st.extraPerMonthCents < 0) invalid()
            if (st.expectedIncomeCents != null && st.expectedIncomeCents < 0) invalid()
        }
        for (tx in file.transactions) {
            if (tx.debtId != null && tx.debtId !in debtSet) invalid()
            if (tx.accountId !in accountSet) invalid()
            if (tx.toAccountId != null && tx.toAccountId !in accountSet) invalid()
            if (tx.categoryId != null && tx.categoryId !in categorySet) invalid()
            if (tx.type == TransactionType.TRANSFER && (tx.toAccountId == null || tx.toAccountId == tx.accountId)) {
                invalid()
            }
            if (tx.type != TransactionType.ADJUSTMENT && tx.amountCents < 0) invalid()
        }
        val budgetIds = file.budgets.map { it.id }
        if (budgetIds.any { it <= 0 } || budgetIds.toSet().size != budgetIds.size) invalid()
        // One row per (category, month); the overall budget (null category) counts as one category.
        if (file.budgets.map { it.categoryId to it.startPeriod }.toSet().size != file.budgets.size) invalid()
        for (b in file.budgets) {
            if (b.categoryId != null && b.categoryId !in categorySet) invalid()
            if (b.limitCents < 0) invalid()
            if (!PERIOD_KEY.matches(b.startPeriod)) invalid()
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

    private fun budgetToJson(b: BackupBudget) = JSONObject().apply {
        put("id", b.id)
        putNullable("categoryId", b.categoryId)
        put("limitCents", b.limitCents)
        put("startPeriod", b.startPeriod)
        put("createdAt", b.createdAt)
    }

    private fun debtToJson(d: BackupDebt) = JSONObject().apply {
        put("id", d.id)
        put("name", d.name)
        put("startingBalanceCents", d.startingBalanceCents)
        putNullable("originalAmountCents", d.originalAmountCents)
        put("aprBasisPoints", d.aprBasisPoints)
        put("minPaymentCents", d.minPaymentCents)
        put("dueDay", d.dueDay)
        put("archived", d.archived)
        put("sortOrder", d.sortOrder)
        put("createdAt", d.createdAt)
    }

    private fun debtEntryToJson(e: BackupDebtEntry) = JSONObject().apply {
        put("id", e.id)
        put("debtId", e.debtId)
        put("type", e.type.name)
        put("amountCents", e.amountCents)
        put("date", e.date.toString())
        put("note", e.note)
        put("createdAt", e.createdAt)
    }

    private fun settingsToJson(s: BackupSettings) = JSONObject().apply {
        put("monthStartDay", s.monthStartDay)
        putNullable("expectedIncomeCents", s.expectedIncomeCents)
        put("extraPerMonthCents", s.extraPerMonthCents)
        put("payoffOrder", s.payoffOrder.name)
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

    private fun budgetFromJson(o: JSONObject) = BackupBudget(
        id = o.getLong("id"),
        categoryId = o.nullableLong("categoryId"),
        limitCents = o.getLong("limitCents"),
        startPeriod = o.getString("startPeriod"),
        createdAt = o.getLong("createdAt"),
    )

    private fun debtFromJson(o: JSONObject) = BackupDebt(
        id = o.getLong("id"),
        name = o.getString("name"),
        startingBalanceCents = o.getLong("startingBalanceCents"),
        originalAmountCents = o.nullableLong("originalAmountCents"),
        aprBasisPoints = o.getInt("aprBasisPoints"),
        minPaymentCents = o.getLong("minPaymentCents"),
        dueDay = o.getInt("dueDay"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        createdAt = o.getLong("createdAt"),
    )

    private fun debtEntryFromJson(o: JSONObject) = BackupDebtEntry(
        id = o.getLong("id"),
        debtId = o.getLong("debtId"),
        type = DebtEntryType.valueOf(o.getString("type")),
        amountCents = o.getLong("amountCents"),
        date = LocalDate.parse(o.getString("date")),
        note = o.getString("note"),
        createdAt = o.getLong("createdAt"),
    )

    private fun settingsFromJson(o: JSONObject) = BackupSettings(
        monthStartDay = if (o.has("monthStartDay")) o.getInt("monthStartDay") else 1,
        expectedIncomeCents = o.nullableLong("expectedIncomeCents"),
        extraPerMonthCents = if (o.has("extraPerMonthCents")) o.getLong("extraPerMonthCents") else 0L,
        payoffOrder = if (o.has("payoffOrder")) PayoffOrder.valueOf(o.getString("payoffOrder")) else PayoffOrder.HIGHEST_INTEREST,
    )
}
