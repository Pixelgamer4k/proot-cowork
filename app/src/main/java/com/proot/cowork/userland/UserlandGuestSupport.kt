package com.proot.cowork.userland

import android.content.Context
import java.io.File

object UserlandGuestSupport {
    private const val ASSET_PREFIX = "userland/guest-support"

    fun install(context: Context, rootfsDir: File) {
        val supportDir = File(rootfsDir, "support").also { it.mkdirs() }
        val assets = context.assets.list(ASSET_PREFIX).orEmpty()
        for (name in assets) {
            val dest = File(supportDir, name)
            context.assets.open("$ASSET_PREFIX/$name").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (name.endsWith(".sh")) {
                dest.setExecutable(true, false)
            }
        }

        val prootVersion = File(supportDir, ".proot_version")
        if (!prootVersion.exists()) {
            // Match UserLAnd proot_meta_leveldb when leveldb build is bundled in host support.
            val useLevelDb = File(context.filesDir, "support/proot_meta_leveldb").exists()
            prootVersion.writeText(if (useLevelDb) "_meta_leveldb" else "")
        }

        val extractionMarker = File(supportDir, ".success_filesystem_extraction")
        if (!extractionMarker.exists()) {
            extractionMarker.writeText("cowork\n")
        }
    }
}
