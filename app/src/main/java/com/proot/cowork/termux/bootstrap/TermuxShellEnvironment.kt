package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.os.Build
import com.proot.cowork.BuildConfig
import java.io.File

/** Termux-compatible environment for subprocesses (matches termux-app essentials). */
object TermuxShellEnvironment {

    fun build(context: Context): Array<String> {
        val filesDir = context.filesDir.absolutePath
        val prefix = TermuxBootstrap.prefixDir(context).absolutePath
        val home = TermuxBootstrap.homeDir(context).absolutePath
        val tmp = File(prefix, "tmp").absolutePath
        val lib = File(prefix, "lib").absolutePath

        val env = mutableListOf(
            "HOME=$home",
            "TERMUX__HOME=$home",
            "PREFIX=$prefix",
            "TERMUX__PREFIX=$prefix",
            "TERMUX__ROOTFS_DIR=$filesDir",
            "PATH=$prefix/bin",
            "LD_LIBRARY_PATH=$lib",
            "TMPDIR=$tmp",
            "PROOT_TMP_DIR=${File(prefix, "var/tmp").absolutePath}",
            "PROOT_LOADER=${File(prefix, "libexec/proot/loader").absolutePath}",
            "PWD=$home",
            "DISPLAY=:0",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=en_US.UTF-8",
            "TERMUX_VERSION=${BuildConfig.VERSION_NAME}",
            "TERMUX_APP_PACKAGE_MANAGER=apt",
            "TERMUX_PACKAGE_MANAGER=apt",
            "TERMUX_PACKAGE_ARCH=aarch64",
            "TERMUX_APP__PACKAGE_NAME=${context.packageName}",
            "ANDROID__BUILD_VERSION_SDK=${Build.VERSION.SDK_INT}",
        )

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
            System.getenv(name)?.let { env += "$name=$it" }
        }

        return env.toTypedArray()
    }
}
