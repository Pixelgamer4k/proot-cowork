package com.proot.cowork.data.files

import android.content.Context
import com.proot.cowork.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ArtifactEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

class ArtifactsRepository(context: Context) {

    private val artifactsDir = SettingsRepository(context.applicationContext).getArtifactsDir()

    suspend fun listArtifacts(): List<ArtifactEntry> = withContext(Dispatchers.IO) {
        artifactsDir.mkdirs()
        artifactsDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                ArtifactEntry(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                )
            }
            .orEmpty()
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.absolutePath.startsWith(artifactsDir.absolutePath)) return@withContext false
        file.delete()
    }

    suspend fun importFromUri(context: Context, uri: android.net.Uri, displayName: String?): String? =
        withContext(Dispatchers.IO) {
            val name = displayName?.takeIf { it.isNotBlank() } ?: "upload_${System.currentTimeMillis()}"
            val safeName = name.replace(Regex("""[^\w.\-]+"""), "_").take(120)
            val dest = File(artifactsDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            dest.absolutePath
        }

    fun formatSize(bytes: Long): String = formatSizeStatic(bytes)

    fun formatDate(millis: Long): String = formatDateStatic(millis)

    companion object {
        fun formatSize(bytes: Long): String = formatSizeStatic(bytes)

        fun formatDate(millis: Long): String = formatDateStatic(millis)

        private fun formatSizeStatic(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            return if (kb < 1024) "${"%.1f".format(kb)} KB" else "${"%.1f".format(kb / 1024.0)} MB"
        }

        private fun formatDateStatic(millis: Long): String =
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
