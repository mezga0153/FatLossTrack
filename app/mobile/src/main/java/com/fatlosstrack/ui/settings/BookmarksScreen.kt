package com.fatlosstrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.BookmarkedMeal
import com.fatlosstrack.data.local.db.BookmarkedMealDao
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarkedMealDao: BookmarkedMealDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bookmarks by bookmarkedMealDao.getAll().collectAsState(initial = emptyList())

    var editingBookmark by remember { mutableStateOf<BookmarkedMeal?>(null) }
    var editName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface,
                    navigationIconContentColor = OnSurface,
                ),
            )
        },
        containerColor = Surface,
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = OnSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.bookmarks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.bookmarks_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(bookmarks, key = { it.id }) { bm ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bm.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
                                Text(
                                    buildString {
                                        append("${bm.totalKcal} kcal")
                                        if (bm.totalProteinG > 0) append(" · ${bm.totalProteinG}g P")
                                        if (bm.totalCarbsG > 0) append(" · ${bm.totalCarbsG}g C")
                                        if (bm.totalFatG > 0) append(" · ${bm.totalFatG}g F")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                editName = bm.name
                                editingBookmark = bm
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch { bookmarkedMealDao.delete(bm) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = Tertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    if (editingBookmark != null) {
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(stringResource(R.string.bookmark_rename_title)) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.bookmark_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = editingBookmark!!.copy(name = editName.trim())
                        scope.launch { bookmarkedMealDao.update(updated) }
                        editingBookmark = null
                    },
                    enabled = editName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.button_save), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
            containerColor = CardSurface,
        )
    }
}
