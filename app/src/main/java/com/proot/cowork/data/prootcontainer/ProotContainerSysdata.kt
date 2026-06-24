package com.proot.cowork.data.prootcontainer

import android.content.Context
import com.proot.cowork.termux.bootstrap.TermuxLayout
import java.io.File

/** Installs proot-distro sysdata stubs required for container login. */
object ProotContainerSysdata {

    private val FILES = listOf(
        "loadavg",
        "stat",
        "uptime",
        "version",
        "vmstat",
        "sysctl_entry_cap_last_cap",
        "sysctl_inotify_max_user_watches",
        "sysctl_kernel_overflowuid",
        "sysctl_kernel_overflowgid",
    )

    fun installIfNeeded(context: Context, containerDir: File) {
        val sysdata = File(containerDir, "sysdata").also { it.mkdirs() }
        File(sysdata, "sys_empty").mkdirs()
        FILES.forEach { name ->
            val dest = File(sysdata, name)
            if (dest.isFile) return@forEach
            try {
                context.assets.open("cowork/ubuntu-sysdata/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // Optional; proot-distro may recreate on login.
            }
        }
    }

    fun installIfNeeded(context: Context, distro: String = ProotContainerValidator.DEFAULT_DISTRO) {
        installIfNeeded(context, ProotContainerValidator.containerDir(context, distro))
    }
}
