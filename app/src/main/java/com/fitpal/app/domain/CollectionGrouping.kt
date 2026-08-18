package com.fitpal.app.domain

import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity

/** A subcategory and the foods filed directly under it. */
data class CategorySection(val subcategory: GalleryCategoryEntity, val foods: List<GalleryFoodEntity>)

/** A top-level category group: its own foods + collapsible subcategory sections (null = "Unsorted"). */
data class CategoryGroup(
    val category: GalleryCategoryEntity?,
    val directFoods: List<GalleryFoodEntity>,
    val sections: List<CategorySection>
)

/**
 * The ready-to-render view of saved foods, grouped by category. When [selectedCategoryId] is null
 * everything is nested under [groups] ("All"); otherwise [directFoods] + [sections] hold just the
 * selected category. Shared by the Collection tab and the "log from collection" picker so both show
 * the same category / subcategory layout.
 */
data class CollectionView(
    val selectedCategoryId: Long?,
    val directFoods: List<GalleryFoodEntity>,
    val sections: List<CategorySection>,
    val groups: List<CategoryGroup>
)

object CollectionGrouping {
    /** Group [foods] under [categories]; pass a [selectedCategoryId] to narrow to one category. */
    fun build(
        foods: List<GalleryFoodEntity>,
        categories: List<GalleryCategoryEntity>,
        selectedCategoryId: Long?
    ): CollectionView {
        if (selectedCategoryId == null) {
            val topCats = categories.filter { it.parentId == null }
            val knownIds = categories.map { it.id }.toSet()
            val groups = buildList {
                topCats.forEach { top ->
                    val subs = categories.filter { it.parentId == top.id }
                    val direct = foods.filter { it.categoryId == top.id }
                    val sections = subs
                        .map { sub -> CategorySection(sub, foods.filter { it.categoryId == sub.id }) }
                        .filter { it.foods.isNotEmpty() }
                    // Only show a category header if it actually holds something.
                    if (direct.isNotEmpty() || sections.isNotEmpty()) add(CategoryGroup(top, direct, sections))
                }
                // Anything unsorted (or filed under a deleted category) collects in one group at the end.
                val unsorted = foods.filter { it.categoryId == null || it.categoryId !in knownIds }
                if (unsorted.isNotEmpty()) add(CategoryGroup(null, unsorted, emptyList()))
            }
            return CollectionView(null, emptyList(), emptyList(), groups)
        }
        val subs = categories.filter { it.parentId == selectedCategoryId }
        val direct = foods.filter { it.categoryId == selectedCategoryId }
        val sections = subs.map { sub -> CategorySection(sub, foods.filter { it.categoryId == sub.id }) }
        return CollectionView(selectedCategoryId, direct, sections, emptyList())
    }
}
