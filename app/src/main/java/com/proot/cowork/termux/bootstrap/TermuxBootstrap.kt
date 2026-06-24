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

    fun filesDir(context: Context): File = context.filesDir

    fun prefixDir(context: Context): File = File(context.filesDir, "usr")

    /** Real Termux home is `files/home`, not `files/usr/home`. */
    fun homeDir(context: Context): File = File(context.filesDir, "home")

    fun bashExecutable(context: Context): File = File(prefixDir(context), "bin/bash")

    fun loginExecutable(context: Context): File = File(prefixDir(context), "bin/login")

    fun nativeBash(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libbash.so")

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

        if (!installBash(context)) {
            Log.e(TAG, "failed to install bash into ${bashExecutable(context).absolutePath}")
            return false
        }

        ensureProotIfMissing(context, prefix)

        if (!TermuxPathPatch.applyIfNeeded(context, prefix)) {
            return false
        }

        // installBash runs before path patch; re-patch copied libbash (RUNPATH + strings).
        patchBashElf(context)

        ensureLayout(context)
        ensureCacheLayout(context)
        TermuxStorageSetup.ensureStorageLinks(context)

        if (!TermuxExecSetup.applyIfNeeded(context, prefix)) {
            Log.w(TAG, "termux-exec setup failed; pkg may not work")
        }

        if (!TermuxBootstrapRunner.runSecondStageIfNeeded(context)) {
            Log.w(TAG, "bootstrap second stage failed; shell may still work")
        }

        val ok = shellExecutable(context) != null
        if (!ok) {
            marker.delete()
        }
        return ok
    }

    /** Copy libbash.so into prefix/bin/bash and patch embedded com.termux paths. */
    private fun installBash(context: Context): Boolean {
        val nativeBash = nativeBash(context)
        if (!nativeBash.isFile) {
            Log.e(TAG, "missing ${nativeBash.absolutePath}")
            return false
        }
        val binDir = File(prefixDir(context), "bin").also { it.mkdirs() }
        val bash = File(binDir, "bash")
        return try {
            if (bash.exists()) bash.delete()
            nativeBash.inputStream().use { input ->
                bash.outputStream().use { output -> input.copyTo(output) }
            }
            chmodExecutable(bash)
            true
        } catch (e: Exception) {
            Log.e(TAG, "install bash failed", e)
            false
        }
    }

    private fun patchBashElf(context: Context) {
        val bash = bashExecutable(context)
        if (!bash.isFile) return
        val elfRoot = "/data/data/${context.packageName}/files"
        val filesRoot = context.filesDir.absolutePath
        TermuxElfPathPatch.patchBinary(bash, elfRoot, filesRoot)
    }

    /** Upgrades from older bootstrap builds that lack bin/proot. */
    private fun ensureProotIfMissing(context: Context, prefix: File) {
        val proot = File(prefix, "bin/proot")
        if (proot.canExecute()) return
        try {
            context.assets.open("termux-proot.tar.gz").use { input ->
                extractGzipTarInto(input, prefix)
            }
            chmodExecutable(proot)
        } catch (e: Exception) {
            Log.w(TAG, "termux-proot.tar.gz missing or extract failed: ${e.message}")
        }
    }

    private fun extractGzipTarInto(input: InputStream, targetDir: File) {
        targetDir.mkdirs()
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(input))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
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
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(file.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR)
        } catch (_: ErrnoException) {
            file.setExecutable(true, false)
        }
    }

    private fun ensureLayout(context: Context) {
        val prefix = prefixDir(context)
        File(prefix, "tmp/.X11-unix").mkdirs()
        File(prefix, "var/tmp").mkdirs()
        val home = homeDir(context)
        home.mkdirs()
        linkDynamicMotd(prefix, home)
    }

    private fun ensureCacheLayout(context: Context) {
        File(context.cacheDir, "apt/archives").mkdirs()
    }

    /** Copy dynamic motd into ~/.termux (symlink execution fails on Android). */
    private fun linkDynamicMotd(prefix: File, home: File) {
        val motdSh = File(prefix, "etc/motd.sh")
        if (!motdSh.isFile) return
        chmodExecutable(motdSh)
        val termuxDir = File(home, ".termux").also { it.mkdirs() }
        val dest = File(termuxDir, "motd.sh")
        try {
            if (dest.exists()) dest.delete()
            motdSh.copyTo(dest, overwrite = true)
            chmodExecutable(dest)
        } catch (e: Exception) {
            Log.w(TAG, "motd.sh copy failed", e)
        }
    }

    fun shellEnvironment(context: Context): Array<String> =
        TermuxShellEnvironment.build(context)
}
