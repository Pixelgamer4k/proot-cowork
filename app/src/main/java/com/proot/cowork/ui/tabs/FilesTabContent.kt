package com.proot.cowork.ui.tabs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.ui.design.CoworkTokens
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileEntry(val name: String, val path: String, val isDirectory: Boolean, val sizeLabel: String, val dateLabel: String)

@Composable
fun FilesTabContent(artifactsDir: File, onOpenPath: (String) -> Unit, modifier: Modifier = Modifier) {
    val entries = remember(artifactsDir.absolutePath) { listDemoAndArtifactEntries(artifactsDir) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = CoworkTokens.TextMuted, modifier = Modifier.size(14.dp))
                Text("  ›  home  ›  ", color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Text("coworker", color = CoworkTokens.Mint, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(12.dp))
        }
        items(entries) { FileRow(it) { onOpenPath(it.path) } }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { }) { Text(stringResource(R.string.files_new_folder), color = CoworkTokens.TextSecondary) }
                TextButton(onClick = { }) { Text(stringResource(R.string.files_upload), color = CoworkTokens.TextSecondary) }
                TextButton(onClick = { }) { Text(stringResource(R.string.files_select), color = CoworkTokens.TextSecondary) }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val accent = if (entry.isDirectory) CoworkTokens.FolderAccent else CoworkTokens.FileAccent
    val icon: ImageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .size(36.dp)
                .border(1.dp, accent.copy(alpha = 0.55f), CoworkTokens.ShapeIconTile)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) }
        Text(
            entry.name,
            Modifier.weight(1f).padding(horizontal = 12.dp),
            fontWeight = FontWeight.Medium,
            color = CoworkTokens.TextPrimary,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (entry.isDirectory) "--" else entry.sizeLabel,
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            Text(entry.dateLabel, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

private fun listDemoAndArtifactEntries(dir: File): List<FileEntry> {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val demo = listOf(
        FileEntry("projects/", "", true, "", "Jun 25"),
        FileEntry("documents/", "", true, "", "Jun 25"),
        FileEntry("downloads/", "", true, "", "Jun 24"),
        FileEntry("app.py", "", false, "4.2 KB", "Jun 25"),
        FileEntry("requirements.txt", "", false, "1.1 KB", "Jun 25"),
    )
    val real = dir.listFiles()?.filter { it.isFile }?.map {
        FileEntry(it.name, it.absolutePath, false, formatSize(it.length()), fmt.format(Date(it.lastModified())))
    }.orEmpty()
    return demo + real
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    return if (kb < 1024) "${"%.1f".format(kb)} KB" else "${"%.1f".format(kb / 1024.0)} MB"
}
