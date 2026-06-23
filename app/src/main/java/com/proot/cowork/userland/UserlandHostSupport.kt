package com.proot.cowork.userland

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Installs patched host-side UserLAnd scripts and materializes proot/busybox/loader
 * as real files in [UserlandFiles.supportDir]. Symlinks into the APK lib dir can fail
 * to execute on Android 10+ (especially Android 14–16); copies + linker64 launch fix that.
 */
object UserlandHostSupport {
    private const val ASSET_PREFIX = "userland/host-support"

    fun install(context: Context, files: UserlandFiles) {
        materializeHostBinary(files, "busybox", "lib_busybox.so")
        materializeHostBinary(files, "proot", prootLibName())
        materializeHostBinary(files, "loader", loaderLibName())
        materializeHostBinary(files, "loader32", loader32LibName())
        installAssetScript(context, files.supportDir, "execInProot.sh")
    }

    private fun prootLibName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "lib_proot.a10.so" else "lib_proot.so"

    private fun loaderLibName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "lib_loader.a10.so" else "lib_loader.so"

    private fun loader32LibName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "lib_loader32.a10.so" else "lib_loader32.so"

    private fun materializeHostBinary(files: UserlandFiles, destName: String, libFileName: String) {
        val source = File(files.libDir, libFileName)
        if (!source.isFile || source.length() <= 0L) return
        val dest = File(files.supportDir, destName)
        if (dest.exists()) dest.delete()
        source.copyTo(dest, overwrite = true)
        dest.setReadable(true, false)
        dest.setWritable(false, false)
        dest.setExecutable(true, false)
    }

    private fun installAssetScript(context: Context, supportDir: File, name: String) {
        val dest = File(supportDir, name)
        context.assets.open("$ASSET_PREFIX/$name").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setReadable(true, false)
        dest.setExecutable(true, false)
    }
}
