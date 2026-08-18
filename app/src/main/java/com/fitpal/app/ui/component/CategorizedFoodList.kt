package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.domain.CollectionView
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight

/**
 * The category / subcategory list of saved foods, with collapsible headers. Shared by the Collection
 * tab and the "log from collection" picker so both look identical; each caller supplies its own
 * [foodCard] (the Collection tab opens a food's detail, the picker logs it). [collapsed] holds the
 * folded header keys ("cat_<id>", "sub_<id>", "unsorted"); [onToggleCollapsed] flips one.
 */
@Composable
fun CategorizedFoodList(
    view: CollectionView,
    collapsed: Set<String>,
    onToggleCollapsed: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    foodCard: @Composable (GalleryFoodEntity) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (view.selectedCategoryId == null) {
            // "All" — nest everything under collapsible category -> subcategory headers.
            view.groups.forEach { group ->
                val topKey = group.category?.let { "cat_${it.id}" } ?: "unsorted"
                val topCollapsed = topKey in collapsed
                val total = group.directFoods.size + group.sections.sumOf { it.foods.size }
                item(key = "grp_$topKey") {
                    CategoryGroupHeader(
                        name = group.category?.name ?: "Unsorted",
                        count = total,
                        collapsed = topCollapsed,
                        onToggle = { onToggleCollapsed(topKey) }
                    )
                }
                if (!topCollapsed) {
                    items(group.directFoods, key = { "gd_${it.id}" }) { food -> foodCard(food) }
                    group.sections.forEach { section ->
                        val subKey = "sub_${section.subcategory.id}"
                        val subCollapsed = subKey in collapsed
                        item(key = "gsec_$subKey") {
                            SubcategoryHeader(
                                name = section.subcategory.name,
                                count = section.foods.size,
                                collapsed = subCollapsed,
                                indent = true,
                                onToggle = { onToggleCollapsed(subKey) }
                            )
                        }
                        if (!subCollapsed) {
                            items(section.foods, key = { "gs_${it.id}" }) { food -> foodCard(food) }
                        }
                    }
                }
            }
        } else {
            // A specific category selected — its direct foods, then subcategory sections.
            items(view.directFoods, key = { it.id }) { food -> foodCard(food) }
            view.sections.forEach { section ->
                val subKey = "sub_${section.subcategory.id}"
                val subCollapsed = subKey in collapsed
                item(key = "sec_$subKey") {
                    SubcategoryHeader(
                        name = section.subcategory.name,
                        count = section.foods.size,
                        collapsed = subCollapsed,
                        onToggle = { onToggleCollapsed(subKey) }
                    )
                }
                if (!subCollapsed) {
                    items(section.foods, key = { "sub_${it.id}" }) { food -> foodCard(food) }
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Cream, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = GoldLight, modifier = Modifier.weight(1f))
        Icon(
            if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = CreamMuted
        )
    }
}

@Composable
private fun SubcategoryHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit, indent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(start = if (indent) 12.dp else 0.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = Cream, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text("($count)", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f))
        Icon(
            if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = CreamMuted
        )
    }
}
