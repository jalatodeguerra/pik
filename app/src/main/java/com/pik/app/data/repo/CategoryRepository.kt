package com.pik.app.data.repo

import com.pik.app.data.db.CategoryDao
import com.pik.app.data.db.CategoryEntity
import com.pik.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) {
    /** Non-archived categories of a kind, most used first. */
    fun observeByUsage(kind: CategoryKind): Flow<List<CategoryEntity>> = dao.observeByUsage(kind)

    /** Idempotent: only seeds when there are no categories yet. */
    suspend fun ensureDefaultCategories() {
        if (dao.count() == 0) {
            dao.insertAll(DefaultCategories.all)
        }
    }
}
