package com.proot.cowork.termux.bootstrap

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Patches null-terminated path strings in ELF binaries when the replacement fits in-place.
 * Complements text patching; many apt/dpkg libs still embed `/data/data/com.termux/files`.
 */
object TermuxElfPathPatch {

    private const val TAG = "TermuxElfPathPatch"
    private const val LEGACY_ROOT = "/data/data/com.termux/files"

    fun applyIfNeeded(prefix: File, filesRoot: String): Boolean {
        val marker = File(prefix, ".termux_elf_patched_v1")
        if (marker.isFile) return true

        var patched = 0
        val roots = listOf(
            File(prefix, "lib"),
            File(prefix, "libexec"),
            File(prefix, "bin"),
        )
        roots.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                if (!file.name.endsWith(".so") && !isElf(file)) return@forEach
                patched += patchFile(file, filesRoot)
            }
        }
        Log.i(TAG, "patched $patched strings under ${prefix.absolutePath}")
        marker.createNewFile()
        return true
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

    private fun patchFile(file: File, filesRoot: String): Int {
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return 0
        }
        val oldPrefix = LEGACY_ROOT.toByteArray(Charsets.US_ASCII)
        val newPrefix = filesRoot.toByteArray(Charsets.US_ASCII)
        var count = 0
        var idx = 0
        while (idx < bytes.size) {
            val hit = indexOf(bytes, oldPrefix, idx)
            if (hit < 0) break
            var end = hit
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            val slotLen = end - hit
            val suffix = bytes.copyOfRange(hit + oldPrefix.size, end)
            val newBytes = newPrefix + suffix
            if (newBytes.size > slotLen) {
                idx = hit + 1
                continue
            }
            System.arraycopy(newBytes, 0, bytes, hit, newBytes.size)
            for (i in newBytes.size until slotLen) {
                bytes[hit + i] = 0
            }
            count++
            idx = hit + slotLen
        }
        if (count > 0) {
            file.writeBytes(bytes)
        }
        return count
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from >= haystack.size) return -1
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
