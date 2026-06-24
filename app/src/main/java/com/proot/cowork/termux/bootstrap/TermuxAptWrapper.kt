package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Wraps apt to pass absolute Dir::* overrides. ELF path patching breaks libapt's
 * built-in Dir strings; CLI -o flags fix pkg reliably.
 */
object TermuxAptWrapper {

    private const val TAG = "TermuxAptWrapper"
    private const val HELPER = "cowork-apt"

    fun installIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_apt_wrapped_v1")
        if (marker.isFile) return

        val cacheRoot = context.cacheDir.absolutePath
        writeHelperScript(prefix, cacheRoot)
        ensureAptWrapper(prefix)
        marker.createNewFile()
        Log.i(TAG, "installed apt wrapper with Dir overrides")
    }

    private fun writeHelperScript(prefix: File, cacheRoot: String) {
        val prefixPath = prefix.absolutePath
        val sh = "$prefixPath/bin/sh"
        File(prefix, "bin/$HELPER").writeText(
            """
            |#!$sh
            |. "$prefixPath/etc/profile"
            |exec "${'$'}PREFIX/bin/apt.real" \
            |  -o Dir::Etc="${'$'}PREFIX/etc/apt" \
            |  -o Dir::State="${'$'}PREFIX/var/lib/apt" \
            |  -o Dir::State::status="${'$'}PREFIX/var/lib/dpkg/status" \
            |  -o Dir::Cache="$cacheRoot/apt" \
            |  -o Dir::Cache::archives="$cacheRoot/apt/archives" \
            |  -o Dir::Bin::methods="${'$'}PREFIX/lib/apt/methods" \
            |  -o Dir::Bin::dpkg="${'$'}PREFIX/bin/dpkg" \
            |  -o Dir::Log="${'$'}PREFIX/var/log/apt" \
            |  "${'$'}@"
            """.trimMargin(),
        ).also { chmodExecutable(it) }
    }

    private fun ensureAptWrapper(prefix: File) {
        val real = File(prefix, "bin/apt.real")
        val apt = File(prefix, "bin/apt")
        if (!real.isFile) {
            if (apt.isFile && isElf(apt)) {
                if (!apt.renameTo(real)) {
                    Log.w(TAG, "failed to rename apt to apt.real")
                    return
                }
            } else {
                return
            }
        }
        val prefixPath = prefix.absolutePath
        File(prefix, "bin/apt").writeText(
            """
            |#!$prefixPath/bin/sh
            |. "$prefixPath/etc/profile"
            |exec "$prefixPath/bin/$HELPER" "${'$'}@"
            """.trimMargin(),
        ).also { chmodExecutable(it) }
    }

    private fun isElf(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.read(magic)
                magic[0] == 0x7f.toByte() && magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(
                file.absolutePath,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR,
            )
        } catch (_: Exception) {
            file.setExecutable(true, false)
        }
    }
}
