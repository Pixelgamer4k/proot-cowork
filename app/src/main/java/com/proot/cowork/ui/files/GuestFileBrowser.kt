package com.proot.cowork.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.files.FilesSortOrder
import com.proot.cowork.data.files.FilesViewMode
import com.proot.cowork.data.files.GuestFileEntry
import com.proot.cowork.data.files.GuestFileRepository
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun GuestFileBrowser(
    entries: List<GuestFileEntry>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    viewMode: FilesViewMode,
    sortOrder: FilesSortOrder,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onOpenEntry: (GuestFileEntry) -> Unit,
    onToggleSelect: (GuestFileEntry) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onShareSelected: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: (String) -> Unit,
    onUpload: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onViewModeChange: (FilesViewMode) -> Unit,
    onSortOrderChange: (FilesSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val segments = remember(currentPath) { GuestFileRepository.breadcrumbSegments(currentPath) }
    val canGoUp = GuestFileRepository.parentPath(currentPath) != null
    val singleSelected = selectedPaths.size == 1

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionTopBar(
                count = selectedPaths.size,
                canRename = singleSelected,
                onClose = onExitSelectionMode,
                onShare = onShareSelected,
                onDownload = onDownloadSelected,
                onRename = { showRenameDialog = true },
                onDelete = onDeleteSelected,
            )
        } else {
            BrowserToolbar(
                canGoUp = canGoUp,
                viewMode = viewMode,
                sortMenuOpen = sortMenuOpen,
                onNavigateUp = onNavigateUp,
                onToggleViewMode = {
                    onViewModeChange(
                        if (viewMode == FilesViewMode.LIST) FilesViewMode.GRID else FilesViewMode.LIST,
                    )
                },
                onSortMenuOpen = { sortMenuOpen = true },
                onSortMenuDismiss = { sortMenuOpen = false },
                onSortOrderChange = {
                    onSortOrderChange(it)
                    sortMenuOpen = false
                },
            )
        }

        BreadcrumbBar(
            segments = segments,
            onNavigate = onNavigateToPath,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Box(Modifier.weight(1f)) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CoworkTokens.Mint, strokeWidth = 2.dp)
                    }
                }
                error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(error, color = CoworkTokens.Failed, textAlign = TextAlign.Center)
                    }
                }
                entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.files_empty),
                            color = CoworkTokens.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                viewMode == FilesViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(entries, key = { it.guestPath }) { entry ->
                            GridEntryTile(
                                entry = entry,
                                selected = entry.guestPath in selectedPaths,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) onToggleSelect(entry) else onOpenEntry(entry)
                                },
                                onLongClick = {
                                    if (!selectionMode) onEnterSelectionMode()
                                    onToggleSelect(entry)
                                },
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        items(entries, key = { it.guestPath }) { entry ->
                            BrowserEntryRow(
                                entry = entry,
                                selected = entry.guestPath in selectedPaths,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) onToggleSelect(entry) else onOpenEntry(entry)
                                },
                                onLongClick = {
                                    if (!selectionMode) onEnterSelectionMode()
                                    onToggleSelect(entry)
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = CoworkTokens.Border)
        BrowserActionBar(
            selectionMode = selectionMode,
            onNewFolder = { showNewFolderDialog = true },
            onUpload = onUpload,
            onSelect = {
                if (selectionMode) onExitSelectionMode() else onEnterSelectionMode()
            },
        )
    }

    if (showNewFolderDialog) {
        NameInputDialog(
            title = stringResource(R.string.files_new_folder),
            hint = stringResource(R.string.files_folder_name_hint),
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            },
        )
    }

    if (showRenameDialog && singleSelected) {
        val currentName = selectedPaths.first().substringAfterLast('/')
        NameInputDialog(
            title = stringResource(R.string.files_rename),
            hint = stringResource(R.string.files_rename_hint),
            initial = currentName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                showRenameDialog = false
                onRenameSelected(name)
            },
        )
    }
}

@Composable
private fun BrowserToolbar(
    canGoUp: Boolean,
    viewMode: FilesViewMode,
    sortMenuOpen: Boolean,
    onNavigateUp: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSortMenuOpen: () -> Unit,
    onSortMenuDismiss: () -> Unit,
    onSortOrderChange: (FilesSortOrder) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoUp) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.files_up), tint = CoworkTokens.TextSecondary)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = onSortMenuOpen) {
                Icon(Icons.Default.Sort, stringResource(R.string.files_sort), tint = CoworkTokens.TextSecondary)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = onSortMenuDismiss) {
                FilesSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(order)) },
                        onClick = { onSortOrderChange(order) },
                    )
                }
            }
        }
        IconButton(onClick = onToggleViewMode) {
            Icon(
                if (viewMode == FilesViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                stringResource(R.string.files_toggle_view),
                tint = CoworkTokens.Mint,
            )
        }
    }
}

@Composable
private fun sortLabel(order: FilesSortOrder): String = when (order) {
    FilesSortOrder.NAME_ASC -> stringResource(R.string.files_sort_name_asc)
    FilesSortOrder.NAME_DESC -> stringResource(R.string.files_sort_name_desc)
    FilesSortOrder.SIZE_ASC -> stringResource(R.string.files_sort_size_asc)
    FilesSortOrder.SIZE_DESC -> stringResource(R.string.files_sort_size_desc)
    FilesSortOrder.DATE_ASC -> stringResource(R.string.files_sort_date_asc)
    FilesSortOrder.DATE_DESC -> stringResource(R.string.files_sort_date_desc)
}

@Composable
private fun BreadcrumbBar(
    segments: List<Pair<String, String>>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                Text("  ›  ", color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            Text(
                text = label,
                color = CoworkTokens.Mint,
                fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else FontWeight.Medium,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onNavigate(path) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserEntryRow(
    entry: GuestFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = if (entry.isDirectory) CoworkTokens.FolderAccent else CoworkTokens.FileAccent
    val displayName = if (entry.isDirectory) "${entry.name}/" else entry.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionMark(selected, Modifier.padding(end = 10.dp))
        }
        EntryIconTile(entry.isDirectory, accent)
        Text(
            text = displayName,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            color = CoworkTokens.TextPrimary,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            if (!entry.isDirectory) {
                Text(
                    GuestFileRepository.formatSize(entry.sizeBytes),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                GuestFileRepository.formatShortDate(entry.lastModified),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridEntryTile(
    entry: GuestFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = if (entry.isDirectory) CoworkTokens.FolderAccent else CoworkTokens.FileAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .border(
                1.dp,
                if (selected) CoworkTokens.Mint else CoworkTokens.Border,
                CoworkTokens.ShapeCard,
            )
            .background(CoworkTokens.SurfaceElevated, CoworkTokens.ShapeCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selectionMode) {
            SelectionMark(selected, Modifier.align(Alignment.End))
        }
        EntryIconTile(entry.isDirectory, accent, tileSize = 52.dp, iconSize = 28.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.name,
            color = CoworkTokens.TextPrimary,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!entry.isDirectory) {
            Text(
                GuestFileRepository.formatSize(entry.sizeBytes),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun EntryIconTile(
    isDirectory: Boolean,
    accent: Color,
    tileSize: Dp = 42.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = Modifier
            .size(tileSize)
            .border(1.dp, accent.copy(alpha = 0.45f), CoworkTokens.ShapeIconTile)
            .background(CoworkTokens.Surface, CoworkTokens.ShapeIconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Circle,
        contentDescription = null,
        tint = if (selected) CoworkTokens.Mint else CoworkTokens.TextMuted,
        modifier = modifier.size(22.dp),
    )
}

@Composable
private fun BrowserActionBar(
    selectionMode: Boolean,
    onNewFolder: () -> Unit,
    onUpload: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoworkTokens.Surface)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionChip(stringResource(R.string.files_new_folder), onNewFolder, enabled = !selectionMode)
        ActionDivider()
        ActionChip(stringResource(R.string.files_upload), onUpload, enabled = !selectionMode)
        ActionDivider()
        ActionChip(
            label = if (selectionMode) stringResource(R.string.files_cancel_select) else stringResource(R.string.files_select),
            onClick = onSelect,
            highlighted = selectionMode,
        )
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit, enabled: Boolean = true, highlighted: Boolean = false) {
    Text(
        text = label,
        color = when {
            !enabled -> CoworkTokens.TextMuted.copy(alpha = 0.5f)
            highlighted -> CoworkTokens.Mint
            else -> CoworkTokens.TextSecondary
        },
        fontWeight = FontWeight.SemiBold,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ActionDivider() {
    Box(Modifier.width(1.dp).height(18.dp).background(CoworkTokens.Border))
}

@Composable
private fun SelectionTopBar(
    count: Int,
    canRename: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoworkTokens.SurfaceElevated)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, stringResource(R.string.files_cancel_select), tint = CoworkTokens.TextSecondary)
        }
        Text(
            stringResource(R.string.files_selected_count, count),
            color = CoworkTokens.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (canRename) {
            IconButton(onClick = onRename) {
                Icon(Icons.Default.DriveFileRenameOutline, stringResource(R.string.files_rename), tint = CoworkTokens.Mint)
            }
        }
        IconButton(onClick = onDownload, enabled = count > 0) {
            Icon(Icons.Default.Download, stringResource(R.string.files_download), tint = CoworkTokens.Mint)
        }
        IconButton(onClick = onShare, enabled = count > 0) {
            Icon(Icons.Default.IosShare, stringResource(R.string.files_share), tint = CoworkTokens.Mint)
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Default.Delete, stringResource(R.string.files_delete), tint = CoworkTokens.Failed)
        }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    hint: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(hint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.files_create), color = CoworkTokens.Mint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = CoworkTokens.TextSecondary)
            }
        },
    )
}
