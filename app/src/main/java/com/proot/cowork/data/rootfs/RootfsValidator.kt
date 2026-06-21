package com.proot.cowork.data.rootfs

import java.io.File

object RootfsValidator {
    fun isValid(rootfsDir: File): Boolean {
        if (!rootfsDir.isDirectory) return false
        val startScript = File(rootfsDir, "start-desktop.sh")
        if (!startScript.isFile || !startScript.canExecute()) return false
        val bash = File(rootfsDir, "bin/bash")
        return bash.isFile || File(rootfsDir, "usr/bin/bash").isFile
    }
}
