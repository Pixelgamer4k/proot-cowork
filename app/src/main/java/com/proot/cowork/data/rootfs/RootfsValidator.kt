package com.proot.cowork.data.rootfs

import android.content.Context
import com.proot.cowork.BuildConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object RootfsValidator {
    fun isValid(rootfsDir: File): Boolean {
        if (!rootfsDir.isDirectory) return false
        repairLayout(rootfsDir)
        val startScript = File(rootfsDir, "start-desktop.sh")
        if (!startScript.isFile) return false
        if (!startScript.canExecute()) {
            startScript.setExecutable(true, false)
        }
        if (!startScript.canExecute()) return false

        val bash = File(rootfsDir, "usr/bin/bash")
        if (!bash.isFile || bash.length() == 0L) return false

        val binLink = File(rootfsDir, "bin")
        if (!binLink.exists()) return false

        return true
    }

    fun hasVncStack(rootfsDir: File): Boolean {
        repairLayout(rootfsDir)
        return resolveGuestBinary(rootfsDir, "Xvfb") != null &&
            resolveGuestBinary(rootfsDir, "x11vnc") != null
    }

    fun ensureVncStartScript(context: Context, rootfsDir: File) {
        val out = File(rootfsDir, "start-desktop.sh")
        val liveOverride = File(context.filesDir, "debug/live-desktop-script")
        if (BuildConfig.DEBUG && liveOverride.isFile && out.isFile) {
            out.setExecutable(true, false)
            ensureGuestBinaries(context, rootfsDir)
            return
        }

        val script = context.assets.open("desktop/start-desktop-vnc.sh")
            .bufferedReader()
            .use { it.readText() }
        out.writeText(script)
        out.setExecutable(true, false)
        ensureGuestBinaries(context, rootfsDir)
    }

    private fun ensureGuestBinaries(context: Context, rootfsDir: File) {
        val assetRoot = "desktop/guest-bin"
        val xtermDest = File(rootfsDir, "usr/bin/xterm")
        if (!xtermDest.isFile || xtermDest.length() == 0L) {
            copyAssetFile(context, "$assetRoot/usr/bin/xterm", xtermDest)
            xtermDest.setExecutable(true, false)
        }
        val utempterDest = File(rootfsDir, "usr/lib/aarch64-linux-gnu/libutempter.so.0")
        if (!utempterDest.isFile || utempterDest.length() == 0L) {
            utempterDest.parentFile?.mkdirs()
            copyAssetFile(context, "$assetRoot/usr/lib/aarch64-linux-gnu/libutempter.so.0", utempterDest)
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, dest: File) {
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun liveDesktopScriptMarker(context: Context): File =
        File(context.filesDir, "debug/live-desktop-script")

    fun resolveGuestBinary(rootfsDir: File, name: String): File? {
        val candidates = listOf(
            File(rootfsDir, "usr/bin/$name"),
            File(rootfsDir, "bin/$name"),
        )
        return candidates.firstOrNull { it.isFile && it.length() > 0L }
    }

    fun repairLayout(rootfsDir: File) {
        repairRootSymlinks(rootfsDir)
        repairGuestLinkerSymlinks(rootfsDir)
        repairStartScriptShebang(rootfsDir)
    }

    private fun repairGuestLinkerSymlinks(rootfsDir: File) {
        val linkerReal = File(rootfsDir, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
        if (!linkerReal.isFile || linkerReal.length() == 0L) return
        repairSymlink(rootfsDir, "usr/lib/ld-linux-aarch64.so.1", "aarch64-linux-gnu/ld-linux-aarch64.so.1")
    }

    private fun repairRootSymlinks(rootfsDir: File) {
        val links = linkedMapOf(
            "bin" to "usr/bin",
            "lib" to "usr/lib",
            "lib64" to "usr/lib64",
            "sbin" to "usr/sbin",
        )
        links.forEach { (name, target) ->
            repairSymlink(rootfsDir, name, target)
        }
    }

    private fun repairSymlink(rootfsDir: File, name: String, target: String) {
        val path = File(rootfsDir, name)
        val targetPath = File(rootfsDir, target)
        if (!targetPath.exists()) return
        if (Files.isSymbolicLink(path.toPath())) {
            val existing = runCatching { Files.readSymbolicLink(path.toPath()).toString() }.getOrNull()
            if (existing == target) return
        }
        if (path.exists() && !path.delete()) return
        try {
            Files.createSymbolicLink(path.toPath(), Paths.get(target))
        } catch (_: Exception) {
            // Symlinks may fail on some devices; caller can use absolute guest paths.
        }
    }

    private fun repairStartScriptShebang(rootfsDir: File) {
        val startScript = File(rootfsDir, "start-desktop.sh")
        if (!startScript.isFile) return
        val bash = File(rootfsDir, "usr/bin/bash")
        if (!bash.isFile) return
        val text = startScript.readText()
        if (!text.startsWith("#!/bin/bash")) return
        startScript.writeText(text.replaceFirst("#!/bin/bash", "#!/usr/bin/bash"))
    }
}
