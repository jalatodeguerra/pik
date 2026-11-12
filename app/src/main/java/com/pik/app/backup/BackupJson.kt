package com.pik.app.backup

import com.pik.app.domain.AccountType
import com.pik.app.domain.Apr
import com.pik.app.domain.CategoryKind
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtType
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.Period
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.TransactionType
import com.pik.app.domain.importing.ImportSource
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Backup file format: one JSON object with the header fields and one array per table holding
 * every column of every row. Dates are ISO yyyy-MM-dd, money is integer cents.
 * formatVersion 2 added the "budgets" array; formatVersion 3 added the "debts" and "debtEntries"
 * arrays and the optional "settings" object. formatVersion 4 added taegBasisPoints, type,
 * totalInstallments and installmentsPaidBefore to every debt (aprBasisPoints is the TAN). formatVersion 5
 * added the "recurringRules" array. formatVersion 6 added the "merchantRules" array (learned categories of the
 * automatic import) and the "importApps" array (which payment apps are read, and for which account); pending
 * inbox items are not backed up. Version 1 to 5 files can still be read: the missing fields and arrays get
 * their defaults (no recurring rules, no merchant rules, no import apps).
 */
object BackupJson {
    const val FORMAT_VERSION = 6
    private const val OLDEST_SUPPORTED_VERSION = 1
    const val MAX_INSTALLMENTS = 1200
    const val MAX_RECURRING_INTERVAL = 120
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
            root.put("debts", JSONArray().also { arr -> file.debts.forEach { arr.put(debtToJson(it, file.formatVersion)) } })
            root.put(
                "debtEntries",
                JSONArray().also { arr -> file.debtEntries.forEach { arr.put(debtEntryToJson(it)) } },
            )
            file.settings?.let { root.put("settings", settingsToJson(it)) }
        }
        if (file.formatVersion >= 5) {
            root.put(
                "recurringRules",
                JSONArray().also { arr -> file.recurringRules.forEach { arr.put(recurringRuleToJson(it)) } },
            )
        }
        if (file.formatVersion >= 6) {
            root.put(
                "merchantRules",
                JSONArray().also { arr -> file.merchantRules.forEach { arr.put(merchantRuleToJson(it)) } },
            )
            root.put("importApps", JSONArray().also { arr -> file.importApps.forEach { arr.put(importAppToJson(it)) } })
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
                debts = if (version >= 3) root.getJSONArray("debts").mapObjects { debtFromJson(it, version) } else emptyList(),
                debtEntries = if (version >= 3) {
                    root.getJSONArray("debtEntries").mapObjects(::debtEntryFromJson)
                } else {
                    emptyList()
                },
                recurringRules = if (version >= 5) {
                    root.getJSONArray("recurringRules").mapObjects(::recurringRuleFromJson)
                } else {
                    emptyList()
                },
                merchantRules = if (version >= 6) {
                    root.getJSONArray("merchantRules").mapObjects(::merchantRuleFromJson)
                } else {
                    emptyList()
                },
                importApps = if (version >= 6) {
                    root.getJSONArray("importApps").mapObjects(::importAppFromJson)
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
        if (file.formatVersion < 4 && file.debts.any {
                it.taegBasisPoints != null || it.type != DebtType.OTHER ||
                    it.totalInstallments != null || it.installmentsPaidBefore != null
            }
        ) {
            invalid()
        }
        if (file.formatVersion < 5 && file.recurringRules.isNotEmpty()) invalid()
        if (file.formatVersion < 6 && (file.merchantRules.isNotEmpty() || file.importApps.isNotEmpty())) invalid()
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
            if (debt.taegBasisPoints != null && debt.taegBasisPoints !in 0..Apr.MAX_BASIS_POINTS) invalid()
            if (debt.totalInstallments != null && debt.totalInstallments !in 1..MAX_INSTALLMENTS) invalid()
            if (debt.installmentsPaidBefore != null &&
                (debt.installmentsPaidBefore !in 0..MAX_INSTALLMENTS ||
                    (debt.totalInstallments != null && debt.installmentsPaidBefore > debt.totalInstallments))
            ) {
                invalid()
            }
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
        val ruleIds = file.recurringRules.map { it.id }
        if (ruleIds.any { it <= 0 } || ruleIds.toSet().size != ruleIds.size) invalid()
        for (r in file.recurringRules) {
            if (r.accountId !in accountSet) invalid()
            if (r.categoryId != null && r.categoryId !in categorySet) invalid()
            if (r.debtId != null && r.debtId !in debtSet) invalid()
            if (r.amountCents < 0) invalid()
            if (r.intervalCount !in 1..MAX_RECURRING_INTERVAL) invalid()
            if (r.dayOfMonth != null && r.dayOfMonth !in 1..31) invalid()
            if (r.maxOccurrences != null && r.maxOccurrences < 1) invalid()
            if (r.occurrencesDone < 0) invalid()
            // INCOME rules make INCOME transactions, every other kind makes EXPENSE transactions.
            val expectedType = if (r.kind == RecurringKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
            if (r.transactionType != expectedType) invalid()
            // A debt payment points to its debt (and has no category); nothing else points to a debt.
            if ((r.kind == RecurringKind.DEBT_PAYMENT) != (r.debtId != null)) invalid()
            if (r.kind == RecurringKind.DEBT_PAYMENT && r.categoryId != null) invalid()
        }
        val ruleKeys = file.merchantRules.map { it.merchantKey }
        if (ruleKeys.any { it.isBlank() } || ruleKeys.toSet().size != ruleKeys.size) invalid()
        for (rule in file.merchantRules) {
            if (rule.categoryId !in categorySet) invalid()
        }
        val packages = file.importApps.map { it.packageName }
        if (packages.any { it.isBlank() || it.contains('|') } || packages.toSet().size != packages.size) invalid()
        for (app in file.importApps) {
            if (app.accountId != null && app.accountId !in accountSet) invalid()
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

    private fun JSONObject.putNullableInt(key: String, value: Int?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun debtToJson(d: BackupDebt, formatVersion: Int) = JSONObject().apply {
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
        if (formatVersion >= 4) {
            putNullableInt("taegBasisPoints", d.taegBasisPoints)
            put("type", d.type.name)
            putNullableInt("totalInstallments", d.totalInstallments)
            putNullableInt("installmentsPaidBefore", d.installmentsPaidBefore)
        }
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

    private fun recurringRuleToJson(r: BackupRecurringRule) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("kind", r.kind.name)
        put("transactionType", r.transactionType.name)
        put("amountCents", r.amountCents)
        put("accountId", r.accountId)
        putNullable("categoryId", r.categoryId)
        putNullable("debtId", r.debtId)
        put("note", r.note)
        put("startDate", r.startDate.toString())
        put("intervalCount", r.intervalCount)
        put("intervalUnit", r.intervalUnit.name)
        putNullableInt("dayOfMonth", r.dayOfMonth)
        put("endDate", r.endDate?.toString() ?: JSONObject.NULL)
        putNullableInt("maxOccurrences", r.maxOccurrences)
        put("occurrencesDone", r.occurrencesDone)
        put("nextDueDate", r.nextDueDate.toString())
        put("remindDayBefore", r.remindDayBefore)
        put("active", r.active)
        put("createdAt", r.createdAt)
    }

    private fun merchantRuleToJson(r: BackupMerchantRule) = JSONObject().apply {
        put("merchantKey", r.merchantKey)
        put("categoryId", r.categoryId)
        put("updatedAt", r.updatedAt)
    }

    private fun importAppToJson(a: BackupImportApp) = JSONObject().apply {
        put("packageName", a.packageName)
        put("source", a.source.name)
        putNullable("accountId", a.accountId)
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

    private fun JSONObject.nullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private fun debtFromJson(o: JSONObject, version: Int) = BackupDebt(
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
        // Version 4 fields: older files simply do not have them (defaults).
        taegBasisPoints = if (version >= 4) o.nullableInt("taegBasisPoints") else null,
        type = if (version >= 4 && o.has("type")) DebtType.valueOf(o.getString("type")) else DebtType.OTHER,
        totalInstallments = if (version >= 4) o.nullableInt("totalInstallments") else null,
        installmentsPaidBefore = if (version >= 4) o.nullableInt("installmentsPaidBefore") else null,
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

    private fun recurringRuleFromJson(o: JSONObject) = BackupRecurringRule(
        id = o.getLong("id"),
        name = o.getString("name"),
        kind = RecurringKind.valueOf(o.getString("kind")),
        transactionType = TransactionType.valueOf(o.getString("transactionType")),
        amountCents = o.getLong("amountCents"),
        accountId = o.getLong("accountId"),
        categoryId = o.nullableLong("categoryId"),
        debtId = o.nullableLong("debtId"),
        note = o.getString("note"),
        startDate = LocalDate.parse(o.getString("startDate")),
        intervalCount = o.getInt("intervalCount"),
        intervalUnit = RecurrenceUnit.valueOf(o.getString("intervalUnit")),
        dayOfMonth = o.nullableInt("dayOfMonth"),
        endDate = if (o.isNull("endDate")) null else LocalDate.parse(o.getString("endDate")),
        maxOccurrences = o.nullableInt("maxOccurrences"),
        occurrencesDone = o.getInt("occurrencesDone"),
        nextDueDate = LocalDate.parse(o.getString("nextDueDate")),
        remindDayBefore = o.getBoolean("remindDayBefore"),
        active = o.getBoolean("active"),
        createdAt = o.getLong("createdAt"),
    )

    private fun merchantRuleFromJson(o: JSONObject) = BackupMerchantRule(
        merchantKey = o.getString("merchantKey"),
        categoryId = o.getLong("categoryId"),
        updatedAt = o.getLong("updatedAt"),
    )

    private fun importAppFromJson(o: JSONObject) = BackupImportApp(
        packageName = o.getString("packageName"),
        source = ImportSource.valueOf(o.getString("source")),
        accountId = o.nullableLong("accountId"),
    )

    private fun settingsFromJson(o: JSONObject) = BackupSettings(
        monthStartDay = if (o.has("monthStartDay")) o.getInt("monthStartDay") else 1,
        expectedIncomeCents = o.nullableLong("expectedIncomeCents"),
        extraPerMonthCents = if (o.has("extraPerMonthCents")) o.getLong("extraPerMonthCents") else 0L,
        payoffOrder = if (o.has("payoffOrder")) PayoffOrder.valueOf(o.getString("payoffOrder")) else PayoffOrder.HIGHEST_INTEREST,
    )
}
