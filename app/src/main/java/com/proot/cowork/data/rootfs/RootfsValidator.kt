package com.proot.cowork.data.rootfs

import android.content.Context
import com.proot.cowork.userland.UserlandGuestSupport
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object RootfsValidator {
    fun isValid(rootfsDir: File): Boolean {
        if (!rootfsDir.isDirectory) return false
        repairLayout(rootfsDir)
        val bash = File(rootfsDir, "usr/bin/bash")
        if (!bash.isFile || bash.length() == 0L) return false

        val binLink = File(rootfsDir, "bin")
        if (!binLink.exists()) return false

        return true
    }

    fun hasVncStack(rootfsDir: File): Boolean {
        repairLayout(rootfsDir)
        return resolveGuestBinary(rootfsDir, "tightvncserver") != null ||
            resolveGuestBinary(rootfsDir, "Xtightvnc") != null ||
            resolveGuestBinary(rootfsDir, "vncserver") != null
    }

    fun hasXfceStack(rootfsDir: File): Boolean {
        repairLayout(rootfsDir)
        return resolveGuestBinary(rootfsDir, "startxfce4") != null ||
            resolveGuestBinary(rootfsDir, "xfce4-session") != null
    }

    fun prepareUserlandGuest(context: Context, rootfsDir: File) {
        UserlandGuestSupport.install(context, rootfsDir)
    }

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
            // Symlinks may fail on some devices.
        }
    }
}
