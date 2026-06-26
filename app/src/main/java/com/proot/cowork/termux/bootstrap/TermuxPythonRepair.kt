package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Restores a clean Python runtime when [TermuxElfPathPatch] corrupted lib-dynload modules
 * or [TermuxPathPatch] damaged stdlib `.py` files.
 * CI bundles an unpatched copy in assets/python-runtime.tar.gz (fallback: bootstrap.bin).
 */
object TermuxPythonRepair {

    private const val TAG = "TermuxPythonRepair"
    private const val ASSET = "python-runtime.tar.gz"
    private const val BOOTSTRAP_ASSET = "bootstrap.bin"

    fun applyIfNeeded(context: Context, prefix: File, elfRoot: String, filesRoot: String): Boolean {
        File(prefix, ".termux_python_repaired_v1").delete()
        File(prefix, ".termux_python_repaired_v2").delete()
        val marker = File(prefix, ".termux_python_repaired_v3")
        if (marker.isFile && probePython(context, prefix)) return true

        val restored = restoreFromAsset(context, prefix, ASSET) ||
            restorePythonFromBootstrap(context, prefix)
        if (!restored) {
            Log.w(TAG, "python-runtime assets missing; applying ELF path patch only")
        }

        TermuxElfPathPatch.patchPythonRuntime(prefix, elfRoot, filesRoot)
        val ok = probePython(context, prefix)
        if (ok) {
            marker.createNewFile()
            Log.i(TAG, "python runtime repaired")
        } else {
            marker.delete()
            Log.e(TAG, "python still broken after repair (check base64.py / proot-distro import)")
        }
        return ok
    }

    private fun restoreFromAsset(context: Context, prefix: File, assetName: String): Boolean {
        return try {
            context.assets.open(assetName).use { input ->
                extractTarGz(input, prefix) { name ->
                    name == "lib" || name.startsWith("lib/") || name == "bin" || name.startsWith("bin/")
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to extract $assetName: ${e.message}")
            false
        }
    }

    private fun restorePythonFromBootstrap(context: Context, prefix: File): Boolean {
        return try {
            context.assets.open(BOOTSTRAP_ASSET).use { input ->
                extractTarGz(input, prefix) { name ->
                    name == "lib/python3.13" || name.startsWith("lib/python3.13/") ||
                        name == "lib/libpython3.13.so" || name == "lib/libpython3.so" ||
                        name == "bin/python3.13" || name == "bin/python3"
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to extract python from $BOOTSTRAP_ASSET: ${e.message}")
            false
        }
    }

    private fun extractTarGz(
        input: InputStream,
        prefix: File,
        include: (String) -> Boolean,
    ) {
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(input))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val name = entry.name
                if (include(name)) {
                    val outFile = File(prefix, name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> tar.copyTo(out) }
                        if (entry.mode and 0b001_001_001 != 0) {
                            outFile.setExecutable(true, false)
                        }
                    }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun probePython(context: Context, prefix: File): Boolean {
        val python = File(prefix, "bin/python3.13")
        if (!python.canExecute()) return false
        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        val env = TermuxShellEnvironment.buildProcessEnvironment(context)
        val probeScript = buildString {
            append(python.absolutePath)
            append(" -c \"")
            append("import base64; ")
            append("import importlib.metadata; ")
            append("import ctypes; ")
            append("from proot_distro.cli import main")
            append("\"")
        }
        val pb = ProcessBuilder(bash.absolutePath, "-c", probeScript)
        pb.directory(TermuxBootstrap.homeDir(context))
        pb.environment().clear()
        pb.environment().putAll(env)
        return try {
            pb.start().waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "python probe failed", e)
            false
        }
    }
}
