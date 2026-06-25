package com.proot.cowork.data.prootcontainer

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportProgressUpdate
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
import kotlin.math.roundToInt

class ProotContainerImporter(private val context: Context) {

    suspend fun importFromUri(
        sourceUri: Uri,
        partialDir: File,
        onProgress: suspend (ImportProgressUpdate) -> Unit,
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
        onProgress: suspend (ImportProgressUpdate) -> Unit,
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
        onProgress: suspend (ImportProgressUpdate) -> Unit,
    ): ImportResult {
        onProgress(
            ImportProgressUpdate(
                phase = ImportPhase.PREPARING,
                progress = 0.02f,
                detail = if (size > 0) formatBytes(size) else "",
            ),
        )

        if (partialDir.exists()) partialDir.deleteRecursively()
        partialDir.mkdirs()

        var lastReported = -1f
        suspend fun reportExtract(bytesRead: Long) {
            val fraction = if (size > 0) {
                0.05f + (bytesRead.toFloat() / size) * 0.80f
            } else {
                0.05f + ((bytesRead / (50L * 1024 * 1024)) % 80) / 100f
            }
            val clamped = fraction.coerceIn(0.05f, 0.85f)
            if (clamped - lastReported >= 0.008f || clamped >= 0.85f) {
                lastReported = clamped
                val detail = if (size > 0) {
                    "${formatBytes(bytesRead)} / ${formatBytes(size)}"
                } else {
                    formatBytes(bytesRead)
                }
                onProgress(
                    ImportProgressUpdate(
                        phase = ImportPhase.EXTRACTING,
                        progress = clamped,
                        detail = detail,
                    ),
                )
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
                                entry.isLink -> {
                                    outFile.parentFile?.mkdirs()
                                    val linkSource = File(partialDir, entry.linkName.removePrefix("./").removePrefix("/"))
                                    if (linkSource.isFile) {
                                        linkSource.copyTo(outFile, overwrite = true)
                                        applyTarMode(outFile, entry.mode)
                                    } else {
                                        bytesRead += extractFile(tar, outFile, entry)
                                    }
                                }
                                else -> {
                                    outFile.parentFile?.mkdirs()
                                    bytesRead += extractFile(tar, outFile, entry)
                                    reportExtract(bytesRead)
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }

        onProgress(
            ImportProgressUpdate(
                phase = ImportPhase.INSTALLING,
                progress = 0.88f,
                detail = "Installing into proot-distro…",
            ),
        )

        val distro = detectDistroName(partialDir) ?: ProotContainerValidator.DEFAULT_DISTRO
        val installError = try {
            installExtractedLayout(partialDir, distro) { detail ->
                onProgress(
                    ImportProgressUpdate(
                        phase = ImportPhase.INSTALLING,
                        progress = 0.92f,
                        detail = detail,
                    ),
                )
            }
        } catch (e: OutOfMemoryError) {
            cleanupInstallTarget(distro)
            partialDir.deleteRecursively()
            return ImportResult.Error("Out of memory while installing. Free storage and try again.")
        } catch (e: Exception) {
            cleanupInstallTarget(distro)
            partialDir.deleteRecursively()
            return ImportResult.Error(e.message ?: "Install failed")
        }
        if (installError != null) {
            cleanupInstallTarget(distro)
            partialDir.deleteRecursively()
            return ImportResult.Error(installError)
        }

        onProgress(
            ImportProgressUpdate(
                phase = ImportPhase.FINALIZING,
                progress = 0.97f,
                detail = "Validating Ubuntu + XFCE…",
            ),
        )

        partialDir.deleteRecursively()
        onProgress(
            ImportProgressUpdate(
                phase = ImportPhase.FINALIZING,
                progress = 1f,
                detail = "",
            ),
        )
        return ImportResult.Success(ProotContainerValidator.containerDir(context, distro))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.roundToInt()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${"%.1f".format(mb)} MB"
        return "${"%.2f".format(mb / 1024.0)} GB"
    }

    private fun detectDistroName(partialDir: File): String? {
        partialDir.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (File(child, "rootfs/usr/bin/bash").isFile) return child.name
        }
        return null
    }

    private fun installExtractedLayout(
        partialDir: File,
        defaultDistro: String,
        onDetail: (String) -> Unit = {},
    ): String? {
        val prefix = TermuxLayout.prefixDir(context)
        val runtimeDir = File(prefix, "var/lib/proot-distro").also { it.mkdirs() }

        val packaged = detectDistroName(partialDir)?.let { File(partialDir, it) }
        val dest = File(runtimeDir, "containers/$defaultDistro")
        dest.parentFile?.mkdirs()

        try {
            when {
                packaged != null -> {
                    if (dest.exists()) dest.deleteRecursively()
                    onDetail("Moving Ubuntu container…")
                    moveDirectoryPreservingSymlinks(packaged, dest)
                }
                File(partialDir, "usr/bin/bash").isFile -> {
                    if (dest.exists()) dest.deleteRecursively()
                    val rootfs = File(dest, "rootfs").also { it.mkdirs() }
                    onDetail("Moving rootfs…")
                    partialDir.listFiles()?.forEach { entry ->
                        val target = File(rootfs, entry.name)
                        moveEntryPreservingSymlinks(entry, target)
                    }
                    writeDefaultManifest(dest)
                }
                else -> return "Invalid archive: expected ubuntu/rootfs/ layout"
            }
        } catch (e: Exception) {
            cleanupInstallTarget(defaultDistro)
            throw e
        }

        ProotContainerSysdata.installIfNeeded(context, dest)
        val rootfs = File(dest, "rootfs")
        if (!File(rootfs, "usr/bin/bash").isFile) {
            return "Imported container missing usr/bin/bash"
        }
        if (!File(rootfs, "usr/bin/xfce4-session").isFile &&
            !File(rootfs, "usr/bin/startxfce4").isFile
        ) {
            return "Imported container missing XFCE desktop"
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

    private fun cleanupInstallTarget(distro: String) {
        try {
            ProotContainerValidator.containerDir(context, distro).deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun moveEntryPreservingSymlinks(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        if (source.renameTo(dest)) return
        if (source.isDirectory) {
            moveDirectoryPreservingSymlinks(source, dest)
        } else {
            copyFilePreservingSymlinks(source, dest)
            source.delete()
        }
    }

    private fun moveDirectoryPreservingSymlinks(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        if (source.renameTo(dest)) return
        if (!dest.exists() && !dest.mkdirs()) {
            throw IllegalStateException("Failed to create $dest")
        }
        val children = source.listFiles() ?: emptyArray()
        for (child in children) {
            moveEntryPreservingSymlinks(child, File(dest, child.name))
        }
        source.delete()
    }

    private fun copyFilePreservingSymlinks(source: File, dest: File) {
        val path = source.toPath()
        if (Files.isSymbolicLink(path)) {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            Files.createSymbolicLink(dest.toPath(), Files.readSymbolicLink(path))
        } else {
            source.copyTo(dest, overwrite = true)
        }
    }

    private fun applyTarMode(file: File, mode: Int) {
        if (mode and 64 != 0) file.setExecutable(true, false)
        if (mode and 128 != 0) file.setReadable(true, false)
        if (mode and 1 != 0) file.setWritable(true, false)
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
        applyTarMode(outFile, mode)
        return bytesRead
    }
}
