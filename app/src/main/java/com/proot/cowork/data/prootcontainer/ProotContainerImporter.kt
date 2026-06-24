package com.proot.cowork.data.prootcontainer

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.termux.bootstrap.TermuxLayout
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

class ProotContainerImporter(private val context: Context) {

    suspend fun importFromUri(
        sourceUri: Uri,
        partialDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (ProotContainerTarballLocator.isReadableTarball(file)) {
                    return@withContext importFromFile(file, partialDir, onProgress)
                }
            }
        }
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { raw ->
                importTarStream(raw, -1L, partialDir, onProgress)
            } ?: ImportResult.Error("Could not open container archive")
        } catch (e: Exception) {
            partialDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    suspend fun importFromFile(
        sourceFile: File,
        partialDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!ProotContainerTarballLocator.isReadableTarball(sourceFile)) {
            return@withContext ImportResult.Error(
                "Cannot read ${sourceFile.absolutePath}",
            )
        }
        try {
            FileInputStream(sourceFile).use { raw ->
                importTarStream(raw, sourceFile.length(), partialDir, onProgress)
            }
        } catch (e: Exception) {
            partialDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    private suspend fun importTarStream(
        raw: InputStream,
        size: Long,
        partialDir: File,
        onProgress: suspend (Float) -> Unit,
    ): ImportResult {
        if (partialDir.exists()) partialDir.deleteRecursively()
        partialDir.mkdirs()

        var lastReported = -1f
        suspend fun reportProgress(value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped - lastReported >= 0.01f || clamped >= 1f) {
                lastReported = clamped
                onProgress(clamped)
            }
        }

        var bytesRead = 0L
        BufferedInputStream(raw).use { buffered ->
            GzipCompressorInputStream(buffered).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name.removePrefix("./").removePrefix("/")
                        if (name.isNotEmpty() && name != ".") {
                            val outFile = File(partialDir, name)
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
                                else -> {
                                    outFile.parentFile?.mkdirs()
                                    bytesRead += extractFile(tar, outFile, entry)
                                    if (size > 0) reportProgress(bytesRead.toFloat() / size)
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }

        val distro = detectDistroName(partialDir) ?: ProotContainerValidator.DEFAULT_DISTRO
        val installError = installExtractedLayout(partialDir, distro)
        if (installError != null) {
            partialDir.deleteRecursively()
            return ImportResult.Error(installError)
        }

        partialDir.deleteRecursively()
        reportProgress(1f)
        return ImportResult.Success(ProotContainerValidator.containerDir(context, distro))
    }

    private fun detectDistroName(partialDir: File): String? {
        partialDir.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (File(child, "rootfs/usr/bin/bash").isFile) return child.name
        }
        return null
    }

    private fun installExtractedLayout(partialDir: File, defaultDistro: String): String? {
        val prefix = TermuxLayout.prefixDir(context)
        val runtimeDir = File(prefix, "var/lib/proot-distro").also { it.mkdirs() }

        val packaged = detectDistroName(partialDir)?.let { File(partialDir, it) }
        val dest = File(runtimeDir, "containers/$defaultDistro")

        when {
            packaged != null -> {
                if (dest.exists()) dest.deleteRecursively()
                packaged.renameTo(dest) || run {
                    copyDirectory(packaged, dest)
                    packaged.deleteRecursively()
                }
            }
            File(partialDir, "usr/bin/bash").isFile -> {
                if (dest.exists()) dest.deleteRecursively()
                val rootfs = File(dest, "rootfs").also { it.mkdirs() }
                partialDir.listFiles()?.forEach { entry ->
                    val target = File(rootfs, entry.name)
                    entry.renameTo(target) || run {
                        if (entry.isDirectory) copyDirectory(entry, target) else entry.copyTo(target, true)
                    }
                }
                writeDefaultManifest(dest)
            }
            else -> return "Invalid archive: expected proot-distro backup (ubuntu/rootfs/) or a flat rootfs"
        }

        ProotContainerSysdata.installIfNeeded(context, dest)
        val rootfs = File(dest, "rootfs")
        if (!File(rootfs, "usr/bin/bash").isFile) {
            return "Imported container missing usr/bin/bash"
        }
        if (!File(rootfs, "usr/bin/xfce4-session").isFile &&
            !File(rootfs, "usr/bin/startxfce4").isFile
        ) {
            return "Imported container missing XFCE (run proot-xfce-install in Termux first)"
        }
        return null
    }

    private fun writeDefaultManifest(containerDir: File) {
        val manifest = File(containerDir, "manifest.json")
        if (manifest.isFile) return
        context.assets.open("cowork/ubuntu-container-manifest.json").use { input ->
            manifest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copyDirectory(source: File, dest: File) {
        if (!dest.exists() && !dest.mkdirs()) {
            throw IllegalStateException("Failed to create $dest")
        }
        source.listFiles()?.forEach { entry ->
            val target = File(dest, entry.name)
            when {
                entry.isDirectory -> copyDirectory(entry, target)
                else -> entry.copyTo(target, overwrite = true)
            }
        }
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
