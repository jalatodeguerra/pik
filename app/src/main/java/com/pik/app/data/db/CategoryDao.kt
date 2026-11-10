package com.pik.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pik.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow

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
}
