package com.proot.cowork.data.prootcontainer

import android.content.Context
import com.proot.cowork.termux.bootstrap.TermuxLayout
import java.io.File

object ProotContainerValidator {

    const val DEFAULT_DISTRO = "ubuntu"

    fun containerDir(context: Context, distro: String = DEFAULT_DISTRO): File =
        File(TermuxLayout.prefixDir(context), "var/lib/proot-distro/containers/$distro")

    fun rootfsDir(context: Context, distro: String = DEFAULT_DISTRO): File =
        File(containerDir(context, distro), "rootfs")

    fun isInstalled(context: Context, distro: String = DEFAULT_DISTRO): Boolean {
        val rootfs = rootfsDir(context, distro)
        if (!File(rootfs, "usr/bin/bash").isFile) return false
        if (!File(rootfs, "usr/bin/xfce4-session").isFile &&
            !File(rootfs, "usr/bin/startxfce4").isFile
        ) {
            return false
        }
        // Reject partial imports that copied only early paths before a crash.
        if (!File(rootfs, "etc/os-release").isFile) return false
        if (!File(rootfs, "usr/lib").isDirectory) return false
        return true
    }
}
