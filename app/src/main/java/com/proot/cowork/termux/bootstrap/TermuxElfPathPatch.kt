package com.proot.cowork.termux.bootstrap

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Patches null-terminated path strings in ELF binaries when the replacement fits in-place.
 * Shared libraries under prefix/lib are never prefix-patched — accidental matches corrupt ELF shdrs.
 * libapt is patched separately via [patchLibAptIfNeeded]; python via [patchPythonRuntime].
 */
object TermuxElfPathPatch {

    private const val TAG = "TermuxElfPathPatch"
    private const val LEGACY_ROOT = "/data/data/com.termux/files"
    private const val LEGACY_ROOT_USER = "/data/user/0/com.termux/files"
    private const val LEGACY_CACHE = "/data/data/com.termux/cache"
    private const val LEGACY_CACHE_USER = "/data/user/0/com.termux/cache"

    private fun isPythonRuntimeBinary(name: String): Boolean =
        name == "python3.13" || name == "python3" || name.startsWith("libpython")

    // lib-dynload native modules must never be prefix-patched; accidental matches corrupt ELF shdrs.
    private fun shouldSkipElfBinaryPatch(file: File, prefix: File): Boolean {
        if (isPythonRuntimeBinary(file.name)) return true
        val prefixPath = prefix.absolutePath
        val path = file.absolutePath
        return path.startsWith("$prefixPath/lib/python3.")
    }

    /** Patch libapt/apt-key paths baked into libapt-pkg (com.termux -> com.proot). */
    fun patchLibAptIfNeeded(prefix: File, elfRoot: String, filesRoot: String, cacheRoot: String): Boolean {
        val marker = File(prefix, ".termux_libapt_patched_v1")
        if (marker.isFile) return true

        val targets = mutableListOf<File>()
        File(prefix, "lib").listFiles()
            ?.filter { it.isFile && it.name.startsWith("libapt") }
            ?.let { targets.addAll(it) }
        listOf("apt.real", "dpkg.real", "gpgv", "gpg").forEach { name ->
            val bin = File(prefix, "bin/$name")
            if (bin.isFile) targets.add(bin)
        }

        var patched = 0
        targets.forEach { file ->
            patched += patchFile(file, LEGACY_ROOT, elfRoot)
            patched += patchFile(file, LEGACY_ROOT_USER, filesRoot)
            patched += patchFile(file, LEGACY_CACHE, cacheRoot)
            patched += patchFile(file, LEGACY_CACHE_USER, cacheRoot)
        }
        marker.createNewFile()
        Log.i(TAG, "libapt: patched $patched strings in ${targets.size} files (elfRoot=$elfRoot)")
        return true
    }

    fun patchBinary(file: File, elfRoot: String, filesRoot: String): Int {
        if (!file.isFile || !isElf(file)) return 0
        var patched = 0
        listOf(
            LEGACY_ROOT to elfRoot,
            LEGACY_ROOT_USER to filesRoot,
        ).forEach { (from, to) ->
            patched += patchFile(file, from, to)
        }
        if (patched > 0) {
            Log.i(TAG, "patched $patched strings in ${file.name}")
        }
        return patched
    }

    fun applyIfNeeded(prefix: File, elfRoot: String, filesRoot: String): Boolean {
        val marker = File(prefix, ".termux_elf_patched_v5")
        if (marker.isFile) return true
        File(prefix, ".termux_elf_patched_v4").delete()
        File(prefix, ".termux_elf_patched_v3").delete()

        var patched = 0
        val roots = listOf(
            File(prefix, "libexec"),
            File(prefix, "bin"),
        )
        val replacements = listOf(
            LEGACY_ROOT to elfRoot,
            LEGACY_ROOT_USER to filesRoot,
        )
        roots.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                if (shouldSkipElfBinaryPatch(file, prefix)) return@forEach
                if (file.name.startsWith("libapt")) return@forEach
                if (file.name == "apt" || file.name == "apt.real") return@forEach
                if (!file.name.endsWith(".so") && !isElf(file)) return@forEach
                replacements.forEach { (from, to) ->
                    patched += patchFile(file, from, to)
                }
            }
        }
        val pythonPatched = patchPythonRuntime(prefix, elfRoot, filesRoot)
        Log.i(
            TAG,
            "patched $patched ELF strings + $pythonPatched python paths under ${prefix.absolutePath}",
        )
        marker.createNewFile()
        return true
    }

    /** Only replace full known path strings in python ELFs (never prefix-match inside libpython). */
    fun patchPythonRuntime(prefix: File, elfRoot: String, filesRoot: String): Int {
        val homeRoot = File(filesRoot, "home").absolutePath
        val exact = listOf(
            "$LEGACY_ROOT/usr/lib:$LEGACY_ROOT/usr/lib" to "$elfRoot/usr/lib:$elfRoot/usr/lib",
            "$LEGACY_ROOT/usr/lib" to "$elfRoot/usr/lib",
            "$LEGACY_ROOT/usr" to "$elfRoot/usr",
            "$LEGACY_ROOT/home" to homeRoot,
            "$LEGACY_ROOT/usr/bin/bash" to "$elfRoot/usr/bin/bash",
            "$LEGACY_ROOT/usr/bin/login" to "$elfRoot/usr/bin/login",
            "$LEGACY_ROOT_USER/usr/lib:$LEGACY_ROOT_USER/usr/lib" to "$filesRoot/usr/lib:$filesRoot/usr/lib",
            "$LEGACY_ROOT_USER/usr/lib" to "$filesRoot/usr/lib",
            "$LEGACY_ROOT_USER/usr" to "$filesRoot/usr",
            "$LEGACY_ROOT_USER/home" to homeRoot,
            "$LEGACY_ROOT_USER/usr/bin/bash" to "$filesRoot/usr/bin/bash",
            "$LEGACY_ROOT_USER/usr/bin/login" to "$filesRoot/usr/bin/login",
        )
        val targets = listOf(
            File(prefix, "lib/libpython3.13.so"),
            File(prefix, "lib/libpython3.so"),
            File(prefix, "bin/python3.13"),
            File(prefix, "bin/python3"),
        )
        var patched = 0
        targets.filter { it.isFile }.forEach { file ->
            patched += patchExactStrings(file, exact)
        }
        if (patched > 0) {
            Log.i(TAG, "python runtime: patched $patched exact path strings")
        }
        return patched
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

    private fun patchExactStrings(file: File, replacements: List<Pair<String, String>>): Int {
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return 0
        }
        var count = 0
        var idx = 0
        while (idx < bytes.size) {
            var end = idx
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            if (end == idx) {
                idx++
                continue
            }
            val slot = bytes.copyOfRange(idx, end).toString(Charsets.US_ASCII)
            val replacement = replacements.find { it.first == slot }?.second
            if (replacement != null) {
                val newBytes = replacement.toByteArray(Charsets.US_ASCII)
                val slotLen = end - idx
                if (newBytes.size <= slotLen) {
                    System.arraycopy(newBytes, 0, bytes, idx, newBytes.size)
                    for (i in newBytes.size until slotLen) {
                        bytes[idx + i] = 0
                    }
                    count++
                }
            }
            idx = end + 1
        }
        if (count > 0) {
            file.writeBytes(bytes)
        }
        return count
    }

    private fun patchFile(file: File, legacy: String, replacement: String): Int {
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return 0
        }
        val oldPrefix = legacy.toByteArray(Charsets.US_ASCII)
        val newPrefix = replacement.toByteArray(Charsets.US_ASCII)
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
