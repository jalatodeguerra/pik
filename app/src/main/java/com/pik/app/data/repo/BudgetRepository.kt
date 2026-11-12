package com.pik.app.data.repo

import androidx.room.withTransaction
import com.pik.app.data.db.BudgetDao
import com.pik.app.data.db.BudgetEntity
import com.pik.app.data.db.PikDatabase
import com.pik.app.domain.BudgetCalculator
import com.pik.app.domain.BudgetFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val db: PikDatabase,
    private val dao: BudgetDao,
) {
    /** Every budget row (all categories, all start periods). */
    fun observeBudgets(): Flow<List<BudgetFact>> =
        dao.observeAll().map { rows -> rows.map { BudgetFact(it.categoryId, it.limitCents, it.startPeriod) } }

    /**
     * Applies [limits] (categoryId, null = overall, to limit in cents; 0 = no budget) from the
     * period [periodKey] on. Earlier periods keep their limits. A value equal to the limit already
     * in force is not written. Inserts or updates the row for exactly (categoryId, periodKey), so
     * there is never a duplicate, including for the overall budget (null category).
     * All writes happen in one transaction.
     */
    suspend fun saveLimitsFrom(periodKey: String, limits: Map<Long?, Long>) {
        db.withTransaction {
            val inForce = BudgetCalculator.effectiveLimits(
                dao.getAll().map { BudgetFact(it.categoryId, it.limitCents, it.startPeriod) },
                periodKey,
            )
            val now = System.currentTimeMillis()
            for ((categoryId, rawLimit) in limits) {
                val limit = rawLimit.coerceAtLeast(0L)
                if (limit == (inForce[categoryId] ?: 0L)) continue
                val existing = dao.find(categoryId, periodKey)
                if (existing != null) {
                    dao.update(existing.copy(limitCents = limit))
                } else {
                    dao.insert(
                        BudgetEntity(
                            categoryId = categoryId,
                            limitCents = limit,
                            startPeriod = periodKey,
                            createdAt = now,
                        ),
                    )
                }
            }
        }
    }
}
