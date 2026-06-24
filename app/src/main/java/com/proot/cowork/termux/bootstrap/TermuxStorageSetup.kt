package com.proot.cowork.termux.bootstrap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

object TermuxStorageSetup {

    private const val TAG = "TermuxStorageSetup"

    private val STORAGE_LINKS = mapOf(
        "shared" to "/storage/emulated/0",
        "downloads" to "/storage/emulated/0/Download",
        "dcim" to "/storage/emulated/0/DCIM",
        "pictures" to "/storage/emulated/0/Pictures",
        "music" to "/storage/emulated/0/Music",
        "movies" to "/storage/emulated/0/Movies",
        "documents" to "/storage/emulated/0/Documents",
    )

    fun hasStorageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureStorageLinks(context: Context) {
        if (!hasStorageAccess(context)) {
            Log.w(TAG, "storage permission not granted; skipping ~/storage symlinks")
            return
        }
        val storageDir = File(TermuxBootstrap.homeDir(context), "storage").also { it.mkdirs() }
        STORAGE_LINKS.forEach { (name, target) ->
            val link = File(storageDir, name)
            if (link.exists()) return@forEach
            try {
                Os.symlink(target, link.absolutePath)
            } catch (e: ErrnoException) {
                Log.w(TAG, "symlink $name -> $target failed", e)
            }
        }
    }

    fun patchSetupStorageScript(prefix: File) {
        val script = File(prefix, "bin/termux-setup-storage")
        if (!script.isFile) return
        var content = script.readText()
        if (content.contains("COWORK_STORAGE_SETUP")) return

        content = content.replace(
            Regex("""am broadcast[\s\S]*?"com\.termux"\s*>\s*/dev/null"""),
            "# storage broadcast removed for embedded app",
        )

        val homePath = File(prefix.parentFile, "home").absolutePath
        val append = """

# COWORK_STORAGE_SETUP — embedded Proot-Cowork (no com.termux app broadcast)
_storage_root="$homePath/storage"
mkdir -p "$_storage_root"
for _pair in \\
	"shared:/storage/emulated/0" \\
	"downloads:/storage/emulated/0/Download" \\
	"dcim:/storage/emulated/0/DCIM" \\
	"pictures:/storage/emulated/0/Pictures" \\
	"music:/storage/emulated/0/Music" \\
	"movies:/storage/emulated/0/Movies" \\
	"documents:/storage/emulated/0/Documents"; do
	_name="${'$'}{_pair%%:*}"
	_target="${'$'}{_pair#*:}"
	_link="$_storage_root/$_name"
	[ -e "$_link" ] || ln -sf "$_target" "$_link"
done
echo "Storage symlinks ready under ~/storage."
exit 0
""".trimIndent()

        script.writeText(content.trimEnd() + "\n" + append + "\n")
    }
}
