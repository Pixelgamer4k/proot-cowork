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
        val name = pathHint?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: DEFAULT_FILENAME
        return RootfsTarballLocator.discover(context, pathHint ?: name)
            ?: RootfsTarballLocator.discover(context, DEFAULT_FILENAME)
    }

    fun isReadableTarball(file: File): Boolean = RootfsTarballLocator.isReadableTarball(file)
}
