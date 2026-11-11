package com.pik.app.data.repo

import com.pik.app.data.db.CategoryDao
import com.pik.app.data.db.CategoryEntity
import com.pik.app.data.db.CategoryWithUsage
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

    /** All categories of a kind (archived last) with their usage count. */
    fun observeWithUsage(kind: CategoryKind): Flow<List<CategoryWithUsage>> = dao.observeWithUsage(kind)

    /** Every category of both kinds, archived included (names and icons for budgets). */
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    /** Idempotent: only seeds when there are no categories yet. */
    suspend fun ensureDefaultCategories() {
        if (dao.count() == 0) {
            dao.insertAll(DefaultCategories.forLocale())
        }
    }

    suspend fun addCategory(name: String, kind: CategoryKind, iconKey: String, colorKey: String): Long =
        dao.insert(
            CategoryEntity(
                name = name.trim(),
                kind = kind,
                colorKey = colorKey,
                iconKey = iconKey,
                sortOrder = dao.maxSortOrder(kind) + 1,
            ),
        )

    suspend fun updateCategory(id: Long, name: String, iconKey: String, colorKey: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name.trim(), iconKey = iconKey, colorKey = colorKey))
    }

    /** Refuses to archive the last active category of a kind. */
    suspend fun setArchived(id: Long, archived: Boolean): Boolean {
        val existing = dao.getById(id) ?: return false
        if (archived && !existing.archived && dao.activeCount(existing.kind) <= 1) return false
        dao.update(existing.copy(archived = archived))
        return true
    }

    /** Deletes only a category no transaction uses. Returns false when it is in use. */
    suspend fun deleteIfUnused(id: Long): Boolean {
        if (dao.usageCount(id) > 0) return false
        dao.deleteById(id)
        return true
    }
}
