package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created folder for organising saved foods. Two levels:
 * - a top-level **category** has [parentId] == null and shows as a tab in the collection;
 * - a **subcategory** has [parentId] set to its parent category's id and shows as a collapsible
 *   section inside that tab.
 *
 * A saved food points at exactly one of these via [GalleryFoodEntity.categoryId] (a top category
 * or a subcategory), or null for "unsorted".
 */
@Entity(tableName = "gallery_categories")
data class GalleryCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** null = top-level category (a tab); otherwise the id of the parent category. */
    val parentId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
