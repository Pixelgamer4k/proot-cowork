package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

/** Writes apt Dir::* overrides and ensures marker files apt expects. */
object TermuxAptConfig {

    private const val TAG = "TermuxAptConfig"

    fun applyIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, ".termux_apt_config_v1")
        if (marker.isFile) return

        val prefixPath = prefix.absolutePath
        val cacheRoot = context.cacheDir.absolutePath
        val confDir = File(prefix, "etc/apt/apt.conf.d").also { it.mkdirs() }

        File(confDir, "99-cowork-paths").writeText(
            """
            |Dir "$prefixPath/";
            |Dir::Bin::Methods "$prefixPath/lib/apt/methods";
            |Dir::Etc "$prefixPath/etc/apt";
            |Dir::State "$prefixPath/var/lib/apt";
            |Dir::State::status "$prefixPath/var/lib/dpkg/status";
            |Dir::Cache "$cacheRoot/apt";
            |Dir::Cache::archives "$cacheRoot/apt/archives";
            |Dir::Log "$prefixPath/var/log/apt";
            """.trimMargin(),
        )

        File(confDir, "DirectoryExists").createNewFile()
        File(prefix, "var/log/apt").mkdirs()
        marker.createNewFile()
        Log.i(TAG, "wrote apt path config under $prefixPath")
    }
}
