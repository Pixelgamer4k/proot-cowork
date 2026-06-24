package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File

/** Installs cowork helper scripts from APK assets into the Termux prefix. */
object CoworkAssetInstaller {

    private const val TAG = "CoworkAssetInstaller"
    private const val MARKER = ".cowork_assets_v4"

    private val SCRIPTS = listOf(
        "proot-xfce-install.sh",
        "proot-xfce-start.sh",
    )

    fun installIfNeeded(context: Context, prefix: File) {
        val marker = File(prefix, MARKER)
        if (marker.isFile) return

        val shareDir = File(prefix, "share/cowork").also { it.mkdirs() }
        val binDir = File(prefix, "bin").also { it.mkdirs() }

        SCRIPTS.forEach { name ->
            val dest = File(shareDir, name)
            try {
                context.assets.open("cowork/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                chmodExecutable(dest)
                linkOrCopy(binDir, name.removeSuffix(".sh"), dest)
            } catch (e: Exception) {
                Log.e(TAG, "failed to install $name", e)
            }
        }

        installXfcePerfConfig(context, shareDir)
        marker.createNewFile()
        Log.i(TAG, "installed proot XFCE scripts under ${shareDir.absolutePath}")
    }

    private fun installXfcePerfConfig(context: Context, shareDir: File) {
        val dest = File(shareDir, "xfce-performance/xfwm4.xml")
        dest.parentFile?.mkdirs()
        try {
            context.assets.open("cowork/xfce-performance/xfwm4.xml").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "xfce performance config missing: ${e.message}")
        }
    }

    private fun linkOrCopy(binDir: File, linkName: String, script: File) {
        val link = File(binDir, linkName)
        if (link.exists()) link.delete()
        try {
            Os.symlink(script.absolutePath, link.absolutePath)
        } catch (_: Exception) {
            script.copyTo(link, overwrite = true)
            chmodExecutable(link)
        }
    }

    private fun chmodExecutable(file: File) {
        try {
            Os.chmod(
                file.absolutePath,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR,
            )
        } catch (_: Exception) {
            file.setExecutable(true, false)
        }
    }
}
