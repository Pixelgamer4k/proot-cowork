package com.proot.cowork.data.proot

import android.content.Context
import android.os.Build
import java.io.File

class RuntimeBootstrap(private val context: Context) {

    fun ensureRuntime(): ProotRuntime {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val prootBin = File(nativeLibDir, PROOT_LIB_NAME)
        if (!prootBin.isFile) {
            throw IllegalStateException(
                "Missing $PROOT_LIB_NAME in nativeLibraryDir (${nativeLibDir.absolutePath}). " +
                    "Rebuild the APK with jniLibs bundled.",
            )
        }

        val supportLibDir = File(context.filesDir, "exec_libs").also { it.mkdirs() }
        ensureVersionedTalloc(nativeLibDir, supportLibDir)

        val ldLibraryPath = listOf(nativeLibDir, supportLibDir)
            .joinToString(":") { it.absolutePath }

        return ProotRuntime(
            prootBinary = prootBin,
            ldLibraryPath = ldLibraryPath,
            tmpDir = File(context.filesDir, "tmp").also { it.mkdirs() },
            useLinker64 = is64BitAbi(),
        )
    }

    /**
     * proot links against libtalloc.so.2, but AGP only packages lib*.so (not lib*.so.N)
     * into the APK. Copy the soname beside the extracted native libs at runtime.
     */
    private fun ensureVersionedTalloc(nativeLibDir: File, supportLibDir: File) {
        val tallocV2 = File(supportLibDir, "libtalloc.so.2")
        if (tallocV2.isFile) return

        val sources = listOf(
            File(nativeLibDir, "libtalloc.so"),
            File(nativeLibDir, "libtalloc.so.2"),
        )
        val source = sources.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Missing libtalloc.so in ${nativeLibDir.absolutePath}")

        source.copyTo(tallocV2, overwrite = true)
        tallocV2.setReadable(true, false)
    }

    private fun is64BitAbi(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return abi.contains("64") || abi == "arm64-v8a" || abi == "x86_64"
    }

    companion object {
        const val PROOT_LIB_NAME = "libproot_exec.so"
    }
}

data class ProotRuntime(
    val prootBinary: File,
    val ldLibraryPath: String,
    val tmpDir: File,
    val useLinker64: Boolean,
) {
    fun launchCommand(prootArgs: List<String>): List<String> {
        val linker = if (useLinker64) "/system/bin/linker64" else "/system/bin/linker"
        return listOf(linker, prootBinary.absolutePath) + prootArgs
    }
}
