package com.proot.cowork.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.files.FilesSortOrder
import com.proot.cowork.data.files.FilesViewMode
import com.proot.cowork.data.files.GuestFileEntry
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.files.GuestFileBrowser

@Composable
fun FilesTabContent(
    entries: List<GuestFileEntry>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    viewMode: FilesViewMode,
    sortOrder: FilesSortOrder,
    containerInstalled: Boolean,
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
    if (!containerInstalled) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.files_container_required), color = CoworkTokens.TextMuted)
        }
        return
    }

    GuestFileBrowser(
        entries = entries,
        currentPath = currentPath,
        isLoading = isLoading,
        error = error,
        viewMode = viewMode,
        sortOrder = sortOrder,
        selectionMode = selectionMode,
        selectedPaths = selectedPaths,
        onNavigateUp = onNavigateUp,
        onNavigateToPath = onNavigateToPath,
        onOpenEntry = onOpenEntry,
        onToggleSelect = onToggleSelect,
        onEnterSelectionMode = onEnterSelectionMode,
        onExitSelectionMode = onExitSelectionMode,
        onShareSelected = onShareSelected,
        onDownloadSelected = onDownloadSelected,
        onDeleteSelected = onDeleteSelected,
        onRenameSelected = onRenameSelected,
        onUpload = onUpload,
        onCreateFolder = onCreateFolder,
        onViewModeChange = onViewModeChange,
        onSortOrderChange = onSortOrderChange,
        modifier = modifier,
    )
}
