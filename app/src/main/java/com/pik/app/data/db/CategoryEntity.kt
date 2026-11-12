package com.pik.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pik.app.domain.CategoryKind

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    /** One of the palette keys in ui.theme.CategoryStyle. */
    val colorKey: String,
    /** Maps to a Material icon in ui.theme.CategoryStyle. */
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)
