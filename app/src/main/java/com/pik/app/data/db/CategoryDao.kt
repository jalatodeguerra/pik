package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pik.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow

/** A category and how many transactions use it. */
data class CategoryWithUsage(
    @Embedded val category: CategoryEntity,
    val usageCount: Int,
)

@Dao
interface CategoryDao {

    /** Non-archived categories of one kind: most used first, then sortOrder. */
    @Query(
        """
        SELECT c.* FROM categories c
        WHERE c.kind = :kind AND c.archived = 0
        ORDER BY (SELECT COUNT(*) FROM transactions t WHERE t.categoryId = c.id) DESC,
                 c.sortOrder ASC, c.id ASC
        """,
    )
    fun observeByUsage(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    /** All categories of a kind (archived last) with their usage, for the management screen. */
    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM transactions t WHERE t.categoryId = c.id) AS usageCount
        FROM categories c
        WHERE c.kind = :kind
        ORDER BY c.archived ASC, c.sortOrder ASC, c.id ASC
        """,
    )
    fun observeWithUsage(kind: CategoryKind): Flow<List<CategoryWithUsage>>

    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE kind = :kind")
    suspend fun maxSortOrder(kind: CategoryKind): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun usageCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM categories WHERE kind = :kind AND archived = 0")
    suspend fun activeCount(kind: CategoryKind): Int

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
