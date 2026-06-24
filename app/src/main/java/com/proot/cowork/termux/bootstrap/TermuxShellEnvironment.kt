package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.os.Build
import com.proot.cowork.BuildConfig
import java.io.File

/** Termux-compatible environment (aligned with termux-app TermuxShellEnvironment). */
object TermuxShellEnvironment {

    fun build(context: Context): Array<String> =
        buildProcessEnvironment(context).map { (k, v) -> "$k=$v" }.toTypedArray()

    fun buildProcessEnvironment(context: Context): Map<String, String> {
        val filesDir = context.filesDir.absolutePath
        val dataDir = File(filesDir).parentFile?.absolutePath ?: filesDir
        val prefix = TermuxBootstrap.prefixDir(context).absolutePath
        val home = TermuxBootstrap.homeDir(context).absolutePath
        val tmp = File(prefix, "tmp").absolutePath
        val cacheDir = context.cacheDir.absolutePath

        val env = linkedMapOf(
            "HOME" to home,
            "TERMUX__HOME" to home,
            "PREFIX" to prefix,
            "TERMUX__PREFIX" to prefix,
            "TERMUX__ROOTFS" to filesDir,
            "TERMUX__ROOTFS_DIR" to filesDir,
            "TERMUX__CACHE_DIR" to cacheDir,
            "TERMUX_APP__DATA_DIR" to dataDir,
            "TERMUX_APP__FILES_DIR" to filesDir,
            "PATH" to prefix + "/bin",
            "TMPDIR" to tmp,
            "PWD" to home,
            "DISPLAY" to ":0",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "en_US.UTF-8",
            "TERMUX_VERSION" to BuildConfig.VERSION_NAME,
            "TERMUX_APP__PACKAGE_MANAGER" to "apt",
            "TERMUX_PACKAGE_MANAGER" to "apt",
            "TERMUX_PACKAGE_ARCH" to "aarch64",
            "TERMUX_APP__PACKAGE_NAME" to context.packageName,
            "ANDROID__BUILD_VERSION_SDK" to Build.VERSION.SDK_INT.toString(),
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            env["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
        }

        TermuxExecSetup.ldPreloadPath(TermuxBootstrap.prefixDir(context))?.let { preload ->
            env["LD_PRELOAD"] = preload
        }

        listOf(
            "BOOTCLASSPATH",
            "ANDROID_ROOT",
            "ANDROID_DATA",
            "EXTERNAL_STORAGE",
            "ANDROID_ART_ROOT",
            "DEX2OATBOOTCLASSPATH",
            "ANDROID_I18N_ROOT",
            "ANDROID_RUNTIME_ROOT",
            "ANDROID_TZDATA_ROOT",
        ).forEach { name ->
            System.getenv(name)?.let { env[name] = it }
        }

        return env
    }
}
