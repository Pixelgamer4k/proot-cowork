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
import java.io.FileOutputStream

class RootfsImporter(private val context: Context) {

    suspend fun import(
        sourceUri: Uri,
        destDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        val resolver = context.contentResolver
        val size = resolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: -1L
        var lastReported = -1f

        suspend fun reportProgress(value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped - lastReported >= 0.01f || clamped >= 1f) {
                lastReported = clamped
                onProgress(clamped)
            }
        }

        try {
            resolver.openInputStream(sourceUri)?.use { raw ->
                BufferedInputStream(raw).use { buffered ->
                    GzipCompressorInputStream(buffered).use { gzip ->
                        TarArchiveInputStream(gzip).use { tar ->
                            var entry: TarArchiveEntry? = tar.nextEntry
                            var bytesRead = 0L
                            while (entry != null) {
                                val name = entry.name.removePrefix("./").removePrefix("/")
                                if (name.isNotEmpty() && name != ".") {
                                    val outFile = File(destDir, name)
                                    if (entry.isDirectory) {
                                        outFile.mkdirs()
                                    } else {
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { fos ->
                                            val buffer = ByteArray(256 * 1024)
                                            var read = tar.read(buffer)
                                            while (read != -1) {
                                                fos.write(buffer, 0, read)
                                                bytesRead += read
                                                if (size > 0) {
                                                    reportProgress(bytesRead.toFloat() / size)
                                                }
                                                read = tar.read(buffer)
                                            }
                                        }
                                        val mode = entry.mode
                                        if (mode and 64 != 0) outFile.setExecutable(true, false)
                                        if (mode and 128 != 0) outFile.setReadable(true, false)
                                        if (mode and 1 != 0) outFile.setWritable(true, false)
                                    }
                                }
                                entry = tar.nextEntry
                            }
                        }
                    }
                }
            } ?: return@withContext ImportResult.Error("Could not open rootfs file")
        } catch (e: OutOfMemoryError) {
            destDir.deleteRecursively()
            return@withContext ImportResult.Error("Out of memory while importing. Free storage and try again.")
        } catch (e: Exception) {
            destDir.deleteRecursively()
            return@withContext ImportResult.Error(e.message ?: "Import failed")
        }

        val startScript = File(destDir, "start-desktop.sh")
        if (!startScript.exists()) {
            destDir.deleteRecursively()
            return@withContext ImportResult.Error("Invalid rootfs: start-desktop.sh not found at root")
        }

        reportProgress(1f)
        startScript.setExecutable(true, false)
        ImportResult.Success(destDir)
    }
}

sealed class ImportResult {
    data class Success(val rootfsDir: File) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
