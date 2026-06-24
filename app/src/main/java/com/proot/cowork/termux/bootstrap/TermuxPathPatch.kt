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
    private const val LEGACY_CACHE = "/data/data/com.termux/cache"
    private const val LEGACY_CACHE_USER = "/data/user/0/com.termux/cache"

    fun applyIfNeeded(context: Context, prefix: File): Boolean {
        val marker = File(prefix, ".termux_paths_patched_v6")
        if (marker.isFile) return true

        val filesRoot = context.filesDir.absolutePath
        val cacheRoot = context.cacheDir.absolutePath
        val elfRoot = "/data/data/${context.packageName}/files"
        val replacements = buildReplacements(filesRoot, cacheRoot)

        TermuxBinaryRestore.unwrapPackageManagers(prefix)

        val hadTree = File(prefix, ".termux_paths_patched_v1").isFile ||
            File(prefix, ".termux_paths_patched_v2").isFile ||
            File(prefix, ".termux_paths_patched_v3").isFile ||
            File(prefix, ".termux_paths_patched_v4").isFile ||
            File(prefix, ".termux_paths_patched_v5").isFile
        if (!hadTree) {
            Log.i(TAG, "Patching Termux bootstrap paths -> $filesRoot")
            patchTree(prefix, replacements)
        } else {
            Log.i(TAG, "Re-patching critical Termux scripts -> $filesRoot")
            patchCriticalScripts(prefix, replacements)
        }

        patchLoginMotd(prefix)
        patchLoginExec(prefix)
        TermuxStorageSetup.patchSetupStorageScript(prefix)
        TermuxAptConfig.applyIfNeeded(context, prefix)
        TermuxElfPathPatch.applyIfNeeded(prefix, elfRoot, filesRoot)
        marker.createNewFile()
        return true
    }

    private fun buildReplacements(filesRoot: String, cacheRoot: String) =
        listOf(
            LEGACY_ROOT to filesRoot,
            LEGACY_ROOT_USER to filesRoot,
            LEGACY_CACHE to cacheRoot,
            LEGACY_CACHE_USER to cacheRoot,
        )

    private fun patchTree(root: File, replacements: List<Pair<String, String>>) {
        if (!root.isDirectory) return
        root.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.endsWith(".so") || file.name.endsWith(".a")) return@forEach
            patchTextFileIfNeeded(file, replacements)
        }
    }

    private fun patchCriticalScripts(prefix: File, replacements: List<Pair<String, String>>) {
        listOf(
            "bin/login",
            "bin/pkg",
            "bin/termux-setup-package-manager",
            "bin/termux-setup-storage",
            "etc/profile",
            "etc/motd.sh",
        ).forEach { rel ->
            val file = File(prefix, rel)
            if (file.isFile) patchTextFileIfNeeded(file, replacements)
        }
        File(prefix, "etc/profile.d").listFiles()?.forEach { file ->
            if (file.isFile) patchTextFileIfNeeded(file, replacements)
        }
    }

    private fun patchTextFileIfNeeded(file: File, replacements: List<Pair<String, String>>) {
        try {
            val bytes = file.readBytes()
            if (bytes.indexOf(0) != -1 && !looksLikeText(bytes)) return
            var text = bytes.toString(StandardCharsets.UTF_8)
            val original = text
            replacements.forEach { (from, to) -> text = text.replace(from, to) }
            if (text != original) {
                file.writeText(text, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "skip ${file.absolutePath}: ${e.message}")
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val sample = bytes.take(minOf(bytes.size, 512))
        return sample.all { b ->
            b == '\n'.code.toByte() || b == '\r'.code.toByte() || b == '\t'.code.toByte() || b in 32..126
        }
    }

    /** Source motd instead of executing through a symlink (Android blocks the latter). */
    private fun patchLoginMotd(prefix: File) {
        val login = File(prefix, "bin/login")
        if (!login.isFile) return
        var content = login.readText()
        if (content.contains("COWORK_MOTD_SOURCE")) return

        val newBlock = """
if [ -f "${'$'}HOME/.termux/motd.sh" ] && [ -r "${'$'}HOME/.termux/motd.sh" ]; then
	# COWORK_MOTD_SOURCE
	. "${'$'}HOME/.termux/motd.sh" 2>/dev/null || true""".trimIndent()

        val replaced = content.replace(
            Regex(
                """if \[ -f ~/.termux/motd\.sh \]; then\s*\n\s*\[ ! -x ~/.termux/motd\.sh \] && chmod u\+x ~/.termux/motd\.sh\s*\n\s*~/.termux/motd\.sh""",
            ),
            newBlock,
        )
        if (replaced != content) {
            login.writeText(replaced)
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
		_proot_bind="-b ${'$'}_real:/data/data/com.termux/files -b ${'$'}_real:${'$'}_real"
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
