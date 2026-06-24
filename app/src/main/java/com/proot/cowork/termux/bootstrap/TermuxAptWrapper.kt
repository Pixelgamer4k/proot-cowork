package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Shell wrappers for apt/dpkg with absolute Dir::* overrides and PREFIX/bin on PATH.
 * libapt embeds com.termux paths; [TermuxElfPathPatch.patchLibAptIfNeeded] patches those ELFs.
 */
object TermuxAptWrapper {

    private const val TAG = "TermuxAptWrapper"
    private const val APT_HELPER = "cowork-apt"
    private const val DPKG_HELPER = "cowork-dpkg"

    fun installIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_apt_wrapped_v4")
        if (marker.isFile) return

        File(prefix, ".termux_apt_wrapped_v1").delete()
        File(prefix, ".termux_apt_wrapped_v2").delete()
        File(prefix, ".termux_apt_wrapped_v3").delete()
        val cacheRoot = context.cacheDir.absolutePath
        val filesRoot = context.filesDir.absolutePath
        val prefixPath = prefix.absolutePath
        writeAptHelper(prefix, cacheRoot, filesRoot, prefixPath)
        ensureAptWrapper(prefix, prefixPath)
        ensureDpkgWrapper(prefix, filesRoot, prefixPath)
        marker.createNewFile()
        Log.i(TAG, "installed apt/dpkg wrappers (dpkg --root=$filesRoot)")
    }

    private fun writeAptHelper(prefix: File, cacheRoot: String, filesRoot: String, prefixPath: String) {
        val sh = "$prefixPath/bin/sh"
        val helper = File(prefix, "bin/$APT_HELPER")
        helper.writeText(
            """
            |#!$sh
            |export PATH="$prefixPath/bin:${'$'}PATH"
            |export LD_LIBRARY_PATH="$prefixPath/lib${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
            |exec "$prefixPath/bin/apt.real" \
            |  -o Dir::Etc="$prefixPath/etc/apt" \
            |  -o Dir::Etc::parts="$prefixPath/etc/apt/apt.conf.d" \
            |  -o Dir::Etc::sourcelist="$prefixPath/etc/apt/sources.list" \
            |  -o Dir::Etc::sourceparts="$prefixPath/etc/apt/sources.list.d" \
            |  -o Dir::State="$prefixPath/var/lib/apt" \
            |  -o Dir::State::status="$prefixPath/var/lib/dpkg/status" \
            |  -o Dir::State::tmpdir="$prefixPath/tmp" \
            |  -o Dir::Cache="$cacheRoot/apt" \
            |  -o Dir::Cache::archives="$cacheRoot/apt/archives" \
            |  -o Dir::Bin::methods="$prefixPath/lib/apt/methods" \
            |  -o Dir::Bin::dpkg="$prefixPath/bin/$DPKG_HELPER" \
            |  -o Dir::Bin::gpg="$prefixPath/bin/gpgv" \
            |  -o Dir::Log="$prefixPath/var/log/apt" \
            |  "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(helper)
    }

    private fun ensureAptWrapper(prefix: File, prefixPath: String) {
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
        val aptWrapper = File(prefix, "bin/apt")
        aptWrapper.writeText(
            """
            |#!$prefixPath/bin/sh
            |exec "$prefixPath/bin/$APT_HELPER" "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(aptWrapper)
    }

    private fun ensureDpkgWrapper(prefix: File, filesRoot: String, prefixPath: String) {
        val real = File(prefix, "bin/dpkg.real")
        val dpkg = File(prefix, "bin/dpkg")
        if (!real.isFile) {
            if (dpkg.isFile && isElf(dpkg)) {
                if (!dpkg.renameTo(real)) {
                    Log.w(TAG, "failed to rename dpkg to dpkg.real")
                    return
                }
            } else {
                return
            }
        }
        val sh = "$prefixPath/bin/sh"
        val helper = File(prefix, "bin/$DPKG_HELPER")
        helper.writeText(
            """
            |#!$sh
            |export PATH="$prefixPath/bin:${'$'}PATH"
            |export LD_LIBRARY_PATH="$prefixPath/lib${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
            |exec "$prefixPath/bin/dpkg.real" --root="$filesRoot" "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(helper)
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
