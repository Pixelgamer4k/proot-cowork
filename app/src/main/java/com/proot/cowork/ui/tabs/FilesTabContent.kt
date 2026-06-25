package com.proot.cowork.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeLabel: String,
    val dateLabel: String,
)

@Composable
fun FilesTabContent(
    artifactsDir: File,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(artifactsDir.absolutePath) { listArtifactEntries(artifactsDir) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "home › coworker › artifacts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(entries) { entry ->
                FileRow(entry = entry, onClick = { onOpenPath(entry.path) })
            }
        }

        item {
            Text(
                text = stringResource(R.string.files_actions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val icon: ImageVector = when {
        entry.isDirectory -> Icons.Default.Folder
        entry.name.endsWith(".png", true) || entry.name.endsWith(".jpg", true) -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(entry.name, fontWeight = FontWeight.Medium)
                if (!entry.isDirectory) {
                    Text(
                        text = entry.sizeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = entry.dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun listArtifactEntries(dir: File): List<FileEntry> {
    if (!dir.isDirectory) return emptyList()
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return dir.listFiles()
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        ?.map { file ->
            FileEntry(
                name = file.name + if (file.isDirectory) "/" else "",
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeLabel = if (file.isFile) formatSize(file.length()) else "",
                dateLabel = fmt.format(Date(file.lastModified())),
            )
        }
        .orEmpty()
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${"%.1f".format(kb)} KB"
    return "${"%.1f".format(kb / 1024.0)} MB"
}
