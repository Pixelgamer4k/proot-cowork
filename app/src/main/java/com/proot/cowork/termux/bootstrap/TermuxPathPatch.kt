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
        val marker = File(prefix, ".termux_paths_patched_v4")
        if (marker.isFile) return true

        val filesRoot = context.filesDir.absolutePath
        val hadTree = File(prefix, ".termux_paths_patched_v1").isFile ||
            File(prefix, ".termux_paths_patched_v2").isFile ||
            File(prefix, ".termux_paths_patched_v3").isFile
        if (!hadTree) {
            Log.i(TAG, "Patching Termux bootstrap paths -> $filesRoot")
            patchTree(prefix, filesRoot)
        }
        patchLoginExec(prefix)
        TermuxStorageSetup.patchSetupStorageScript(prefix)
        TermuxElfPathPatch.applyIfNeeded(prefix, filesRoot)
        TermuxProotWrapper.installIfNeeded(prefix)
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
        return sample.all { b ->
            b == '\n'.code.toByte() || b == '\r'.code.toByte() || b == '\t'.code.toByte() || b in 32..126
        }
    }

    /** Interactive shell uses direct bash; apt/dpkg use [TermuxProotWrapper]. */
    private fun patchLoginExec(prefix: File) {
        val login = File(prefix, "bin/login")
        if (!login.isFile) return
        val prefixPath = prefix.absolutePath
        val targetBlock = loginExecBlockDirectBash(prefixPath)
        var content = login.readText()
        if (content.contains("COWORK_DIRECT_BASH")) return

        val replacements = listOf(
            loginExecBlockProot(prefixPath),
            loginExecBlockDirectBashLegacy(prefixPath),
            brokenBashismBlock(prefixPath),
            stockBlock(),
        )
        for (old in replacements) {
            if (content.contains(old)) {
                content = content.replace(old, targetBlock)
                login.writeText(content)
                return
            }
        }
        Log.w(TAG, "login exec block not found; leaving login script unchanged")
    }

    private fun stockBlock() = """
if [ -n "${'$'}TERM" ]; then
	exec "${'$'}SHELL" -l "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()

    private fun brokenBashismBlock(prefixPath: String) = """
if [ -n "${'$'}TERM" ]; then
	. "$prefixPath/etc/profile"
	exec -a "-bash" "${'$'}SHELL" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()

    private fun loginExecBlockDirectBashLegacy(prefixPath: String) = """
if [ -n "${'$'}TERM" ]; then
	. "$prefixPath/etc/profile"
	exec "$prefixPath/bin/bash" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()

    private fun loginExecBlockDirectBash(prefixPath: String) = """
if [ -n "${'$'}TERM" ]; then
	. "$prefixPath/etc/profile"
	# COWORK_DIRECT_BASH — interactive shell without proot (apt/dpkg use cowork-proot)
	exec "$prefixPath/bin/bash" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()

  private fun loginExecBlockProot(prefixPath: String) = """
if [ -n "${'$'}TERM" ]; then
	. "$prefixPath/etc/profile"
	export PROOT_NO_SECCOMP=1
	_real="${'$'}{TERMUX__ROOTFS_DIR:-$(dirname "$prefixPath")}"
	# COWORK_PROOT_BIND: map hardcoded com.termux paths for apt/pkg ELFs
	if [ -x "$prefixPath/bin/proot" ]; then
		_proot_bind="-b ${'$'}_real:/data/data/com.termux/files"
		for _mnt in /storage /storage/emulated /storage/emulated/0; do
			[ -d "${'$'}_mnt" ] && _proot_bind="${'$'}_proot_bind -b ${'$'}_mnt:${'$'}_mnt"
		done
		[ -d /storage/emulated/0 ] && _proot_bind="${'$'}_proot_bind -b /storage/emulated/0:/sdcard"
		# shellcheck disable=SC2086
		exec "$prefixPath/bin/proot" ${'$'}_proot_bind -0 \
			"$prefixPath/bin/bash" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
	fi
	exec "$prefixPath/bin/bash" --noprofile --rcfile "$prefixPath/etc/bash.bashrc" -i "${'$'}@"
else
	exec "${'$'}SHELL" "${'$'}@"
fi
""".trimIndent()
}
