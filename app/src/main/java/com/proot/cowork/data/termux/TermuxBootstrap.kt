package com.proot.cowork.data.termux

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Installs the official Termux bootstrap into app-private storage (files/usr).
 * Same layout as com.termux — enables pkg/proot-distro tooling inside the app.
 */
object TermuxBootstrap {
    private const val TAG = "TermuxBootstrap"
    private const val ASSET_NAME = "termux/bootstrap-aarch64.zip"
    private const val STAMP = "bootstrap.stamp"

    fun prefixDir(context: Context): File = File(context.filesDir, "usr")

    fun isInstalled(context: Context): Boolean {
        val proot = File(prefixDir(context), "bin/proot")
        return proot.isFile && proot.length() > 0L
    }

    fun ensureInstalled(context: Context) {
        if (isInstalled(context)) return
        val assetList = context.assets.list("termux") ?: emptyArray()
        if (ASSET_NAME.substringAfter("termux/") !in assetList) {
            Log.w(TAG, "No $ASSET_NAME in assets — run scripts/fetch-termux-bootstrap.sh")
            return
        }
        val stamp = File(context.filesDir, STAMP)
        val prefix = prefixDir(context)
        Log.i(TAG, "Extracting Termux bootstrap to ${prefix.absolutePath}")
        prefix.mkdirs()
        context.assets.open(ASSET_NAME).use { raw ->
            ZipArchiveInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextZipEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.isNotBlank()) {
                        val out = File(prefix, entry.name)
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                        if (entry.name.startsWith("bin/") || entry.name.startsWith("libexec/")) {
                            out.setExecutable(true, false)
                        }
                    }
                    entry = zip.nextZipEntry
                }
            }
        }
        stamp.writeText("ok")
        Log.i(TAG, "Termux bootstrap installed")
    }

    fun termuxEnv(context: Context): Map<String, String> {
        val dataDir = context.applicationInfo.dataDir
        val prefix = prefixDir(context).absolutePath
        return mapOf(
            "TERMUX_APP__DATA_DIR" to dataDir,
            "TERMUX_APP__LEGACY_DATA_DIR" to "/data/data/${context.packageName}",
            "TERMUX_APP__PREFIX" to prefix,
            "TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE" to "enable",
            "PREFIX" to prefix,
        )
    }
}
