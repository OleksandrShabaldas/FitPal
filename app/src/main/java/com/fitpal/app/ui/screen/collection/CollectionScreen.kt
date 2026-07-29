@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitpal.app.ui.screen.collection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import java.io.File

@Composable
fun CollectionScreen(
    onOpenFood: (Long) -> Unit = {},
    onSwipeToAnalytics: () -> Unit = {},
    onSwipeToSettings: () -> Unit = {},
    viewModel: CollectionViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val view by viewModel.collectionView.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val savedWorkouts by viewModel.savedWorkouts.collectAsStateWithLifecycle()
    val logConfirmation by viewModel.logConfirmation.collectAsStateWithLifecycle()

    var editingFood by remember { mutableStateOf<GalleryFoodEntity?>(null) }
    var nameText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var assigningFood by remember { mutableStateOf<GalleryFoodEntity?>(null) }
    var showManageCategories by remember { mutableStateOf(false) }

    // Which category / subcategory sections are collapsed (default: expanded). Keyed by a string so
    // top categories ("cat_<id>"), subcategories ("sub_<id>") and "unsorted" never collide. The set
    // is persisted in settings, so folding a category shut survives an app restart.
    val collapsed by viewModel.collapsedSections.collectAsStateWithLifecycle()

    // Confirm a saved-workout log with a toast (item: clearer "it was logged" feedback).
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(logConfirmation) {
        logConfirmation?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearLogConfirmation()
        }
    }

    var thumbnailFoodId by remember { mutableStateOf<Long?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && thumbnailFoodId != null) viewModel.setThumbnail(thumbnailFoodId!!, uri)
        thumbnailFoodId = null
    }

    GradientBackdrop(theme = BackdropTheme.GARDEN) {
        Column(
            modifier = Modifier.fillMaxSize().swipeNavigation(
                onSwipeLeft = onSwipeToSettings,
                onSwipeRight = onSwipeToAnalytics
            )
        ) {
            GlassTopBar(title = "My collection")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CollectionTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (tab) {
                                    CollectionTab.FOOD -> Icons.Default.Restaurant
                                    CollectionTab.EXERCISE -> Icons.Default.FitnessCenter
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                CollectionTab.FOOD -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = {
                            Text(
                                if (selectedCategoryId == null) "Search foods..."
                                else "Search in this category..."
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    CategoryTabs(
                        categories = topCategories,
                        selectedId = selectedCategoryId,
                        onSelect = viewModel::selectCategory,
                        onManage = { showManageCategories = true }
                    )

                    // One place to build a food card, reused by every list path below.
                    val foodCard: @Composable (GalleryFoodEntity) -> Unit = { food ->
                        CollectionFoodCard(
                            food = food,
                            onOpen = { onOpenFood(food.id) },
                            onDelete = { viewModel.deleteFood(food) },
                            onEdit = { editingFood = food; nameText = food.name; notesText = food.notes },
                            onAddThumbnail = { thumbnailFoodId = food.id; imagePicker.launch("image/*") },
                            onAssign = { assigningFood = food }
                        )
                    }

                    val isEmpty = if (selectedCategoryId == null) view.groups.isEmpty()
                        else view.directFoods.isEmpty() && view.sections.all { it.foods.isEmpty() }
                    if (isEmpty) {
                        EmptyState(
                            message = when {
                                searchQuery.isNotBlank() -> "No foods matching \"$searchQuery\""
                                selectedCategoryId != null -> "Nothing filed here yet"
                                else -> "Your food collection is empty"
                            },
                            hint = when {
                                searchQuery.isNotBlank() -> null
                                selectedCategoryId != null -> "Tap the label icon on a food to file it here"
                                else -> "Save foods after scanning to build it up"
                            }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedCategoryId == null) {
                                // "All" — nest everything under collapsible category → subcategory headers.
                                view.groups.forEach { group ->
                                    val topKey = group.category?.let { "cat_${it.id}" } ?: "unsorted"
                                    val topCollapsed = topKey in collapsed
                                    val total = group.directFoods.size + group.sections.sumOf { it.foods.size }
                                    item(key = "grp_$topKey") {
                                        CategoryGroupHeader(
                                            name = group.category?.name ?: "Unsorted",
                                            count = total,
                                            collapsed = topCollapsed,
                                            onToggle = { viewModel.toggleCollapsed(topKey) }
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
                                                    onToggle = { viewModel.toggleCollapsed(subKey) }
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
                                            onToggle = { viewModel.toggleCollapsed(subKey) }
                                        )
                                    }
                                    if (!subCollapsed) {
                                        items(section.foods, key = { "sub_${it.id}" }) { food -> foodCard(food) }
                                    }
                                }
                            }
                        }
                    }
                }
                CollectionTab.EXERCISE -> {
                    if (savedWorkouts.isEmpty()) {
                        EmptyState(
                            message = "No saved workouts yet",
                            hint = "Open a logged workout and tap the bookmark to save it here"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedWorkouts, key = { it.id }) { workout ->
                                SavedWorkoutCard(
                                    workout = workout,
                                    onLog = { viewModel.quickLogWorkout(workout) },
                                    onDelete = { viewModel.deleteWorkout(workout) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingFood != null) {
        AlertDialog(
            onDismissRequest = { editingFood = null },
            title = { Text("Edit food") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                        placeholder = { Text("Add your notes...") },
                        minLines = 3,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingFood?.let { food ->
                            if (nameText.isNotBlank() && nameText.trim() != food.name) viewModel.renameFood(food.id, nameText)
                            viewModel.updateNotes(food.id, notesText)
                        }
                        editingFood = null
                    },
                    enabled = nameText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingFood = null }) { Text("Cancel") } }
        )
    }

    assigningFood?.let { food ->
        AssignCategoryDialog(
            food = food,
            categories = allCategories,
            onAssign = { categoryId -> viewModel.assignFoodToCategory(food.id, categoryId); assigningFood = null },
            onManage = { assigningFood = null; showManageCategories = true },
            onDismiss = { assigningFood = null }
        )
    }

    if (showManageCategories) {
        ManageCategoriesDialog(
            categories = allCategories,
            onAdd = { name, parentId -> viewModel.addCategory(name, parentId) },
            onRename = { category, newName -> viewModel.renameCategory(category, newName) },
            onDelete = { id -> viewModel.deleteCategory(id) },
            onDismiss = { showManageCategories = false }
        )
    }
}

/** "All" + each top-level category as selectable chips, with a trailing manage button. */
@Composable
private fun CategoryTabs(
    categories: List<GalleryCategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("All") })
        categories.forEach { cat ->
            FilterChip(
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
                label = { Text(cat.name) }
            )
        }
        AssistChip(
            onClick = onManage,
            label = { Text(if (categories.isEmpty()) "New category" else "Edit") },
            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
}

/** Bold, collapsible header for a top-level category group in the "All" view. */
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

@Composable
private fun CollectionFoodCard(
    food: GalleryFoodEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddThumbnail: () -> Unit,
    onAssign: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onOpen)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = when {
                food.thumbnailPath.isNotEmpty() && File(food.thumbnailPath).exists() -> food.thumbnailPath
                food.photoPath != null -> food.photoPath
                else -> null
            }
            if (thumb != null) {
                Image(
                    painter = rememberAsyncImagePainter(thumb),
                    contentDescription = food.name,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleSmall, color = Cream, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${food.totalCalories.toInt()} kcal • ${food.defaultPortionGrams.toInt()} ${if (food.isDrink) "ml" else "g"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted
                )
                if (food.notes.isNotEmpty()) {
                    Text(food.notes, style = MaterialTheme.typography.bodySmall, color = GoldLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            IconButton(onClick = onAssign, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Label, contentDescription = "File in a category", modifier = Modifier.size(18.dp), tint = CreamMuted)
            }
            IconButton(onClick = onAddThumbnail, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo", modifier = Modifier.size(18.dp), tint = CreamMuted)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Rename & edit notes", modifier = Modifier.size(18.dp), tint = CreamMuted)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = CreamMuted)
            }
        }
    }
}

/** Pick which category/subcategory a food is filed under. */
@Composable
private fun AssignCategoryDialog(
    food: GalleryFoodEntity,
    categories: List<GalleryCategoryEntity>,
    onAssign: (Long?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val tops = categories.filter { it.parentId == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File \"${food.name}\"") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 380.dp)) {
                CategoryChoiceRow("Unsorted", selected = food.categoryId == null) { onAssign(null) }
                if (tops.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No categories yet — create one first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tops.forEach { top ->
                    CategoryChoiceRow(top.name, selected = food.categoryId == top.id, bold = true) { onAssign(top.id) }
                    categories.filter { it.parentId == top.id }.forEach { sub ->
                        CategoryChoiceRow("    ${sub.name}", selected = food.categoryId == sub.id) { onAssign(sub.id) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManage) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CategoryChoiceRow(label: String, selected: Boolean, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Default.Label, contentDescription = "Filed here", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** Create / rename / delete categories and subcategories. */
@Composable
private fun ManageCategoriesDialog(
    categories: List<GalleryCategoryEntity>,
    onAdd: (name: String, parentId: Long?) -> Unit,
    onRename: (GalleryCategoryEntity, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val tops = categories.filter { it.parentId == null }
    var newTop by remember { mutableStateOf("") }
    var addSubFor by remember { mutableStateOf<Long?>(null) }
    var newSub by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage categories") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 440.dp)) {
                tops.forEach { top ->
                    CategoryEditRow(category = top, bold = true, onRename = onRename, onDelete = onDelete, onAddSub = { addSubFor = if (addSubFor == top.id) null else top.id })
                    categories.filter { it.parentId == top.id }.forEach { sub ->
                        Row(modifier = Modifier.padding(start = 16.dp)) {
                            CategoryEditRow(category = sub, bold = false, onRename = onRename, onDelete = onDelete, onAddSub = null)
                        }
                    }
                    if (addSubFor == top.id) {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newSub,
                                onValueChange = { newSub = it },
                                label = { Text("New subcategory") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { if (newSub.isNotBlank()) { onAdd(newSub, top.id); newSub = ""; addSubFor = null } }) { Text("Add") }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTop,
                        onValueChange = { newTop = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (newTop.isNotBlank()) { onAdd(newTop, null); newTop = "" } }) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun CategoryEditRow(
    category: GalleryCategoryEntity,
    bold: Boolean,
    onRename: (GalleryCategoryEntity, String) -> Unit,
    onDelete: (Long) -> Unit,
    onAddSub: (() -> Unit)?
) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; if (it.isNotBlank()) onRename(category, it) },
            singleLine = true,
            textStyle = if (bold) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (onAddSub != null) {
            IconButton(onClick = onAddSub) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Add subcategory", tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = { onDelete(category.id) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${category.name}", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SavedWorkoutCard(
    workout: SavedWorkoutEntity,
    onLog: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onLog).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = GoldLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "${workout.minutes} min · MET ${"%.1f".format(workout.met)} · tap to log today",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${workout.name}", tint = CreamMuted)
        }
    }
}

@Composable
private fun EmptyState(message: String, hint: String?) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = CreamMuted)
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = CreamMuted.copy(alpha = 0.7f))
            }
        }
    }
}
