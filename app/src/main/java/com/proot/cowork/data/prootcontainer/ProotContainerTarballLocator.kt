package com.proot.cowork.data.prootcontainer

import android.content.Context
import com.proot.cowork.data.rootfs.RootfsTarballLocator
import java.io.File

/** Locates proot-distro container backup tarballs for import into Cowork. */
object ProotContainerTarballLocator {

    const val DEFAULT_FILENAME = "proot-cowork-ubuntu.tar.gz"

    fun dropDirectory(context: Context): File = RootfsTarballLocator.dropDirectory(context)

    fun dropDirectoryLabel(context: Context): String = dropDirectory(context).absolutePath

    fun discover(context: Context, pathHint: String? = null): File? {
        val names = buildList {
            pathHint?.let { File(it).name }?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(DEFAULT_FILENAME)
            add("proot-cowork-rootfs.tar.gz")
        }.distinct()
        for (name in names) {
            RootfsTarballLocator.discover(context, name)?.let { return it }
        }
        return RootfsTarballLocator.discoverAnyTarball(context, "proot-cowork")
    }

    fun isReadableTarball(file: File): Boolean = RootfsTarballLocator.isReadableTarball(file)
}
