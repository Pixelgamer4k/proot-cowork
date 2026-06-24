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
        return File(rootfs, "usr/bin/bash").isFile &&
            (File(rootfs, "usr/bin/xfce4-session").isFile ||
                File(rootfs, "usr/bin/startxfce4").isFile)
    }
}
