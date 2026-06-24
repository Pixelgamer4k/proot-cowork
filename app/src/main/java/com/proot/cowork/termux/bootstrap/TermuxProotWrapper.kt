package com.proot.cowork.termux.bootstrap

import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Runs apt/dpkg through proot with com.termux path binds. Interactive login stays direct bash.
 */
object TermuxProotWrapper {

    private const val TAG = "TermuxProotWrapper"
    private const val HELPER = "cowork-proot"
    private val WRAPPED_BINARIES = listOf("apt", "dpkg")

    fun installIfNeeded(prefix: File) {
        val marker = File(prefix, ".termux_proot_wrapped_v1")
        if (marker.isFile) return

        writeHelperScript(prefix)
        WRAPPED_BINARIES.forEach { name -> wrapBinary(prefix, name) }
        marker.createNewFile()
        Log.i(TAG, "installed $HELPER wrappers for apt/dpkg")
    }

    private fun writeHelperScript(prefix: File) {
        val prefixPath = prefix.absolutePath
        val script = File(prefix, "bin/$HELPER")
        script.writeText(
            """
            |#!/bin/sh
            |# COWORK_PROOT_RUN — package manager helper (not used for interactive shell)
            |. "$prefixPath/etc/profile"
            |export PROOT_NO_SECCOMP=1
            |export PROOT_TMP_DIR="${'$'}PREFIX/var/tmp"
            |export PROOT_LOADER="${'$'}PREFIX/libexec/proot/loader"
            |mkdir -p "${'$'}PROOT_TMP_DIR"
            |_real="${'$'}{TERMUX__ROOTFS_DIR:-$(dirname "${'$'}PREFIX")}"
            |_bind="-b ${'$'}_real:/data/data/com.termux/files -b ${'$'}_real:${'$'}_real"
            |for _mnt in /storage /storage/emulated /storage/emulated/0; do
            |	[ -d "${'$'}_mnt" ] && _bind="${'$'}_bind -b ${'$'}_mnt:${'$'}_mnt"
            |done
            |[ -d /storage/emulated/0 ] && _bind="${'$'}_bind -b /storage/emulated/0:/sdcard"
            |# shellcheck disable=SC2086
            |exec "${'$'}PREFIX/bin/proot" --link2symlink ${'$'}_bind -0 "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(script)
    }

    private fun wrapBinary(prefix: File, name: String) {
        val bin = File(prefix, "bin/$name")
        val real = File(prefix, "bin/$name.real")
        if (!bin.isFile || real.isFile) return
        if (!isElf(bin)) return
        if (!bin.renameTo(real)) {
            Log.w(TAG, "failed to rename $name to $name.real")
            return
        }
        val prefixPath = prefix.absolutePath
        val wrapper = File(prefix, "bin/$name")
        wrapper.writeText(
            """
            |#!/bin/sh
            |. "$prefixPath/etc/profile"
            |exec "$prefixPath/bin/$HELPER" "$prefixPath/bin/$name.real" "${'$'}@"
            """.trimMargin(),
        )
        chmodExecutable(wrapper)
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
