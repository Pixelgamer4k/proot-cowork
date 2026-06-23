package com.proot.cowork.data.rootfs

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream

/**
 * Finds rootfs tarballs in locations the app can read without Storage Access Framework grants.
 *
 * Scoped storage blocks most direct reads under /sdcard; adb and users should drop files into
 * [Context.getExternalFilesDir] (e.g. Android/data/&lt;package&gt;/files/).
 */
object RootfsTarballLocator {

    const val DEFAULT_FILENAME = "proot-cowork-rootfs.tar.gz"

    /** Preferred directory for adb push / manual file drops (no SAF needed). */
    fun dropDirectory(context: Context): File =
        appExternalDataDirs(context).firstOrNull()
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir

    fun dropDirectoryLabel(context: Context): String = dropDirectory(context).absolutePath

    /**
     * @param pathHint optional absolute path or filename; basename is also searched in app dirs.
     */
    fun discover(context: Context, pathHint: String? = null): File? {
        val name = pathHint?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: DEFAULT_FILENAME
        val candidates = linkedSetOf<File>()

        if (!pathHint.isNullOrBlank()) {
            candidates += pathAliases(pathHint)
        }

        for (dir in appExternalDataDirs(context)) {
            candidates += File(dir, name)
        }
        candidates += File(context.filesDir, name)
        context.cacheDir?.let { candidates += File(it, name) }

        if (pathHint.isNullOrBlank()) {
            candidates += pathAliases("/sdcard/$DEFAULT_FILENAME")
        }

        return candidates.firstOrNull { isReadableTarball(it) }
    }

    /** App-scoped external storage paths (work even when [Context.getExternalFilesDir] is null). */
    private fun appExternalDataDirs(context: Context): List<File> {
        val pkg = context.packageName
        val dirs = linkedSetOf<File>()
        context.getExternalFilesDir(null)?.let { dirs += it }
        dirs += File("/storage/emulated/0/Android/data/$pkg/files")
        dirs += File("/sdcard/Android/data/$pkg/files")
        return dirs.toList()
    }

    fun isReadableTarball(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return try {
            FileInputStream(file).use { input ->
                input.read() >= 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun pathAliases(path: String): List<File> = buildList {
        add(File(path))
        if (path.startsWith("/sdcard/")) {
            add(File(path.replace("/sdcard/", "/storage/emulated/0/")))
        }
        val legacyRoot = Environment.getExternalStorageDirectory()
        if (legacyRoot != null && path.startsWith("${legacyRoot.absolutePath}/")) {
            add(File(path))
        }
    }
}
