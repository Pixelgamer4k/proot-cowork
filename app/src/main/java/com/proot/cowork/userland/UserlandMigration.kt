package com.proot.cowork.userland

import android.content.Context
import java.io.File

object UserlandMigration {
    fun migrateRootfsLayout(filesDir: File) {
        val legacy = File(filesDir, "rootfs")
        val target = File(filesDir, UserlandConfig.FILESYSTEM_DIR)
        if (legacy.isDirectory && !target.exists()) {
            legacy.renameTo(target)
        }
    }
}
