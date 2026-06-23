package com.proot.cowork.data.rootfs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

class RootfsImporter(private val context: Context) {

    suspend fun importFromUri(
        sourceUri: Uri,
        destDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (RootfsTarballLocator.isReadableTarball(file)) {
                    return@withContext importFromFile(file, destDir, onProgress)
                }
            }
        }

        val resolver = context.contentResolver
        val size = resolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: -1L
        try {
            resolver.openInputStream(sourceUri)?.use { raw ->
                importTarStream(raw, size, destDir, onProgress)
            } ?: ImportResult.Error("Could not open rootfs file")
        } catch (e: OutOfMemoryError) {
            destDir.deleteRecursively()
            ImportResult.Error("Out of memory while importing. Free storage and try again.")
        } catch (e: Exception) {
            destDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    suspend fun importFromFile(
        sourceFile: File,
        destDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!RootfsTarballLocator.isReadableTarball(sourceFile)) {
            return@withContext ImportResult.Error(
                "Cannot read ${sourceFile.absolutePath}. Copy the tarball to ${RootfsTarballLocator.dropDirectoryLabel(context)}",
            )
        }
        try {
            FileInputStream(sourceFile).use { raw ->
                importTarStream(raw, sourceFile.length(), destDir, onProgress)
            }
        } catch (e: OutOfMemoryError) {
            destDir.deleteRecursively()
            ImportResult.Error("Out of memory while importing. Free storage and try again.")
        } catch (e: Exception) {
            destDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    private suspend fun importTarStream(
        raw: InputStream,
        size: Long,
        destDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        var lastReported = -1f

        suspend fun reportProgress(value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped - lastReported >= 0.01f || clamped >= 1f) {
                lastReported = clamped
                onProgress(clamped)
            }
        }

        BufferedInputStream(raw).use { buffered ->
            GzipCompressorInputStream(buffered).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    var bytesRead = 0L
                    while (entry != null) {
                        val name = entry.name.removePrefix("./").removePrefix("/")
                        if (name.isNotEmpty() && name != ".") {
                            val outFile = File(destDir, name)
                            when {
                                entry.isDirectory -> outFile.mkdirs()
                                entry.isSymbolicLink -> {
                                    outFile.parentFile?.mkdirs()
                                    if (outFile.exists()) outFile.delete()
                                    Files.createSymbolicLink(
                                        outFile.toPath(),
                                        Paths.get(entry.linkName),
                                    )
                                }
                                entry.isLink -> {
                                    outFile.parentFile?.mkdirs()
                                    extractFile(tar, outFile, entry)
                                }
                                else -> {
                                    outFile.parentFile?.mkdirs()
                                    bytesRead += extractFile(tar, outFile, entry)
                                    if (size > 0) {
                                        reportProgress(bytesRead.toFloat() / size)
                                    }
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }

        RootfsValidator.repairLayout(destDir)

        val startScript = File(destDir, "start-desktop.sh")
        if (!startScript.exists()) {
            destDir.deleteRecursively()
            return ImportResult.Error("Invalid rootfs: start-desktop.sh not found at root")
        }

        reportProgress(1f)
        startScript.setExecutable(true, false)
        return ImportResult.Success(destDir)
    }

    private fun extractFile(tar: TarArchiveInputStream, outFile: File, entry: TarArchiveEntry): Long {
        var bytesRead = 0L
        FileOutputStream(outFile).use { fos ->
            val buffer = ByteArray(256 * 1024)
            var read = tar.read(buffer)
            while (read != -1) {
                fos.write(buffer, 0, read)
                bytesRead += read
                read = tar.read(buffer)
            }
        }
        val mode = entry.mode
        if (mode and 64 != 0) outFile.setExecutable(true, false)
        if (mode and 128 != 0) outFile.setReadable(true, false)
        if (mode and 1 != 0) outFile.setWritable(true, false)
        return bytesRead
    }
}

sealed class ImportResult {
    data class Success(val rootfsDir: File) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
