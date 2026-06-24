package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

object AssetExtractor {

  private const val TAG = "TermuxBootstrap"

    fun extractGzipTar(input: InputStream, targetDir: File): Boolean {
        val marker = File(targetDir, ".extraction_complete")
        if (marker.exists()) return true

        targetDir.mkdirs()
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(input))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else if (entry.isSymbolicLink) {
                    outFile.parentFile?.mkdirs()
                    outFile.delete()
                    try {
                        Os.symlink(entry.linkName, outFile.absolutePath)
                    } catch (e: ErrnoException) {
                        Log.e(TAG, "symlink failed: ${outFile.absolutePath} -> ${entry.linkName}", e)
                        return false
                    }
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> tar.copyTo(out) }
                    if (entry.mode and 0b001_001_001 != 0) {
                        chmodExecutable(outFile)
                    }
                }
                entry = tar.nextEntry
            }
        }
        marker.createNewFile()
        return true
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(file.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR)
        } catch (_: ErrnoException) {
            file.setExecutable(true, false)
        }
    }
}

object TermuxBootstrap {

    private const val TAG = "TermuxBootstrap"

    fun prefixDir(context: Context): File = File(context.filesDir, "usr")

    fun bashExecutable(context: Context): File = File(prefixDir(context), "bin/bash")

    fun nativeBash(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libbash.so")

    /** Path to pass to TerminalSession — prefers prefix bin/bash, falls back to libbash.so. */
    fun shellExecutable(context: Context): File? {
        val bash = bashExecutable(context)
        if (bash.canExecute()) return bash
        val native = nativeBash(context)
        return native.takeIf { it.canExecute() }
    }

    fun isInstalled(context: Context): Boolean = shellExecutable(context) != null

    fun ensureInstalled(context: Context): Boolean {
        val prefix = prefixDir(context)
        val marker = File(prefix, ".extraction_complete")

        if (!marker.isFile) {
            context.assets.open("bootstrap.bin").use { input ->
                if (!AssetExtractor.extractGzipTar(input, prefix)) return false
            }
        }

        if (!linkBash(context)) {
            Log.e(TAG, "failed to link bash into ${bashExecutable(context).absolutePath}")
            return false
        }
        ensureLayout(prefix)

        val ok = shellExecutable(context) != null
        if (!ok) {
            marker.delete()
        }
        return ok
    }

    private fun linkBash(context: Context): Boolean {
        val nativeBash = nativeBash(context)
        if (!nativeBash.isFile) {
            Log.e(TAG, "missing ${nativeBash.absolutePath}")
            return false
        }
        val binDir = File(prefixDir(context), "bin").also { it.mkdirs() }
        val bash = File(binDir, "bash")
        return try {
            if (bash.exists()) bash.delete()
            Os.symlink(nativeBash.absolutePath, bash.absolutePath)
            true
        } catch (e: ErrnoException) {
            Log.e(TAG, "Os.symlink bash failed", e)
            false
        }
    }

    private fun ensureLayout(prefix: File) {
        File(prefix, "tmp/.X11-unix").mkdirs()
        File(prefix, "home").mkdirs()
    }

    fun shellEnvironment(context: Context): Array<String> {
        val prefix = prefixDir(context).absolutePath
        val home = File(prefix, "home").absolutePath
        val tmp = File(prefix, "tmp").absolutePath
        val lib = File(prefix, "lib").absolutePath
        return arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$prefix/bin",
            "LD_LIBRARY_PATH=$lib",
            "TMPDIR=$tmp",
            "DISPLAY=:0",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
        )
    }
}
