package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Wraps apt with absolute Dir::* overrides. libapt still embeds com.termux paths for
 * apt-key and apt.conf.d; [TermuxElfPathPatch.patchLibAptIfNeeded] fixes those ELFs.
 */
object TermuxAptWrapper {

    private const val TAG = "TermuxAptWrapper"
    private const val HELPER = "cowork-apt"

    fun installIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_apt_wrapped_v2")
        if (marker.isFile) return

        File(prefix, ".termux_apt_wrapped_v1").delete()
        val cacheRoot = context.cacheDir.absolutePath
        writeHelperScript(prefix, cacheRoot)
        ensureAptWrapper(prefix)
        marker.createNewFile()
        Log.i(TAG, "installed apt wrapper with absolute Dir overrides")
    }

    private fun writeHelperScript(prefix: File, cacheRoot: String) {
        val prefixPath = prefix.absolutePath
        val sh = "$prefixPath/bin/sh"
        val helper = File(prefix, "bin/$HELPER")
        helper.writeText(
            """
            |#!$sh
            |exec "$prefixPath/bin/apt.real" \
            |  -o Dir::Etc="$prefixPath/etc/apt" \
            |  -o Dir::State="$prefixPath/var/lib/apt" \
            |  -o Dir::State::status="$prefixPath/var/lib/dpkg/status" \
            |  -o Dir::State::tmpdir="$prefixPath/tmp" \
            |  -o Dir::Cache="$cacheRoot/apt" \
            |  -o Dir::Cache::archives="$cacheRoot/apt/archives" \
            |  -o Dir::Bin::methods="$prefixPath/lib/apt/methods" \
            |  -o Dir::Bin::dpkg="$prefixPath/bin/dpkg" \
            |  -o Dir::Bin::gpg="$prefixPath/bin/gpgv" \
            |  -o Dir::Log="$prefixPath/var/log/apt" \
            |  "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(helper)
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
        val aptWrapper = File(prefix, "bin/apt")
        aptWrapper.writeText(
            """
            |#!$prefixPath/bin/sh
            |exec "$prefixPath/bin/$HELPER" "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(aptWrapper)
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
