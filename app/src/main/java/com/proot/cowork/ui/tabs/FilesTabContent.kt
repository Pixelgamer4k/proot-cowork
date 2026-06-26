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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.files.ArtifactEntry
import com.proot.cowork.data.files.ArtifactsRepository
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun FilesTabContent(
    artifacts: List<ArtifactEntry>,
    artifactsDirLabel: String,
    onOpenPath: (String) -> Unit,
    onSharePath: (String) -> Unit,
    onDeletePath: (String) -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = CoworkTokens.TextMuted, modifier = Modifier.size(14.dp))
                Text("  ›  artifacts", color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            Text(
                artifactsDirLabel,
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }

        if (artifacts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.files_empty),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(artifacts, key = { it.path }) { entry ->
                ArtifactRow(
                    entry = entry,
                    onOpen = { onOpenPath(entry.path) },
                    onShare = { onSharePath(entry.path) },
                    onDelete = { onDeletePath(entry.path) },
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onUpload) {
                    Text(stringResource(R.string.files_upload), color = CoworkTokens.Mint)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtifactRow(
    entry: ArtifactEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
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
                .border(1.dp, CoworkTokens.FileAccent.copy(alpha = 0.55f), CoworkTokens.ShapeIconTile)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Description, null, tint = CoworkTokens.FileAccent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.name, fontWeight = FontWeight.Medium, color = CoworkTokens.TextPrimary)
            Text(
                "${ArtifactsRepository.formatSize(entry.sizeBytes)} · ${ArtifactsRepository.formatDate(entry.lastModified)}",
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.IosShare, stringResource(R.string.files_share), tint = CoworkTokens.TextMuted, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.files_delete), tint = CoworkTokens.Failed, modifier = Modifier.size(18.dp))
        }
    }
}
