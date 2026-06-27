package com.proot.cowork.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.files.GuestFileEntry
import com.proot.cowork.data.files.GuestFileRepository
import com.proot.cowork.data.files.GuestPaths
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun FilesTabContent(
    entries: List<GuestFileEntry>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    containerInstalled: Boolean,
    onNavigateUp: () -> Unit,
    onOpenEntry: (GuestFileEntry) -> Unit,
    onSharePath: (String) -> Unit,
    onDeletePath: (String) -> Unit,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!containerInstalled) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.files_container_required),
                color = CoworkTokens.TextMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onGoHome, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Home, stringResource(R.string.files_home), tint = CoworkTokens.Mint)
                }
                if (currentPath != GuestPaths.ARTIFACTS_DIR && GuestFileRepository.parentPath(currentPath) != null) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.files_up), tint = CoworkTokens.TextSecondary)
                    }
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.files_refresh), tint = CoworkTokens.TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNewFolder, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CreateNewFolder, stringResource(R.string.files_new_folder), tint = CoworkTokens.Mint)
                }
            }
            Text(
                breadcrumbLabel(currentPath),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                currentPath,
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (isLoading) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = CoworkTokens.Mint, strokeWidth = 2.dp)
                }
            }
        }

        error?.let { message ->
            item {
                Text(message, color = CoworkTokens.Failed, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }

        if (!isLoading && error == null && entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.files_empty),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(entries, key = { it.guestPath }) { entry ->
                GuestFileRow(
                    entry = entry,
                    onOpen = { onOpenEntry(entry) },
                    onShare = { onSharePath(entry.guestPath) },
                    onDelete = { onDeletePath(entry.guestPath) },
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onUpload) {
                    Text(stringResource(R.string.files_upload), color = CoworkTokens.Mint)
                }
            }
            Text(
                stringResource(R.string.files_actions_hint),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuestFileRow(
    entry: GuestFileEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (entry.isDirectory) CoworkTokens.Mint else CoworkTokens.FileAccent
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onShare)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .size(36.dp)
                .border(1.dp, accent.copy(alpha = 0.55f), CoworkTokens.ShapeIconTile)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.name, fontWeight = FontWeight.Medium, color = CoworkTokens.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDirectory) {
                Text(
                    "${GuestFileRepository.formatSize(entry.sizeBytes)} · ${GuestFileRepository.formatDate(entry.lastModified)}",
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (!entry.isDirectory) {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.IosShare, stringResource(R.string.files_share), tint = CoworkTokens.TextMuted)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.files_delete), tint = CoworkTokens.TextMuted)
        }
    }
}

private fun breadcrumbLabel(path: String): String {
    val relative = path.removePrefix(GuestPaths.HOME).trimStart('/')
    return if (relative.isBlank()) "~" else "~ / ${relative.replace("/", " / ")}"
}
