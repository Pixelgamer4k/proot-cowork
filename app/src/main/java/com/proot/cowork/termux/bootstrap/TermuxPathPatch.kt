package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Rewrites `/data/data/com.termux/files` paths baked into the official bootstrap
 * so the prefix works under this app's private files directory.
 */
object TermuxPathPatch {

    private const val TAG = "TermuxPathPatch"
    private const val LEGACY_ROOT = "/data/data/com.termux/files"
    private const val LEGACY_ROOT_USER = "/data/user/0/com.termux/files"

    fun applyIfNeeded(context: Context, prefix: File): Boolean {
        val marker = File(prefix, ".termux_paths_patched_v1")
        if (marker.isFile) return true

        val filesRoot = context.filesDir.absolutePath
        Log.i(TAG, "Patching Termux bootstrap paths -> $filesRoot")

        patchTree(prefix, filesRoot)
        patchLoginExec(prefix)
        marker.createNewFile()
        return true
    }

    private fun patchTree(root: File, filesRoot: String) {
        if (!root.isDirectory) return
        root.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.endsWith(".so") || file.name.endsWith(".a")) return@forEach
            try {
                val bytes = file.readBytes()
                if (bytes.indexOf(0) != -1 && !looksLikeText(bytes)) return@forEach
                var text = bytes.toString(StandardCharsets.UTF_8)
                val original = text
                text = text.replace(LEGACY_ROOT, filesRoot)
                text = text.replace(LEGACY_ROOT_USER, filesRoot)
                if (text != original) {
                    file.writeText(text, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.w(TAG, "skip ${file.absolutePath}: ${e.message}")
            }
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val sample = bytes.take(minOf(bytes.size, 512))
        return sample.all { b -> b == '\n'.code.toByte() || b == '\r'.code.toByte() || b == '\t'.code.toByte() || b in 32..126 }
    }

    /**
     * libbash.so still has Termux paths compiled in; avoid `bash -l` reading them.
     * Source profile from patched login script, then start an interactive bash.
     */
    private fun patchLoginExec(prefix: File) {
        val login = File(prefix, "bin/login")
        if (!login.isFile) return
        val prefixPath = prefix.absolutePath
        val oldBlock = """
if [ -n "${'$'}TERM" ]; then
	exec "${'$'}SHELL" -l "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()
        val newBlock = """
if [ -n "${'$'}TERM" ]; then
	. "$prefixPath/etc/profile"
	exec -a "-bash" "${'$'}SHELL" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()
        val content = login.readText()
        if (!content.contains(oldBlock)) {
            Log.w(TAG, "login exec block not found; leaving login script unchanged")
            return
        }
        login.writeText(content.replace(oldBlock, newBlock))
    }
}
