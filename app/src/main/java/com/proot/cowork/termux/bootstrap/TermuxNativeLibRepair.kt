package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Restores shared libraries under prefix/lib when broad ELF path patching corrupted them.
 * CI ships pristine copies inside bootstrap.bin.
 */
object TermuxNativeLibRepair {

    private const val TAG = "TermuxNativeLibRepair"
    private const val MARKER = ".termux_native_lib_repaired_v1"

    fun applyIfNeeded(
        context: Context,
        prefix: File,
        elfRoot: String,
        filesRoot: String,
        cacheRoot: String,
    ): Boolean {
        if (File(prefix, MARKER).isFile && probeNativeLinker(context, prefix)) {
            return true
        }
        if (probeNativeLinker(context, prefix)) {
            File(prefix, MARKER).createNewFile()
            return true
        }

        Log.w(TAG, "native linker probe failed; restoring lib/ from bootstrap.bin")
        if (!restoreLibTree(context, prefix)) {
            Log.e(TAG, "failed to restore lib/ from bootstrap.bin")
            return false
        }

        File(prefix, ".termux_libapt_patched_v1").delete()
        File(prefix, ".termux_python_repaired_v1").delete()
        File(prefix, ".termux_python_repaired_v2").delete()
        File(prefix, ".termux_python_repaired_v3").delete()
        TermuxElfPathPatch.patchLibAptIfNeeded(prefix, elfRoot, filesRoot, cacheRoot)
        TermuxPythonRepair.applyIfNeeded(context, prefix, elfRoot, filesRoot)

        val ok = probeNativeLinker(context, prefix)
        if (ok) {
            File(prefix, MARKER).createNewFile()
            Log.i(TAG, "restored native libs from bootstrap")
        } else {
            Log.e(TAG, "native linker still broken after lib restore")
        }
        return ok
    }

    private fun restoreLibTree(context: Context, prefix: File): Boolean {
        return try {
            context.assets.open("bootstrap.bin").use { input ->
                TarArchiveInputStream(GZIPInputStream(BufferedInputStream(input))).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name == "lib" || name.startsWith("lib/")) {
                            val outFile = File(prefix, name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                outFile.parentFile?.mkdirs()
                                outFile.delete()
                                try {
                                    Os.symlink(entry.linkName, outFile.absolutePath)
                                } catch (e: ErrnoException) {
                                    Log.e(TAG, "symlink failed: ${outFile.absolutePath}", e)
                                    return false
                                }
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out -> tar.copyTo(out) }
                                if (entry.mode and 0b001_001_001 != 0) {
                                    chmodExecutable(outFile)
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreLibTree failed", e)
            false
        }
    }

    private fun probeNativeLinker(context: Context, prefix: File): Boolean {
        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        val env = TermuxShellEnvironment.buildProcessEnvironment(context)
        val pb = ProcessBuilder(bash.absolutePath, "-c", "true")
        pb.directory(TermuxBootstrap.homeDir(context))
        pb.environment().clear()
        pb.environment().putAll(env)
        return try {
            pb.start().waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "native linker probe failed", e)
            false
        }
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(
                file.absolutePath,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR,
            )
        } catch (_: ErrnoException) {
            file.setExecutable(true, false)
        }
    }
}
