package com.proot.cowork.data.rootfs

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
        return File(rootfsDir, "usr/bin/bash").isFile
    }

    fun repairLayout(rootfsDir: File) {
        repairRootSymlinks(rootfsDir)
        repairXkbSymlink(rootfsDir)
        repairStartScriptShebang(rootfsDir)
    }

    fun resolveXkbConfigRoot(rootfsDir: File): File? {
        repairXkbSymlink(rootfsDir)
        val candidates = listOf(
            File(rootfsDir, "usr/share/X11/xkb"),
            File(rootfsDir, "usr/share/xkeyboard-config-2"),
            File(rootfsDir, "etc/X11/xkb"),
        )
        return candidates.firstOrNull { dir ->
            dir.isDirectory && !dir.list().isNullOrEmpty()
        }
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

    private fun repairXkbSymlink(rootfsDir: File) {
        val xkbPath = File(rootfsDir, "usr/share/X11/xkb")
        val xkbData = File(rootfsDir, "usr/share/xkeyboard-config-2")
        if (!xkbData.isDirectory || xkbPath.isDirectory) return
        if (xkbPath.exists() && !xkbPath.delete()) return
        val parent = xkbPath.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) return
        try {
            Files.createSymbolicLink(
                xkbPath.toPath(),
                Paths.get("../xkeyboard-config-2"),
            )
        } catch (_: Exception) {
            // X11 can still use XKB_CONFIG_ROOT pointing at xkeyboard-config-2.
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
