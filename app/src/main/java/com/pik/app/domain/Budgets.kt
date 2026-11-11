package com.pik.app.domain

/** A budget row as the domain sees it. [categoryId] null is the overall budget. */
data class BudgetFact(val categoryId: Long?, val limitCents: Long, val startPeriod: String)

/**
 * Money spent (EXPENSE transactions) in one category in a period. [categoryId] null = uncategorised,
 * unless [isDebtPayment]: then it is the "Debt payments" bucket (expenses with a debtId and no category).
 */
data class CategorySpend(val categoryId: Long?, val spentCents: Long, val isDebtPayment: Boolean = false)

enum class BudgetStatus { OK, WARNING, OVER }

data class BudgetProgress(
    val spentCents: Long,
    val limitCents: Long,
    /** limit - spent: negative when over budget. */
    val leftCents: Long,
    /** spent / limit clamped to 0..1, for a progress bar. */
    val fraction: Float,
    val status: BudgetStatus,
)

data class BudgetLine(val categoryId: Long, val progress: BudgetProgress)

data class BudgetOverview(
    val overall: BudgetProgress?,
    /** Categories that have a limit, most used first. */
    val lines: List<BudgetLine>,
    /** Categories (or null = uncategorised) with spending but no limit, biggest first. */
    val notBudgeted: List<CategorySpend>,
) {
    val hasAnyBudget: Boolean get() = overall != null || lines.isNotEmpty()
}

object BudgetCalculator {
    /** Percent of the limit at which the bar turns amber. */
    const val WARNING_PERCENT = 80

    /**
     * The limit in force for [periodKey] ("yyyy-MM") for each category: the row with the latest
     * startPeriod <= periodKey. A limit of 0 (or less) means "no budget" and is left out.
     */
    fun effectiveLimits(budgets: List<BudgetFact>, periodKey: String): Map<Long?, Long> {
        val latest = HashMap<Long?, BudgetFact>()
        for (b in budgets) {
            if (b.startPeriod > periodKey) continue
            val current = latest[b.categoryId]
            if (current == null || b.startPeriod > current.startPeriod) latest[b.categoryId] = b
        }
        return latest.filterValues { it.limitCents > 0 }.mapValues { it.value.limitCents }
    }

    fun progress(spentCents: Long, limitCents: Long): BudgetProgress {
        val status = when {
            spentCents > limitCents -> BudgetStatus.OVER
            limitCents > 0 && spentCents * 100 >= limitCents * WARNING_PERCENT -> BudgetStatus.WARNING
            else -> BudgetStatus.OK
        }
        val fraction = if (limitCents <= 0) 0f else (spentCents.toDouble() / limitCents).coerceIn(0.0, 1.0).toFloat()
        return BudgetProgress(spentCents, limitCents, limitCents - spentCents, fraction, status)
    }

    /**
     * Sums EXPENSE transactions per category. Income, transfers and adjustments are ignored.
     * Debt payments (debtId set) go to their own bucket, separate from "Uncategorised"; they
     * still count in the overall total.
     */
    fun spendByCategory(transactions: List<TxFacts>): List<CategorySpend> {
        val sums = LinkedHashMap<Pair<Long?, Boolean>, Long>()
        for (tx in transactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            val key = if (tx.debtId != null) null to true else tx.categoryId to false
            sums[key] = (sums[key] ?: 0L) + tx.amountCents
        }
        return sums.map { CategorySpend(it.key.first, it.value, it.key.second) }
    }

    /** [limits] comes from [effectiveLimits]; the overall budget is the entry with a null key. */
    fun overview(limits: Map<Long?, Long>, spend: List<CategorySpend>): BudgetOverview {
        val totalSpent = spend.sumOf { it.spentCents }
        val spentById = spend.filter { !it.isDebtPayment }.associate { it.categoryId to it.spentCents }
        val overall = limits[null]?.let { progress(totalSpent, it) }
        val lines = limits.entries
            .filter { it.key != null }
            .map { BudgetLine(it.key!!, progress(spentById[it.key] ?: 0L, it.value)) }
            .sortedWith(compareByDescending<BudgetLine> { it.progress.spentCents * 1000 / it.progress.limitCents }
                .thenBy { it.categoryId })
        val notBudgeted = spend
            .filter { it.spentCents > 0 && (it.isDebtPayment || it.categoryId == null || it.categoryId !in limits) }
            .sortedByDescending { it.spentCents }
        return BudgetOverview(overall, lines, notBudgeted)
    }
}
