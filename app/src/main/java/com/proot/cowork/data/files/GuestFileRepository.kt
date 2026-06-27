package com.proot.cowork.data.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.proot.ProotGuestShellExecutor
import com.proot.cowork.data.proot.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GuestFileEntry(
    val name: String,
    val guestPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
)

class GuestFileRepository(
    context: Context,
    private val shell: ProotGuestShellExecutor = ProotGuestShellExecutor(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val shareCacheDir = File(appContext.cacheDir, "guest_share").also { it.mkdirs() }

    suspend fun ensureArtifactsDir(): ShellResult = shell.run(GuestPaths.ensureArtifactsCmd())

    suspend fun listDirectory(path: String): Result<List<GuestFileEntry>> = withContext(Dispatchers.IO) {
        val normalizedPath = GuestPaths.normalize(path)
        if (!GuestPaths.isAllowed(normalizedPath)) {
            return@withContext Result.failure(IllegalArgumentException("Path not allowed: $path"))
        }
        val quoted = shellQuote(normalizedPath)
        val cmd = """
            python3 -c '
import json, os, sys
path = sys.argv[1]
if not os.path.isdir(path):
    print("[]")
    sys.exit(0)
entries = []
for name in sorted(os.listdir(path), key=lambda s: s.lower()):
    if name in (".", ".."):
        continue
    full = os.path.join(path, name)
    try:
        st = os.stat(full)
        entries.append({
            "name": name,
            "dir": os.path.isdir(full),
            "size": int(st.st_size),
            "mtime": int(st.st_mtime),
        })
    except OSError:
        pass
print(json.dumps(entries))
' $quoted
        """.trimIndent()
        val result = shell.run(cmd)
        if (!result.success) {
            return@withContext Result.failure(Exception(result.error ?: result.output.ifBlank { "List failed" }))
        }
        runCatching {
            val array = JSONArray(result.output.trim())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.getString("name")
                    add(
                        GuestFileEntry(
                            name = name,
                            guestPath = joinPath(normalizedPath, name),
                            isDirectory = obj.getBoolean("dir"),
                            sizeBytes = obj.getLong("size"),
                            lastModified = obj.getLong("mtime") * 1000L,
                        ),
                    )
                }
            }.sortedWith(compareBy<GuestFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun delete(guestPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!GuestPaths.isAllowed(guestPath)) return@withContext false
        val quoted = shellQuote(guestPath)
        val result = shell.run("rm -rf -- $quoted")
        result.success
    }

    suspend fun createDirectory(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = name.trim().replace(Regex("""[^\w.\- ]+"""), "_").take(80)
        if (safeName.isBlank()) return@withContext false
        val target = joinPath(parentPath, safeName)
        if (!GuestPaths.isAllowed(target)) return@withContext false
        shell.run("mkdir -p -- ${shellQuote(target)}").success
    }

    suspend fun uploadFromUri(
        context: Context,
        uri: Uri,
        displayName: String?,
        destDir: String = GuestPaths.ARTIFACTS_DIR,
    ): String? = withContext(Dispatchers.IO) {
        if (!GuestPaths.isAllowed(destDir)) return@withContext null
        ensureArtifactsDir()
        val name = displayName?.takeIf { it.isNotBlank() } ?: "upload_${System.currentTimeMillis()}"
        val safeName = name.replace(Regex("""[^\w.\-]+"""), "_").take(120)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        val destPath = joinPath(destDir, safeName)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val quotedDest = shellQuote(destPath)
        val quotedDir = shellQuote(destDir)
        val cmd = "mkdir -p -- $quotedDir && printf '%s' '$b64' | base64 -d > $quotedDest"
        val result = shell.run(cmd)
        if (result.success) destPath else null
    }

    suspend fun rename(guestPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = newName.trim().replace(Regex("""[^\w.\- ]+"""), "_").take(120)
        if (safeName.isBlank()) return@withContext false
        val parent = parentPath(guestPath) ?: return@withContext false
        val target = joinPath(parent, safeName)
        if (!GuestPaths.isAllowed(guestPath) || !GuestPaths.isAllowed(target)) return@withContext false
        val quotedFrom = shellQuote(guestPath)
        val quotedTo = shellQuote(target)
        shell.run("mv -- $quotedFrom $quotedTo").success
    }

    suspend fun downloadToDevice(guestPath: String): File? = withContext(Dispatchers.IO) {
        val cached = pullToCache(guestPath) ?: return@withContext null
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads.mkdirs()
        val dest = File(downloads, cached.name)
        runCatching {
            cached.copyTo(dest, overwrite = true)
            dest
        }.getOrNull()
    }

    suspend fun pullManyToCache(guestPaths: List<String>): List<File> = withContext(Dispatchers.IO) {
        guestPaths.mapNotNull { pullToCache(it) }
    }

    suspend fun pullToCache(guestPath: String): File? = withContext(Dispatchers.IO) {
        if (!GuestPaths.isAllowed(guestPath)) return@withContext null
        val quoted = shellQuote(guestPath)
        val result = shell.run("base64 -- $quoted 2>/dev/null | head -c 16000000")
        if (!result.success || result.output.isBlank()) return@withContext null
        val name = guestPath.substringAfterLast('/').ifBlank { "file" }
        val dest = File(shareCacheDir, "${System.currentTimeMillis()}_$name")
        runCatching {
            dest.writeBytes(Base64.decode(result.output.trim(), Base64.DEFAULT))
            dest
        }.getOrNull()
    }

    suspend fun readTextSnippet(guestPath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (!GuestPaths.isAllowed(guestPath)) {
            return@withContext guestPath.substringAfterLast('/') to "(Path not allowed)"
        }
        val quoted = shellQuote(guestPath)
        val result = shell.run("head -c 8000 -- $quoted 2>&1")
        val name = guestPath.substringAfterLast('/')
        if (!result.success) {
            name to (result.error ?: result.output).take(8000)
        } else {
            name to result.output.take(8000)
        }
    }

    suspend fun listArtifactNames(): List<String> {
        ensureArtifactsDir()
        return listDirectory(GuestPaths.ARTIFACTS_DIR).getOrNull()
            ?.filter { !it.isDirectory }
            ?.map { it.name }
            .orEmpty()
    }

    suspend fun migrateHostArtifactsIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val hostDir = SettingsRepository(context.applicationContext).getArtifactsDir()
        val files = hostDir.listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) return@withContext
        ensureArtifactsDir()
        for (file in files) {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val destPath = GuestFileRepository.joinPath(GuestPaths.ARTIFACTS_DIR, file.name)
            val quotedDest = shellQuote(destPath)
            shell.run("printf '%s' '$b64' | base64 -d > $quotedDest")
            file.delete()
        }
    }

    fun formatSize(bytes: Long): String = formatSizeStatic(bytes)

    fun formatDate(millis: Long): String = formatDateStatic(millis)

    companion object {
        fun joinPath(parent: String, name: String): String {
            val base = GuestPaths.normalize(parent)
            val child = name.trim().trim('/')
            if (child.isEmpty()) return base
            return if (base == GuestPaths.ROOT) "/$child" else "$base/$child"
        }

        fun parentPath(path: String): String? {
            val trimmed = GuestPaths.normalize(path)
            if (trimmed == GuestPaths.ROOT) return null
            val idx = trimmed.lastIndexOf('/')
            return if (idx <= 0) GuestPaths.ROOT else trimmed.substring(0, idx)
        }

        fun breadcrumbSegments(path: String): List<Pair<String, String>> {
            val normalized = GuestPaths.normalize(path)
            if (normalized == GuestPaths.ROOT) {
                return listOf("root" to GuestPaths.ROOT)
            }
            val parts = normalized.removePrefix("/").split('/')
            var accum = ""
            return buildList {
                parts.forEach { part ->
                    accum = if (accum.isEmpty()) "/$part" else "$accum/$part"
                    add(part to accum)
                }
            }
        }

        fun formatSize(bytes: Long): String = formatSizeStatic(bytes)

        fun formatDate(millis: Long): String = formatDateStatic(millis)

        fun formatShortDate(millis: Long): String = formatShortDateStatic(millis)

        private fun formatSizeStatic(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            return if (kb < 1024) "${"%.1f".format(kb)} KB" else "${"%.1f".format(kb / 1024.0)} MB"
        }

        private fun formatDateStatic(millis: Long): String =
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))

        private fun formatShortDateStatic(millis: Long): String =
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

        private fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"
    }
}
