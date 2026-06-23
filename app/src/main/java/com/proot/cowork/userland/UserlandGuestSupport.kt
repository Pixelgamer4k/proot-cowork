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
        // Match execInProot.sh: plain proot unless guest was extracted with support/meta_db.
        val hasMetaDb = File(supportDir, "meta_db").isDirectory
        prootVersion.writeText(if (hasMetaDb) "_meta_leveldb" else "")

        val extractionMarker = File(supportDir, ".success_filesystem_extraction")
        if (!extractionMarker.exists()) {
            extractionMarker.writeText("cowork\n")
        }
    }
}
