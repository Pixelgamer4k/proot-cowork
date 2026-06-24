package com.proot.cowork.termux.bootstrap

import android.content.Context
import java.io.File

/**
 * Termux rootfs layout inside the app files directory.
 * Official `.deb` packages install under `data/data/com.termux/files/...` relative to rootfs.
 */
object TermuxLayout {

    private const val DEB_ROOT = "data/data/com.termux/files"

    fun rootfsDir(context: Context): File = context.filesDir

    fun debPrefixDir(context: Context): File = File(context.filesDir, "$DEB_ROOT/usr")

    fun debHomeDir(context: Context): File = File(context.filesDir, "$DEB_ROOT/home")

    fun legacyPrefixDir(context: Context): File = File(context.filesDir, "usr")

    fun legacyHomeDir(context: Context): File = File(context.filesDir, "home")

    /** Active prefix: nested deb layout after migration, else legacy bootstrap `files/usr`. */
    fun prefixDir(context: Context): File {
        migrateLegacyLayoutIfNeeded(context)
        val nested = debPrefixDir(context)
        if (File(nested, ".extraction_complete").isFile || File(nested, "bin/sh").isFile) {
            return nested
        }
        return legacyPrefixDir(context)
    }

    fun homeDir(context: Context): File {
        migrateLegacyLayoutIfNeeded(context)
        val nested = debHomeDir(context)
        if (nested.isDirectory) return nested
        return legacyHomeDir(context)
    }

    private fun migrateLegacyLayoutIfNeeded(context: Context) {
        val marker = File(context.filesDir, ".termux_layout_migrated_v1")
        if (marker.isFile) return

        val legacy = legacyPrefixDir(context)
        val nested = debPrefixDir(context)
        if (!File(legacy, ".extraction_complete").isFile) {
            marker.createNewFile()
            return
        }
        if (File(nested, ".extraction_complete").isFile) {
            marker.createNewFile()
            return
        }

        nested.parentFile?.mkdirs()
        if (!legacy.renameTo(nested)) {
            return
        }
        val legacyHome = legacyHomeDir(context)
        if (legacyHome.isDirectory) {
            debHomeDir(context).parentFile?.mkdirs()
            legacyHome.renameTo(debHomeDir(context))
        }
        marker.createNewFile()
    }
}
