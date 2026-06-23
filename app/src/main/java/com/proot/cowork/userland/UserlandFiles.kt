package com.proot.cowork.userland

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.nio.file.Files

/**
 * Vendored from UserLAnd (BSD-2-Clause) — paths and symlinks for proot/busybox support.
 */
class UserlandFiles(
    context: Context,
    libDirPath: String,
    private val symlinker: Symlinker = Symlinker(),
) {
    val filesDir: File = context.filesDir
    val libDir: File = File(libDirPath)
    val supportDir: File = File(filesDir, "support")
    val emulatedScopedDir = context.getExternalFilesDir(null)!!
    val emulatedUserDir = File(emulatedScopedDir, "storage")

    val sdCardScopedDir: File? = resolveSdCardScopedStorage(context)
    val sdCardUserDir: File? = sdCardScopedDir?.let { File(it, "storage") }

    val busybox = File(supportDir, "busybox")
    val proot = File(supportDir, "proot")

    init {
        emulatedUserDir.mkdirs()
        sdCardUserDir?.mkdirs()
        setupLinks()
    }

    fun makePermissionsUsable(containingDirectoryPath: String, filename: String) {
        val containingDirectory = File(containingDirectoryPath)
        containingDirectory.mkdirs()
        val pb = ProcessBuilder(listOf(busybox.path, "chmod", "0777", filename))
        pb.directory(containingDirectory)
        pb.start().waitFor()
    }

    private fun resolveSdCardScopedStorage(context: Context): File? {
        val externals = context.getExternalFilesDirs(null)
        return if (externals.size > 1) externals[1] else null
    }

    private fun String.toSupportName(): String =
        substringAfter("lib_").substringBeforeLast(".so")

    private fun isUserlandHostLib(fileName: String): Boolean {
        if (!fileName.startsWith("lib_") || !fileName.endsWith(".so")) return false
        if (fileName.startsWith("libandroid") || fileName.startsWith("libdatastore")) return false
        return true
    }

    private fun setupLinks() {
        supportDir.mkdirs()
        supportDir.listFiles()?.forEach { entry ->
            if (Files.isSymbolicLink(entry.toPath())) {
                entry.delete()
            }
        }
        libDir.listFiles()?.forEach { libFile ->
            if (!isUserlandHostLib(libFile.name)) return@forEach
            var libFileName = libFile.name
            if (libFileName.startsWith("lib_proot.") ||
                libFileName.startsWith("lib_libtalloc") ||
                libFileName.startsWith("lib_loader")
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (libFileName.endsWith(".a10.so")) {
                        libFileName = libFileName.replace(".a10.so", ".so")
                    } else {
                        return@forEach
                    }
                } else if (libFileName.endsWith(".a10.so")) {
                    return@forEach
                }
            }
            val name = libFileName.toSupportName()
            val linkFile = File(supportDir, name)
            linkFile.delete()
            symlinker.createSymlink(libFile.path, linkFile.path)
        }
    }
}

class Symlinker {
    fun createSymlink(targetPath: String, linkPath: String) {
        Os.symlink(targetPath, linkPath)
    }
}
