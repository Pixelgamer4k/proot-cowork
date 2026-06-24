package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.util.Log
import java.io.File

/**
 * Repairs bootstrap symlinks that still target `/data/data/com.termux/files/...`
 * (e.g. `etc/apt/trusted.gpg.d/*.gpg` → termux-keyring). Text patching does not
 * touch symlink targets.
 */
object TermuxSymlinkFix {

    private const val TAG = "TermuxSymlinkFix"
    private const val LEGACY_ROOT = "/data/data/com.termux/files"
    private const val LEGACY_ROOT_USER = "/data/user/0/com.termux/files"
    private const val LEGACY_CACHE = "/data/data/com.termux/cache"
    private const val LEGACY_CACHE_USER = "/data/user/0/com.termux/cache"

    fun repairIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_symlinks_fixed_v1")
        if (marker.isFile) return

        val filesRoot = context.filesDir.absolutePath
        val cacheRoot = context.cacheDir.absolutePath
        var fixed = 0
        prefix.walkTopDown().forEach { file ->
            if (!isSymlink(file)) return@forEach
            val target = readLink(file) ?: return@forEach
            val rewritten = rewriteTarget(target, filesRoot, cacheRoot)
            if (rewritten == target) return@forEach
            try {
                file.delete()
                Os.symlink(rewritten, file.absolutePath)
                fixed++
                Log.i(TAG, "${file.relativeTo(prefix)} -> $rewritten")
            } catch (e: Exception) {
                Log.w(TAG, "failed ${file.absolutePath}: ${e.message}")
            }
        }
        marker.createNewFile()
        Log.i(TAG, "repaired $fixed symlinks under ${prefix.absolutePath}")
    }

    private fun rewriteTarget(target: String, filesRoot: String, cacheRoot: String): String =
        target
            .replace(LEGACY_ROOT, filesRoot)
            .replace(LEGACY_ROOT_USER, filesRoot)
            .replace(LEGACY_CACHE, cacheRoot)
            .replace(LEGACY_CACHE_USER, cacheRoot)

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat: StructStat = Os.lstat(file.absolutePath)
            (stat.st_mode and OsConstants.S_IFLNK.toInt()) != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun readLink(file: File): String? {
        return try {
            Os.readlink(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}
