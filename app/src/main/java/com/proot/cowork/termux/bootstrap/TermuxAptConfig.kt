package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Apt path tweaks for embedded Termux. Do not override Dir::Etc — that breaks sources.list
 * parsing (apt treats etc/apt as apt.conf.d and ignores sources.list).
 */
object TermuxAptConfig {

    private const val TAG = "TermuxAptConfig"

    fun applyIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_apt_config_v3")
        if (marker.isFile) return

        File(prefix, ".termux_apt_config_v2").delete()
        File(prefix, ".termux_apt_config_v1").delete()
        // Remove broken v1 config that overrode Dir::Etc.
        File(prefix, "etc/apt/apt.conf.d/99-cowork-paths").delete()

        val cacheRoot = context.cacheDir.absolutePath
        val prefixPath = prefix.absolutePath
        val confDir = File(prefix, "etc/apt/apt.conf.d").also { it.mkdirs() }

        File(confDir, "99-cowork-cache").writeText(
            """
            |Dir::Cache "$cacheRoot/apt";
            |Dir::Cache::archives "$cacheRoot/apt/archives";
            """.trimMargin(),
        )

        File(confDir, "99-cowork-dirs").writeText(
            """
            |Dir::Etc::parts "$prefixPath/etc/apt/apt.conf.d";
            |Dir::Etc::sourcelist "$prefixPath/etc/apt/sources.list";
            |Dir::Etc::sourceparts "$prefixPath/etc/apt/sources.list.d";
            |Dir::State::tmpdir "$prefixPath/tmp";
            """.trimMargin(),
        )

        ensureSourcesList(prefix)
        File(confDir, "DirectoryExists").createNewFile()
        File(prefix, "var/log/apt").mkdirs()
        File(cacheRoot, "apt/archives").mkdirs()
        marker.createNewFile()
        Log.i(TAG, "wrote apt config (dirs + cache), cache=$cacheRoot/apt")
    }

    private fun ensureSourcesList(prefix: File) {
        val sources = File(prefix, "etc/apt/sources.list")
        if (sources.isDirectory) {
            sources.deleteRecursively()
        }
        if (sources.isFile) return
        sources.parentFile?.mkdirs()
        sources.writeText(
            """
            |# Proot-Cowork default mirror
            |deb https://packages-cf.termux.dev/apt/termux-main/ stable main
            """.trimMargin(),
        )
    }
}
